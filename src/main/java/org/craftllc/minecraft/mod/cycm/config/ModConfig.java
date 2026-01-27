package org.craftllc.minecraft.mod.cycm.config;

// Клас, що представляє структуру JSON-файлу конфігурації
public class ModConfig {

    private boolean modEnabled = true;
    private boolean youtubeEnabled = true;
    private String youtubeApiKey = "";
    private String youtubeVideoId = "";
    private ChatMode chatMode = ChatMode.API;
    private int httpPort = 21456;
    private boolean telegramEnabled = false;
    private String telegramToken = "";
    private boolean groupingMessages = true;
    private boolean actionbarEnabled = false;

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

    public int getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(int httpPort) {
        this.httpPort = httpPort;
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
}
