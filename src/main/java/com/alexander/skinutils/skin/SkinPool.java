package com.alexander.skinutils.skin;

import com.alexander.skinutils.SkinUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class SkinPool {

    private static final String[] NAMES = {
            "Notch", "jeb_", "Dinnerbone", "Grumm", "MHF_Steve", "MHF_Alex",
            "MHF_Creeper", "MHF_Zombie", "MHF_Skeleton", "MHF_Spider",
            "MHF_Pig", "MHF_Sheep", "MHF_Cow", "MHF_Chicken", "MHF_Squid",
            "MHF_Golem", "MHF_Enderman", "MHF_Blaze", "MHF_Guardian", "MHF_Villager"
    };

    private SkinPool() {
    }

    public static Optional<SkinData> random(SkinUtils plugin) {
        String name = NAMES[ThreadLocalRandom.current().nextInt(NAMES.length)];
        try {
            HttpURLConnection uuidConn = (HttpURLConnection) new URL(
                    "https://api.mojang.com/users/profiles/minecraft/" + name).openConnection();
            uuidConn.setRequestProperty("User-Agent", "SkinUtils/1.0");
            uuidConn.setConnectTimeout(5000);
            uuidConn.setReadTimeout(5000);
            if (uuidConn.getResponseCode() != 200) return Optional.empty();

            JsonObject uuidJson = JsonParser.parseReader(new InputStreamReader(uuidConn.getInputStream())).getAsJsonObject();
            String id = uuidJson.get("id").getAsString();

            HttpURLConnection skinConn = (HttpURLConnection) new URL(
                    "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false").openConnection();
            skinConn.setRequestProperty("User-Agent", "SkinUtils/1.0");
            skinConn.setConnectTimeout(5000);
            skinConn.setReadTimeout(5000);
            if (skinConn.getResponseCode() != 200) return Optional.empty();

            JsonObject skinJson = JsonParser.parseReader(new InputStreamReader(skinConn.getInputStream())).getAsJsonObject();
            JsonObject prop = skinJson.getAsJsonArray("properties").get(0).getAsJsonObject();
            return Optional.of(new SkinData(prop.get("value").getAsString(), prop.get("signature").getAsString()));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch random skin: " + e.getMessage());
            return Optional.empty();
        }
    }

    public static int size() {
        return NAMES.length;
    }
}
