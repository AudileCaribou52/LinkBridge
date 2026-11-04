package com.directdrop.linkbridge.storage;

import com.directdrop.linkbridge.model.LinkData;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Handles storage and retrieval of link data using SQLite database.
 * Shared between Minecraft plugin and Discord bot.
 */
public class SQLiteManager {

    private final Plugin plugin;
    private final File dbFile;
    private Connection connection;

    public SQLiteManager(Plugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), plugin.getConfig().getString("database.file", "linkbridge.db"));
        initialize();
    }

  private void initialize() {
    try {
        dbFile.getParentFile().mkdirs();

        String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        connection = DriverManager.getConnection(url);

        connection.setAutoCommit(true);

        createTables();

        plugin.getLogger().info("SQLite database initialized: " + dbFile.getAbsolutePath());

    } catch (SQLException e) {
        plugin.getLogger().severe("Failed to initialize SQLite database: " + e.getMessage());
        e.printStackTrace();
    }
}
private void debugRow(UUID uuid) {
    String sql = "SELECT minecraft_uuid, used, rank FROM links WHERE minecraft_uuid = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, uuid.toString());
        ResultSet rs = stmt.executeQuery();

        if (!rs.next()) {
            plugin.getLogger().warning("[DEBUG] Row NOT FOUND in runtime DB!");
            return;
        }

        plugin.getLogger().info("[DEBUG] runtime used = " + rs.getInt("used"));
        plugin.getLogger().info("[DEBUG] runtime rank = " + rs.getString("rank"));

    } catch (Exception e) {
        e.printStackTrace();
    }
}
private void checkRow(UUID uuid) {
    String sql = "SELECT minecraft_uuid, used, rank FROM links WHERE minecraft_uuid = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, uuid.toString());

        ResultSet rs = stmt.executeQuery();

        if (!rs.next()) {
            plugin.getLogger().warning("[LinkBridge] ❌ Row NOT FOUND in DB for UUID: " + uuid);
            return;
        }

        plugin.getLogger().info("[LinkBridge] 🔍 DB CHECK");
        plugin.getLogger().info("[LinkBridge] UUID = " + rs.getString("minecraft_uuid"));
        plugin.getLogger().info("[LinkBridge] USED = " + rs.getInt("used"));
        plugin.getLogger().info("[LinkBridge] RANK = " + rs.getString("rank"));

    } catch (SQLException e) {
        plugin.getLogger().severe("[LinkBridge] DB check failed: " + e.getMessage());
        e.printStackTrace();
    }
}
    private void createTables() throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS links (
                code TEXT PRIMARY KEY,
                minecraft_uuid TEXT NOT NULL,
                minecraft_name TEXT NOT NULL,
                discord_id TEXT,
                rank TEXT,
                expires INTEGER NOT NULL,
                used INTEGER DEFAULT 0
            );
            CREATE INDEX IF NOT EXISTS idx_minecraft_uuid ON links(minecraft_uuid);
            CREATE INDEX IF NOT EXISTS idx_discord_id ON links(discord_id);
            CREATE INDEX IF NOT EXISTS idx_expires ON links(expires);
            """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to close database connection: " + e.getMessage());
            }
        }
    }

    /**
     * Creates a new pending linking code.
     */
    public void createPendingCode(UUID uuid, String code, String minecraftName) {
        long now = Instant.now().toEpochMilli();
        long expiryMinutes = plugin.getConfig().getLong("code.expiry-minutes", 10);
        long expires = now + (expiryMinutes * 60 * 1000);

        String sql = "INSERT OR REPLACE INTO links (code, minecraft_uuid, minecraft_name, discord_id, rank, expires, used) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code.toUpperCase());
            stmt.setString(2, uuid.toString());
            stmt.setString(3, minecraftName);
            stmt.setString(4, "");
            stmt.setString(5, "");
            stmt.setLong(6, expires);
            stmt.setInt(7, 0);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create pending code: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gets pending code for a UUID.
     */
    public String getPendingCode(UUID uuid) {
        String sql = "SELECT code, expires, used FROM links WHERE minecraft_uuid = ? AND used = 0 ORDER BY expires DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                long expires = rs.getLong("expires");
                int used = rs.getInt("used");
                long now = Instant.now().toEpochMilli();
                
                if (used == 0 && expires > now) {
                    return rs.getString("code");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get pending code: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Validates a code and returns the link data if valid.
     */
    public LinkData validateCode(String code) {
        String sql = "SELECT * FROM links WHERE code = ? AND used = 0";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, code.toUpperCase());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                long expires = rs.getLong("expires");
                long now = Instant.now().toEpochMilli();
                
                if (expires <= now) {
                    return null; // Expired
                }
                
                return mapResultSetToLinkData(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to validate code: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Links a Discord account to a Minecraft account using a code.
     */
    public LinkData linkAccount(String code, String discordId) {
        LinkData data = validateCode(code);
        if (data == null) {
            return null;
        }

        // Check if Discord account is already linked
        if (isDiscordLinked(discordId)) {
            return null;
        }

        // Check if Minecraft account is already linked
        if (isMinecraftLinked(data.getUuid())) {
            return null;
        }

        String sql = "UPDATE links SET discord_id = ?, used = 1 WHERE code = ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            stmt.setString(2, code.toUpperCase());
            int affected = stmt.executeUpdate();
            
            if (affected > 0) {
                data.setDiscordId(discordId);
                data.setUsed(true);
                return data;
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to link account: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Updates the rank for a linked account.
     */
public void updateRank(UUID uuid, String rank) {
    String sql = "UPDATE links SET rank = ? WHERE minecraft_uuid = ? AND used = 1";

    plugin.getLogger().info("[LinkBridge] SQL UPDATE rank");
    plugin.getLogger().info("[LinkBridge] rank = " + rank);
    plugin.getLogger().info("[LinkBridge] uuid = '" + uuid.toString() + "'");

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, rank);
        stmt.setString(2, uuid.toString());

        int rows = stmt.executeUpdate();

        if (rows == 0) {
            String checkSql = "SELECT used FROM links WHERE minecraft_uuid = ?";
            try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
                checkStmt.setString(1, uuid.toString());
                ResultSet rs = checkStmt.executeQuery();
                if (rs.next()) {
                    int used = rs.getInt("used");
                    plugin.getLogger().warning(
                            "[LinkBridge] ⚠ No row updated! UUID exists but used=" + used + ": "
                                    + uuid + " -> attempted rank: " + rank
                    );
                } else {
                    plugin.getLogger().warning(
                            "[LinkBridge] ⚠ No row updated! UUID not found in DB: "
                                    + uuid + " -> attempted rank: " + rank
                    );
                }
            }
        } else {
            plugin.getLogger().info(
                    "[LinkBridge] Updated rank in DB: "
                            + uuid
                            + " -> "
                            + rank
                            + " (" + rows + " rows)"
            );
        }

    } catch (SQLException e) {
        plugin.getLogger().severe("[LinkBridge] Failed to update rank: " + e.getMessage());
        e.printStackTrace();
    }
}

    /**
     * Gets link data by Minecraft UUID.
     */
    public LinkData getLinkData(UUID uuid) {
        String sql = "SELECT * FROM links WHERE minecraft_uuid = ? AND used = 1 ORDER BY expires DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToLinkData(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get link data: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Gets link data by Discord ID.
     */
    public LinkData getLinkDataByDiscordId(String discordId) {
        String sql = "SELECT * FROM links WHERE discord_id = ? AND used = 1 ORDER BY expires DESC LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToLinkData(rs);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get link data by Discord ID: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks if a Minecraft account is already linked.
     */
    public boolean isMinecraftLinked(UUID uuid) {
        String sql = "SELECT 1 FROM links WHERE minecraft_uuid = ? AND used = 1 LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, uuid.toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check Minecraft link: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Checks if a Discord account is already linked.
     */
    public boolean isDiscordLinked(String discordId) {
        String sql = "SELECT 1 FROM links WHERE discord_id = ? AND used = 1 LIMIT 1";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, discordId);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to check Discord link: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Unlinks a Minecraft account.
     */
public boolean unlink(UUID uuid) {
    String sql = "DELETE FROM links WHERE minecraft_uuid = ?";

    try (PreparedStatement stmt = connection.prepareStatement(sql)) {
        stmt.setString(1, uuid.toString());

        int rows = stmt.executeUpdate();
        return rows > 0; // true if something was deleted

    } catch (SQLException e) {
        plugin.getLogger().severe("Failed to unlink: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}

    /**
     * Cleans up expired unused codes.
     */
    public void cleanupExpiredCodes() {
        long now = Instant.now().toEpochMilli();
        String sql = "DELETE FROM links WHERE used = 0 AND expires < ?";
        
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setLong(1, now);
            int deleted = stmt.executeUpdate();
            if (deleted > 0) {
                plugin.getLogger().info("Cleaned up " + deleted + " expired codes");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to cleanup expired codes: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private LinkData mapResultSetToLinkData(ResultSet rs) throws SQLException {
        LinkData data = new LinkData(
            UUID.fromString(rs.getString("minecraft_uuid")),
            rs.getString("minecraft_name"),
            rs.getString("discord_id"),
            rs.getString("rank"),
            rs.getLong("expires"),
            rs.getInt("used") == 1
        );
        data.setCode(rs.getString("code"));
        return data;
    }
}