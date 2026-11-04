package com.directdrop.linkbridge.hook;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.storage.SQLiteManager;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsHook {

    private final Plugin plugin;
    private final SQLiteManager sqliteManager;
    private final LuckPerms luckPerms;
    private final LinkBridge linkBridgePlugin;

    public LuckPermsHook(Plugin plugin, SQLiteManager sqliteManager) {
        this.plugin = plugin;
        this.sqliteManager = sqliteManager;

        plugin.getLogger().info("========== LuckPerms Hook ==========");

        this.luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);

        if (luckPerms == null) {
            plugin.getLogger().warning("LuckPerms NOT FOUND");
            this.linkBridgePlugin = null;
        } else {
            plugin.getLogger().info("LuckPerms FOUND");
            plugin.getLogger().info("Provider: " + luckPerms.getClass().getName());
            this.linkBridgePlugin = (plugin instanceof LinkBridge) ? (LinkBridge) plugin : null;
        }

        plugin.getLogger().info("====================================");
        
        // Register listener for LuckPerms rank changes
        if (this.luckPerms != null && this.linkBridgePlugin != null) {
            registerLuckPermsListener();
        }
    }

    private void registerLuckPermsListener() {
        // Listen for user data recalculation events (rank changes)
        luckPerms.getEventBus().subscribe(plugin, net.luckperms.api.event.user.UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            
            // Small delay to let LuckPerms process the change
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                syncRankToDiscord(uuid);
            }, 20L); // 1 second delay
        });
    }

    /**
     * Syncs a player's current rank to Discord when it changes.
     */
    private void syncRankToDiscord(UUID uuid) {
        if (luckPerms == null || linkBridgePlugin == null) return;

        String rank = getCachedRank(uuid);
        if (rank == null || rank.isEmpty()) {
            rank = "default";
        }

        // Update database
        sqliteManager.updateRank(uuid, rank);

        // Sync to Discord if bot is running
        try {
            Class<?> botClass = Class.forName("com.directdrop.linkbridge.bot.DiscordBotMain");
            java.lang.reflect.Method getJDA = botClass.getMethod("getJDA");
            Object jda = getJDA.invoke(null);
            
            if (jda != null) {
                // Get link data to find Discord ID
                var linkData = sqliteManager.getLinkData(uuid);
                if (linkData != null && linkData.isLinked()) {
                    String discordId = linkData.getDiscordId();
                    
                    // Get roleManager and update role
                    java.lang.reflect.Field roleManagerField = botClass.getDeclaredField("roleManager");
                    roleManagerField.setAccessible(true);
                    Object roleManager = roleManagerField.get(null);
                    
                    if (roleManager != null) {
                        java.lang.reflect.Method syncFull = roleManager.getClass().getMethod("syncFull", String.class, String.class);
                        plugin.getLogger().info(
                            "[RoleSync] Discord="
                                + discordId
                                + " Rank="
                                + rank
                    );
                        syncFull.invoke(roleManager, discordId, rank);
                        
                        plugin.getLogger().info("[LuckPerms] Auto-synced rank for " + uuid + " -> " + rank + " to Discord");
                    }
                    
                    // Also sync nickname
                    java.lang.reflect.Field nicknameManagerField = botClass.getDeclaredField("nicknameManager");
                    nicknameManagerField.setAccessible(true);
                    Object nicknameManager = nicknameManagerField.get(null);
                    
                    if (nicknameManager != null) {
                        java.lang.reflect.Method forceSync = nicknameManager.getClass().getMethod("forceSync", String.class, String.class, String.class);
                        forceSync.invoke(nicknameManager, discordId, linkData.getMinecraftName(), rank);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[LuckPerms] Failed to auto-sync rank: " + e.getMessage());
        }
    }

    public boolean isLuckPermsEnabled() {
        return luckPerms != null;
    }

    public CompletableFuture<String> getRank(UUID uuid) {

        if (luckPerms == null) {
            return CompletableFuture.completedFuture("default");
        }

        User loadedUser = luckPerms.getUserManager().getUser(uuid);

        if (loadedUser != null) {

            String rank = loadedUser.getPrimaryGroup();

            plugin.getLogger().info(
                    "[LP] Cached rank for "
                            + uuid
                            + " = "
                            + rank
            );

            return CompletableFuture.completedFuture(rank);
        }

        return luckPerms.getUserManager()
                .loadUser(uuid)
                .thenApply(user -> {

                    if (user == null) {
                        plugin.getLogger().warning(
                                "[LP] User load returned null for "
                                        + uuid
                        );
                        return "default";
                    }

                    String rank = user.getPrimaryGroup();

                    plugin.getLogger().info(
                            "[LP] Loaded rank for "
                                    + uuid
                                    + " = "
                                    + rank
                    );

                    return rank;
                });
    }

    public void updateRankInDatabase(UUID uuid, String playerName) {

        getRank(uuid).thenAccept(rank -> {

            plugin.getLogger().info(
                    "[LP] Saving rank for "
                            + playerName
                            + " -> "
                            + rank
            );

            sqliteManager.updateRank(uuid, rank);

        }).exceptionally(ex -> {

            plugin.getLogger().severe(
                    "[LP] Failed to update rank for "
                            + playerName
            );

            ex.printStackTrace();

            return null;
        });
    }

    public String getCachedRank(UUID uuid) {

        if (luckPerms == null) {
            return "default";
        }

        User user = luckPerms.getUserManager().getUser(uuid);

        if (user == null) {
            return "default";
        }

        return user.getPrimaryGroup();
    }
}