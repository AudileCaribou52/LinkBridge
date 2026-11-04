package com.directdrop.linkbridge.command;

import com.directdrop.linkbridge.manager.LinkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DiscordCommand extends BaseCommand implements TabCompleter {

    public DiscordCommand(Plugin plugin, LinkManager linkManager) {
        super(plugin, linkManager);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sendMessage(sender, plugin.getConfig().getString("messages.no-permission"));
            return true;
        }

        // ===== HELP MENU =====
        if (args.length == 0) {
            String prefix = plugin.getConfig().getString("messages.prefix");

            sendMessage(player, prefix + "§bDiscord Commands:");
            sendMessage(player, prefix + "§7/discord link §8- §fGet a linking code");
            sendMessage(player, prefix + "§7/discord unlink §8- §fUnlink your account");
            sendMessage(player, prefix + "§7/discord status §8- §fCheck your link status");
            return true;
        }

        // ===== SUBCOMMANDS =====
        switch (args[0].toLowerCase()) {

            case "link":
                linkManager.handleLink(player);
                break;

            case "unlink":
                linkManager.handleUnlink(player);
                break;

            case "status":
                linkManager.handleStatus(player);
                break;

            default:
                sendMessage(player,
                        plugin.getConfig().getString("messages.prefix") +
                                "§cUnknown subcommand. Use /discord");
                break;
        }

        return true;
    }

    // ===== TAB COMPLETION =====
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            return Arrays.asList("link", "unlink", "status");
        }

        return Collections.emptyList();
    }
}