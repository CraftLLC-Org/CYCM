package org.craftllc.minecraft.mod.cycm.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.craftllc.minecraft.mod.cycm.Constants;
import org.craftllc.minecraft.mod.cycm.CYCMClient;
import org.craftllc.minecraft.mod.cycm.http.HttpChatServer;
import org.craftllc.minecraft.mod.cycm.config.ModConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;

public class YouTubeClient {
    private static final String VIDEOS_API_URL = "https://www.googleapis.com/youtube/v3/videos";
    private static final String MESSAGES_API_URL = "https://www.googleapis.com/youtube/v3/liveChat/messages";

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static ScheduledExecutorService scheduler;
    private static String activeLiveChatId = null;
    private static String nextPageToken = null;
    private static boolean isPolling = false;
    private static boolean connectionAttempted = false;
    private static boolean isFetchingChatId = false;
    private static String pendingMessage = null;

    public static void startPolling() {
        if (isPolling || connectionAttempted) return;
        
        LiveStateManager.load();
        
        String apiKey = CYCMClient.configManager.getConfig().getYoutubeApiKey();
        String videoId = CYCMClient.configManager.getConfig().getYoutubeVideoId();

        if (apiKey == null || apiKey.isEmpty() || videoId == null || videoId.isEmpty()) {
            connectionAttempted = true; 
            return;
        }
        
        LiveStateManager.checkVideoId(videoId);

        isPolling = true;
        connectionAttempted = true;
        fetchLiveChatId(videoId, apiKey);
    }
    
    public static void forceStartPolling() {
        connectionAttempted = false;
        isPolling = false;
        startPolling();
    }

    public static void stopPolling() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        scheduler = null;
        isPolling = false;
        activeLiveChatId = null;
        nextPageToken = null;
        connectionAttempted = false; 
    }

    private static void fetchLiveChatId(String videoId, String apiKey) {
        String url = VIDEOS_API_URL + "?part=liveStreamingDetails&id=" + videoId + "&key=" + apiKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    isFetchingChatId = false;
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        if (json.has("items") && json.getAsJsonArray("items").size() > 0) {
                            JsonObject item = json.getAsJsonArray("items").get(0).getAsJsonObject();
                            if (item.has("liveStreamingDetails")) {
                                JsonObject details = item.getAsJsonObject("liveStreamingDetails");
                                if (details.has("activeLiveChatId")) {
                                    activeLiveChatId = details.get("activeLiveChatId").getAsString();
                                    Constants.LOGGER.info("Found Live Chat ID: " + activeLiveChatId);
                                    if (pendingMessage != null) {
                                        Constants.LOGGER.info("Sending pending message...");
                                        sendMessageToChat(pendingMessage);
                                        pendingMessage = null;
                                    }
                                    if (isPolling) {
                                        startMessageLoop();
                                    }
                                    return;
                                }
                            }
                        }
                        CYCMClient.sendLocalizedMessage("yt_no_chat_id");
                        isPolling = false;
                    } else {
                        Constants.LOGGER.error("Failed to fetch video details: " + response.statusCode());
                         CYCMClient.sendLocalizedMessage("yt_api_error", Component.literal(String.valueOf(response.statusCode())));
                        isPolling = false;
                    }
                })
                .exceptionally(e -> {
                    Constants.LOGGER.error("Error fetching Live Chat ID", e);
                    isFetchingChatId = false;
                    isPolling = false;
                    return null;
                });
    }

    private static void startMessageLoop() {
        if (scheduler == null || scheduler.isShutdown()) {
             scheduler = Executors.newSingleThreadScheduledExecutor();
        }
        scheduler.schedule(YouTubeClient::pollMessages, 0, TimeUnit.MILLISECONDS);
    }

    private static void pollMessages() {
        if (!isPolling || activeLiveChatId == null) return;

        String apiKey = CYCMClient.configManager.getConfig().getYoutubeApiKey();
        String url = MESSAGES_API_URL + "?liveChatId=" + activeLiveChatId + "&part=snippet,authorDetails&key=" + apiKey;
        if (nextPageToken != null) {
            url += "&pageToken=" + nextPageToken;
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    long delay = 5000; 
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        if (json.has("pollingIntervalMillis")) {
                            delay = json.get("pollingIntervalMillis").getAsLong();
                        }
                        if (json.has("nextPageToken")) {
                            nextPageToken = json.get("nextPageToken").getAsString();
                        }
                        if (json.has("items")) {
                            processItems(json.getAsJsonArray("items"));
                        }
                    } else {
                         Constants.LOGGER.error("Error polling messages: " + response.statusCode());
                    }
                    
                    if (isPolling) {
                         scheduler.schedule(YouTubeClient::pollMessages, delay, TimeUnit.MILLISECONDS);
                    }
                })
                .exceptionally(e -> {
                    Constants.LOGGER.error("Error polling messages", e);
                     if (isPolling) {
                         scheduler.schedule(YouTubeClient::pollMessages, 5000, TimeUnit.MILLISECONDS);
                    }
                    return null;
                });
    }

    private static void processItems(JsonArray items) {
        for (int i = 0; i < items.size(); i++) {
            JsonObject item = items.get(i).getAsJsonObject();
            String messageId = item.get("id").getAsString(); 
            
            if (LiveStateManager.isProcessed(messageId)) {
                continue; 
            }
            
            String message = item.getAsJsonObject("snippet").get("displayMessage").getAsString();
            String author = item.getAsJsonObject("authorDetails").get("displayName").getAsString();

        CYCMClient.getInstance().processYouTubeMessage(author, message);
            LiveStateManager.markProcessed(messageId); 
        }
    }

    public static void sendMessageToChat(String message) {
        Constants.LOGGER.info("Attempting to send message to YouTube: " + message);
        if (activeLiveChatId == null) {
            if (!isFetchingChatId) {
                String apiKey = CYCMClient.configManager.getConfig().getYoutubeApiKey();
                String videoId = CYCMClient.configManager.getConfig().getYoutubeVideoId();
                if (apiKey != null && !apiKey.isEmpty() && videoId != null && !videoId.isEmpty()) {
                    isFetchingChatId = true;
                    pendingMessage = message;
                    fetchLiveChatId(videoId, apiKey);
                } else {
                    CYCMClient.sendLocalizedMessage("yt_not_configured");
                }
            } else {
                pendingMessage = message;
            }
            return;
        }
        
        final String cleanMessage = message.replaceAll("§[0-9a-fk-or]", "");
        
        ensureTokenValid().thenRun(() -> {
            String accessToken = LiveStateManager.getYoutubeAccessToken();
            if (accessToken == null || accessToken.isEmpty()) {
                CYCMClient.sendLocalizedMessage("yt_not_authorized");
                return;
            }

            JsonObject snippet = new JsonObject();
            snippet.addProperty("type", "textMessageEvent");
            snippet.addProperty("liveChatId", activeLiveChatId);

            JsonObject textMessageDetails = new JsonObject();
            textMessageDetails.addProperty("messageText", cleanMessage);
            snippet.add("textMessageDetails", textMessageDetails);

            JsonObject body = new JsonObject();
            body.add("snippet", snippet);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(MESSAGES_API_URL + "?part=snippet"))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200 || response.statusCode() == 204) {
                            Constants.LOGGER.info("Message successfully sent to YouTube.");
                        } else {
                            Constants.LOGGER.error("Failed to send message: " + response.statusCode() + " " + response.body());
                        }
                    });
        });
    }

    public static CompletableFuture<Void> ensureTokenValid() {
        String refreshToken = LiveStateManager.getYoutubeRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (System.currentTimeMillis() < LiveStateManager.getYoutubeTokenExpiry() - 60000) {
            return CompletableFuture.completedFuture(null);
        }

        return refreshAccessToken(refreshToken);
    }

    private static CompletableFuture<Void> refreshAccessToken(String refreshToken) {
        String clientId = CYCMClient.configManager.getConfig().getYoutubeClientId();
        String clientSecret = CYCMClient.configManager.getConfig().getYoutubeClientSecret();

        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        String body = String.format("client_id=%s&client_secret=%s&refresh_token=%s&grant_type=refresh_token",
                URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                URLEncoder.encode(refreshToken, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        String newAccessToken = json.get("access_token").getAsString();
                        long expiresIn = json.get("expires_in").getAsLong();
                        LiveStateManager.setYoutubeAccessToken(newAccessToken, System.currentTimeMillis() + expiresIn * 1000);
                        Constants.LOGGER.info("YouTube Access Token refreshed.");
                    } else {
                        Constants.LOGGER.error("Failed to refresh access token: " + response.statusCode() + " " + response.body());
                    }
                }).thenApply(v -> null);
    }

    public static void exchangeCodeForTokens(String code) {
        String clientId = CYCMClient.configManager.getConfig().getYoutubeClientId();
        String clientSecret = CYCMClient.configManager.getConfig().getYoutubeClientSecret();
        int port = CYCMClient.configManager.getConfig().getHttpMessagesPort();
        String redirectUri = "http://localhost:" + port;

        String body = String.format("code=%s&client_id=%s&client_secret=%s&redirect_uri=%s&grant_type=authorization_code",
                URLEncoder.encode(code, StandardCharsets.UTF_8),
                URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                URLEncoder.encode(clientSecret, StandardCharsets.UTF_8),
                URLEncoder.encode(redirectUri, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        String accessToken = json.get("access_token").getAsString();
                        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                        long expiresIn = json.get("expires_in").getAsLong();
                        
                        LiveStateManager.setYoutubeAccessToken(accessToken, System.currentTimeMillis() + expiresIn * 1000);
                        if (refreshToken != null) {
                            LiveStateManager.setYoutubeRefreshToken(refreshToken);
                        }
                        CYCMClient.sendLocalizedMessage("yt_auth_success");
                        Constants.LOGGER.info("YouTube OAuth2 authentication successful.");
                    } else {
                        Constants.LOGGER.error("Failed to exchange code: " + response.statusCode() + " " + response.body());
                        CYCMClient.sendLocalizedMessage("yt_auth_error", response.statusCode());
                    }
                    
                    // Stop server if we are in API mode (it was started temporarily)
                    if (CYCMClient.configManager.getConfig().getChatMode() == ModConfig.ChatMode.API) {
                        HttpChatServer.stopServer();
                    }
                });
    }

    public static String getAuthUrl() {
        String clientId = CYCMClient.configManager.getConfig().getYoutubeClientId();
        int port = CYCMClient.configManager.getConfig().getHttpMessagesPort();
        String redirectUri = "http://localhost:" + port;
        String scope = "https://www.googleapis.com/auth/youtube.force-ssl";
        
        return String.format("https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=%s&access_type=offline&prompt=consent",
                clientId, redirectUri, scope);
    }
}

