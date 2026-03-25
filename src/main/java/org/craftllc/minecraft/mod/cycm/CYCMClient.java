package org.craftllc.minecraft.mod.cycm;

import org.craftllc.minecraft.mod.cycm.config.ModConfigManager;
import org.craftllc.minecraft.mod.cycm.config.ModConfig.ChatMode;
import org.craftllc.minecraft.mod.cycm.http.HttpChatServer;
import org.craftllc.minecraft.mod.cycm.telegram.TelegramClient;
import org.craftllc.minecraft.mod.cycm.youtube.LiveStateManager;
import org.craftllc.minecraft.mod.cycm.youtube.YouTubeClient;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.net.URI;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class CYCMClient implements ClientModInitializer {
    private static final Path MOD_CFG_DIR = FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID);
    private static final Path LEGACY_BLOCKED_FILE = MOD_CFG_DIR.resolve("blocked_commands.txt");
    private static final Path LEGACY_REPEATING_FILE = MOD_CFG_DIR.resolve("repeating_settings.txt");
    private static final Path CMD_LOG_FILE = MOD_CFG_DIR.resolve("commands_log.txt");
    private static final Path CHAT_LOG_FILE = MOD_CFG_DIR.resolve("chat_log.txt");
    private static final Pattern REPEAT_SUFFIX_PATTERN = Pattern.compile("^(.*?)\\s*(?<!\\\\)\\+(\\d+)(?:\\s+(\\d+))?\\s*$");
    private static final Pattern SLEEP_PATTERN = Pattern.compile("^!sleep\\s+(\\d+)\\s*$", Pattern.CASE_INSENSITIVE);

    private static ScheduledExecutorService scheduler;
    public static ModConfigManager configManager;
    private static Set<String> blockedCommands = Collections.synchronizedSet(new HashSet<>());
    private static int maxRepeats = 20;
    private static int maxDelaySeconds = 5;
    private static int maxTntCount = 20;
    private static int maxTntRadius = 8;
    private static CYCMClient instance;
    private static boolean chatSourceRunning = false;

    public CYCMClient() {
        instance = this;
    }

    public static CYCMClient getInstance() {
        return instance;
    }

    public static Set<String> getBlockedCommands() {
        return blockedCommands;
    }

    public static int getMaxRepeats() {
        return maxRepeats;
    }

    public static int getMaxDelaySeconds() {
        return maxDelaySeconds;
    }

    public static int getMaxTntCount() {
        return maxTntCount;
    }

    public static int getMaxTntRadius() {
        return maxTntRadius;
    }

    @Override
    public void onInitializeClient() {
        Constants.LOGGER.info("CYCM: Ініціалізація.");
        configManager = ModConfigManager.getInstance();
        configManager.loadConfig();
        configManager.startWatchingConfigFile();
        LiveStateManager.load();
        Constants.LOGGER.info("CYCM Мод " + (configManager.getConfig().isModEnabled() ? "увімкнено" : "вимкнено"));
        loadBlockedCommands();
        loadRepeatingSettings();
        loadTntSettings();
        registerEventHandlers();
        registerClientCommands();
    }

    private void registerEventHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                if (configManager.getConfig().isModEnabled()) {
                    startChatSource();
                    if (configManager.getConfig().isActionbarEnabled()) {
                        updateActionbar(client);
                    }
                }
            } else {
                stopChatSource();
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Constants.LOGGER.info("CYCM: Вимкнення.");
            configManager.stopWatchingConfigFile();
            stopChatSource();
        }));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                scheduler = null;
            }
            configManager.stopWatchingConfigFile();
            stopChatSource();
        });

        ClientSendMessageEvents.CHAT.register(message -> {
            if (configManager.getConfig().isYoutubeEnabled() && configManager.getConfig().isYoutubeSendEnabled()) {
                YouTubeClient.sendMessageToChat(message);
            }
        });
    }

    public static void startChatSource() {
        if (chatSourceRunning) return;
        chatSourceRunning = true;
        
        if (configManager.getConfig().isYoutubeEnabled()) {
            if (configManager.getConfig().getChatMode() == ChatMode.API) {
                HttpChatServer.stopServer();
                YouTubeClient.startPolling();
            } else {
                YouTubeClient.stopPolling();
                HttpChatServer.startServer(configManager.getConfig().getHttpMessagesPort());
            }
        } else {
            YouTubeClient.stopPolling();
            HttpChatServer.stopServer();
        }

        if (configManager.getConfig().isTelegramEnabled()) {
            TelegramClient.startPolling();
        } else {
            TelegramClient.stopPolling();
        }
    }

    public static void stopChatSource() {
        if (!chatSourceRunning) return;
        chatSourceRunning = false;
        
        YouTubeClient.stopPolling();
        HttpChatServer.stopServer();
        TelegramClient.stopPolling();
    }

    public static void setModEnabled(boolean enabled) {
        if (configManager.getConfig().isModEnabled() == enabled) {
            sendLocalizedMessage("mod_already_state",
                    (enabled ? Component.translatable("cycm.state.enabled") : Component.translatable("cycm.state.disabled")));
            return;
        }
        configManager.getConfig().setModEnabled(enabled);
        configManager.saveConfig();
        sendLocalizedMessage("mod_state",
                (enabled ? Component.translatable("cycm.state.enabled") : Component.translatable("cycm.state.disabled")));
        if (enabled && Minecraft.getInstance().player != null)
            startChatSource();
        else
            stopChatSource();
    }

    // Called from YouTubeClient thread
    public void processYouTubeMessage(String author, String message) {
        if (!configManager.getConfig().isModEnabled() || Minecraft.getInstance().player == null)
            return;

        // Log message (using original for logging or translated?)
        // Let's log original to keep logs clean of §
        logMessage(message.startsWith("/") ? CMD_LOG_FILE : CHAT_LOG_FILE, author + ": " + message);

        if (message.startsWith("/")) {
            // It's a command
            // Execute on main thread to be safe with Minecraft
            final String fMessage = message;
            Minecraft.getInstance().execute(() -> {
                procCmdLine(author, fMessage);
            });
        } else {
            // It's chat
            // Translate & codes ONLY for chat to avoid crashes in sendChatCommand
            final String fMessage = translateColorCodes(message);
            Minecraft.getInstance().execute(() -> {
                procChatLine(author, fMessage);
            });
        }
    }

    private String translateColorCodes(String msg) {
        if (msg == null)
            return null;
        return msg.replace("&cred", "§c")
                .replace("&kobfuscated", "§k")
                .replace("&fwhite", "§f")
                .replace("&rreset", "§r")
                .replace("&", "§"); // General replacement for plugins compatibility
    }

    private void logMessage(Path file, String content) {
        ensureFile(file);
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardOpenOption.APPEND)) {
            w.write(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " + content);
            w.newLine();
        } catch (IOException e) {
            Constants.LOGGER.error("Logger error", e);
        }
    }

    private void procCmdLine(String nick, String fullCmd) {
        if (!fullCmd.startsWith("/"))
            return;

        executeMulti(nick, fullCmd.substring(1), false);
    }

    private void executeMulti(String nick, String multiCmd, boolean isLocal) {
        java.util.List<String> rawSegments = splitWithEscape(multiCmd, '|');
        java.util.List<String> segments = new java.util.ArrayList<>();
        for (String s : rawSegments) {
            String t = s.trim();
            if (!t.isEmpty())
                segments.add(t);
        }

        int totalUsedReps = 0;
        long maxPossibleDuration = (long) maxRepeats * maxDelaySeconds;
        long timelineOffsetSeconds = 0L;
        int n = segments.size();

        for (int i = 0; i < n; i++) {
            String segment = segments.get(i);

            // Stop if we've reached the total repetitions limit for this message
            if (totalUsedReps >= maxRepeats)
                break;

            int reps = 1;
            int delay = 0;
            String cmdWithPotentialSlash = segment;

            // Parse for +reps [delay] suffix, ignoring escaped \+
            Matcher m = REPEAT_SUFFIX_PATTERN.matcher(segment);
            if (m.find()) {
                cmdWithPotentialSlash = m.group(1).trim();
                try {
                    // Cap individual segment reps by maxRepeats
                    reps = (int) Math.min(Long.parseLong(m.group(2)), (long) maxRepeats);
                    if (m.group(3) != null) {
                        // Cap individual segment delay by maxDelaySeconds
                        delay = (int) Math.min(Long.parseLong(m.group(3)), (long) maxDelaySeconds);
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            // Unescape the command
            String cleanCmd = unescape(cmdWithPotentialSlash);

            // Handle optional leading slash in segments
            if (cleanCmd.startsWith("/")) {
                cleanCmd = cleanCmd.substring(1).trim();
            }

            Matcher sleepMatcher = SLEEP_PATTERN.matcher(cleanCmd);
            if (sleepMatcher.matches()) {
                try {
                    timelineOffsetSeconds += Math.min(Long.parseLong(sleepMatcher.group(1)), maxPossibleDuration);
                } catch (NumberFormatException ignored) {
                }
                continue;
            }

            // Calculate fair budget for this segment with priority to the first ones
            int remainingSegs = n - i;
            int totalRemaining = maxRepeats - totalUsedReps;
            int budgetForThisOne = (totalRemaining + remainingSegs - 1) / remainingSegs;

            // Ensure at least 1 if we have budget, to allow multiple simple commands
            if (budgetForThisOne < 1 && totalRemaining > 0) {
                budgetForThisOne = 1;
            }

            // Enforce global repetition budget and fair distribution
            int allowedReps = Math.min(reps, budgetForThisOne);
            if (allowedReps <= 0)
                break;

            // Enforce duration limit per segment: reps * delay <= maxPossibleDuration
            if (delay > 0 && (long) allowedReps * delay > maxPossibleDuration) {
                allowedReps = (int) (maxPossibleDuration / delay);
            }

            if (allowedReps > 0) {
                execSingleCmdLogic(nick, cleanCmd, allowedReps, delay, timelineOffsetSeconds, isLocal);
                totalUsedReps += allowedReps;
                if (delay > 0 && allowedReps > 1) {
                    timelineOffsetSeconds += (long) (allowedReps - 1) * delay;
                }
            }
        }
    }

    private java.util.List<String> splitWithEscape(String text, char delimiter) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                current.append('\\').append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == delimiter) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private String unescape(String text) {
        StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else {
                sb.append(c);
            }
        }
        if (escaped)
            sb.append('\\');
        return sb.toString();
    }

    private void execSingleCmdLogic(String nick, String cleanCmd, int reps, int delay, long initialOffsetSeconds, boolean isLocal) {
        if (scheduler == null || scheduler.isShutdown())
            scheduler = Executors.newSingleThreadScheduledExecutor();

        String baseCmd = cleanCmd.split(" ")[0].toLowerCase();

        if (!isLocal && isModCmd(baseCmd)) {
            return;
        }

        if (isCmdBlocked(baseCmd) && !isModCmd(baseCmd)) {
            sendLocalizedMessage("cmd_blocked", Component.literal("/" + cleanCmd));
            return;
        }

        final String fCmd = cleanCmd;
        final int fReps = reps;
        final int fDel = delay;

        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().player == null)
                    return;
                if (isLocal) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.translatable("cycm.message.executing_command", Component.literal("/" + fCmd)));
                } else {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal("§f" + nick + " ")
                                    .append(Component.translatable("cycm.message.cmd_executed",
                                            Component.literal("/" + fCmd))));
                }
            });
        }

        for (int r = 0; r < fReps; r++) {
            scheduler.schedule(() -> {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().execute(() -> {
                        if (Minecraft.getInstance().player == null)
                            return;
                        if (handleSpecialCommand(fCmd)) {
                            return;
                        }
                        String finalCmd = fCmd;
                        if (configManager.getConfig().isCompatibilityMode() && !fCmd.contains(":")) {
                            finalCmd = "minecraft:" + fCmd;
                        }
                        Minecraft.getInstance().getConnection().sendCommand(finalCmd);
                    });
                }
            }, initialOffsetSeconds + (long) r * fDel, TimeUnit.SECONDS);
        }
    }

    private boolean handleSpecialCommand(String cleanCmd) {
        String[] parts = cleanCmd.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return false;
        }
        String base = parts[0].toLowerCase();
        return switch (base) {
            case "tnt" -> {
                int count = parts.length >= 2 ? parsePositiveInt(parts[1], 1) : 1;
                int radius = parts.length >= 3 ? parsePositiveInt(parts[2], 0) : 0;
                execTntRing(count, radius);
                yield true;
            }
            case "ka", "killaura" -> {
                execKillAura(20.0);
                yield true;
            }
            case "ke", "killentities" -> {
                execKillEntities();
                yield true;
            }
            case "blocklist" -> {
                dispBlockList();
                yield true;
            }
            default -> false;
        };
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int execTntRing(int count, int radius) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            sendLocalizedMessage("no_player");
            return 0;
        }
        int requestedCount = Math.max(1, count);
        int requestedRadius = Math.max(0, radius);
        int safeCount = Math.min(requestedCount, maxTntCount);
        int safeRadius = Math.min(requestedRadius, maxTntRadius);

        if (requestedCount > maxTntCount) {
            sendLocalizedMessage("tnt_count_limit_hit", Component.literal(String.valueOf(requestedCount)),
                    Component.literal(String.valueOf(maxTntCount)));
        }
        if (requestedRadius > maxTntRadius) {
            sendLocalizedMessage("tnt_radius_limit_hit", Component.literal(String.valueOf(requestedRadius)),
                    Component.literal(String.valueOf(maxTntRadius)));
        }

        double centerX = client.player.getX();
        double centerY = client.player.getY();
        double centerZ = client.player.getZ();
        if (safeCount == 1 || safeRadius <= 0) {
            for (int i = 0; i < safeCount; i++) {
                sendToSrv(String.format(Locale.ROOT, "summon minecraft:tnt %.2f %.2f %.2f", centerX, centerY, centerZ));
            }
            return 1;
        }
        for (int i = 0; i < safeCount; i++) {
            double angle = 2.0 * Math.PI * i / safeCount;
            double x = centerX + safeRadius * Math.cos(angle);
            double z = centerZ + safeRadius * Math.sin(angle);
            sendToSrv(String.format(Locale.ROOT, "summon minecraft:tnt %.2f %.2f %.2f", x, centerY, z));
        }
        return 1;
    }

    private void execCmdInGame(String fullCmd) {
        if (Minecraft.getInstance().player == null) {
            sendLocalizedMessage("no_player");
            return;
        }

        executeMulti(null, fullCmd, true);
    }

    private boolean isModCmd(String cmd) {
        return "cycm".equalsIgnoreCase(cmd) || "ce".equalsIgnoreCase(cmd);
    }

    private void procChatLine(String nick, String msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(Component.literal("<")
                    .append(Component.literal(nick).withStyle(ChatFormatting.WHITE))
                    .append("> ")
                    .append(Component.literal(msg)));
        }
    }

    public void ensureFile(Path fp) {
        try {
            if (!Files.exists(fp)) {
                Files.createDirectories(fp.getParent());
                Files.createFile(fp);
                Constants.LOGGER.info("Створено {}.", fp.getFileName());
            }
        } catch (IOException e) {
            Constants.LOGGER.error("Помилка створення файлу {}: {}", fp.getFileName(), e.getMessage());
        }
    }

    private void loadBlockedCommands() {
        synchronized (blockedCommands) {
            blockedCommands.clear();
            configManager.getConfig().getBlockedCommands().stream()
                    .filter(l -> l != null && !l.trim().isEmpty())
                    .map(String::trim)
                    .map(l -> l.startsWith("/") ? l.substring(1).toLowerCase() : l.toLowerCase())
                    .forEach(blockedCommands::add);
        }
        if (blockedCommands.isEmpty() && Files.exists(LEGACY_BLOCKED_FILE)) {
            try {
                synchronized (blockedCommands) {
                    Files.readAllLines(LEGACY_BLOCKED_FILE).stream()
                            .filter(l -> !l.trim().isEmpty() && !l.trim().startsWith("#"))
                            .map(String::trim)
                            .map(l -> l.startsWith("/") ? l.substring(1).toLowerCase() : l.toLowerCase())
                            .forEach(blockedCommands::add);
                }
                saveBlockedCommands();
            } catch (IOException e) {
                Constants.LOGGER.error("Legacy blocked commands migration failed: {}", e.getMessage());
            }
        }
        initBlockedCommands();
    }

    private void saveBlockedCommands() {
        List<String> snapshot;
        synchronized (blockedCommands) {
            snapshot = new ArrayList<>(blockedCommands);
        }
        configManager.getConfig().setBlockedCommands(snapshot);
        configManager.saveConfig();
    }

    public boolean isCmdBlocked(String cmd) {
        return blockedCommands.contains(cmd.split(" ")[0].toLowerCase());
    }

    private void initBlockedCommands() {
        boolean chg = false;
        Set<String> defaultBlocked = new HashSet<>(Arrays.asList(
                "op", "clear", "deop", "kill", "execute", "ban", "reload", "kick", "stop", "particle"));

        for (String cmd : defaultBlocked) {
            if (blockedCommands.add(cmd.toLowerCase())) {
                chg = true;
            }
        }
        if (blockedCommands.add("cycm")) {
            chg = true;
        }
        if (blockedCommands.add("ce")) {
            chg = true;
        }

        if (chg)
            saveBlockedCommands();
    }

    private void blockCommand(String cmd) {
        String cleanCmd = cmd.toLowerCase().startsWith("/") ? cmd.substring(1).toLowerCase() : cmd.toLowerCase();
        if (isModCmd(cleanCmd)) {
            sendLocalizedMessage("mod_cmd_cannot_be_blocked", Component.literal("/" + cleanCmd));
            return;
        }
        if (blockedCommands.add(cleanCmd)) {
            sendLocalizedMessage("cmd_blocked_success", Component.literal("/" + cleanCmd));
            saveBlockedCommands();
        } else {
            sendLocalizedMessage("cmd_already_blocked", Component.literal("/" + cleanCmd));
        }
    }

    public static void blockCommandFromUI(String cmd) {
        if (instance != null) {
            instance.blockCommand(cmd);
        }
    }

    private void unblockCommand(String cmd) {
        String cleanCmd = cmd.toLowerCase().startsWith("/") ? cmd.substring(1).toLowerCase() : cmd.toLowerCase();
        if (isModCmd(cleanCmd)) {
            sendLocalizedMessage("mod_cmd_cannot_be_unblocked", Component.literal("/" + cleanCmd));
            return;
        }
        if ("all".equalsIgnoreCase(cleanCmd)) {
            synchronized (blockedCommands) {
                blockedCommands.clear();
            }
            initBlockedCommands();
            sendLocalizedMessage("all_cmds_unblocked");
        } else if (blockedCommands.remove(cleanCmd)) {
            sendLocalizedMessage("cmd_unblocked_success", Component.literal("/" + cleanCmd));
            saveBlockedCommands();
        } else {
            sendLocalizedMessage("cmd_not_blocked", Component.literal("/" + cleanCmd));
        }
    }

    public static void unblockCommandFromUI(String cmd) {
        if (instance != null) {
            instance.unblockCommand(cmd);
        }
    }

    private void loadRepeatingSettings() {
        maxRepeats = Math.max(1, configManager.getConfig().getMaxRepeats());
        maxDelaySeconds = Math.max(0, configManager.getConfig().getMaxDelaySeconds());
        if (Files.exists(LEGACY_REPEATING_FILE) && configManager.getConfig().getMaxRepeats() == 20 && configManager.getConfig().getMaxDelaySeconds() == 5) {
            try {
                String sLine = Files.readAllLines(LEGACY_REPEATING_FILE).stream()
                        .filter(l -> !l.trim().isEmpty() && !l.trim().startsWith("#")).findFirst().orElse(null);
                if (sLine != null && sLine.split(":").length == 2) {
                    maxRepeats = Math.max(1, Integer.parseInt(sLine.split(":")[0].trim()));
                    maxDelaySeconds = Math.max(0, Integer.parseInt(sLine.split(":")[1].trim()));
                    saveRepeatingSettings();
                }
            } catch (IOException | NumberFormatException e) {
                Constants.LOGGER.error("Legacy repeating settings migration failed: {}", e.getMessage());
            }
        }
    }

    private void loadTntSettings() {
        maxTntCount = Math.max(1, configManager.getConfig().getMaxTntCount());
        maxTntRadius = Math.max(0, configManager.getConfig().getMaxTntRadius());
    }

    public static void setMaxRepeats(int num) {
        if (num <= 0) {
            sendLocalizedMessage("num_repeats_positive_warning");
            return;
        }
        if (maxRepeats == num) {
            return;
        }
        maxRepeats = num;
        saveRepeatingSettings();
        sendLocalizedMessage("repeats_set_success", Component.literal(String.valueOf(num)));
    }

    public static void setMaxDelaySeconds(int delay) {
        if (delay < 0) {
            sendLocalizedMessage("delay_positive_warning");
            return;
        }
        if (maxDelaySeconds == delay) {
            return;
        }
        maxDelaySeconds = delay;
        saveRepeatingSettings();
        sendLocalizedMessage("delay_set_success", Component.literal(String.valueOf(delay)));
    }

    private static void saveRepeatingSettings() {
        configManager.getConfig().setMaxRepeats(maxRepeats);
        configManager.getConfig().setMaxDelaySeconds(maxDelaySeconds);
        configManager.saveConfig();
    }

    public static void setMaxTntCount(int count) {
        if (count <= 0) {
            sendLocalizedMessage("tnt_count_positive_warning");
            return;
        }
        if (maxTntCount == count) {
            return;
        }
        maxTntCount = count;
        saveTntSettings();
        sendLocalizedMessage("tnt_count_set_success", Component.literal(String.valueOf(count)));
    }

    public static void setMaxTntRadius(int radius) {
        if (radius < 0) {
            sendLocalizedMessage("tnt_radius_positive_warning");
            return;
        }
        if (maxTntRadius == radius) {
            return;
        }
        maxTntRadius = radius;
        saveTntSettings();
        sendLocalizedMessage("tnt_radius_set_success", Component.literal(String.valueOf(radius)));
    }

    private static void saveTntSettings() {
        configManager.getConfig().setMaxTntCount(maxTntCount);
        configManager.getConfig().setMaxTntRadius(maxTntRadius);
        configManager.saveConfig();
    }

    private void stopScheduledCommands() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            scheduler = null;
            sendLocalizedMessage("scheduled_commands_stopped");
        } else {
            sendLocalizedMessage("no_scheduled_commands");
        }
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((disp, regAcc) -> {
            disp.register(literal("tnt")
                    .executes(ctx -> execTntRing(1, 0))
                    .then(argument("count", IntegerArgumentType.integer(1)).executes(ctx ->
                            execTntRing(IntegerArgumentType.getInteger(ctx, "count"), 0))
                            .then(argument("radius", IntegerArgumentType.integer(0)).executes(ctx ->
                                    execTntRing(IntegerArgumentType.getInteger(ctx, "count"),
                                            IntegerArgumentType.getInteger(ctx, "radius"))))));
            disp.register(literal("blocklist").executes(ctx -> {
                dispBlockList();
                return 1;
            }));
            disp.register(literal("ka").executes(ctx -> execKillAura(20.0)));
            disp.register(literal("killaura").executes(ctx -> execKillAura(20.0)));
            disp.register(literal("ke").executes(ctx -> execKillEntities()));
            disp.register(literal("killentities").executes(ctx -> execKillEntities()));
            disp.register(literal("cycm")
                    .executes(ctx -> {
                        sendLocalizedMessage("cycm_usage");
                        return 1;
                    })
                    // YouTube Config Commands
                    .then(literal("youtube")
                            .then(literal("key").then(argument("key", StringArgumentType.string()).executes(ctx -> {
                                String key = StringArgumentType.getString(ctx, "key");
                                configManager.getConfig().setYoutubeApiKey(key);
                                configManager.saveConfig();
                                sendLocalizedMessage("yt_key_set_success");
                                if (configManager.getConfig().getChatMode() == ChatMode.API)
                                    YouTubeClient.forceStartPolling();
                                return 1;
                            })))
                            .then(literal("id").then(argument("id", StringArgumentType.string()).executes(ctx -> {
                                String id = StringArgumentType.getString(ctx, "id");
                                configManager.getConfig().setYoutubeVideoId(id);
                                configManager.saveConfig();
                                sendLocalizedMessage("yt_id_set_success");
                                if (configManager.getConfig().getChatMode() == ChatMode.API)
                                    YouTubeClient.forceStartPolling();
                                return 1;
                            })))
                            .then(literal("oa2client").then(argument("client", StringArgumentType.string()).executes(ctx -> {
                                String client = StringArgumentType.getString(ctx, "client");
                                configManager.getConfig().setYoutubeClientId(client);
                                configManager.saveConfig();
                                sendLocalizedMessage("yt_client_set_success");
                                return 1;
                            })))
                            .then(literal("oa2secret").then(argument("secret", StringArgumentType.string()).executes(ctx -> {
                                String secret = StringArgumentType.getString(ctx, "secret");
                                configManager.getConfig().setYoutubeClientSecret(secret);
                                configManager.saveConfig();
                                sendLocalizedMessage("yt_secret_set_success");
                                return 1;
                            })))
                            .then(literal("connect").executes(ctx -> {
                                String url = YouTubeClient.getAuthUrl();
                                // Ensure server is running for callback regardless of mode
                                HttpChatServer.startServer(configManager.getConfig().getHttpMessagesPort());

                                MutableComponent link = Component.literal(url).withStyle(style -> style
                                        .withClickEvent(new ClickEvent.OpenUrl(URI.create(url)))
                                        .applyFormats(ChatFormatting.UNDERLINE, ChatFormatting.AQUA));

                                sendLocalizedMessage("yt_auth_url", link);
                                return 1;
                            }))
                            .then(literal("send")
                                    .then(literal("on").executes(ctx -> {
                                        configManager.getConfig().setYoutubeSendEnabled(true);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("yt_send_state", Component.translatable("cycm.state.enabled"));
                                        return 1;
                                    }))
                                    .then(literal("off").executes(ctx -> {
                                        configManager.getConfig().setYoutubeSendEnabled(false);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("yt_send_state", Component.translatable("cycm.state.disabled"));
                                        return 1;
                                    }))))
                    .then(literal("ytmode")
                            .then(literal("api").executes(ctx -> {
                                configManager.getConfig().setChatMode(ChatMode.API);
                                configManager.saveConfig();
                                sendLocalizedMessage("mode_set_success", "API");
                                if (configManager.getConfig().isModEnabled())
                                    startChatSource();
                                return 1;
                            }))
                            .then(literal("http").executes(ctx -> {
                                configManager.getConfig().setChatMode(ChatMode.HTTP);
                                configManager.saveConfig();
                                sendLocalizedMessage("mode_set_success", "HTTP");
                                if (configManager.getConfig().isModEnabled())
                                    startChatSource();
                                return 1;
                            })))
                    .then(literal("source")
                            .then(literal("list").executes(ctx -> {
                                displaySourceList();
                                return 1;
                            }))
                            .then(literal("on")
                                    .executes(ctx -> {
                                        sendLocalizedMessage("source_on_usage");
                                        return 1;
                                    })
                                    .then(argument("src", StringArgumentType.string()).executes(ctx -> {
                                        setSourceState(StringArgumentType.getString(ctx, "src"), true);
                                        return 1;
                                    })))
                            .then(literal("off")
                                    .executes(ctx -> {
                                        sendLocalizedMessage("source_off_usage");
                                        return 1;
                                    })
                                    .then(argument("src", StringArgumentType.string()).executes(ctx -> {
                                        setSourceState(StringArgumentType.getString(ctx, "src"), false);
                                        return 1;
                                    }))))
                    .then(literal("tg")
                            .then(literal("token").then(argument("token", StringArgumentType.string()).executes(ctx -> {
                                String token = StringArgumentType.getString(ctx, "token");
                                configManager.getConfig().setTelegramToken(token);
                                configManager.saveConfig();
                                sendLocalizedMessage("tg_token_set_success");
                                if (configManager.getConfig().isModEnabled()
                                        && configManager.getConfig().isTelegramEnabled())
                                    TelegramClient.startPolling();
                                return 1;
                            }))))
                    .then(literal("block").then(argument("cmd", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> sugBlock(ctx, builder)).executes(ctx -> {
                                blockCommand(StringArgumentType.getString(ctx, "cmd"));
                                return 1;
                            })))
                    .then(literal("unblock").then(argument("cmd", StringArgumentType.greedyString())
                            .suggests(this::sugUnblock).executes(ctx -> {
                                unblockCommand(StringArgumentType.getString(ctx, "cmd"));
                                return 1;
                            })))

                    .then(literal("grouping")
                            .then(literal("on").executes(ctx -> {
                                configManager.getConfig().setGroupingMessages(true);
                                configManager.saveConfig();
                                sendLocalizedMessage("grouping_set", Component.translatable("cycm.state.enabled"));
                                return 1;
                            }))
                            .then(literal("off").executes(ctx -> {
                                configManager.getConfig().setGroupingMessages(false);
                                configManager.saveConfig();
                                sendLocalizedMessage("grouping_set", Component.translatable("cycm.state.disabled"));
                                return 1;
                            })))
                    // Server compatibility mode commands
                    .then(literal("server")
                            .then(literal("compat")
                                    .then(literal("on").executes(ctx -> {
                                        configManager.getConfig().setCompatibilityMode(true);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("compat_mode_set", Component.translatable("cycm.state.enabled"));
                                        return 1;
                                    }))
                                    .then(literal("off").executes(ctx -> {
                                        configManager.getConfig().setCompatibilityMode(false);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("compat_mode_set", Component.translatable("cycm.state.disabled"));
                                        return 1;
                                    }))))
                    // Update http commands to include UI controls
                    .then(literal("http")
                            .then(literal("messages")
                                    .then(literal("port").then(argument("port", IntegerArgumentType.integer()).executes(ctx -> {
                                        int port = IntegerArgumentType.getInteger(ctx, "port");
                                        configManager.getConfig().setHttpMessagesPort(port);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("http_messages_port_set_success", port);
                                        if (configManager.getConfig().getChatMode() == ChatMode.HTTP
                                                && configManager.getConfig().isModEnabled()) {
                                            HttpChatServer.startServer(port);
                                        }
                                        return 1;
                                    }))))
                            .then(literal("ui")
                                    .then(literal("on").executes(ctx -> {
                                        configManager.getConfig().setWebUIEnabled(true);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("webui_state", Component.translatable("cycm.state.enabled"));
                                        // Start web UI server
                                        org.craftllc.minecraft.mod.cycm.http.WebUIServer.startServer(configManager.getConfig().getWebUIPort());
                                        return 1;
                                    }))
                                    .then(literal("off").executes(ctx -> {
                                        configManager.getConfig().setWebUIEnabled(false);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("webui_state", Component.translatable("cycm.state.disabled"));
                                        // Stop web UI server
                                        org.craftllc.minecraft.mod.cycm.http.WebUIServer.stopServer();
                                        return 1;
                                    }))
                                    .then(literal("port").then(argument("port", IntegerArgumentType.integer()).executes(ctx -> {
                                        int port = IntegerArgumentType.getInteger(ctx, "port");
                                        configManager.getConfig().setWebUIPort(port);
                                        configManager.saveConfig();
                                        sendLocalizedMessage("webui_port_set_success", port);
                                        if (configManager.getConfig().isWebUIEnabled()) {
                                            org.craftllc.minecraft.mod.cycm.http.WebUIServer.startServer(port);
                                        }
                                        return 1;
                                    })))))
                    .then(literal("actionbar")
                            .then(literal("on").executes(ctx -> {
                                configManager.getConfig().setActionbarEnabled(true);
                                configManager.saveConfig();
                                sendLocalizedMessage("actionbar_set", Component.translatable("cycm.state.enabled"));
                                return 1;
                            }))
                            .then(literal("off").executes(ctx -> {
                                configManager.getConfig().setActionbarEnabled(false);
                                configManager.saveConfig();
                                sendLocalizedMessage("actionbar_set", Component.translatable("cycm.state.disabled"));
                                return 1;
                            })))
                    .then(literal("on").executes(ctx -> {
                        setModEnabled(true);
                        return 1;
                    }))
                    .then(literal("off").executes(ctx -> {
                        setModEnabled(false);
                        return 1;
                    }))
                    .then(literal("restart").executes(ctx -> {
                        restartMod();
                        return 1;
                    }))
                    .then(literal("stop").executes(ctx -> {
                        stopScheduledCommands();
                        return 1;
                    }))
                    .then(literal("execute")
                            .then(argument("cmd_reps", StringArgumentType.greedyString()).executes(ctx -> {
                                execCmdInGame(StringArgumentType.getString(ctx, "cmd_reps"));
                                return 1;
                            })))
                    // Нові підкоманди для повторів та затримки
                    .then(literal("num")
                            .then(argument("N", IntegerArgumentType.integer()).executes(ctx -> {
                                setMaxRepeats(IntegerArgumentType.getInteger(ctx, "N"));
                                return 1;
                            })))
                    .then(literal("delay")
                            .then(argument("Y", IntegerArgumentType.integer()).executes(ctx -> {
                                setMaxDelaySeconds(IntegerArgumentType.getInteger(ctx, "Y"));
                                return 1;
                            })))
                    .then(literal("tnt")
                            .then(literal("count")
                                    .then(argument("N", IntegerArgumentType.integer(1)).executes(ctx -> {
                                        setMaxTntCount(IntegerArgumentType.getInteger(ctx, "N"));
                                        return 1;
                                    })))
                            .then(literal("radius")
                                    .then(argument("R", IntegerArgumentType.integer(0)).executes(ctx -> {
                                        setMaxTntRadius(IntegerArgumentType.getInteger(ctx, "R"));
                                        return 1;
                                    })))));
            disp.register(literal("ce")
                    .then(argument("cmd_reps", StringArgumentType.greedyString()).executes(ctx -> {
                        execCmdInGame(StringArgumentType.getString(ctx, "cmd_reps"));
                        return 1;
                    })));
        });
    }

    private CompletableFuture<Suggestions> sugBlock(CommandContext<FabricClientCommandSource> ctx,
            SuggestionsBuilder b) {
        return b.buildFuture();
    }

    private CompletableFuture<Suggestions> sugUnblock(CommandContext<FabricClientCommandSource> ctx,
            SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase();
        blockedCommands.stream().filter(c -> !c.equals("cycm") && c.startsWith(rem)).forEach(b::suggest);
        if ("all".startsWith(rem))
            b.suggest("all");
        return b.buildFuture();
    }

    private void restartMod() {
        sendLocalizedMessage("restarting_cycm_message");
        Constants.LOGGER.info("Перезапускаю мод...");
        stopChatSource();
        configManager.stopWatchingConfigFile();
        blockedCommands.clear();
        configManager.loadConfig();
        loadBlockedCommands();
        loadRepeatingSettings();
        loadTntSettings();
        if (configManager.getConfig().isModEnabled() && Minecraft.getInstance().player != null) {
            startChatSource();
            sendLocalizedMessage("cycm_restarted_success");
        } else {
            sendLocalizedMessage("mod_off_no_player_auto_start");
        }
    }

    private void dispBlockList() {
        if (blockedCommands.isEmpty()) {
            sendLocalizedMessage("no_blocked_cmds");
        } else {
            sendLocalizedMessage("blocked_cmds_header");
            synchronized (blockedCommands) {
                blockedCommands.stream().sorted()
                        .forEach(cmd -> sendLocalizedMessage("blocked_cmd_item", Component.literal("/" + cmd)));
            }
        }
    }

    private void displaySourceList() {
        sendLocalizedMessage("sources_header");
        Component ytStatus = configManager.getConfig().isYoutubeEnabled() ? Component.translatable("cycm.state.on") : Component.translatable("cycm.state.off");
        Component tgStatus = configManager.getConfig().isTelegramEnabled() ? Component.translatable("cycm.state.on") : Component.translatable("cycm.state.off");
        
        sendLocalizedMessage("source_item", Component.translatable("cycm.source.youtube"), ytStatus);
        sendLocalizedMessage("source_item", Component.translatable("cycm.source.telegram"), tgStatus);
    }

    private void setSourceState(String source, boolean enabled) {
        if ("youtube".equalsIgnoreCase(source) || "yt".equalsIgnoreCase(source)) {
            configManager.getConfig().setYoutubeEnabled(enabled);
            configManager.saveConfig();
            sendLocalizedMessage("source_state_changed", Component.translatable("cycm.source.youtube"),
                    (enabled ? Component.translatable("cycm.state.enabled") : Component.translatable("cycm.state.disabled")));
            if (configManager.getConfig().isModEnabled()) {
                stopChatSource();
                startChatSource();
            }
        } else if ("telegram".equalsIgnoreCase(source) || "tg".equalsIgnoreCase(source)) {
            configManager.getConfig().setTelegramEnabled(enabled);
            configManager.saveConfig();
            sendLocalizedMessage("source_state_changed", Component.translatable("cycm.source.telegram"),
                    (enabled ? Component.translatable("cycm.state.enabled") : Component.translatable("cycm.state.disabled")));
            if (configManager.getConfig().isModEnabled()) {
                stopChatSource();
                startChatSource();
            }
        } else {
            sendLocalizedMessage("unknown_source", source);
        }
    }

    public static void setSourceStateFromUI(String source, boolean enabled) {
        if (instance != null) {
            instance.setSourceState(source, enabled);
        }
    }

    private int execKillAura(double r) {
        Minecraft c = Minecraft.getInstance();
        if (c == null || c.player == null) {
            sendLocalizedMessage("no_player");
            return 0;
        }
        sendToSrv(String.format("kill @e[type=!player,distance=..%.0f]", r));
        sendLocalizedMessage("executing_killaura", Component.literal(String.valueOf((int) r)));
        if (c.level != null) {
            double px = c.player.getX();
            double py = c.player.getY();
            double pz = c.player.getZ();
            int numP = 30;
            for (int i = 0; i < numP; i++) {
                double angle = 2 * Math.PI * i / numP;
                double x = px + r * Math.cos(angle);
                double z = pz + r * Math.sin(angle);
                c.particleEngine.createParticle(ParticleTypes.CRIT, x, py + c.player.getBbHeight() / 2, z, 0, 0.1, 0);
            }
        }
        return 1;
    }

    private int execKillEntities() {
        Minecraft c = Minecraft.getInstance();
        if (c == null || c.player == null) {
            sendLocalizedMessage("no_player");
            return 0;
        }
        sendToSrv("kill @e[type=!player]");
        sendLocalizedMessage("executing_killentities");
        return 1;
    }

    @FunctionalInterface
    private interface LineProcessor {
        void process(String line);
    }

    public static void sendLocalizedMessage(String key, Object... args) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        
        client.execute(() -> {
            if (client.player != null) {
                Object[] processedArgs = Arrays.stream(args)
                        .map(arg -> arg instanceof String ? Component.literal((String) arg) : arg).toArray();
                MutableComponent message = Component.translatable("cycm.message." + key, processedArgs);
                ChatFormatting defaultColor = ChatFormatting.GOLD;

                if (key.startsWith("bad_") || key.endsWith("_error") || key.endsWith("_warning")
                        || key.equals("cmd_blocked") || key.equals("mod_cmd_cannot_be_blocked")
                        || key.equals("mod_cmd_cannot_be_unblocked") || key.equals("no_player") || key.startsWith("ai_")) {
                    message = message.copy().withStyle(ChatFormatting.RED);
                } else if (key.endsWith("_success") || key.equals("cmd_unblocked_success")
                        || key.equals("all_cmds_unblocked") || key.startsWith("yt_key") || key.startsWith("yt_id")
                        || key.startsWith("ai_key")) {
                    message = message.copy().withStyle(ChatFormatting.GREEN);
                } else if (key.endsWith("_message") || key.equals("restarting_cycm_message")
                        || key.equals("mod_off_no_player_auto_start") || key.equals("no_blocked_cmds")
                        || key.equals("blocked_cmds_header")) {
                    message = message.copy().withStyle(ChatFormatting.AQUA);
                } else if (key.endsWith("already_blocked") || key.endsWith("already_state")
                        || key.equals("cmd_not_blocked")) {
                    message = message.copy().withStyle(ChatFormatting.YELLOW);
                } else {
                    message = message.copy().withStyle(defaultColor);
                }
                client.player.sendSystemMessage(message);
            }
        });
    }

    public static void sendMsg(Component msg) {
        Minecraft c = Minecraft.getInstance();
        if (c != null && c.player != null)
            c.player.sendSystemMessage(msg);
    }

    public static void sendMsg(String msg) {
        sendMsg(Component.literal(msg));
    }

    private void sendToSrv(String cmd) {
        if (Minecraft.getInstance().player != null) {
            String finalCmd = cmd;
            if (configManager.getConfig().isCompatibilityMode() && !cmd.contains(":")) {
                // Add minecraft: prefix if compatibility mode is enabled and no namespace is present
                finalCmd = "minecraft:" + cmd;
            }
            Minecraft.getInstance().getConnection().sendCommand(finalCmd);
        }
    }

    private void updateActionbar(Minecraft client) {
        if (client.player == null)
            return;
        String yt = configManager.getConfig().isYoutubeEnabled() ? "§aYT" : "§7YT";
        String tg = configManager.getConfig().isTelegramEnabled() ? "§aTG" : "§7TG";
        String mode = configManager.getConfig().getChatMode().name();

        Component status = Component.translatable("cycm.message.actionbar_status", yt, mode, tg);
        client.player.sendOverlayMessage(status);
    }
}








