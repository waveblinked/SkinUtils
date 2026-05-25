package com.alexander.skinutils.webhook;

import com.alexander.skinutils.SkinUtils;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class DiscordWebhook {

    private final SkinUtils plugin;

    public DiscordWebhook(SkinUtils plugin) {
        this.plugin = plugin;
    }

    public void sendSkinChange(String playerName, String source, String skinTarget) {
        if (!plugin.getConfig().getBoolean("webhook.enabled", false)) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String webhookUrl = plugin.getConfig().getString("webhook.url", "");
            String botToken = plugin.getConfig().getString("webhook.bot-token", "");
            String channelId = plugin.getConfig().getString("webhook.channel-id", "");

            String json = buildEmbed(playerName, source, skinTarget);

            if (!botToken.isEmpty() && !channelId.isEmpty()) {
                sendViaBot(botToken, channelId, json);
            } else if (!webhookUrl.isEmpty()) {
                String username = plugin.getConfig().getString("webhook.username", "SkinUtils");
                String avatarUrl = plugin.getConfig().getString("webhook.avatar-url", "");
                String wrapped = "{" +
                        "\"username\":\"" + escape(username) + "\"," +
                        "\"avatar_url\":\"" + escape(avatarUrl) + "\"," +
                        "\"embeds\":[" + json + "]" +
                        "}";
                sendPost(webhookUrl, wrapped, null);
            }
        });
    }

    private void sendViaBot(String botToken, String channelId, String embedJson) {
        String url = "https://discord.com/api/v10/channels/" + channelId + "/messages";
        String payload = "{\"embeds\":[" + embedJson + "]}";
        sendPost(url, payload, "Bot " + botToken);
    }

    private void sendPost(String urlStr, String json, String auth) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "SkinUtils/1.0");
            if (auth != null) {
                conn.setRequestProperty("Authorization", auth);
            }
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200 && code != 204) {
                plugin.getLogger().warning("Discord API returned code " + code);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Discord send failed: " + e.getMessage());
        }
    }

    private String buildEmbed(String playerName, String source, String skinTarget) {
        String targetForUrl = skinTarget.replaceAll("[^a-zA-Z0-9_]", "");
        if (targetForUrl.isEmpty() || targetForUrl.equalsIgnoreCase("Random") || targetForUrl.equalsIgnoreCase("Default")) {
            targetForUrl = "MHF_Steve";
        }
        String headUrl = "https://mc-heads.net/avatar/" + targetForUrl + "/128";
        String bodyUrl = "https://mc-heads.net/body/" + targetForUrl + "/128";

        String lang = plugin.getLang().getLanguageCode();
        String title = switch (lang) {
            case "ru" -> "\uD83C\uDFA8 Скин изменён";
            case "uk" -> "\uD83C\uDFA8 Скін змінено";
            case "ja" -> "\uD83C\uDFA8 スキン変更";
            case "es" -> "\uD83C\uDFA8 Skin Cambiado";
            default -> "\uD83C\uDFA8 Skin Changed";
        };
        String playerLabel = switch (lang) {
            case "ru" -> "\uD83D\uDC64 Игрок";
            case "uk" -> "\uD83D\uDC64 Гравець";
            case "ja" -> "\uD83D\uDC64 プレイヤー";
            case "es" -> "\uD83D\uDC64 Jugador";
            default -> "\uD83D\uDC64 Player";
        };
        String commandLabel = switch (lang) {
            case "ru" -> "\uD83D\uDD27 Команда";
            case "uk" -> "\uD83D\uDD27 Команда";
            case "ja" -> "\uD83D\uDD27 コマンド";
            case "es" -> "\uD83D\uDD27 Comando";
            default -> "\uD83D\uDD27 Command";
        };
        String targetLabel = switch (lang) {
            case "ru" -> "\uD83C\uDFAF Цель";
            case "uk" -> "\uD83C\uDFAF Ціль";
            case "ja" -> "\uD83C\uDFAF ターゲット";
            case "es" -> "\uD83C\uDFAF Objetivo";
            default -> "\uD83C\uDFAF Target";
        };

        return "{" +
                "\"title\":\"" + escape(title) + "\"," +
                "\"color\":16766720," +
                "\"thumbnail\":{\"url\":\"" + escape(headUrl) + "\"}," +
                "\"image\":{\"url\":\"" + escape(bodyUrl) + "\"}," +
                "\"fields\":[" +
                "{\"name\":\"" + escape(playerLabel) + "\",\"value\":\"```" + escape(playerName) + "```\",\"inline\":true}," +
                "{\"name\":\"" + escape(commandLabel) + "\",\"value\":\"```" + escape(source) + "```\",\"inline\":true}," +
                "{\"name\":\"" + escape(targetLabel) + "\",\"value\":\"```" + escape(skinTarget) + "```\",\"inline\":true}" +
                "]," +
                "\"timestamp\":\"" + Instant.now().toString() + "\"," +
                "\"footer\":{\"text\":\"SkinUtils v" + plugin.getDescription().getVersion() + "\"}" +
                "}";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
