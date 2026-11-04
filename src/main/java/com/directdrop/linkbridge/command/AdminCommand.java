package com.directdrop.linkbridge.command;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.manager.LinkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handles admin commands for LinkBridge (/linkbridge reload, forceunlink).
 */
public class AdminCommand implements CommandExecutor {

    private final LinkBridge plugin;
    private final LinkManager linkManager;

    public AdminCommand(LinkBridge plugin, LinkManager linkManager) {
        this.plugin = plugin;
        this.linkManager = linkManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        String prefix = plugin.getConfig().getString("messages.prefix", "");

        if (args.length == 0) {
            send(sender, prefix + "<aqua>/linkbridge reload <gray>- Reload config");
            send(sender, prefix + "<aqua>/linkbridge forceunlink <player> <gray>- Unlink player");
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "reload" -> {
                if (!sender.hasPermission("linkbridge.reload")) {
                    send(sender, prefix + plugin.getConfig().getString("messages.no-permission", "No permission"));
                    return true;
                }

                linkManager.reload();
                send(sender, prefix + plugin.getConfig().getString("messages.reload-success", "Reloaded"));
            }

            case "forceunlink" -> {
                if (!sender.hasPermission("linkbridge.forceunlink")) {
                    send(sender, prefix + plugin.getConfig().getString("messages.no-permission", "No permission"));
                    return true;
                }

                if (args.length < 2) {
                    send(sender, prefix + "<red>Usage: /linkbridge forceunlink <player>");
                    return true;
                }

                Player target = plugin.getServer().getPlayerExact(args[1]);

                if (target == null) {
                    send(sender, prefix + plugin.getConfig().getString(
                            "messages.forceunlink-player-not-found",
                            "<red>Player not found"
                    ));
                    return true;
                }

                // FIX: no forceUnlink method — use SQLite via LinkManager
                boolean success = linkManager.getSqliteManager().unlink(target.getUniqueId());

                if (success) {
                    send(sender, prefix + plugin.getConfig().getString(
                                    "messages.forceunlink-success",
                                    "<green>Force unlinked %player%"
                            ).replace("%player%", target.getName()));
                } else {
                    send(sender, prefix + plugin.getConfig().getString(
                            "messages.forceunlink-not-linked",
                            "<red>Player not linked"
                    ));
                }
            }

            default -> send(sender, prefix + "<red>Unknown subcommand");
        }

        return true;
    }

    private void send(CommandSender sender, String msg) {
        if (msg != null) sender.sendMessage(msg);
    }
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            return Arrays.asList("reload", "forceunlink");
        }

        return Collections.emptyList();
    }
}
