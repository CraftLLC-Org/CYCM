package org.craftllc.minecraft.mod.cycm.config;

import java.util.ArrayList;
import java.util.List;

// Клас, що представляє структуру JSON-файлу конфігурації
public class ModConfig {

    private boolean modEnabled = true;
    private boolean youtubeEnabled = true;
    private String youtubeApiKey = "";
    private String youtubeVideoId = "";
    private ChatMode chatMode = ChatMode.API;
    private int httpMessagesPort = 21456;  // Renamed from httpPort
    private String youtubeClientId = "";
    private String youtubeClientSecret = "";
    private boolean youtubeSendEnabled = false;
    private boolean telegramEnabled = false;
    private String telegramToken = "";
    private boolean groupingMessages = true;
    private boolean actionbarEnabled = false;
    private boolean compatibilityMode = false;  // Add minecraft: prefix to commands
    private boolean webUIEnabled = false;
    private int webUIPort = 21457;
    private List<String> blockedCommands = new ArrayList<>();
    private int maxRepeats = 20;
    private int maxDelaySeconds = 5;
    private int maxTntCount = 20;
    private int maxTntRadius = 8;

    public enum ChatMode {
        API,
        HTTP
    }

    public ModConfig() {
        // Конструктор за замовчуванням для Gson
    }

    public boolean isModEnabled() {
        return modEnabled;
    }

    public void setModEnabled(boolean modEnabled) {
        this.modEnabled = modEnabled;
    }

    public boolean isYoutubeEnabled() {
        return youtubeEnabled;
    }

    public void setYoutubeEnabled(boolean youtubeEnabled) {
        this.youtubeEnabled = youtubeEnabled;
    }

    public String getYoutubeApiKey() {
        return youtubeApiKey;
    }

    public void setYoutubeApiKey(String youtubeApiKey) {
        this.youtubeApiKey = youtubeApiKey;
    }

    public String getYoutubeVideoId() {
        return youtubeVideoId;
    }

    public void setYoutubeVideoId(String youtubeVideoId) {
        this.youtubeVideoId = youtubeVideoId;
    }

    public ChatMode getChatMode() {
        return chatMode;
    }

    public void setChatMode(ChatMode chatMode) {
        this.chatMode = chatMode;
    }

    public int getHttpMessagesPort() {
        return httpMessagesPort;
    }

    public void setHttpMessagesPort(int httpMessagesPort) {
        this.httpMessagesPort = httpMessagesPort;
    }

    public boolean isTelegramEnabled() {
        return telegramEnabled;
    }

    public void setTelegramEnabled(boolean telegramEnabled) {
        this.telegramEnabled = telegramEnabled;
    }

    public String getTelegramToken() {
        return telegramToken;
    }

    public void setTelegramToken(String telegramToken) {
        this.telegramToken = telegramToken;
    }

    public boolean isGroupingMessages() {
        return groupingMessages;
    }

    public void setGroupingMessages(boolean groupingMessages) {
        this.groupingMessages = groupingMessages;
    }

    public boolean isActionbarEnabled() {
        return actionbarEnabled;
    }

    public void setActionbarEnabled(boolean actionbarEnabled) {
        this.actionbarEnabled = actionbarEnabled;
    }

    public String getYoutubeClientId() {
        return youtubeClientId;
    }

    public void setYoutubeClientId(String youtubeClientId) {
        this.youtubeClientId = youtubeClientId;
    }

    public String getYoutubeClientSecret() {
        return youtubeClientSecret;
    }

    public void setYoutubeClientSecret(String youtubeClientSecret) {
        this.youtubeClientSecret = youtubeClientSecret;
    }

    public boolean isYoutubeSendEnabled() {
        return youtubeSendEnabled;
    }

    public void setYoutubeSendEnabled(boolean youtubeSendEnabled) {
        this.youtubeSendEnabled = youtubeSendEnabled;
    }

    public boolean isCompatibilityMode() {
        return compatibilityMode;
    }

    public void setCompatibilityMode(boolean compatibilityMode) {
        this.compatibilityMode = compatibilityMode;
    }

    public boolean isWebUIEnabled() {
        return webUIEnabled;
    }

    public void setWebUIEnabled(boolean webUIEnabled) {
        this.webUIEnabled = webUIEnabled;
    }

    public int getWebUIPort() {
        return webUIPort;
    }

    public void setWebUIPort(int webUIPort) {
        this.webUIPort = webUIPort;
    }

    public List<String> getBlockedCommands() {
        if (blockedCommands == null) {
            blockedCommands = new ArrayList<>();
        }
        return blockedCommands;
    }

    public void setBlockedCommands(List<String> blockedCommands) {
        this.blockedCommands = blockedCommands == null ? new ArrayList<>() : new ArrayList<>(blockedCommands);
    }

    public int getMaxRepeats() {
        return maxRepeats;
    }

    public void setMaxRepeats(int maxRepeats) {
        this.maxRepeats = maxRepeats;
    }

    public int getMaxDelaySeconds() {
        return maxDelaySeconds;
    }

    public void setMaxDelaySeconds(int maxDelaySeconds) {
        this.maxDelaySeconds = maxDelaySeconds;
    }

    public int getMaxTntCount() {
        return maxTntCount;
    }

    public void setMaxTntCount(int maxTntCount) {
        this.maxTntCount = maxTntCount;
    }

    public int getMaxTntRadius() {
        return maxTntRadius;
    }

    public void setMaxTntRadius(int maxTntRadius) {
        this.maxTntRadius = maxTntRadius;
    }
}
