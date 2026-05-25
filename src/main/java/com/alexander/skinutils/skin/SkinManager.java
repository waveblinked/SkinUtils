package com.alexander.skinutils.skin;

import com.alexander.skinutils.SkinUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinManager {

    private final SkinUtils plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, SkinData> cache = new ConcurrentHashMap<>();

    public SkinManager(SkinUtils plugin) {
        this.plugin = plugin;
    }

    public CompletableFuture<Optional<SkinData>> fetchByName(String name) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID uuid = resolveUUID(name).orElse(null);
                if (uuid == null) return Optional.empty();
                return fetchByUUID(uuid);
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    public CompletableFuture<Optional<SkinData>> fetchFromUrl(String imageUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL("https://api.mineskin.org/generate/url").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", "SkinUtils/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.getOutputStream().write(("{\"url\":\"" + imageUrl + "\"}").getBytes());

                if (conn.getResponseCode() != 200) return Optional.empty();

                JsonObject resp = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
                JsonObject texture = resp.getAsJsonObject("data").getAsJsonObject("texture");
                return Optional.of(new SkinData(texture.get("value").getAsString(), texture.get("signature").getAsString()));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    public void applySkin(Player player, SkinData skin) {
        try {
            Object craftPlayer = player.getClass().getMethod("getHandle").invoke(player);
            GameProfile profile = null;

            for (java.lang.reflect.Method m : craftPlayer.getClass().getMethods()) {
                if (m.getReturnType() == GameProfile.class && m.getParameterCount() == 0) {
                    profile = (GameProfile) m.invoke(craftPlayer);
                    break;
                }
            }

            if (profile == null) {
                Object mcPlayer = player.getClass().getMethod("getHandle").invoke(player);
                for (java.lang.reflect.Field f : mcPlayer.getClass().getSuperclass().getDeclaredFields()) {
                    if (f.getType() == GameProfile.class) {
                        f.setAccessible(true);
                        profile = (GameProfile) f.get(mcPlayer);
                        break;
                    }
                }
            }

            if (profile == null) {
                plugin.getLogger().warning("Could not find GameProfile for " + player.getName());
                return;
            }

            profile.getProperties().removeAll("textures");
            profile.getProperties().put("textures", new Property("textures", skin.value(), skin.signature()));

            plugin.getStorage().save(player.getUniqueId(), skin);
            cache.put(player.getUniqueId(), skin);

            refreshPlayerVisibility(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to apply skin: " + e.getMessage());
        }
    }

    public void resetSkin(Player player) {
        plugin.getStorage().remove(player.getUniqueId());
        cache.remove(player.getUniqueId());
    }

    public Optional<SkinData> getCached(UUID uuid) {
        SkinData data = cache.get(uuid);
        if (data != null) {
            long expiry = plugin.getConfig().getInt("settings.cache-expiry-minutes", 60) * 60_000L;
            if (!data.isExpired(expiry)) return Optional.of(data);
            cache.remove(uuid);
        }
        return plugin.getStorage().load(uuid);
    }

    public boolean isOnCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return false;
        long cd = plugin.getConfig().getInt("settings.cooldown-seconds", 30) * 1000L;
        return System.currentTimeMillis() - last < cd;
    }

    public int getRemainingCooldown(UUID uuid) {
        Long last = cooldowns.get(uuid);
        if (last == null) return 0;
        long cd = plugin.getConfig().getInt("settings.cooldown-seconds", 30) * 1000L;
        long remaining = cd - (System.currentTimeMillis() - last);
        return remaining > 0 ? (int) (remaining / 1000) + 1 : 0;
    }

    public void setCooldown(UUID uuid) {
        cooldowns.put(uuid, System.currentTimeMillis());
    }

    private void refreshPlayerVisibility(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getUniqueId().equals(player.getUniqueId())) continue;
                if (online.canSee(player)) {
                    online.hidePlayer(plugin, player);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> online.showPlayer(plugin, player), 2L);
                }
            }
        });
    }

    private Optional<UUID> resolveUUID(String name) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                    "https://api.mojang.com/users/profiles/minecraft/" + name).openConnection();
            conn.setRequestProperty("User-Agent", "SkinUtils/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return Optional.empty();

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            String id = json.get("id").getAsString();
            return Optional.of(UUID.fromString(
                    id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5")));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<SkinData> fetchByUUID(UUID uuid) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" +
                            uuid.toString().replace("-", "") + "?unsigned=false").openConnection();
            conn.setRequestProperty("User-Agent", "SkinUtils/1.0");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() != 200) return Optional.empty();

            JsonObject json = JsonParser.parseReader(new InputStreamReader(conn.getInputStream())).getAsJsonObject();
            JsonObject prop = json.getAsJsonArray("properties").get(0).getAsJsonObject();
            return Optional.of(new SkinData(prop.get("value").getAsString(), prop.get("signature").getAsString()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
