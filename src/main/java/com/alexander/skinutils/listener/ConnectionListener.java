package com.alexander.skinutils.listener;

import com.alexander.skinutils.SkinUtils;
import com.alexander.skinutils.skin.SkinData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Optional;

public final class ConnectionListener implements Listener {

    private final SkinUtils plugin;

    public ConnectionListener(SkinUtils plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<SkinData> skin = plugin.getSkinManager().getCached(player.getUniqueId());
            skin.ifPresent(data ->
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            plugin.getSkinManager().applySkin(player, data)));
        });
    }
}
