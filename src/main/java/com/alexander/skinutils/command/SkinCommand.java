package com.alexander.skinutils.command;

import com.alexander.skinutils.SkinUtils;
import com.alexander.skinutils.lang.Lang;
import com.alexander.skinutils.skin.SkinData;
import com.alexander.skinutils.skin.SkinManager;
import com.alexander.skinutils.skin.SkinPool;
import com.alexander.skinutils.storage.StorageProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class SkinCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of(
            "set", "url", "random", "copy", "history", "reset", "info", "reload", "help");

    private final SkinUtils plugin;

    public SkinCommand(SkinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "set" -> handleSet(sender, args);
            case "url" -> handleUrl(sender, args);
            case "random" -> handleRandom(sender);
            case "copy" -> handleCopy(sender);
            case "history" -> handleHistory(sender, args);
            case "reset" -> handleReset(sender);
            case "info" -> handleInfo(sender);
            case "reload" -> handleReload(sender);
            case "help" -> showHelp(sender);
            default -> sender.sendMessage(lang().get("unknown-command"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("copy")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("history")) {
                return List.of("apply").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "skinutils.set")) return;
        if (args.length < 2) {
            sender.sendMessage(lang().colorize("&#FFD700Usage: &f/skin set <player>"));
            return;
        }

        Player player = (Player) sender;
        SkinManager sm = plugin.getSkinManager();
        if (checkCooldown(player, sm)) return;

        String target = args[1];
        sender.sendMessage(lang().get("skin-applying"));
        sm.fetchByName(target).thenAccept(opt -> {
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(lang().get("player-not-found", "{player}", target)));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                sm.applySkin(player, opt.get());
                sm.setCooldown(player.getUniqueId());
                plugin.getStorage().saveHistory(player.getUniqueId(), opt.get(), "set:" + target);
                plugin.getWebhook().sendSkinChange(player.getName(), "/skin set", target);
                sender.sendMessage(lang().get("skin-applied"));
                sender.sendMessage(lang().get("skin-reconnect"));
            });
        });
    }

    private void handleUrl(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "skinutils.url")) return;
        if (args.length < 2) {
            sender.sendMessage(lang().colorize("&#FFD700Usage: &f/skin url <link>"));
            return;
        }

        Player player = (Player) sender;
        SkinManager sm = plugin.getSkinManager();
        String urlStr = args[1];

        if (!isValidSkinUrl(urlStr)) {
            sender.sendMessage(lang().get("invalid-url"));
            return;
        }
        if (!isDomainAllowed(urlStr)) {
            sender.sendMessage(lang().get("domain-not-allowed"));
            return;
        }
        if (checkCooldown(player, sm)) return;

        sender.sendMessage(lang().get("skin-applying"));
        sm.fetchFromUrl(urlStr).thenAccept(opt -> {
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(lang().get("invalid-url")));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                sm.applySkin(player, opt.get());
                sm.setCooldown(player.getUniqueId());
                plugin.getStorage().saveHistory(player.getUniqueId(), opt.get(), "url");
                plugin.getWebhook().sendSkinChange(player.getName(), "/skin url", urlStr);
                sender.sendMessage(lang().get("skin-applied"));
                sender.sendMessage(lang().get("skin-reconnect"));
            });
        });
    }

    private void handleRandom(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "skinutils.random")) return;

        Player player = (Player) sender;
        SkinManager sm = plugin.getSkinManager();
        if (checkCooldown(player, sm)) return;

        sender.sendMessage(lang().get("skin-applying"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<SkinData> opt = SkinPool.random(plugin);
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(lang().get("skin-random-failed")));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                sm.applySkin(player, opt.get());
                sm.setCooldown(player.getUniqueId());
                plugin.getStorage().saveHistory(player.getUniqueId(), opt.get(), "random");
                plugin.getWebhook().sendSkinChange(player.getName(), "/skin random", "Random");
                sender.sendMessage(lang().get("skin-applied"));
                sender.sendMessage(lang().get("skin-reconnect"));
            });
        });
    }

    private void handleCopy(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "skinutils.copy")) return;

        Player player = (Player) sender;
        SkinManager sm = plugin.getSkinManager();
        if (checkCooldown(player, sm)) return;

        Player targetPlayer = null;
        for (Entity entity : player.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player p && !p.getUniqueId().equals(player.getUniqueId())) {
                if (player.hasLineOfSight(entity)) {
                    targetPlayer = p;
                    break;
                }
            }
        }

        if (targetPlayer == null) {
            sender.sendMessage(lang().get("copy-no-target"));
            return;
        }

        final String targetName = targetPlayer.getName();
        sender.sendMessage(lang().get("skin-applying"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Optional<SkinData> opt = sm.fetchByName(targetName).join();
            if (opt.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(lang().get("copy-failed")));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                sm.applySkin(player, opt.get());
                sm.setCooldown(player.getUniqueId());
                plugin.getStorage().saveHistory(player.getUniqueId(), opt.get(), "copy:" + targetName);
                plugin.getWebhook().sendSkinChange(player.getName(), "/skin copy", targetName);
                sender.sendMessage(lang().get("skin-applied"));
                sender.sendMessage(lang().get("skin-reconnect"));
            });
        });
    }

    private void handleHistory(CommandSender sender, String[] args) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "skinutils.use")) return;

        Player player = (Player) sender;
        int max = plugin.getConfig().getInt("settings.history-max-size", 10);
        List<StorageProvider.SkinHistory> history = plugin.getStorage().getHistory(player.getUniqueId(), max);

        if (args.length >= 3 && args[1].equalsIgnoreCase("apply")) {
            try {
                int index = Integer.parseInt(args[2]) - 1;
                if (index < 0 || index >= history.size()) {
                    sender.sendMessage(lang().get("history-invalid-index"));
                    return;
                }
                SkinManager sm = plugin.getSkinManager();
                if (checkCooldown(player, sm)) return;

                SkinData skin = history.get(index).skin();
                sm.applySkin(player, skin);
                sm.setCooldown(player.getUniqueId());
                plugin.getWebhook().sendSkinChange(player.getName(), "/skin history apply", "#" + (index + 1));
                sender.sendMessage(lang().get("skin-applied"));
                sender.sendMessage(lang().get("skin-reconnect"));
            } catch (NumberFormatException e) {
                sender.sendMessage(lang().get("history-invalid-index"));
            }
            return;
        }

        if (history.isEmpty()) {
            sender.sendMessage(lang().get("history-empty"));
            return;
        }

        Lang l = lang();
        sender.sendMessage("");
        sender.sendMessage(l.raw("history-header"));
        sender.sendMessage(l.raw("help-separator"));

        SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm");
        for (int i = 0; i < history.size(); i++) {
            StorageProvider.SkinHistory entry = history.get(i);
            String date = sdf.format(new Date(entry.timestamp()));
            sender.sendMessage(l.colorize("  &#FFD700#" + (i + 1) + " &8| &7" + entry.source() + " &8| &f" + date));
        }

        sender.sendMessage(l.raw("help-separator"));
        sender.sendMessage(l.raw("history-usage"));
        sender.sendMessage("");
    }

    private void handleReset(CommandSender sender) {
        if (!requirePlayer(sender)) return;
        if (!requirePermission(sender, "skinutils.reset")) return;

        Player player = (Player) sender;
        plugin.getSkinManager().resetSkin(player);
        plugin.getWebhook().sendSkinChange(player.getName(), "/skin reset", "Default");
        sender.sendMessage(lang().get("skin-reset"));
    }

    private void handleInfo(CommandSender sender) {
        Lang l = lang();
        sender.sendMessage("");
        sender.sendMessage(l.raw("info-header"));
        sender.sendMessage(l.raw("help-separator"));
        sender.sendMessage(l.raw("info-version").replace("{version}", plugin.getDescription().getVersion()));
        sender.sendMessage(l.raw("info-server").replace("{server}", Bukkit.getVersion()));
        sender.sendMessage(l.raw("info-storage").replace("{storage}", plugin.getConfig().getString("storage.type", "sqlite").toUpperCase()));
        sender.sendMessage(l.raw("info-language").replace("{language}", l.getLanguageCode()));
        sender.sendMessage(l.raw("info-author"));
        sender.sendMessage(l.raw("help-separator"));
        sender.sendMessage("");
    }

    private void handleReload(CommandSender sender) {
        if (!requirePermission(sender, "skinutils.reload")) return;
        plugin.reload();
        sender.sendMessage(lang().get("reload-success"));
    }

    private void showHelp(CommandSender sender) {
        Lang l = lang();
        sender.sendMessage("");
        sender.sendMessage(l.raw("help-header"));
        sender.sendMessage(l.raw("help-separator"));
        sender.sendMessage(l.colorize("  &#FFD700/skin set <player> &8- &7" + l.raw("cmd-set")));
        sender.sendMessage(l.colorize("  &#FFD700/skin url <link> &8- &7" + l.raw("cmd-url")));
        sender.sendMessage(l.colorize("  &#FFD700/skin random &8- &7" + l.raw("cmd-random")));
        sender.sendMessage(l.colorize("  &#FFD700/skin copy &8- &7" + l.raw("cmd-copy")));
        sender.sendMessage(l.colorize("  &#FFD700/skin history &8- &7" + l.raw("cmd-history")));
        sender.sendMessage(l.colorize("  &#FFD700/skin reset &8- &7" + l.raw("cmd-reset")));
        sender.sendMessage(l.colorize("  &#FFD700/skin info &8- &7" + l.raw("cmd-info")));
        sender.sendMessage(l.colorize("  &#FFD700/skin reload &8- &7" + l.raw("cmd-reload")));
        sender.sendMessage(l.colorize("  &#FFD700/skin help &8- &7" + l.raw("cmd-help")));
        sender.sendMessage(l.raw("help-separator"));
        sender.sendMessage(l.raw("help-footer"));
        sender.sendMessage("");
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        sender.sendMessage(lang().get("player-only"));
        return false;
    }

    private boolean requirePermission(CommandSender sender, String perm) {
        if (sender.hasPermission(perm)) return true;
        sender.sendMessage(lang().get("no-permission"));
        return false;
    }

    private boolean checkCooldown(Player player, SkinManager sm) {
        if (sm.isOnCooldown(player.getUniqueId())) {
            int remaining = sm.getRemainingCooldown(player.getUniqueId());
            player.sendMessage(lang().get("cooldown", "{time}", String.valueOf(remaining)));
            return true;
        }
        return false;
    }

    private boolean isValidSkinUrl(String urlStr) {
        try {
            URL url = new URL(urlStr);
            String proto = url.getProtocol();
            if (!proto.equals("http") && !proto.equals("https")) return false;
            String path = url.getPath().toLowerCase();
            return path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg");
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private boolean isDomainAllowed(String urlStr) {
        try {
            String host = new URL(urlStr).getHost().toLowerCase();
            return plugin.getConfig().getStringList("settings.allowed-domains").stream()
                    .anyMatch(d -> host.equals(d.toLowerCase()) || host.endsWith("." + d.toLowerCase()));
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private Lang lang() {
        return plugin.getLang();
    }
}
