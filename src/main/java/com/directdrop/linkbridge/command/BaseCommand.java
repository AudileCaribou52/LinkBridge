package com.directdrop.linkbridge.command;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.manager.LinkManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Base command class for LinkBridge commands.
 */
public abstract class BaseCommand implements CommandExecutor {

    protected final Plugin plugin;
    protected final LinkManager linkManager;

    public BaseCommand(Plugin plugin, LinkManager linkManager) {
        this.plugin = plugin;
        this.linkManager = linkManager;
    }

    /**
     * Checks if the sender is a player.
     *
     * @param sender the command sender
     * @return true if sender is a player
     */
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }

    /**
     * Gets the player from the sender.
     *
     * @param sender the command sender
     * @return the player, or null if not a player
     */
    protected Player getPlayer(CommandSender sender) {
        return isPlayer(sender) ? (Player) sender : null;
    }

    /**
     * Sends a message to the sender.
     *
     * @param sender  the command sender
     * @param message the message to send
     */
    protected void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(message);
    }
}