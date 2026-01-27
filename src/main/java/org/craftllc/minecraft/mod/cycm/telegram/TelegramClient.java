package org.craftllc.minecraft.mod.cycm.telegram;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.craftllc.minecraft.mod.cycm.Constants;
import org.craftllc.minecraft.mod.cycm.CYCMClient;
import org.craftllc.minecraft.mod.cycm.youtube.LiveStateManager;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelegramClient {
    private static final String API_URL = "https://api.telegram.org/bot";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private static final Gson gson = new Gson();
    private static ScheduledExecutorService scheduler;
    private static boolean isPolling = false;

    public static void startPolling() {
        if (isPolling)
            return;

        String token = CYCMClient.configManager.getConfig().getTelegramToken();
        if (token == null || token.isEmpty()) {
            Constants.LOGGER.warn("Telegram token not set. Skipping Telegram polling.");
            return;
        }

        isPolling = true;
        Constants.LOGGER.info("Telegram polling started.");
        startLoop();
    }

    public static void stopPolling() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = null;
        isPolling = false;
        Constants.LOGGER.info("Telegram polling stopped.");
    }

    private static void startLoop() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        scheduler.schedule(TelegramClient::pollUpdates, 0, TimeUnit.MILLISECONDS);
    }

    private static void pollUpdates() {
        if (!isPolling)
            return;

        String token = CYCMClient.configManager.getConfig().getTelegramToken();
        long lastId = LiveStateManager.getLastTelegramUpdateId();

        String url = API_URL + token + "/getUpdates?offset=" + (lastId + 1) + "&timeout=10";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        if (json.has("ok") && json.get("ok").getAsBoolean()) {
                            JsonArray updates = json.getAsJsonArray("result");
                            processUpdates(updates);
                        }
                    } else if (response.statusCode() != 200) {
                        // Avoid spamming logs if there's a constant error, but log it once
                        Constants.LOGGER.error("Telegram API error: " + response.statusCode());
                    }

                    if (isPolling) {
                        scheduler.schedule(TelegramClient::pollUpdates, 1000, TimeUnit.MILLISECONDS);
                    }
                })
                .exceptionally(e -> {
                    Constants.LOGGER.error("Error polling Telegram", e);
                    if (isPolling) {
                        scheduler.schedule(TelegramClient::pollUpdates, 5000, TimeUnit.MILLISECONDS);
                    }
                    return null;
                });
    }

    private static void processUpdates(JsonArray updates) {
        for (int i = 0; i < updates.size(); i++) {
            JsonObject update = updates.get(i).getAsJsonObject();
            long updateId = update.get("update_id").getAsLong();
            LiveStateManager.setLastTelegramUpdateId(updateId);

            if (update.has("message")) {
                JsonObject msg = update.getAsJsonObject("message");
                if (msg.has("text") && msg.has("from") && msg.has("chat")) {
                    String text = msg.get("text").getAsString();
                    long chatId = msg.getAsJsonObject("chat").get("id").getAsLong();
                    JsonObject from = msg.getAsJsonObject("from");
                    String author = from.get("first_name").getAsString();
                    String lang = from.has("language_code") ? from.get("language_code").getAsString() : "en";

                    if (from.has("username")) {
                        author = "@" + from.get("username").getAsString();
                    }

                    if (text.equalsIgnoreCase("/start")) {
                        sendWelcome(chatId, lang);
                    } else if (text.equals(TelegramTranslator.getTranslation(lang, "blocked_commands_btn"))) {
                        sendBlockedList(chatId, lang);
                    } else {
                        // Split by newline to support consecutive commands
                        String[] lines = text.split("\\r?\\n");
                        for (String line : lines) {
                            if (line.trim().isEmpty())
                                continue;
                            // Using processYouTubeMessage as it handles the logic for command execution
                            CYCMClient.getInstance().processYouTubeMessage("[TG] " + author, line.trim());
                        }
                    }
                }
            }
        }
    }

    private static void sendWelcome(long chatId, String lang) {
        String btnText = TelegramTranslator.getTranslation(lang, "blocked_commands_btn");
        String welcome = TelegramTranslator.getTranslation(lang, "welcome");

        JsonObject keyboard = new JsonObject();
        JsonArray row = new JsonArray();
        JsonObject button = new JsonObject();
        button.addProperty("text", btnText);
        row.add(button);
        JsonArray markup = new JsonArray();
        markup.add(row);
        keyboard.add("keyboard", markup);
        keyboard.addProperty("resize_keyboard", true);

        sendMessage(chatId, welcome, keyboard);
    }

    private static void sendBlockedList(long chatId, String lang) {
        String header = TelegramTranslator.getTranslation(lang, "blocked_commands_header");

        StringBuilder sb = new StringBuilder(header);
        CYCMClient.getBlockedCommands().stream().sorted().forEach(cmd -> sb.append("- /").append(cmd).append("\n"));
        sendMessage(chatId, sb.toString(), null);
    }

    private static void sendMessage(long chatId, String text, JsonObject replyMarkup) {
        String token = CYCMClient.configManager.getConfig().getTelegramToken();
        if (token == null || token.isEmpty())
            return;

        JsonObject payload = new JsonObject();
        payload.addProperty("chat_id", chatId);
        payload.addProperty("text", text);
        payload.addProperty("parse_mode", "Markdown");
        if (replyMarkup != null) {
            payload.add("reply_markup", replyMarkup);
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + token + "/sendMessage"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }
}
