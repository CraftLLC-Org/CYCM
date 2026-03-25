package org.craftllc.minecraft.mod.cycm.youtube;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.craftllc.minecraft.mod.cycm.Constants;
import org.craftllc.minecraft.mod.cycm.CYCMClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class LiveStateManager {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static LiveState state = new LiveState();

    private static Path getLiveStateFile() {
        return CYCMClient.configManager.getModConfigDir().resolve("live.json");
    }

    public static class LiveState {
        public String lastVideoId = "";
        public Set<String> processedMessageIds = new HashSet<>();
        public long lastTelegramUpdateId = 0;
        public String youtubeAccessToken = "";
        public String youtubeRefreshToken = "";
        public long youtubeTokenExpiry = 0;
    }

    public static void load() {
        Path path = getLiveStateFile();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                state = gson.fromJson(reader, LiveState.class);
                if (state == null)
                    state = new LiveState();
                if (state.processedMessageIds == null)
                    state.processedMessageIds = new HashSet<>();
            } catch (IOException e) {
                Constants.LOGGER.error("Failed to load live state", e);
                state = new LiveState();
            }
        } else {
            state = new LiveState();
        }
    }

    public static void save() {
        try {
            Path path = getLiveStateFile();
            if (!Files.exists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                gson.toJson(state, writer);
            }
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to save live state", e);
        }
    }

    public static void checkVideoId(String currentVideoId) {
        if (!currentVideoId.equals(state.lastVideoId)) {
            Constants.LOGGER.info("Video ID changed from '{}' to '{}'. Clearing processed messages.", state.lastVideoId,
                    currentVideoId);
            state.lastVideoId = currentVideoId;
            state.processedMessageIds.clear();
            save();
        }
    }

    public static boolean isProcessed(String messageId) {
        return state.processedMessageIds.contains(messageId);
    }

    public static void markProcessed(String messageId) {
        state.processedMessageIds.add(messageId);
        save();
    }

    public static long getLastTelegramUpdateId() {
        return state.lastTelegramUpdateId;
    }

    public static void setLastTelegramUpdateId(long id) {
        state.lastTelegramUpdateId = id;
        save();
    }

    public static int getProcessedCount() {
        return state.processedMessageIds == null ? 0 : state.processedMessageIds.size();
    }

    public static String getYoutubeAccessToken() {
        return state.youtubeAccessToken;
    }

    public static void setYoutubeAccessToken(String token, long expiry) {
        state.youtubeAccessToken = token;
        state.youtubeTokenExpiry = expiry;
        save();
    }

    public static String getYoutubeRefreshToken() {
        return state.youtubeRefreshToken;
    }

    public static void setYoutubeRefreshToken(String token) {
        state.youtubeRefreshToken = token;
        save();
    }

    public static long getYoutubeTokenExpiry() {
        return state.youtubeTokenExpiry;
    }
}
