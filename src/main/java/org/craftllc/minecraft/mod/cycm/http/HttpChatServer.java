package org.craftllc.minecraft.mod.cycm.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.craftllc.minecraft.mod.cycm.Constants;
import org.craftllc.minecraft.mod.cycm.CYCMClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class HttpChatServer {
    private static final Gson gson = new Gson();
    private static HttpServer server;

    public static void startServer(int port) {
        stopServer();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new ChatHandler());
            server.setExecutor(null);
            server.start();
            Constants.LOGGER.info("HTTP Chat Server started on port: " + port);
        } catch (IOException e) {
            Constants.LOGGER.error("Failed to start HTTP Chat Server", e);
        }
    }

    public static void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    static class ChatHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Handle CORS (optional but good for browser scripts)
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }

            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                if (query != null && query.contains("code=")) {
                    String code = query.split("code=")[1].split("&")[0];
                    org.craftllc.minecraft.mod.cycm.youtube.YouTubeClient.exchangeCodeForTokens(code);

                    String response = "Authentication successful! You can now close this window and return to Minecraft.";
                    byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, rb.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(rb);
                    }
                } else {
                    String response = "CYCM HTTP Server is running.";
                    byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, rb.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(rb);
                    }
                }
                exchange.close();
                return;
            }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try (InputStream is = exchange.getRequestBody()) {
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                    // Respond immediately to avoid browser timeout
                    String response = "OK";
                    byte[] rb = response.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, rb.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(rb);
                    }
                    exchange.close();

                    // Process message in background
                    JsonObject json = gson.fromJson(body, JsonObject.class);
                    if (json.has("id") && json.has("author") && json.has("text")) {
                        String id = json.get("id").getAsString();
                        String author = json.get("author").getAsString();
                        String text = json.get("text").getAsString();

                        if (!org.craftllc.minecraft.mod.cycm.youtube.LiveStateManager.isProcessed(id)) {
                            CYCMClient.getInstance().processYouTubeMessage(author, text);
                            org.craftllc.minecraft.mod.cycm.youtube.LiveStateManager.markProcessed(id);
                        }
                    }
                } catch (Exception e) {
                    Constants.LOGGER.error("Error processing HTTP request", e);
                    try {
                        exchange.sendResponseHeaders(500, -1);
                        exchange.close();
                    } catch (IOException ignored) {
                    }
                }
            } else {
                exchange.sendResponseHeaders(405, -1); // Method Not Allowed
                exchange.close();
            }
        }
    }
}
