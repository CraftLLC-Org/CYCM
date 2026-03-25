package org.craftllc.minecraft.mod.cycm.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.network.chat.Component;
import org.craftllc.minecraft.mod.cycm.Constants;
import org.craftllc.minecraft.mod.cycm.CYCMClient;
import org.craftllc.minecraft.mod.cycm.youtube.YouTubeClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class WebUIServer {
    private static final Gson gson = new Gson();
    private static HttpServer server;

    public static void startServer(int port) {
        stopServer();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new WebUIHandler());
            server.createContext("/api/config", new ConfigHandler());
            server.createContext("/api/blocked", new BlockedCommandsHandler());
            server.createContext("/api/repeating", new RepeatingHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/connect_youtube", new ConnectYouTubeHandler());
            server.setExecutor(null);
            server.start();
            Constants.LOGGER.info("Web UI Server started on port: " + port);
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to start Web UI Server", e);
        }
    }

    public static void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private static void applyWebUIRuntimeChange(boolean shouldBeEnabled, int port) {
        Thread worker = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (shouldBeEnabled) {
                startServer(port);
            } else {
                stopServer();
            }
        }, "cycm-webui-runtime");
        worker.setDaemon(true);
        worker.start();
    }

    static class WebUIHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())){
                String response = buildHTMLPage();
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            exchange.close();
        }

        private String buildHTMLPage() {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html lang=\"en\">");
            html.append("<head>\n");
            html.append("    <meta charset=\"UTF-8\">");
            html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">");
            html.append("    <title>CYCM - Control Panel</title>");
            html.append("    <style>");
            // Darker Theme CSS
            html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }");
            html.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%); min-height: 100vh; padding: 20px; color: #e2e8f0; }");
            html.append("        .container { max-width: 1400px; margin: 0 auto; }");
            html.append("        .header { display: flex; justify-content: space-between; align-items: center; padding: 30px; background: rgba(30, 41, 59, 0.7); backdrop-filter: blur(10px); border-radius: 20px; margin-bottom: 30px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); border: 1px solid rgba(255, 255, 255, 0.05); }");
            html.append("        .header-title h1 { font-size: 2.5em; font-weight: 700; margin-bottom: 5px; background: linear-gradient(to right, #60a5fa, #a78bfa); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }");
            html.append("        .header-title p { font-size: 1.1em; color: #94a3b8; }");
            html.append("        .lang-select { padding: 8px 12px; background: rgba(15, 23, 42, 0.6); border: 1px solid rgba(255, 255, 255, 0.1); color: #e2e8f0; border-radius: 8px; cursor: pointer; }");
            html.append("        .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(350px, 1fr)); gap: 25px; }");
            html.append("        .card { background: rgba(30, 41, 59, 0.6); backdrop-filter: blur(10px); border-radius: 16px; padding: 25px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1); border: 1px solid rgba(255, 255, 255, 0.05); transition: transform 0.2s; }");
            html.append("        .card:hover { transform: translateY(-2px); box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); }");
            html.append("        .card h2 { font-size: 1.5em; margin-bottom: 20px; padding-bottom: 15px; border-bottom: 1px solid rgba(255, 255, 255, 0.1); color: #f8fafc; }");
            html.append("        .setting-group { margin-bottom: 20px; }");
            html.append("        .setting-group label { display: block; margin-bottom: 8px; font-weight: 500; color: #cbd5e1; }");
            html.append("        input[type=\"text\"], input[type=\"number\"], input[type=\"password\"], select { width: 100%; padding: 12px; background: rgba(15, 23, 42, 0.6); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 8px; color: #fff; font-size: 0.95em; transition: all 0.2s; }");
            html.append("        input:focus, select:focus { outline: none; border-color: #60a5fa; box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.2); }");
            html.append("        input::placeholder { color: #475569; }");
            html.append("        .toggle-switch { display: flex; align-items: center; justify-content: space-between; padding: 12px 0; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }");
            html.append("        .toggle-switch:last-child { border-bottom: none; }");
            html.append("        .switch { position: relative; width: 50px; height: 26px; }");
            html.append("        .switch input { opacity: 0; width: 0; height: 0; }");
            html.append("        .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #334155; transition: .4s; border-radius: 34px; }");
            html.append("        .slider:before { position: absolute; content: \"\"; height: 18px; width: 18px; left: 4px; bottom: 4px; background-color: white; transition: .4s; border-radius: 50%; }");
            html.append("        input:checked + .slider { background-color: #3b82f6; }");
            html.append("        input:checked + .slider:before { transform: translateX(24px); }");
            html.append("        .btn { padding: 12px 24px; border: none; border-radius: 8px; color: #fff; font-size: 1em; font-weight: 600; cursor: pointer; transition: all 0.2s; width: 100%; text-align: center; }");
            html.append("        .btn:hover { filter: brightness(110%); transform: translateY(-1px); }");
            html.append("        .btn:active { transform: translateY(0); }");
            html.append("        .btn-primary { background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%); }");
            html.append("        .btn-danger { background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%); }");
            html.append("        .btn-warning { background: linear-gradient(135deg, #f59e0b 0%, #d97706 100%); color: #fff; margin-top: 10px; }");
            html.append("        .blocked-commands { max-height: 200px; overflow-y: auto; background: rgba(15, 23, 42, 0.4); border-radius: 8px; padding: 10px; margin-top: 10px; border: 1px solid rgba(255, 255, 255, 0.05); }");
            html.append("        .cmd-item { display: flex; justify-content: space-between; align-items: center; padding: 8px; margin: 5px 0; background: rgba(30, 41, 59, 0.5); border-radius: 6px; }");
            html.append("        .notification { position: fixed; top: 20px; right: 20px; padding: 15px 25px; border-radius: 8px; background: #22c55e; color: #fff; font-weight: 600; box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1); display: none; z-index: 1000; }");
            html.append("        .save-bar { position: fixed; bottom: 0; left: 0; right: 0; background: rgba(15, 23, 42, 0.9); backdrop-filter: blur(10px); padding: 20px; text-align: center; border-top: 1px solid rgba(255, 255, 255, 0.1); }");
            html.append("        .save-bar button { width: auto; min-width: 200px; }");
            html.append("    </style>");
            html.append("</head>");
            html.append("<body>");
            html.append("    <div class=\"container\">");
            html.append("        <div class=\"header\">");
            html.append("            <div class=\"header-title\">");
            html.append("                <h1>CYCM Control</h1>");
            html.append("                <p data-i18n=\"subtitle\">Chat from YouTube to Minecraft</p>");
            html.append("            </div>");
            html.append("            <select id=\"langSelect\" class=\"lang-select\" onchange=\"changeLanguage(this.value)\">");
            html.append("                <option value=\"en\">English</option>");
            html.append("                <option value=\"ua\">Українська</option>");
            html.append("                <option value=\"ru\">Русский</option>");
            html.append("            </select>");
            html.append("        </div>");
            
            html.append("        <div class=\"grid\">");
            
            // Mod Status Card
            html.append("            <div class=\"card\">");
            html.append("                <h2 data-i18n=\"modStatus\">Mod Status</h2>");
            addToggle(html, "modEnabled", "modEnabled", CYCMClient.configManager.getConfig().isModEnabled());
            addToggle(html, "compatibilityMode", "compatibilityMode", CYCMClient.configManager.getConfig().isCompatibilityMode());
            addToggle(html, "webUIEnabled", "webUIEnabled", CYCMClient.configManager.getConfig().isWebUIEnabled());
            addToggle(html, "groupingMessages", "groupingMessages", CYCMClient.configManager.getConfig().isGroupingMessages());
            addToggle(html, "actionbarEnabled", "actionbarEnabled", CYCMClient.configManager.getConfig().isActionbarEnabled());
            html.append("            </div>");
            
            // Sources Card
            html.append("            <div class=\"card\">");
            html.append("                <h2 data-i18n=\"sources\">Sources</h2>");
            addToggle(html, "youtubeEnabled", "youtubeEnabled", CYCMClient.configManager.getConfig().isYoutubeEnabled());
            addToggle(html, "telegramEnabled", "telegramEnabled", CYCMClient.configManager.getConfig().isTelegramEnabled());
            addToggle(html, "youtubeSendEnabled", "youtubeSendEnabled", CYCMClient.configManager.getConfig().isYoutubeSendEnabled());
            html.append("                <div class=\"setting-group\" style=\"margin-top: 15px;\">");
            html.append("                    <label data-i18n=\"chatMode\">Chat Mode</label>");
            html.append("                    <select id=\"chatMode\">");
            html.append("                        <option value=\"API\"" + (CYCMClient.configManager.getConfig().getChatMode() == org.craftllc.minecraft.mod.cycm.config.ModConfig.ChatMode.API ? " selected" : "") + ">API</option>");
            html.append("                        <option value=\"HTTP\"" + (CYCMClient.configManager.getConfig().getChatMode() == org.craftllc.minecraft.mod.cycm.config.ModConfig.ChatMode.HTTP ? " selected" : "") + ">HTTP</option>");
            html.append("                    </select>");
            html.append("                </div>");
            html.append("            </div>");
            
            // YouTube Settings Card
            html.append("            <div class=\"card\">");
            html.append("                <h2 data-i18n=\"youtubeSettings\">YouTube Settings</h2>");
            addInput(html, "apiKey", "youtubeApiKey", "password", "enterApiKey", CYCMClient.configManager.getConfig().getYoutubeApiKey());
            addInput(html, "videoId", "youtubeVideoId", "text", "enterVideoId", CYCMClient.configManager.getConfig().getYoutubeVideoId());
            addInput(html, "clientId", "youtubeClientId", "text", "enterClientId", CYCMClient.configManager.getConfig().getYoutubeClientId());
            addInput(html, "clientSecret", "youtubeClientSecret", "password", "enterClientSecret", CYCMClient.configManager.getConfig().getYoutubeClientSecret());
            html.append("                <button class=\"btn btn-warning\" onclick=\"connectYouTube()\" data-i18n=\"connectYoutube\">Connect YouTube</button>");
            html.append("            </div>");
            
            // Telegram Settings Card
            html.append("            <div class=\"card\">");
            html.append("                <h2 data-i18n=\"telegramSettings\">Telegram Settings</h2>");
            addInput(html, "botToken", "telegramToken", "password", "enterBotToken", CYCMClient.configManager.getConfig().getTelegramToken());
            html.append("            </div>");
            
            // Ports Configuration Card
            html.append("            <div class=\"card\">");
            html.append("                <h2 data-i18n=\"portsConfig\">Ports Configuration</h2>");
            addInput(html, "httpPort", "httpMessagesPort", "number", "21456", String.valueOf(CYCMClient.configManager.getConfig().getHttpMessagesPort()));
            addInput(html, "webUIPort", "webUIPort", "number", "21457", String.valueOf(CYCMClient.configManager.getConfig().getWebUIPort()));
            html.append("            </div>");
            
            // Command Limits Card
            html.append("            <div class=\"card\">");
            html.append("                <h2 data-i18n=\"commandLimits\">Command Limits</h2>");
            addInput(html, "maxRepeats", "maxRepeats", "number", "enterMaxRepeats", String.valueOf(CYCMClient.getMaxRepeats()));
            addInput(html, "maxDelay", "maxDelay", "number", "enterMaxDelay", String.valueOf(CYCMClient.getMaxDelaySeconds()));
            addInput(html, "maxTntCount", "maxTntCount", "number", "enterMaxTntCount", String.valueOf(CYCMClient.getMaxTntCount()));
            addInput(html, "maxTntRadius", "maxTntRadius", "number", "enterMaxTntRadius", String.valueOf(CYCMClient.getMaxTntRadius()));
            html.append("            </div>");
            
            // Blocked Commands Card
            html.append("            <div class=\"card\" style=\"grid-column: span 2;\">");
            html.append("                <h2 data-i18n=\"blockedCommands\">Blocked Commands</h2>");
            html.append("                <div class=\"setting-group\">");
            html.append("                    <input type=\"text\" id=\"newBlockedCmd\" placeholder=\"/op\" data-i18n-placeholder=\"blockCmdPlaceholder\">");
            html.append("                    <button class=\"btn btn-primary\" onclick=\"addBlockedCommand()\" style=\"margin-top: 10px; width: 100%;\" data-i18n=\"addBlockedCmd\">Add Blocked Command</button>");
            html.append("                </div>");
            html.append("                <div class=\"blocked-commands\" id=\"blockedCommandsList\"></div>");
            html.append("            </div>");
            
            html.append("        </div>");
            html.append("        <div style=\"height: 40px;\"></div>  </div>"); // Reduced spacer
            
            html.append("    <div class=\"notification\" id=\"notification\"></div>");
            
            // Add JavaScript
            html.append(getJavaScript());
            
            html.append("</body>");
            html.append("</html>");
            
            return html.toString();
        }
        
        private void addToggle(StringBuilder html, String labelKey, String id, boolean checked) {
            html.append("                <div class=\"toggle-switch\">");
            html.append("                    <span data-i18n=\"").append(labelKey).append("\">").append(labelKey).append("</span>");
            html.append("                    <label class=\"switch\">");
            html.append("                        <input type=\"checkbox\" id=\"").append(id).append("\"").append(checked ? " checked" : "").append(">");
            html.append("                        <span class=\"slider\"></span>");
            html.append("                    </label>");
            html.append("                </div>");
        }
        
        private void addInput(StringBuilder html, String labelKey, String id, String type, String placeholderKey, String value) {
            html.append("                <div class=\"setting-group\">");
            html.append("                    <label data-i18n=\"").append(labelKey).append("\">").append(labelKey).append("</label>");
            html.append("                    <input type=\"" ).append(type).append("\" id=\"" ).append(id).append("\" data-i18n-placeholder=\"" ).append(placeholderKey).append("\" value=\"").append(value).append("\">");
            html.append("                </div>");
        }
        
        private String getJavaScript() {
            return "<script>\n" +
                    "const API_BASE = '';\n" +
                    "const translations = {\n" +
                    "    en: {\n" +
                    "        subtitle: 'Chat from YouTube to Minecraft',\n" +
                    "        modStatus: 'Mod Status',\n" +
                    "        modEnabled: 'Mod Enabled',\n" +
                    "        compatibilityMode: 'Compatibility Mode',\n" +
                    "        webUIEnabled: 'Web UI Enabled',\n" +
                    "        groupingMessages: 'Grouping Messages',\n" +
                    "        actionbarEnabled: 'Actionbar Enabled',\n" +
                    "        sources: 'Sources',\n" +
                    "        youtubeEnabled: 'YouTube Enabled',\n" +
                    "        telegramEnabled: 'Telegram Enabled',\n" +
                    "        youtubeSendEnabled: 'YouTube Send',\n" +
                    "        chatMode: 'Chat Mode',\n" +
                    "        youtubeSettings: 'YouTube Settings',\n" +
                    "        apiKey: 'API Key',\n" +
                    "        enterApiKey: 'Enter YouTube API Key',\n" +
                    "        videoId: 'Video ID',\n" +
                    "        enterVideoId: 'Enter YouTube Video ID',\n" +
                    "        clientId: 'OAuth2 Client ID',\n" +
                    "        enterClientId: 'Enter OAuth2 Client ID',\n" +
                    "        clientSecret: 'OAuth2 Client Secret',\n" +
                    "        enterClientSecret: 'Enter OAuth2 Client Secret',\n" +
                    "        connectYoutube: 'Connect YouTube',\n" +
                    "        telegramSettings: 'Telegram Settings',\n" +
                    "        botToken: 'Bot Token',\n" +
                    "        enterBotToken: 'Enter Telegram Bot Token',\n" +
                    "        portsConfig: 'Ports Configuration',\n" +
                    "        httpPort: 'HTTP Messages Port',\n" +
                    "        webUIPort: 'Web UI Port',\n" +
                    "        commandLimits: 'Command Limits',\n" +
                    "        maxRepeats: 'Max Repeats',\n" +
                    "        enterMaxRepeats: 'Enter max repeats',\n" +
                    "        maxDelay: 'Max Delay (s)',\n" +
                    "        enterMaxDelay: 'Enter max delay',\n" +
                    "        maxTntCount: 'Max TNT Count',\n" +
                    "        enterMaxTntCount: 'Enter max TNT count',\n" +
                    "        maxTntRadius: 'Max TNT Radius',\n" +
                    "        enterMaxTntRadius: 'Enter max TNT radius',\n" +
                    "        blockedCommands: 'Blocked Commands',\n" +
                    "        blockCmdPlaceholder: 'Enter command (e.g., /op)',\n" +
                    "        addBlockedCmd: 'Add Blocked Command',\n" +
                    "        saved: '✅ Settings saved!',\n" +
                    "        error: '❌ Error!',\n" +
                    "        remove: 'Remove',\n" +
                    "        cmdBlocked: '✅ Command blocked!',\n" +
                    "        cmdUnblocked: '✅ Command unblocked!'\n" +
                    "    },\n" +
                    "    ua: {\n" +
                    "        subtitle: 'Чат з YouTube в Minecraft',\n" +
                    "        modStatus: 'Статус Мода',\n" +
                    "        modEnabled: 'Мод Увімкнено',\n" +
                    "        compatibilityMode: 'Режим Сумісності',\n" +
                    "        webUIEnabled: 'Web UI Увімкнено',\n" +
                    "        groupingMessages: 'Групування Повідомлень',\n" +
                    "        actionbarEnabled: 'Actionbar Увімкнено',\n" +
                    "        sources: 'Джерела',\n" +
                    "        youtubeEnabled: 'YouTube Увімкнено',\n" +
                    "        telegramEnabled: 'Telegram Увімкнено',\n" +
                    "        youtubeSendEnabled: 'YouTube Надсилання',\n" +
                    "        chatMode: 'Режим Чату',\n" +
                    "        youtubeSettings: 'Налаштування YouTube',\n" +
                    "        apiKey: 'API Ключ',\n" +
                    "        enterApiKey: 'Введіть YouTube API Key',\n" +
                    "        videoId: 'ID Відео',\n" +
                    "        enterVideoId: 'Введіть YouTube Video ID',\n" +
                    "        clientId: 'OAuth2 Client ID',\n" +
                    "        enterClientId: 'Введіть OAuth2 Client ID',\n" +
                    "        clientSecret: 'OAuth2 Client Secret',\n" +
                    "        enterClientSecret: 'Введіть OAuth2 Client Secret',\n" +
                    "        connectYoutube: 'Підключити YouTube',\n" +
                    "        telegramSettings: 'Налаштування Telegram',\n" +
                    "        botToken: 'Бот Токен',\n" +
                    "        enterBotToken: 'Введіть Telegram Bot Token',\n" +
                    "        portsConfig: 'Налаштування Портів',\n" +
                    "        httpPort: 'Порт HTTP Повідомлень',\n" +
                    "        webUIPort: 'Web UI Порт',\n" +
                    "        commandLimits: 'Ліміти Команд',\n" +
                    "        maxRepeats: 'Макс. Повторів',\n" +
                    "        enterMaxRepeats: 'Введіть макс. повторів',\n" +
                    "        maxDelay: 'Макс. Затримка (с)',\n" +
                    "        enterMaxDelay: 'Введіть макс. затримку',\n" +
                    "        blockedCommands: 'Заблоковані Команди',\n" +
                    "        blockCmdPlaceholder: 'Введіть команду (напр., /op)',\n" +
                    "        addBlockedCmd: 'Заблокувати Комунду',\n" +
                    "        saved: '✅ Налаштування збережено!',\n" +
                    "        error: '❌ Помилка!',\n" +
                    "        remove: 'Видалити',\n" +
                    "        cmdBlocked: '✅ Команду заблоковано!',\n" +
                    "        cmdUnblocked: '✅ Команду розблоковано!'\n" +
                    "    },\n" +
                    "    ru: {\n" +
                    "        subtitle: 'Чат из YouTube в Minecraft',\n" +
                    "        modStatus: 'Статус Мода',\n" +
                    "        modEnabled: 'Мод Включен',\n" +
                    "        compatibilityMode: 'Режим Совместимости',\n" +
                    "        webUIEnabled: 'Web UI Включен',\n" +
                    "        groupingMessages: 'Группировка Сообщений',\n" +
                    "        actionbarEnabled: 'Actionbar Включен',\n" +
                    "        sources: 'Источники',\n" +
                    "        youtubeEnabled: 'YouTube Включен',\n" +
                    "        telegramEnabled: 'Telegram Включен',\n" +
                    "        youtubeSendEnabled: 'YouTube Отправка',\n" +
                    "        chatMode: 'Режим Чата',\n" +
                    "        youtubeSettings: 'Настройки YouTube',\n" +
                    "        apiKey: 'API Ключ',\n" +
                    "        enterApiKey: 'Введите YouTube API Key',\n" +
                    "        videoId: 'ID Видео',\n" +
                    "        enterVideoId: 'Введите YouTube Video ID',\n" +
                    "        clientId: 'OAuth2 Client ID',\n" +
                    "        enterClientId: 'Введите OAuth2 Client ID',\n" +
                    "        clientSecret: 'OAuth2 Client Secret',\n" +
                    "        enterClientSecret: 'Введите OAuth2 Client Secret',\n" +
                    "        connectYoutube: 'Подключить YouTube',\n" +
                    "        telegramSettings: 'Настройки Telegram',\n" +
                    "        botToken: 'Бот Токен',\n" +
                    "        enterBotToken: 'Введите Telegram Bot Token',\n" +
                    "        portsConfig: 'Настройки Портов',\n" +
                    "        httpPort: 'Порт HTTP Сообщений',\n" +
                    "        webUIPort: 'Web UI Порт',\n" +
                    "        commandLimits: 'Лимиты Команд',\n" +
                    "        maxRepeats: 'Макс. Повторов',\n" +
                    "        enterMaxRepeats: 'Введите макс. повторов',\n" +
                    "        maxDelay: 'Макс. Задержка (с)',\n" +
                    "        enterMaxDelay: 'Введите макс. задержку',\n" +
                    "        blockedCommands: 'Заблокированные Команды',\n" +
                    "        blockCmdPlaceholder: 'Введите команду (напр., /op)',\n" +
                    "        addBlockedCmd: 'Заблокировать Команду',\n" +
                    "        saved: '✅ Настройки сохранены!',\n" +
                    "        error: '❌ Ошибка!',\n" +
                    "        remove: 'Удалить',\n" +
                    "        cmdBlocked: '✅ Команда заблокирована!',\n" +
                    "        cmdUnblocked: '✅ Команда разблокирована!'\n" +
                    "    }\n" +
                    "};" +
                    "let currentLang = 'en';" +
                    "let saveTimeout;" +
                    "let syncInterval;" +
                    "window.addEventListener('DOMContentLoaded', () => { " +
                    "    loadConfig().then(() => { " +
                    "        addAutoSaveListeners();" +
                    "    });" +
                    "    loadBlockedCommands(); " +
                    "    loadRepeatingSettings(); " +
                    "    startStatusSync(); " +
                    "    changeLanguage('en');" +
                    "});" +
                    "function addAutoSaveListeners() {" +
                    "    document.querySelectorAll('input, select').forEach(el => {" +
                    "        if (el.id === 'newBlockedCmd') return;" +
                    "        const eventType = (el.type === 'checkbox' || el.tagName === 'SELECT') ? 'change' : 'input';" +
                    "        el.addEventListener(eventType, () => {" +
                    "            if (saveTimeout) clearTimeout(saveTimeout);" +
                    "            saveTimeout = setTimeout(saveAllSettings, 500);" +
                    "        });" +
                    "    });" +
                    "}" +
                    "function changeLanguage(lang) {" +
                    "    currentLang = lang;" +
                    "    document.querySelectorAll('[data-i18n]').forEach(el => {" +
                    "        const key = el.getAttribute('data-i18n');" +
                    "        if (translations[lang][key]) el.textContent = translations[lang][key];" +
                    "    });" +
                    "    document.querySelectorAll('[data-i18n-placeholder]').forEach(el => {" +
                    "        const key = el.getAttribute('data-i18n-placeholder');" +
                    "        if (translations[lang][key]) el.placeholder = translations[lang][key];" +
                    "    });" +
                    "}" +
                    "async function loadConfig() {" +
                    "    try {" +
                    "        const response = await fetch(API_BASE + '/api/config');" +
                    "        const config = await response.json();" +
                    "        document.getElementById('modEnabled').checked = config.modEnabled;" +
                    "        document.getElementById('youtubeEnabled').checked = config.youtubeEnabled;" +
                    "        document.getElementById('youtubeApiKey').value = config.youtubeApiKey || '';" +
                    "        document.getElementById('youtubeVideoId').value = config.youtubeVideoId || '';" +
                    "        document.getElementById('chatMode').value = config.chatMode;" +
                    "        document.getElementById('httpMessagesPort').value = config.httpMessagesPort;" +
                    "        document.getElementById('youtubeClientId').value = config.youtubeClientId || '';" +
                    "        document.getElementById('youtubeClientSecret').value = config.youtubeClientSecret || '';" +
                    "        document.getElementById('youtubeSendEnabled').checked = config.youtubeSendEnabled;" +
                    "        document.getElementById('telegramEnabled').checked = config.telegramEnabled;" +
                    "        document.getElementById('telegramToken').value = config.telegramToken || '';" +
                    "        document.getElementById('groupingMessages').checked = config.groupingMessages;" +
                    "        document.getElementById('actionbarEnabled').checked = config.actionbarEnabled;" +
                    "        document.getElementById('compatibilityMode').checked = config.compatibilityMode;" +
                    "        document.getElementById('webUIEnabled').checked = config.webUIEnabled;" +
                    "        document.getElementById('webUIPort').value = config.webUIPort;" +
                    "    } catch (error) { console.error('Error loading config:', error); }" +
                    "}" +
                    "async function loadBlockedCommands() {" +
                    "    try {" +
                    "        const response = await fetch(API_BASE + '/api/blocked');" +
                    "        const commands = await response.json();" +
                    "        const list = document.getElementById('blockedCommandsList');" +
                    "        list.innerHTML = '';" +
                    "        commands.forEach(cmd => {" +
                    "            const item = document.createElement('div');" +
                    "            item.className = 'cmd-item';" +
                    "            item.innerHTML = `<span>/${cmd}</span><button class=\"btn btn-danger\" style=\"padding: 5px 15px; width: auto;\" onclick=\"removeBlockedCommand('${cmd}')\">${translations[currentLang].remove}</button>`;" +
                    "            list.appendChild(item);" +
                    "        });" +
                    "    } catch (error) { console.error('Error loading blocked commands:', error); }" +
                    "}" +
                    "async function loadRepeatingSettings() {" +
                    "    try {" +
                    "        const response = await fetch(API_BASE + '/api/repeating');" +
                    "        const settings = await response.json();" +
                    "        setFieldValueIfIdle('maxRepeats', settings.maxRepeats);" +
                    "        setFieldValueIfIdle('maxDelay', settings.maxDelay);" +
                    "        setFieldValueIfIdle('maxTntCount', settings.maxTntCount);" +
                    "        setFieldValueIfIdle('maxTntRadius', settings.maxTntRadius);" +
                    "    } catch (error) { console.error('Error loading repeating settings:', error); }" +
                    "}" +
                    "async function loadStatus() {" +
                    "    try {" +
                    "        const response = await fetch(API_BASE + '/api/status');" +
                    "        const status = await response.json();" +
                    "        setCheckedIfIdle('modEnabled', status.modEnabled);" +
                    "        setCheckedIfIdle('youtubeEnabled', status.youtubeEnabled);" +
                    "        setCheckedIfIdle('telegramEnabled', status.telegramEnabled);" +
                    "        setCheckedIfIdle('youtubeSendEnabled', status.youtubeSendEnabled);" +
                    "        setCheckedIfIdle('groupingMessages', status.groupingMessages);" +
                    "        setCheckedIfIdle('actionbarEnabled', status.actionbarEnabled);" +
                    "        setCheckedIfIdle('compatibilityMode', status.compatibilityMode);" +
                    "        setCheckedIfIdle('webUIEnabled', status.webUIEnabled);" +
                    "        setFieldValueIfIdle('chatMode', status.chatMode);" +
                    "        setFieldValueIfIdle('maxRepeats', status.maxRepeats);" +
                    "        setFieldValueIfIdle('maxDelay', status.maxDelay);" +
                    "        setFieldValueIfIdle('maxTntCount', status.maxTntCount);" +
                    "        setFieldValueIfIdle('maxTntRadius', status.maxTntRadius);" +
                    "    } catch (error) { console.error('Error loading status:', error); }" +
                    "}" +
                    "function startStatusSync() {" +
                    "    if (syncInterval) clearInterval(syncInterval);" +
                    "    syncInterval = setInterval(loadStatus, 3000);" +
                    "}" +
                    "function setFieldValueIfIdle(id, value) {" +
                    "    const el = document.getElementById(id);" +
                    "    if (!el || document.activeElement === el) return;" +
                    "    const nextValue = value == null ? '' : String(value);" +
                    "    if (el.value !== nextValue) el.value = nextValue;" +
                    "}" +
                    "function setCheckedIfIdle(id, value) {" +
                    "    const el = document.getElementById(id);" +
                    "    if (!el || document.activeElement === el) return;" +
                    "    el.checked = Boolean(value);" +
                    "}" +
                    "async function saveAllSettings() {" +
                    "    try {" +
                    "        const config = {\n" +
                    "            modEnabled: document.getElementById('modEnabled').checked,\n" +
                    "            youtubeEnabled: document.getElementById('youtubeEnabled').checked,\n" +
                    "            youtubeApiKey: document.getElementById('youtubeApiKey').value,\n" +
                    "            youtubeVideoId: document.getElementById('youtubeVideoId').value,\n" +
                    "            chatMode: document.getElementById('chatMode').value,\n" +
                    "            httpMessagesPort: parseInt(document.getElementById('httpMessagesPort').value),\n" +
                    "            youtubeClientId: document.getElementById('youtubeClientId').value,\n" +
                    "            youtubeClientSecret: document.getElementById('youtubeClientSecret').value,\n" +
                    "            youtubeSendEnabled: document.getElementById('youtubeSendEnabled').checked,\n" +
                    "            telegramEnabled: document.getElementById('telegramEnabled').checked,\n" +
                    "            telegramToken: document.getElementById('telegramToken').value,\n" +
                    "            groupingMessages: document.getElementById('groupingMessages').checked,\n" +
                    "            actionbarEnabled: document.getElementById('actionbarEnabled').checked,\n" +
                    "            compatibilityMode: document.getElementById('compatibilityMode').checked,\n" +
                    "            webUIEnabled: document.getElementById('webUIEnabled').checked,\n" +
                    "            webUIPort: parseInt(document.getElementById('webUIPort').value)\n" +
                    "        };" +
                    "        const repeating = {\n" +
                    "            maxRepeats: parseInt(document.getElementById('maxRepeats').value),\n" +
                    "            maxDelay: parseInt(document.getElementById('maxDelay').value),\n" +
                    "            maxTntCount: parseInt(document.getElementById('maxTntCount').value),\n" +
                    "            maxTntRadius: parseInt(document.getElementById('maxTntRadius').value)\n" +
                    "        };" +
                    "        await fetch(API_BASE + '/api/repeating', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(repeating) });" +
                    "        await fetch(API_BASE + '/api/config', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(config) });" +
                    "        showNotification(translations[currentLang].saved);" +
                    "    } catch (error) { console.error('Error saving settings:', error); }" +
                    "}" +
                    "async function addBlockedCommand() {" +
                    "    const input = document.getElementById('newBlockedCmd');" +
                    "    let cmd = input.value.trim();" +
                    "    if (!cmd) return;" +
                    "    if (cmd.startsWith('/')) cmd = cmd.substring(1);" +
                    "    try {" +
                    "        await fetch(API_BASE + '/api/blocked', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ command: cmd }) });" +
                    "        input.value = '';" +
                    "        loadBlockedCommands();" +
                    "        showNotification(translations[currentLang].cmdBlocked);" +
                    "    } catch (error) { console.error('Error adding blocked command:', error); }" +
                    "}" +
                    "async function removeBlockedCommand(cmd) {" +
                    "    try {" +
                    "        await fetch(API_BASE + `/api/blocked?cmd=${encodeURIComponent(cmd)}`, { method: 'DELETE' });" +
                    "        loadBlockedCommands();" +
                    "        showNotification(translations[currentLang].cmdUnblocked);" +
                    "    } catch (error) { console.error('Error removing blocked command:', error); }" +
                    "}" +
                    "async function connectYouTube() {" +
                    "    try {" +
                    "        const response = await fetch(API_BASE + '/api/connect_youtube');" +
                    "        const data = await response.json();" +
                    "        if (data.url) {" +
                    "             window.open(data.url, '_blank');" +
                    "        }" +
                    "    } catch (error) { console.error('Error connecting YouTube:', error); showNotification(translations[currentLang].error); }" +
                    "}" +
                    "function showNotification(message) {" +
                    "    const notification = document.getElementById('notification');" +
                    "    notification.textContent = message;" +
                    "    notification.style.display = 'block';" +
                    "    notification.style.background = message.includes('❌') ? '#ef4444' : '#22c55e';" +
                    "    setTimeout(() => { notification.style.display = 'none'; }, 2000);" +
                    "}" +
                    "</script>";
        }
    }

    static class ConfigHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonObject config = new JsonObject();
                config.addProperty("modEnabled", CYCMClient.configManager.getConfig().isModEnabled());
                config.addProperty("youtubeEnabled", CYCMClient.configManager.getConfig().isYoutubeEnabled());
                config.addProperty("youtubeApiKey", CYCMClient.configManager.getConfig().getYoutubeApiKey());
                config.addProperty("youtubeVideoId", CYCMClient.configManager.getConfig().getYoutubeVideoId());
                config.addProperty("chatMode", CYCMClient.configManager.getConfig().getChatMode().name());
                config.addProperty("httpMessagesPort", CYCMClient.configManager.getConfig().getHttpMessagesPort());
                config.addProperty("youtubeClientId", CYCMClient.configManager.getConfig().getYoutubeClientId());
                config.addProperty("youtubeClientSecret", CYCMClient.configManager.getConfig().getYoutubeClientSecret());
                config.addProperty("youtubeSendEnabled", CYCMClient.configManager.getConfig().isYoutubeSendEnabled());
                config.addProperty("telegramEnabled", CYCMClient.configManager.getConfig().isTelegramEnabled());
                config.addProperty("telegramToken", CYCMClient.configManager.getConfig().getTelegramToken());
                config.addProperty("groupingMessages", CYCMClient.configManager.getConfig().isGroupingMessages());
                config.addProperty("actionbarEnabled", CYCMClient.configManager.getConfig().isActionbarEnabled());
                config.addProperty("compatibilityMode", CYCMClient.configManager.getConfig().isCompatibilityMode());
                config.addProperty("webUIEnabled", CYCMClient.configManager.getConfig().isWebUIEnabled());
                config.addProperty("webUIPort", CYCMClient.configManager.getConfig().getWebUIPort());

                String response = gson.toJson(config);
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject config = gson.fromJson(body, JsonObject.class);
                    boolean shouldSaveConfig = false;
                    boolean restartChatSource = false;

                    boolean webUIEnabled = CYCMClient.configManager.getConfig().isWebUIEnabled();
                    int webUIPort = CYCMClient.configManager.getConfig().getWebUIPort();
                    boolean webUIRuntimeChanged = false;

                    if (config.has("modEnabled")) {
                        boolean enabled = config.get("modEnabled").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isModEnabled() != enabled) {
                            CYCMClient.setModEnabled(enabled);
                        }
                    }
                    if (config.has("youtubeEnabled")) {
                        boolean enabled = config.get("youtubeEnabled").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isYoutubeEnabled() != enabled) {
                            CYCMClient.setSourceStateFromUI("youtube", enabled);
                        }
                    }
                    if (config.has("telegramEnabled")) {
                        boolean enabled = config.get("telegramEnabled").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isTelegramEnabled() != enabled) {
                            CYCMClient.setSourceStateFromUI("telegram", enabled);
                        }
                    }
                    if (config.has("youtubeApiKey")) {
                        String apiKey = config.get("youtubeApiKey").getAsString();
                        if (!apiKey.equals(CYCMClient.configManager.getConfig().getYoutubeApiKey())) {
                            CYCMClient.configManager.getConfig().setYoutubeApiKey(apiKey);
                            CYCMClient.sendLocalizedMessage("yt_key_set_success");
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("youtubeVideoId")) {
                        String videoId = config.get("youtubeVideoId").getAsString();
                        if (!videoId.equals(CYCMClient.configManager.getConfig().getYoutubeVideoId())) {
                            CYCMClient.configManager.getConfig().setYoutubeVideoId(videoId);
                            CYCMClient.sendLocalizedMessage("yt_id_set_success");
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("chatMode")) {
                        org.craftllc.minecraft.mod.cycm.config.ModConfig.ChatMode mode = org.craftllc.minecraft.mod.cycm.config.ModConfig.ChatMode
                                .valueOf(config.get("chatMode").getAsString());
                        if (CYCMClient.configManager.getConfig().getChatMode() != mode) {
                            CYCMClient.configManager.getConfig().setChatMode(mode);
                            CYCMClient.sendLocalizedMessage("mode_set_success", mode.name());
                            shouldSaveConfig = true;
                            restartChatSource = true;
                        }
                    }
                    if (config.has("httpMessagesPort")) {
                        int port = config.get("httpMessagesPort").getAsInt();
                        if (CYCMClient.configManager.getConfig().getHttpMessagesPort() != port) {
                            CYCMClient.configManager.getConfig().setHttpMessagesPort(port);
                            CYCMClient.sendLocalizedMessage("http_messages_port_set_success", port);
                            shouldSaveConfig = true;
                            restartChatSource = true;
                        }
                    }
                    if (config.has("youtubeClientId")) {
                        String clientId = config.get("youtubeClientId").getAsString();
                        if (!clientId.equals(CYCMClient.configManager.getConfig().getYoutubeClientId())) {
                            CYCMClient.configManager.getConfig().setYoutubeClientId(clientId);
                            CYCMClient.sendLocalizedMessage("yt_client_set_success");
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("youtubeClientSecret")) {
                        String clientSecret = config.get("youtubeClientSecret").getAsString();
                        if (!clientSecret.equals(CYCMClient.configManager.getConfig().getYoutubeClientSecret())) {
                            CYCMClient.configManager.getConfig().setYoutubeClientSecret(clientSecret);
                            CYCMClient.sendLocalizedMessage("yt_secret_set_success");
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("youtubeSendEnabled")) {
                        boolean enabled = config.get("youtubeSendEnabled").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isYoutubeSendEnabled() != enabled) {
                            CYCMClient.configManager.getConfig().setYoutubeSendEnabled(enabled);
                            CYCMClient.sendLocalizedMessage("yt_send_state",
                                    enabled ? Component.translatable("cycm.state.enabled")
                                            : Component.translatable("cycm.state.disabled"));
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("telegramToken")) {
                        String token = config.get("telegramToken").getAsString();
                        if (!token.equals(CYCMClient.configManager.getConfig().getTelegramToken())) {
                            CYCMClient.configManager.getConfig().setTelegramToken(token);
                            CYCMClient.sendLocalizedMessage("tg_token_set_success");
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("groupingMessages")) {
                        boolean enabled = config.get("groupingMessages").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isGroupingMessages() != enabled) {
                            CYCMClient.configManager.getConfig().setGroupingMessages(enabled);
                            CYCMClient.sendLocalizedMessage("grouping_set",
                                    enabled ? Component.translatable("cycm.state.enabled")
                                            : Component.translatable("cycm.state.disabled"));
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("actionbarEnabled")) {
                        boolean enabled = config.get("actionbarEnabled").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isActionbarEnabled() != enabled) {
                            CYCMClient.configManager.getConfig().setActionbarEnabled(enabled);
                            CYCMClient.sendLocalizedMessage("actionbar_set",
                                    enabled ? Component.translatable("cycm.state.enabled")
                                            : Component.translatable("cycm.state.disabled"));
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("compatibilityMode")) {
                        boolean enabled = config.get("compatibilityMode").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isCompatibilityMode() != enabled) {
                            CYCMClient.configManager.getConfig().setCompatibilityMode(enabled);
                            CYCMClient.sendLocalizedMessage("compat_mode_set",
                                    enabled ? Component.translatable("cycm.state.enabled")
                                            : Component.translatable("cycm.state.disabled"));
                            shouldSaveConfig = true;
                        }
                    }
                    if (config.has("webUIPort")) {
                        int port = config.get("webUIPort").getAsInt();
                        if (CYCMClient.configManager.getConfig().getWebUIPort() != port) {
                            CYCMClient.configManager.getConfig().setWebUIPort(port);
                            CYCMClient.sendLocalizedMessage("webui_port_set_success", port);
                            shouldSaveConfig = true;
                            webUIPort = port;
                            if (CYCMClient.configManager.getConfig().isWebUIEnabled()) {
                                webUIRuntimeChanged = true;
                            }
                        }
                    }
                    if (config.has("webUIEnabled")) {
                        boolean enabled = config.get("webUIEnabled").getAsBoolean();
                        if (CYCMClient.configManager.getConfig().isWebUIEnabled() != enabled) {
                            CYCMClient.configManager.getConfig().setWebUIEnabled(enabled);
                            CYCMClient.sendLocalizedMessage("webui_state",
                                    enabled ? Component.translatable("cycm.state.enabled")
                                            : Component.translatable("cycm.state.disabled"));
                            shouldSaveConfig = true;
                            webUIEnabled = enabled;
                            webUIRuntimeChanged = true;
                        }
                    }

                    if (shouldSaveConfig) {
                        CYCMClient.configManager.saveConfig();
                    }
                    if (restartChatSource && CYCMClient.configManager.getConfig().isModEnabled()) {
                        CYCMClient.stopChatSource();
                        CYCMClient.startChatSource();
                    }

                    String response = "{\"status\":\"success\"}";
                    byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, rb.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(rb);
                    }

                    if (webUIRuntimeChanged) {
                        applyWebUIRuntimeChange(webUIEnabled, webUIPort);
                    }
                }
            }
            exchange.close();
        }
    }

    static class BlockedCommandsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String response = gson.toJson(CYCMClient.getBlockedCommands());
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    String cmd = json.get("command").getAsString();
                    CYCMClient.blockCommandFromUI(cmd);

                    String response = "{\"status\":\"success\"}";
                    byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, rb.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(rb);
                    }
                }
            } else if ("DELETE".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.startsWith("cmd=")) {
                    String cmd = java.net.URLDecoder.decode(query.substring(4), StandardCharsets.UTF_8);
                    CYCMClient.unblockCommandFromUI(cmd);
                }

                String response = "{\"status\":\"success\"}";
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            }
            exchange.close();
        }
    }

    static class RepeatingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonObject settings = new JsonObject();
                settings.addProperty("maxRepeats", CYCMClient.getMaxRepeats());
                settings.addProperty("maxDelay", CYCMClient.getMaxDelaySeconds());
                settings.addProperty("maxTntCount", CYCMClient.getMaxTntCount());
                settings.addProperty("maxTntRadius", CYCMClient.getMaxTntRadius());

                String response = gson.toJson(settings);
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            } else if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    
                    if (json.has("maxRepeats")) {
                        CYCMClient.setMaxRepeats(json.get("maxRepeats").getAsInt());
                    }
                    if (json.has("maxDelay")) {
                        CYCMClient.setMaxDelaySeconds(json.get("maxDelay").getAsInt());
                    }
                    if (json.has("maxTntCount")) {
                        CYCMClient.setMaxTntCount(json.get("maxTntCount").getAsInt());
                    }
                    if (json.has("maxTntRadius")) {
                        CYCMClient.setMaxTntRadius(json.get("maxTntRadius").getAsInt());
                    }

                    JsonObject responseJson = new JsonObject();
                    responseJson.addProperty("status", "success");
                    responseJson.addProperty("maxRepeats", CYCMClient.getMaxRepeats());
                    responseJson.addProperty("maxDelay", CYCMClient.getMaxDelaySeconds());
                    responseJson.addProperty("maxTntCount", CYCMClient.getMaxTntCount());
                    responseJson.addProperty("maxTntRadius", CYCMClient.getMaxTntRadius());
                    String response = gson.toJson(responseJson);
                    byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, rb.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(rb);
                    }
                }
            }
            exchange.close();
        }
    }

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                JsonObject status = new JsonObject();
                status.addProperty("modEnabled", CYCMClient.configManager.getConfig().isModEnabled());
                status.addProperty("youtubeEnabled", CYCMClient.configManager.getConfig().isYoutubeEnabled());
                status.addProperty("telegramEnabled", CYCMClient.configManager.getConfig().isTelegramEnabled());
                status.addProperty("youtubeSendEnabled", CYCMClient.configManager.getConfig().isYoutubeSendEnabled());
                status.addProperty("groupingMessages", CYCMClient.configManager.getConfig().isGroupingMessages());
                status.addProperty("actionbarEnabled", CYCMClient.configManager.getConfig().isActionbarEnabled());
                status.addProperty("compatibilityMode", CYCMClient.configManager.getConfig().isCompatibilityMode());
                status.addProperty("webUIEnabled", CYCMClient.configManager.getConfig().isWebUIEnabled());
                status.addProperty("chatMode", CYCMClient.configManager.getConfig().getChatMode().name());
                status.addProperty("maxRepeats", CYCMClient.getMaxRepeats());
                status.addProperty("maxDelay", CYCMClient.getMaxDelaySeconds());
                status.addProperty("maxTntCount", CYCMClient.getMaxTntCount());
                status.addProperty("maxTntRadius", CYCMClient.getMaxTntRadius());

                String response = gson.toJson(status);
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            }
            exchange.close();
        }
    }

    static class ConnectYouTubeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String url = YouTubeClient.getAuthUrl();
                // Ensure server is running for callback
                HttpChatServer.startServer(CYCMClient.configManager.getConfig().getHttpMessagesPort());
                CYCMClient.sendLocalizedMessage("yt_auth_url", Component.literal(url));

                JsonObject responseJson = new JsonObject();
                responseJson.addProperty("url", url);

                String response = gson.toJson(responseJson);
                byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, rb.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(rb);
                }
            }
            exchange.close();
        }
    }
}

