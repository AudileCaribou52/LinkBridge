package com.directdrop.linkbridge;

import com.directdrop.linkbridge.command.AdminCommand;
import com.directdrop.linkbridge.command.DiscordCommand;
import com.directdrop.linkbridge.manager.LinkManager;
import com.directdrop.linkbridge.storage.SQLiteManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LinkBridge extends JavaPlugin {

    private LinkManager linkManager;
    private SQLiteManager sqliteManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // ===== DATABASE =====
        this.sqliteManager = new SQLiteManager(this);

        // ===== MANAGER =====
        this.linkManager = new LinkManager(this);

        // ===== COMMANDS =====
        registerCommands();

        // ===== DISCORD BOT =====
        startDiscordBot();

        getLogger().info("LinkBridge enabled successfully.");
    }

    private void registerCommands() {
        if (getCommand("discord") != null) {
            DiscordCommand discordCommand = new DiscordCommand(this, linkManager);
            getCommand("discord").setExecutor(discordCommand);
            getCommand("discord").setTabCompleter(discordCommand);
        } else {
            getLogger().severe("Command 'discord' not found in plugin.yml!");
        }

        if (getCommand("linkbridge") != null) {
            getCommand("linkbridge").setExecutor(new AdminCommand(this, linkManager));
        } else {
            getLogger().severe("Command 'linkbridge' not found in plugin.yml!");
        }
    }

    private void startDiscordBot() {
        try {
            Class<?> botClass = Class.forName("com.directdrop.linkbridge.bot.DiscordBotMain");
            botClass.getMethod("start", LinkBridge.class).invoke(null, this);

            getLogger().info("Discord bot started.");
        } catch (ClassNotFoundException e) {
            getLogger().warning("Discord bot not found (class missing). Bot not started.");
        } catch (Exception e) {
            getLogger().severe("Failed to start Discord bot:");
            e.printStackTrace();
        }
    }

    @Override
    public void onDisable() {

        // ===== STOP BOT =====
        try {
            Class<?> botClass = Class.forName("com.directdrop.linkbridge.bot.DiscordBotMain");
            botClass.getMethod("stop").invoke(null);
            getLogger().info("Discord bot stopped.");
        } catch (Exception e) {
            getLogger().warning("Bot shutdown error: " + e.getMessage());
        }

        // ===== CLOSE DATABASE =====
        if (sqliteManager != null) {
            sqliteManager.close();
        }

        getLogger().info("LinkBridge disabled.");
    }

    // ===== GETTERS =====
    public LinkManager getLinkManager() {
        return linkManager;
    }

    public SQLiteManager getSqliteManager() {
        return sqliteManager;
    }
}