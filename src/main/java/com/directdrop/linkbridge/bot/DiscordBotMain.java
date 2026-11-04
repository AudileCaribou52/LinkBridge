package com.directdrop.linkbridge.bot;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.hook.LuckPermsHook;
import com.directdrop.linkbridge.storage.SQLiteManager;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

public class DiscordBotMain {

    private static JDA jda;
    private static LinkBridge plugin;
    private static SQLiteManager sqliteManager;

    private static LinkCommand linkCommand;
    private static RoleManager roleManager;
    private static NicknameManager nicknameManager;

    public static void start(LinkBridge pluginInstance) {
        plugin = pluginInstance;
        sqliteManager = plugin.getSqliteManager();

        LuckPermsHook luckPermsHook = plugin.getLinkManager().getLuckPermsHook();

        String token = plugin.getConfig().getString("discord.token");
        String guildId = plugin.getConfig().getString("discord.guildId");

        if (token == null || token.isBlank()) {
            plugin.getLogger().severe("Missing Discord bot token");
            return;
        }

        if (guildId == null || guildId.isBlank()) {
            plugin.getLogger().severe("Missing Discord guild ID");
            return;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setActivity(Activity.playing("LinkBridge"))
                    .build();

            jda.awaitReady();

            roleManager = new RoleManager(jda, plugin, sqliteManager);
            nicknameManager = new NicknameManager(jda, plugin, sqliteManager);

            linkCommand = new LinkCommand(
                    plugin,
                    sqliteManager,
                    roleManager,
                    nicknameManager,
                    luckPermsHook
            );

            jda.addEventListener(linkCommand);

            registerCommands(guildId);

            plugin.getLogger().info("Discord bot started successfully!");

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start Discord bot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void registerCommands(String guildId) {
        Guild guild = jda.getGuildById(guildId);

        if (guild == null) {
            plugin.getLogger().warning("Guild not found");
            return;
        }

        guild.upsertCommand("link", "Link your Minecraft account")
                .addOption(OptionType.STRING, "code", "Your linking code", true)
                .queue();

        guild.upsertCommand("unlink", "Unlink your Minecraft account")
                .queue();
    }

    public static void stop() {
        if (jda != null) {
            jda.shutdown();
            jda = null;
        }
    }

    public static JDA getJDA() {
        return jda;
    }

    public static void logToDiscord(String message) {
        String channelId = plugin.getConfig().getString("discord.logChannel");

        if (channelId == null || jda == null) return;

        var channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }
}