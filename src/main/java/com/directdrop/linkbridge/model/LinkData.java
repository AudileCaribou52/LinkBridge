package com.directdrop.linkbridge.model;

import java.util.UUID;

/**
 * Represents a link between a Minecraft player and a Discord user.
 */
public class LinkData {

    private final UUID uuid;
    private final String minecraftName;
    private String discordId;
    private String rank;
    private final long expires;
    private boolean used;
    private String code;

    public LinkData(UUID uuid, String minecraftName, String discordId, String rank, long expires, boolean used) {
        this.uuid = uuid;
        this.minecraftName = minecraftName;
        this.discordId = discordId;
        this.rank = rank;
        this.expires = expires;
        this.used = used;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getMinecraftName() {
        return minecraftName;
    }

    public String getDiscordId() {
        return discordId;
    }

    public void setDiscordId(String discordId) {
        this.discordId = discordId;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public long getExpires() {
        return expires;
    }

    public boolean isUsed() {
        return used;
    }

    public void setUsed(boolean used) {
        this.used = used;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isLinked() {
        return used && discordId != null && !discordId.isEmpty();
    }
}