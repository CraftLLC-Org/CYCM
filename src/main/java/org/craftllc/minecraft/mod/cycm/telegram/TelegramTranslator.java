package org.craftllc.minecraft.mod.cycm.telegram;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.craftllc.minecraft.mod.cycm.Constants;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TelegramTranslator {
    private static final Map<String, JsonObject> cache = new HashMap<>();
    private static final Gson gson = new Gson();

    public static String getTranslation(String lang, String key) {
        String baseLang = lang.split("-")[0].toLowerCase();
        JsonObject translations = cache.get(baseLang);

        if (translations == null) {
            translations = load(baseLang);
            if (translations == null && !baseLang.equals("en")) {
                translations = load("en");
            }
            if (translations != null) {
                cache.put(baseLang, translations);
            }
        }

        if (translations != null && translations.has(key)) {
            return translations.get(key).getAsString();
        }

        // Fallback to English if key missing in specific lang
        if (!baseLang.equals("en")) {
            return getTranslation("en", key);
        }

        return key;
    }

    private static JsonObject load(String lang) {
        String path = "/assets/cycm/telegram/" + lang + ".json";
        try (var stream = TelegramTranslator.class.getResourceAsStream(path)) {
            if (stream == null)
                return null;
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return gson.fromJson(reader, JsonObject.class);
            }
        } catch (Exception e) {
            Constants.LOGGER.error("Failed to load Telegram translation: " + path, e);
            return null;
        }
    }
}
