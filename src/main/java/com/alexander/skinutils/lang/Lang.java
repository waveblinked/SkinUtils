package com.alexander.skinutils.lang;

import com.alexander.skinutils.SkinUtils;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lang {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static final String[] SUPPORTED = {"en", "ru", "uk", "ja", "es"};

    private final SkinUtils plugin;
    private YamlConfiguration messages;
    private String languageCode;

    public Lang(SkinUtils plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        this.languageCode = plugin.getConfig().getString("language", "en").toLowerCase();
        if (!isSupported(languageCode)) {
            plugin.getLogger().warning("Language '" + languageCode + "' not found, falling back to 'en'.");
            this.languageCode = "en";
        }

        extractLangFiles();

        File langFile = new File(plugin.getDataFolder(), "lang/" + languageCode + ".yml");
        if (langFile.exists()) {
            this.messages = YamlConfiguration.loadConfiguration(langFile);
        } else {
            InputStream stream = plugin.getResource("lang/" + languageCode + ".yml");
            if (stream != null) {
                this.messages = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
            } else {
                this.messages = new YamlConfiguration();
            }
        }
    }

    public String get(String key) {
        String prefix = messages.getString("prefix", "");
        String msg = messages.getString(key, "&cMissing: " + key);
        return colorize(prefix + msg);
    }

    public String raw(String key) {
        return colorize(messages.getString(key, "&cMissing: " + key));
    }

    public String get(String key, String placeholder, String value) {
        return get(key).replace(placeholder, value);
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length() + 32);
        while (matcher.find()) {
            StringBuilder hex = new StringBuilder("\u00A7x");
            for (char c : matcher.group(1).toCharArray()) {
                hex.append('\u00A7').append(c);
            }
            matcher.appendReplacement(sb, hex.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }

    private boolean isSupported(String code) {
        for (String s : SUPPORTED) {
            if (s.equals(code)) return true;
        }
        return false;
    }

    private void extractLangFiles() {
        for (String code : SUPPORTED) {
            File file = new File(plugin.getDataFolder(), "lang/" + code + ".yml");
            if (!file.exists()) {
                plugin.saveResource("lang/" + code + ".yml", false);
            }
        }
    }
}
