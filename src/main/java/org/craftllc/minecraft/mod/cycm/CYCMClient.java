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
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Formatting;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public class CYCMClient implements ClientModInitializer {
    private static final Path MOD_CFG_DIR = FabricLoader.getInstance().getConfigDir().resolve(Constants.MOD_ID);
    private static final Path BLOCKED_FILE = MOD_CFG_DIR.resolve("blocked_commands.txt");
    private static final Path REPEATING_FILE = MOD_CFG_DIR.resolve("repeating_settings.txt");
    private static final Path CMD_LOG_FILE = MOD_CFG_DIR.resolve("commands_log.txt");
    private static final Path CHAT_LOG_FILE = MOD_CFG_DIR.resolve("chat_log.txt");

    private static ScheduledExecutorService scheduler;
    public static ModConfigManager configManager;
    private static Set<String> blockedCommands = Collections.synchronizedSet(new HashSet<>());
    private static int maxRepeats = 20;
    private static int maxDelaySeconds = 5;
    private static CYCMClient instance;

    public CYCMClient() {
        instance = this;
    }

    public static CYCMClient getInstance() {
        return instance;
    }

    public static Set<String> getBlockedCommands() {
        return blockedCommands;
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
    }

    public static void startChatSource() {
        if (configManager.getConfig().isYoutubeEnabled()) {
            if (configManager.getConfig().getChatMode() == ChatMode.API) {
                HttpChatServer.stopServer();
                YouTubeClient.startPolling();
            } else {
                YouTubeClient.stopPolling();
                HttpChatServer.startServer(configManager.getConfig().getHttpPort());
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
        YouTubeClient.stopPolling();
        HttpChatServer.stopServer();
        TelegramClient.stopPolling();
    }

    public static void setModEnabled(boolean enabled) {
        if (configManager.getConfig().isModEnabled() == enabled) {
            sendLocalizedMessage("mod_already_state",
                    (enabled ? Text.translatable("cycm.state.enabled") : Text.translatable("cycm.state.disabled")));
            return;
        }
        configManager.getConfig().setModEnabled(enabled);
        configManager.saveConfig();
        sendLocalizedMessage("mod_state",
                (enabled ? Text.translatable("cycm.state.enabled") : Text.translatable("cycm.state.disabled")));
        if (enabled && MinecraftClient.getInstance().player != null)
            startChatSource();
        else
            stopChatSource();
    }

    // Called from YouTubeClient thread
    public void processYouTubeMessage(String author, String message) {
        if (!configManager.getConfig().isModEnabled() || MinecraftClient.getInstance().player == null)
            return;

        // Log message (using original for logging or translated?)
        // Let's log original to keep logs clean of §
        logMessage(message.startsWith("/") ? CMD_LOG_FILE : CHAT_LOG_FILE, author + ": " + message);

        if (message.startsWith("/")) {
            // It's a command
            // Execute on main thread to be safe with Minecraft
            final String fMessage = message;
            MinecraftClient.getInstance().execute(() -> {
                procCmdLine(author, fMessage);
            });
        } else {
            // It's chat
            // Translate & codes ONLY for chat to avoid crashes in sendChatCommand
            final String fMessage = translateColorCodes(message);
            MinecraftClient.getInstance().execute(() -> {
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
            Matcher m = Pattern.compile("^(.*?)\\s*(?<!\\\\)\\+(\\d+)(?:\\s+(\\d+))?\\s*$").matcher(segment);
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
                execSingleCmdLogic(nick, cleanCmd, allowedReps, delay, isLocal);
                totalUsedReps += allowedReps;
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

    private void execSingleCmdLogic(String nick, String cleanCmd, int reps, int delay, boolean isLocal) {
        if (scheduler == null || scheduler.isShutdown())
            scheduler = Executors.newSingleThreadScheduledExecutor();

        String baseCmd = cleanCmd.split(" ")[0].toLowerCase();

        if (isCmdBlocked(baseCmd) && !isModCmd(baseCmd)) {
            sendLocalizedMessage("cmd_blocked", Text.literal("/" + cleanCmd));
            return;
        }

        final String fCmd = cleanCmd; // Clean command (no slash) for sendChatCommand
        final int fReps = reps;
        final int fDel = delay;
        for (int r = 0; r < fReps; r++) {
            scheduler.schedule(() -> {
                if (MinecraftClient.getInstance().player != null) {
                    MinecraftClient.getInstance().execute(() -> {
                        if (MinecraftClient.getInstance().player == null)
                            return;
                        if (isLocal) {
                            MinecraftClient.getInstance().player.sendMessage(
                                    Text.translatable("cycm.message.executing_command", Text.literal("/" + fCmd)),
                                    false);
                        } else {
                            MinecraftClient.getInstance().player.sendMessage(
                                    Text.literal("§f" + nick + " ")
                                            .append(Text.translatable("cycm.message.cmd_executed",
                                                    Text.literal("/" + fCmd))),
                                    false);
                        }
                        // Send command without slash
                        MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(fCmd);
                    });
                }
            }, (long) r * fDel, TimeUnit.SECONDS);
        }
    }

    private void execCmdInGame(String fullCmd) {
        if (MinecraftClient.getInstance().player == null) {
            sendLocalizedMessage("no_player");
            return;
        }

        executeMulti(null, fullCmd, true);
    }

    private boolean isModCmd(String cmd) {
        return cmd.equals("cycm");
    }

    private void procChatLine(String nick, String msg) {
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("<")
                    .append(Text.literal(nick).formatted(Formatting.WHITE)).append("> ").append(Text.literal(msg)),
                    false);
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
        ensureFile(BLOCKED_FILE);
        try {
            synchronized (blockedCommands) {
                blockedCommands.clear();
                Files.readAllLines(BLOCKED_FILE).stream()
                        .filter(l -> !l.trim().isEmpty() && !l.trim().startsWith("#"))
                        .map(String::trim)
                        .map(l -> l.startsWith("/") ? l.substring(1).toLowerCase() : l.toLowerCase())
                        .forEach(blockedCommands::add);
            }
        } catch (IOException e) {
            Constants.LOGGER.error("Помилка читання файлу заблокованих команд: {}", e.getMessage());
        }
        initBlockedCommands();
    }

    private void saveBlockedCommands() {
        try {
            Files.write(BLOCKED_FILE, blockedCommands.stream().map(s -> "/" + s).collect(Collectors.toList()));
        } catch (IOException e) {
            Constants.LOGGER.error("Помилка збереження файлу заблокованих команд: {}", e.getMessage());
        }
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

        if (chg)
            saveBlockedCommands();
    }

    private void blockCommand(String cmd) {
        String cleanCmd = cmd.toLowerCase().startsWith("/") ? cmd.substring(1).toLowerCase() : cmd.toLowerCase();
        if (isModCmd(cleanCmd)) {
            sendLocalizedMessage("mod_cmd_cannot_be_blocked", Text.literal("/" + cleanCmd));
            return;
        }
        if (blockedCommands.add(cleanCmd)) {
            sendLocalizedMessage("cmd_blocked_success", Text.literal("/" + cleanCmd));
            saveBlockedCommands();
        } else {
            sendLocalizedMessage("cmd_already_blocked", Text.literal("/" + cleanCmd));
        }
    }

    private void unblockCommand(String cmd) {
        String cleanCmd = cmd.toLowerCase().startsWith("/") ? cmd.substring(1).toLowerCase() : cmd.toLowerCase();
        if (isModCmd(cleanCmd)) {
            sendLocalizedMessage("mod_cmd_cannot_be_unblocked", Text.literal("/" + cleanCmd));
            return;
        }
        if ("all".equalsIgnoreCase(cleanCmd)) {
            synchronized (blockedCommands) {
                blockedCommands.clear();
            }
            initBlockedCommands();
            sendLocalizedMessage("all_cmds_unblocked");
        } else if (blockedCommands.remove(cleanCmd)) {
            sendLocalizedMessage("cmd_unblocked_success", Text.literal("/" + cleanCmd));
            saveBlockedCommands();
        } else {
            sendLocalizedMessage("cmd_not_blocked", Text.literal("/" + cleanCmd));
        }
    }

    private void loadRepeatingSettings() {
        ensureFile(REPEATING_FILE);
        try {
            String sLine = Files.readAllLines(REPEATING_FILE).stream()
                    .filter(l -> !l.trim().isEmpty() && !l.trim().startsWith("#")).findFirst().orElse(null);
            if (sLine != null && sLine.split(":").length == 2) {
                try {
                    maxRepeats = Integer.parseInt(sLine.split(":")[0].trim());
                    maxDelaySeconds = Integer.parseInt(sLine.split(":")[1].trim());
                } catch (NumberFormatException e) {
                    Constants.LOGGER.error("Невірні налаштування повторів: {}. Використовуються стандартні.", sLine);
                }
            } else {
                Files.write(REPEATING_FILE, Collections.singletonList(maxRepeats + ":" + maxDelaySeconds));
            }
        } catch (IOException e) {
            Constants.LOGGER.error("Помилка читання налаштувань повторів: {}", e.getMessage());
        }
    }

    private void setMaxRepeats(int num) {
        if (num <= 0) {
            sendLocalizedMessage("num_repeats_positive_warning");
            return;
        }
        maxRepeats = num;
        saveRepeatingSettings();
        sendLocalizedMessage("repeats_set_success", Text.literal(String.valueOf(num)));
    }

    private void setMaxDelaySeconds(int delay) {
        if (delay < 0) {
            sendLocalizedMessage("delay_positive_warning");
            return;
        }
        maxDelaySeconds = delay;
        saveRepeatingSettings();
        sendLocalizedMessage("delay_set_success", Text.literal(String.valueOf(delay)));
    }

    private void saveRepeatingSettings() {
        try {
            Files.write(REPEATING_FILE, Collections.singletonList(maxRepeats + ":" + maxDelaySeconds));
        } catch (IOException e) {
            Constants.LOGGER.error("Помилка збереження налаштувань повторів: {}", e.getMessage());
        }
    }

    private void registerClientCommands() {
        ClientCommandRegistrationCallback.EVENT.register((disp, regAcc) -> {
            disp.register(literal("tnt").executes(ctx -> {
                sendToSrv("summon minecraft:tnt");
                return 1;
            }));
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
                            }))))
                    .then(literal("http")
                            .then(literal("port").then(argument("port", IntegerArgumentType.integer()).executes(ctx -> {
                                int port = IntegerArgumentType.getInteger(ctx, "port");
                                configManager.getConfig().setHttpPort(port);
                                configManager.saveConfig();
                                sendLocalizedMessage("http_port_set_success", port);
                                if (configManager.getConfig().getChatMode() == ChatMode.HTTP
                                        && configManager.getConfig().isModEnabled()) {
                                    HttpChatServer.startServer(port);
                                }
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
                                sendLocalizedMessage("grouping_set", Text.translatable("cycm.state.enabled"));
                                return 1;
                            }))
                            .then(literal("off").executes(ctx -> {
                                configManager.getConfig().setGroupingMessages(false);
                                configManager.saveConfig();
                                sendLocalizedMessage("grouping_set", Text.translatable("cycm.state.disabled"));
                                return 1;
                            })))
                    .then(literal("actionbar")
                            .then(literal("on").executes(ctx -> {
                                configManager.getConfig().setActionbarEnabled(true);
                                configManager.saveConfig();
                                sendLocalizedMessage("actionbar_set", Text.translatable("cycm.state.enabled"));
                                return 1;
                            }))
                            .then(literal("off").executes(ctx -> {
                                configManager.getConfig().setActionbarEnabled(false);
                                configManager.saveConfig();
                                sendLocalizedMessage("actionbar_set", Text.translatable("cycm.state.disabled"));
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
                    .then(literal("resetfile").then(
                            argument("fname", StringArgumentType.string()).suggests(this::sugReset).executes(ctx -> {
                                resetConfig(StringArgumentType.getString(ctx, "fname"));
                                return 1;
                            })))
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
                            }))));
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

    private CompletableFuture<Suggestions> sugReset(CommandContext<FabricClientCommandSource> ctx,
            SuggestionsBuilder b) {
        String rem = b.getRemaining().toLowerCase();
        if ("blocked_commands.txt".startsWith(rem))
            b.suggest("blocked_commands.txt");
        if ("repeating_settings.txt".startsWith(rem))
            b.suggest("repeating_settings.txt");
        return b.buildFuture();
    }

    private void resetConfig(String fname) {
        Path tf;
        Runnable pa = null;
        if (fname.equalsIgnoreCase("blocked_commands.txt")) {
            tf = BLOCKED_FILE;
            pa = this::loadBlockedCommands;
        } else if (fname.equalsIgnoreCase("repeating_settings.txt")) {
            tf = REPEATING_FILE;
            pa = this::loadRepeatingSettings;
        } else {
            sendLocalizedMessage("unknown_file", Text.literal(fname));
            return;
        }
        try {
            if (Files.exists(tf))
                Files.delete(tf);
            ensureFile(tf);
            if (pa != null)
                pa.run();
            sendLocalizedMessage("file_reset_success", Text.literal(fname));
        } catch (IOException e) {
            sendLocalizedMessage("reset_error", Text.literal(fname), Text.literal(e.getMessage()));
        }
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
        if (configManager.getConfig().isModEnabled() && MinecraftClient.getInstance().player != null) {
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
                        .forEach(cmd -> sendLocalizedMessage("blocked_cmd_item", Text.literal("/" + cmd)));
            }
        }
    }

    private void displaySourceList() {
        sendLocalizedMessage("sources_header");
        String ytStatus = configManager.getConfig().isYoutubeEnabled() ? "§aON" : "§cOFF";
        String tgStatus = configManager.getConfig().isTelegramEnabled() ? "§aON" : "§cOFF";
        sendMsg("§f- YouTube: " + ytStatus);
        sendMsg("§f- Telegram: " + tgStatus);
    }

    private void setSourceState(String source, boolean enabled) {
        if ("youtube".equalsIgnoreCase(source) || "yt".equalsIgnoreCase(source)) {
            configManager.getConfig().setYoutubeEnabled(enabled);
            configManager.saveConfig();
            sendLocalizedMessage("source_state_changed", "YouTube",
                    (enabled ? Text.translatable("cycm.state.enabled") : Text.translatable("cycm.state.disabled")));
            if (configManager.getConfig().isModEnabled())
                startChatSource();
        } else if ("telegram".equalsIgnoreCase(source) || "tg".equalsIgnoreCase(source)) {
            configManager.getConfig().setTelegramEnabled(enabled);
            configManager.saveConfig();
            sendLocalizedMessage("source_state_changed", "Telegram",
                    (enabled ? Text.translatable("cycm.state.enabled") : Text.translatable("cycm.state.disabled")));
            if (configManager.getConfig().isModEnabled())
                startChatSource();
        } else {
            sendLocalizedMessage("unknown_source", source);
        }
    }

    private int execKillAura(double r) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null) {
            sendLocalizedMessage("no_player");
            return 0;
        }
        sendToSrv(String.format("kill @e[type=!player,distance=..%.0f]", r));
        sendLocalizedMessage("executing_killaura", Text.literal(String.valueOf((int) r)));
        if (c.world != null) {
            double px = c.player.getX();
            double py = c.player.getY();
            double pz = c.player.getZ();
            int numP = 30;
            for (int i = 0; i < numP; i++) {
                double angle = 2 * Math.PI * i / numP;
                double x = px + r * Math.cos(angle);
                double z = pz + r * Math.sin(angle);
                c.particleManager.addParticle(ParticleTypes.CRIT, x, py + c.player.getHeight() / 2, z, 0, 0.1, 0);
            }
        }
        return 1;
    }

    private int execKillEntities() {
        MinecraftClient c = MinecraftClient.getInstance();
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
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            Object[] processedArgs = Arrays.stream(args)
                    .map(arg -> arg instanceof String ? Text.literal((String) arg) : arg).toArray();
            Text message = Text.translatable("cycm.message." + key, processedArgs);
            Formatting defaultColor = Formatting.GOLD;

            if (key.startsWith("bad_") || key.endsWith("_error") || key.endsWith("_warning")
                    || key.equals("cmd_blocked") || key.equals("mod_cmd_cannot_be_blocked")
                    || key.equals("mod_cmd_cannot_be_unblocked") || key.equals("no_player") || key.startsWith("ai_")) {
                message = message.copy().formatted(Formatting.RED);
            } else if (key.endsWith("_success") || key.equals("cmd_unblocked_success")
                    || key.equals("all_cmds_unblocked") || key.startsWith("yt_key") || key.startsWith("yt_id")
                    || key.startsWith("ai_key")) {
                message = message.copy().formatted(Formatting.GREEN);
            } else if (key.endsWith("_message") || key.equals("restarting_cycm_message")
                    || key.equals("mod_off_no_player_auto_start") || key.equals("no_blocked_cmds")
                    || key.equals("blocked_cmds_header")) {
                message = message.copy().formatted(Formatting.AQUA);
            } else if (key.endsWith("already_blocked") || key.endsWith("already_state")
                    || key.equals("cmd_not_blocked")) {
                message = message.copy().formatted(Formatting.YELLOW);
            } else {
                message = message.copy().formatted(defaultColor);
            }
            client.player.sendMessage(message, false);
        }
    }

    public static void sendMsg(Text msg) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c != null && c.player != null)
            c.player.sendMessage(msg, false);
    }

    public static void sendMsg(String msg) {
        sendMsg(Text.literal(msg));
    }

    private void sendToSrv(String cmd) {
        if (MinecraftClient.getInstance().player != null)
            MinecraftClient.getInstance().getNetworkHandler().sendChatCommand(cmd);
    }

    private void updateActionbar(MinecraftClient client) {
        if (client.player == null)
            return;
        String yt = configManager.getConfig().isYoutubeEnabled() ? "§aYT" : "§7YT";
        String tg = configManager.getConfig().isTelegramEnabled() ? "§aTG" : "§7TG";
        String mode = configManager.getConfig().getChatMode().name();

        Text status = Text.translatable("cycm.message.actionbar_status", yt, mode, tg);
        client.player.sendMessage(status, true);
    }
}
