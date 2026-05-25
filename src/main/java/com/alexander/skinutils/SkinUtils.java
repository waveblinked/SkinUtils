package com.alexander.skinutils;

import com.alexander.skinutils.command.SkinCommand;
import com.alexander.skinutils.lang.Lang;
import com.alexander.skinutils.listener.ConnectionListener;
import com.alexander.skinutils.skin.SkinManager;
import com.alexander.skinutils.storage.StorageProvider;
import com.alexander.skinutils.storage.MySQLStorage;
import com.alexander.skinutils.storage.SQLiteStorage;
import com.alexander.skinutils.webhook.DiscordWebhook;
import org.bukkit.plugin.java.JavaPlugin;

public final class SkinUtils extends JavaPlugin {

    private static SkinUtils instance;
    private Lang lang;
    private StorageProvider storage;
    private SkinManager skinManager;
    private DiscordWebhook webhook;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.lang = new Lang(this);
        this.storage = createStorage();
        this.storage.init();
        this.skinManager = new SkinManager(this);
        this.webhook = new DiscordWebhook(this);

        getServer().getPluginManager().registerEvents(new ConnectionListener(this), this);

        SkinCommand cmd = new SkinCommand(this);
        getCommand("skin").setExecutor(cmd);
        getCommand("skin").setTabCompleter(cmd);

        getLogger().info("SkinUtils v" + getDescription().getVersion() + " enabled. Language: " + lang.getLanguageCode());
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.shutdown();
        instance = null;
    }

    public void reload() {
        reloadConfig();
        lang.reload();
    }

    private StorageProvider createStorage() {
        String type = getConfig().getString("storage.type", "sqlite");
        if (type.equalsIgnoreCase("mysql")) {
            return new MySQLStorage(this);
        }
        return new SQLiteStorage(this);
    }

    public static SkinUtils getInstance() {
        return instance;
    }

    public Lang getLang() {
        return lang;
    }

    public StorageProvider getStorage() {
        return storage;
    }

    public SkinManager getSkinManager() {
        return skinManager;
    }

    public DiscordWebhook getWebhook() {
        return webhook;
    }
}
