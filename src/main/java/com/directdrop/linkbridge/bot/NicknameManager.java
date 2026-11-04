package com.directdrop.linkbridge.bot;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.storage.SQLiteManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

import java.util.concurrent.CompletableFuture;

public class NicknameManager {

    private final JDA jda;
    private final LinkBridge plugin;
    private final SQLiteManager sqliteManager;

    private final String format;
    private final int maxLength = 32;

    public NicknameManager(JDA jda, LinkBridge plugin, SQLiteManager sqliteManager) {
        this.jda = jda;
        this.plugin = plugin;
        this.sqliteManager = sqliteManager;

        this.format = plugin.getConfig().getString(
                "nickname-sync.format",
                "[%rank%] %name%"
        );
    }

    public CompletableFuture<Boolean> setNickname(String discordId, String minecraftName, String rank) {
        return CompletableFuture.supplyAsync(() -> {

            String guildId = plugin.getConfig().getString("discord.guildId");
            if (guildId == null) return false;

            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return false;

            Member self = guild.getSelfMember();

            if (!self.hasPermission(Permission.NICKNAME_MANAGE)) {
                plugin.getLogger().warning("Missing MANAGE NICKNAMES permission");
                return false;
            }

            Member member;
            try {
                member = guild.retrieveMemberById(discordId).complete();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to fetch member: " + discordId);
                return false;
            }

            if (member == null) return false;

            String nickname = buildNickname(minecraftName, rank);

            if (nickname.equals(member.getEffectiveName())) {
                return true;
            }

            member.modifyNickname(nickname).queue(
                    success -> plugin.getLogger().info(
                            "Updated nickname: " + member.getUser().getName() + " -> " + nickname
                    ),
                    error -> plugin.getLogger().warning(
                            "Nickname update failed: " + error.getMessage()
                    )
            );

            return true;
        });
    }

    public CompletableFuture<Boolean> resetNickname(String discordId) {
        return CompletableFuture.supplyAsync(() -> {

            String guildId = plugin.getConfig().getString("discord.guildId");
            if (guildId == null) return false;

            Guild guild = jda.getGuildById(guildId);
            if (guild == null) return false;

            try {
                Member member = guild.retrieveMemberById(discordId).complete();

                if (member == null) return false;

                member.modifyNickname(null).queue();
                return true;

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to reset nickname: " + e.getMessage());
                return false;
            }
        });
    }

    private String buildNickname(String minecraftName, String rank) {

        if (rank == null || rank.isBlank()) {
            rank = "Member";
        }

        String nickname = format
                .replace("%name%", minecraftName)
                .replace("%rank%", rank);

        if (nickname.length() <= maxLength) {
            return nickname;
        }

        // simple safe truncate (prevents broken math bugs)
        return nickname.substring(0, Math.min(maxLength, nickname.length()));
    }
    public CompletableFuture<Boolean> forceSync(String discordId, String minecraftName, String rank) {
    return CompletableFuture.supplyAsync(() -> {

        try {
            // always rebuild fresh nickname
            return setNickname(discordId, minecraftName, rank).get();
        } catch (Exception e) {
            plugin.getLogger().warning("Force sync failed: " + e.getMessage());
            return false;
        }

    });
    }
}