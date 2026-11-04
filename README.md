# LinkBridge

LinkBridge is a Paper plugin that links Minecraft accounts to Discord accounts using a built-in JDA bot. It does not use DiscordSRV.

## Features

- Generate a one-time linking code in Minecraft and redeem it with the Discord `/link` command.
- Check link status or unlink from Minecraft.
- Store links and pending codes in a local SQLite database.
- Optionally use LuckPerms primary groups to assign mapped Discord roles and format Discord nicknames.
- Refresh mapped roles and nicknames when a player's LuckPerms primary group changes.
- Configure an optional Discord channel for link-event logs.

Chat bridging is not implemented. The chat-bridge entries in the generated configuration do not enable chat forwarding.

## Requirements

- Paper server compatible with the Paper API version in `pom.xml` (currently `26.3.build.218-stable`)
- Java 25
- A Discord bot application and server where you have permission to add the bot
- LuckPerms is optional; without it, LinkBridge uses the `default` rank

## Installation

1. Download the LinkBridge JAR from the project's releases.
2. Create a Discord application and bot in the [Discord Developer Portal](https://discord.com/developers/applications).
3. Invite the bot to your Discord server with the `bot` and `applications.commands` OAuth scopes. Grant it permission to view the server, manage roles, and manage nicknames. Place its role above the roles LinkBridge will manage.
4. Put the LinkBridge JAR in the server's `plugins` directory and start the server once to generate its configuration.
5. In `plugins/LinkBridge/config.yml`, set `discord.token` to the bot token and `discord.guildId` to your Discord server ID.
6. (Optional) Set `discord.logChannel` to a channel ID for link-event logs, and configure the `groups` section with your LuckPerms group names and Discord role IDs.
7. Restart the server. Check the console for bot startup errors.

The bot uses the `GUILD_MEMBERS`, `GUILD_MESSAGES`, and `MESSAGE_CONTENT` gateway intents. Enable the privileged Server Members and Message Content intents for the bot in the Developer Portal if Discord requires them.

## Configuration

The relevant settings in `config.yml` are:

```yaml
discord:
  token: "YOUR_BOT_TOKEN_HERE"
  guildId: "YOUR_GUILD_ID_HERE"
  logChannel: "YOUR_LOG_CHANNEL_ID_HERE"

database:
  file: "linkbridge.db"

code:
  length: 6
  charset: "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
  expiry-minutes: 10

groups:
  owner: "DISCORD_ROLE_ID_FOR_OWNER"
  admin: "DISCORD_ROLE_ID_FOR_ADMIN"
  default: "DISCORD_ROLE_ID_FOR_DEFAULT"

nickname-sync:
  format: "[%rank%] %name%"
```

`database.file` is stored in the plugin's data folder. Codes expire according to `code.expiry-minutes`; the bot treats them case-insensitively. Use lowercase keys for `groups`, since LinkBridge lowercases LuckPerms primary group names when looking up role mappings. When LuckPerms is unavailable or a group has no mapping, LinkBridge uses the `default` mapping if one is configured.

Role assignment and nickname updates currently happen automatically on account linking, and rank changes are synchronized when LuckPerms is installed. The `role-sync.enabled`, `role-sync.remove-on-unlink`, and `nickname-sync.enabled` settings in the generated config are not currently used by the implementation. Restart the server after changing Discord credentials, guild settings, or role mappings; `/linkbridge reload` does not restart or reconfigure the bot.

## Usage

### Players

1. Run `/discord link` in Minecraft to get a code.
2. In your Discord server, run `/link` and enter that code.
3. Use `/discord status` in Minecraft to check your link, or `/discord unlink` to unlink.

Codes are one-time use, expire after the configured interval, and each Minecraft and Discord account can be linked to only one account on the other platform.

### Administrators

- `/linkbridge reload` reloads the plugin configuration.
- `/linkbridge forceunlink <player>` removes the stored link for an online player. This command does not also clear the member's Discord roles or nickname.

Permissions:

- `linkbridge.use` — use `/discord` (default: everyone)
- `linkbridge.admin` — use `/linkbridge` (default: operators)
- `linkbridge.reload` — reload the configuration (default: operators)
- `linkbridge.forceunlink` — force unlink (default: operators)

The reload and force-unlink subcommands also require `linkbridge.admin`, which is the base `/linkbridge` command permission.

## Building from source

Install Java 25 and Maven, then run the build from the project root. Confirm `java -version` reports Java 25. If Maven is on your `PATH`, run:

```sh
mvn clean package
```

In Windows PowerShell, if `mvn` is not recognized but `MAVEN_HOME` points to your Maven installation, run:

```powershell
& "$env:MAVEN_HOME\bin\mvn.cmd" clean package
```

Otherwise, add Maven's `bin` directory to your `PATH` or invoke `mvn.cmd` using its full path. The build downloads the Paper API version configured in `pom.xml` (`26.3.build.218-stable`) and writes the packaged plugin to `target/LinkBridge-1.0.1.jar`.
