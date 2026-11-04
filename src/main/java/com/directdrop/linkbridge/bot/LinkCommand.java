package com.directdrop.linkbridge.bot;

import com.directdrop.linkbridge.LinkBridge;
import com.directdrop.linkbridge.hook.LuckPermsHook;
import com.directdrop.linkbridge.model.LinkData;
import com.directdrop.linkbridge.storage.SQLiteManager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Handles the /link slash command.
 */
public class LinkCommand extends ListenerAdapter {

    private final LinkBridge plugin;
    private final SQLiteManager sqliteManager;
    private final RoleManager roleManager;
    private final NicknameManager nicknameManager;
    private final LuckPermsHook luckPermsHook;

    public LinkCommand(LinkBridge plugin,
                        SQLiteManager sqliteManager,
                        RoleManager roleManager,
                        NicknameManager nicknameManager,
                        LuckPermsHook luckPermsHook) {

        this.plugin = plugin;
        this.sqliteManager = sqliteManager;
        this.roleManager = roleManager;
        this.nicknameManager = nicknameManager;
        this.luckPermsHook = luckPermsHook;

        DiscordBotMain.getJDA().addEventListener(this);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {

        if (!event.getName().equals("link")) return;

        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            event.reply("Please provide a linking code.").setEphemeral(true).queue();
            return;
        }

        String code = codeOption.getAsString().trim().toUpperCase();
        String discordId = event.getUser().getId();

        event.deferReply(true).queue();

        LinkData linkData = sqliteManager.validateCode(code);
        if (linkData == null) {
            sendError(event, "Invalid or expired code.");
            return;
        }

        if (sqliteManager.isDiscordLinked(discordId)) {
            sendError(event, "This Discord account is already linked.");
            return;
        }

        if (sqliteManager.isMinecraftLinked(linkData.getUuid())) {
            sendError(event, "This Minecraft account is already linked.");
            return;
        }

        LinkData result = sqliteManager.linkAccount(code, discordId);
        if (result == null) {
            sendError(event, "Failed to link account.");
            return;
        }

        UUID uuid = result.getUuid();

        String rank = "default";
        if (luckPermsHook != null && luckPermsHook.isLuckPermsEnabled()) {
            rank = luckPermsHook.getCachedRank(uuid);
            if (rank == null || rank.isEmpty()) rank = "default";

            sqliteManager.updateRank(uuid, rank);
            result.setRank(rank);
        }

        String minecraftName = result.getMinecraftName();
        final String finalRank = rank;

        roleManager.syncFull(discordId, finalRank);
        nicknameManager.setNickname(discordId, minecraftName, finalRank);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Successfully Linked!")
                .setDescription("Linked to **" + minecraftName + "**")
                .setColor(Color.GREEN)
                .addField("UUID", uuid.toString(), true)
                .addField("Rank", finalRank, true)
                .setTimestamp(Instant.now())
                .setFooter("LinkBridge");

        event.getHook().sendMessageEmbeds(embed.build()).queue();

        DiscordBotMain.logToDiscord(
                "🔗 **" + event.getUser().getAsTag() +
                        "** linked to **" + minecraftName + "**"
        );

        plugin.getLogger().info("Linked " + discordId + " -> " + minecraftName);
    }

    // =========================
    // UNLINK (FIXED VERSION)
    // =========================
    public void unlink(String discordId, UUID uuid) {

        sqliteManager.unlink(uuid);
        sqliteManager.updateRank(uuid, null);

        Guild guild = DiscordBotMain.getJDA()
                .getGuildById(plugin.getConfig().getString("discord.guildId"));

        if (guild == null) return;

        Member member = guild.getMemberById(discordId);
        if (member == null) return;

        member.modifyNickname(null).queue();

        for (String roleId : roleManager.getAllRoleIds()) {
            Role role = guild.getRoleById(roleId);
            if (role != null && member.getRoles().contains(role)) {
                guild.removeRoleFromMember(member, role).queue();
            }
        }
    }

    // =========================
    // ERROR HELPER
    // =========================
    private void sendError(SlashCommandInteractionEvent event, String message) {

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("❌ Error")
                .setDescription(message)
                .setColor(Color.RED)
                .setTimestamp(Instant.now());

        event.getHook().sendMessageEmbeds(embed.build()).queue();
    }
}