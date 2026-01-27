package org.craftllc.minecraft.mod.cycm.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.craftllc.minecraft.mod.cycm.Constants;
import org.craftllc.minecraft.mod.cycm.CYCMClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.text.Text;

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
        Constants.LOGGER.info("YouTube Chat polling stopped.");
    }

    private static void fetchLiveChatId(String videoId, String apiKey) {
        String url = VIDEOS_API_URL + "?part=liveStreamingDetails&id=" + videoId + "&key=" + apiKey;
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        if (json.has("items") && json.getAsJsonArray("items").size() > 0) {
                            JsonObject item = json.getAsJsonArray("items").get(0).getAsJsonObject();
                            if (item.has("liveStreamingDetails")) {
                                JsonObject details = item.getAsJsonObject("liveStreamingDetails");
                                if (details.has("activeLiveChatId")) {
                                    activeLiveChatId = details.get("activeLiveChatId").getAsString();
                                    Constants.LOGGER.info("Found Live Chat ID: " + activeLiveChatId);
                                    startMessageLoop();
                                    return;
                                }
                            }
                        }
                        CYCMClient.sendLocalizedMessage("yt_no_chat_id");
                        isPolling = false;
                    } else {
                        Constants.LOGGER.error("Failed to fetch video details: " + response.statusCode());
                         CYCMClient.sendLocalizedMessage("yt_api_error", Text.literal(String.valueOf(response.statusCode())));
                        isPolling = false;
                    }
                })
                .exceptionally(e -> {
                    Constants.LOGGER.error("Error fetching Live Chat ID", e);
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
}
