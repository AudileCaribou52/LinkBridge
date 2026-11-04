package com.directdrop.linkbridge.bot;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.storage.SQLiteManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Manages Discord role assignment based on LuckPerms ranks.
 */
public class RoleManager {

    private final JDA jda;
    private final LinkBridge plugin;
    private final SQLiteManager sqliteManager;
    private final Map<String, String> rankToRoleId;

    public RoleManager(JDA jda, LinkBridge plugin, SQLiteManager sqliteManager) {
        this.jda = jda;
        this.plugin = plugin;
        this.sqliteManager = sqliteManager;
        this.rankToRoleId = plugin.getConfig().getConfigurationSection("groups") != null
                ? plugin.getConfig().getConfigurationSection("groups").getKeys(false).stream()
                .collect(java.util.stream.Collectors.toMap(
                        key -> key,
                        key -> plugin.getConfig().getString("groups." + key)
                ))
                : Map.of();
    }

    /**
     * Assigns a role to a Discord user based on their Minecraft rank.
     * First removes ALL managed roles, then adds the new one.
     */
    public CompletableFuture<Boolean> assignRole(String discordId, String rank) {
        return syncFull(discordId, rank);
    }

    /**
     * Removes all LinkBridge roles from a Discord user.
     */
    public CompletableFuture<Boolean> removeRoles(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String guildId = plugin.getConfig().getString("discord.guildId");
                if (guildId == null || guildId.isEmpty()) {
                    return false;
                }

                Guild guild = jda.getGuildById(guildId);
                if (guild == null) {
                    return false;
                }

                Member member = guild.retrieveMemberById(discordId).complete();
                if (member == null) {
                    return false;
                }

                // Remove all configured roles
                for (String roleId : rankToRoleId.values()) {
                    if (roleId == null || roleId.isEmpty()) continue;
                    
                    Role role = guild.getRoleById(roleId);
                    if (role != null && member.getRoles().contains(role)) {
                        guild.removeRoleFromMember(member, role).complete();
                        plugin.getLogger().info("Removed role " + role.getName() + " from " + member.getUser().getName());
                    }
                }

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error removing roles: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    /**
     * Full sync: removes ALL managed roles, then adds the new rank role.
     * Uses .complete() to ensure removal happens before addition.
     */
    public CompletableFuture<Boolean> syncFull(String discordId, String rank) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String guildId = plugin.getConfig().getString("discord.guildId");
                if (guildId == null) return false;

                Guild guild = jda.getGuildById(guildId);
                if (guild == null) return false;

                Member member = guild.retrieveMemberById(discordId).complete();
                if (member == null) return false;

                // 1. REMOVE ALL MANAGED ROLES (synchronous)
                for (String roleId : rankToRoleId.values()) {
                    if (roleId == null) continue;

                    Role role = guild.getRoleById(roleId);
                    if (role != null && member.getRoles().contains(role)) {
                        guild.removeRoleFromMember(member, role).complete();
                        plugin.getLogger().info("Removed role " + role.getName() + " from " + member.getUser().getName());
                    }
                }

                // 2. ADD NEW ROLE (synchronous)
                String roleId = rankToRoleId.getOrDefault(rank.toLowerCase(), rankToRoleId.get("default"));

                if (roleId != null) {
                    Role newRole = guild.getRoleById(roleId);
                    if (newRole != null) {
                        guild.addRoleToMember(member, newRole).complete();
                        plugin.getLogger().info("Added role " + newRole.getName() + " to " + member.getUser().getName());
                    }
                }

                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error in syncFull: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        });
    }

    public java.util.Collection<String> getAllRoleIds() {
        return rankToRoleId.values();
    }
}