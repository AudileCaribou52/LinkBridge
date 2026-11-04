package com.directdrop.linkbridge.manager;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.hook.LuckPermsHook;
import com.directdrop.linkbridge.model.LinkData;
import com.directdrop.linkbridge.storage.SQLiteManager;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.SecureRandom;
import java.util.UUID;

public class LinkManager {

private final LinkBridge plugin;
private final SQLiteManager sqliteManager;
private final LuckPermsHook luckPermsHook;
private final SecureRandom random = new SecureRandom();

public LinkManager(LinkBridge plugin) {
    this.plugin = plugin;

    // Use the plugin database instance
    this.sqliteManager = plugin.getSqliteManager();

    // Initialize LuckPerms hook
    this.luckPermsHook = new LuckPermsHook(plugin, sqliteManager);

    plugin.getLogger().info("LinkManager initialized.");
    plugin.getLogger().info("LuckPerms enabled: " + luckPermsHook.isLuckPermsEnabled());

    startCleanupTask();
}

private void startCleanupTask() {
    new BukkitRunnable() {
        @Override
        public void run() {
            sqliteManager.cleanupExpiredCodes();
        }
    }.runTaskTimerAsynchronously(plugin, 72000L, 72000L);
}

public void handleLink(Player player) {

    UUID uuid = player.getUniqueId();

    LinkData existing = sqliteManager.getLinkData(uuid);

    if (existing != null && existing.isLinked()) {
        send(player, "messages.link-already-linked");
        return;
    }

    if (sqliteManager.getPendingCode(uuid) != null) {
        send(player, "messages.link-code-exists");
        return;
    }

    String code = generateCode();

    sqliteManager.createPendingCode(
            uuid,
            code,
            player.getName()
    );

    player.sendMessage(
            color("messages.link-code-generated")
                    .replace("%code%", code)
    );

    player.sendMessage(
            color("messages.link-instruction")
                    .replace("%code%", code)
    );

    plugin.getLogger().info(
            "Generated link code for "
                    + player.getName()
                    + ": "
                    + code
    );
}

public void handleUnlink(Player player) {

    UUID uuid = player.getUniqueId();

    LinkData data = sqliteManager.getLinkData(uuid);

    if (data == null || !data.isLinked()) {
        send(player, "messages.unlink-not-linked");
        return;
    }

    String discordId = data.getDiscordId();
    String playerName = player.getName();

    // Remove from database
    sqliteManager.unlink(uuid);
    sqliteManager.updateRank(uuid, null);

    // Call Discord bot to remove roles and reset nickname
    cleanupDiscord(discordId, uuid);

    send(player, "messages.unlink-success");

    plugin.getLogger().info(playerName + " unlinked their account");
}

private void cleanupDiscord(String discordId, UUID uuid) {
    try {
        // Use reflection to call Discord bot cleanup
        Class<?> botClass = Class.forName("com.directdrop.linkbridge.bot.DiscordBotMain");
        java.lang.reflect.Method getJDA = botClass.getMethod("getJDA");
        Object jda = getJDA.invoke(null);
        
        if (jda != null) {
            // Get RoleManager
            java.lang.reflect.Field roleManagerField = botClass.getDeclaredField("roleManager");
            roleManagerField.setAccessible(true);
            Object roleManager = roleManagerField.get(null);
            
            if (roleManager != null) {
                java.lang.reflect.Method removeRoles = roleManager.getClass().getMethod("removeRoles", String.class);
                removeRoles.invoke(roleManager, discordId);
            }
            
            // Get NicknameManager
            java.lang.reflect.Field nicknameManagerField = botClass.getDeclaredField("nicknameManager");
            nicknameManagerField.setAccessible(true);
            Object nicknameManager = nicknameManagerField.get(null);
            
            if (nicknameManager != null) {
                java.lang.reflect.Method resetNickname = nicknameManager.getClass().getMethod("resetNickname", String.class);
                resetNickname.invoke(nicknameManager, discordId);
            }
        }
    } catch (Exception e) {
        plugin.getLogger().warning("Failed to cleanup Discord on unlink: " + e.getMessage());
    }
}

public void handleStatus(Player player) {

    UUID uuid = player.getUniqueId();

    LinkData data = sqliteManager.getLinkData(uuid);

    if (data == null || !data.isLinked()) {
        send(player, "messages.status-not-linked");
        return;
    }

    player.sendMessage(
            color("messages.status-linked")
                    .replace("%discord_id%", data.getDiscordId())
    );

    if (luckPermsHook.isLuckPermsEnabled()) {
        String rank = luckPermsHook.getCachedRank(uuid);

        player.sendMessage(
                plugin.getConfig().getString("messages.prefix", "")
                        + "§7Rank: §e" + rank
        );
    }
}

private String generateCode() {

    String charset = plugin.getConfig().getString(
            "code.charset",
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    );

    int length = plugin.getConfig().getInt(
            "code.length",
            6
    );

    StringBuilder builder = new StringBuilder();

    for (int i = 0; i < length; i++) {
        builder.append(
                charset.charAt(
                        random.nextInt(charset.length())
                )
        );
    }

    return builder.toString();
}

private void send(Player player, String path) {
    player.sendMessage(color(path));
}

private String color(String path) {
    return plugin.getConfig().getString(
            "messages.prefix",
            ""
    ) + plugin.getConfig().getString(
            path,
            ""
    );
}

public void reload() {
    plugin.reloadConfig();
    plugin.getLogger().info("LinkBridge reloaded.");
}

public SQLiteManager getSqliteManager() {
    return sqliteManager;
}

public LuckPermsHook getLuckPermsHook() {
    return luckPermsHook;
}
}