/*
 * Copyright (C) 2020 Ashley "luna" Davis, Airside Networks and other contributors
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package luna.discord.listener;

import luna.discord.DiscordCommandSender;
import luna.discord.IMCHandler;
import luna.discord.core.DiscordClient;
import luna.discord.core.DiscordIntegrationLogger;
import luna.discord.core.Message;
import luna.discord.core.Patterns;
import luna.discord.core.config.ConfigWrapper;
import luna.discord.core.config.Configuration;
import luna.discord.core.config.discord.CommandConfig;
import luna.discord.core.config.discord.DiscordChannelGenericConfig;
import luna.discord.core.config.discord.DiscordConfig;
import luna.discord.core.config.linking.LinkingRequest;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.core.Permission;
import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class DiscordListener extends ListenerAdapter {
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        ConfigWrapper config = Configuration.getConfig();
        DiscordConfig discordConfig = config.discord;

        if (shouldIgnoreMessage(event)) return;

        String content = event.getMessage().getContentDisplay().trim();

        if (event.getChannelType() == ChannelType.TEXT) {
            Long channelId = event.getChannel().getIdLong();

            DiscordChannelGenericConfig channelConfig;
            ArrayList<Integer> dimensions;
            boolean stripMinecraftCodes = discordConfig.channels.generic.stripMinecraftCodes;
            if (discordConfig.channels.channels.containsKey(channelId)) {
                channelConfig = discordConfig.channels.channels.get(channelId);
                dimensions = channelConfig.relayChat.getDimensions(discordConfig.channels.generic.relayChat);
                if (channelConfig.stripMinecraftCodes != null) {
                    stripMinecraftCodes = channelConfig.stripMinecraftCodes;
                }
            } else {
                // Don't relay messages from channels not configured
                return;
            }

            if (dimensions == null) {
                return;
            }

            if (IMCHandler.haveListeners()) {
                emitIMCMessage(event);
            }

            String prefix = channelConfig.commandPrefix != null ? channelConfig.commandPrefix : discordConfig.channels.generic.commandPrefix;
            if (content.startsWith(prefix)) {
                List<String> args = new ArrayList<>(Arrays.asList(content.substring(prefix.length()).split(" ")));
                tryExecuteCommand(event, args);
                return;
            }

            broadcastMessageIngame(event, dimensions, stripMinecraftCodes);
        } else if (event.getChannelType() == ChannelType.PRIVATE && Configuration.getConfig().discord.channels.generic.allowDMCommands) {
            String prefix = discordConfig.channels.generic.commandPrefix;
            if (content.startsWith(prefix)) {
                List<String> args = new ArrayList<>(Arrays.asList(content.substring(prefix.length()).split(" ")));
                tryExecuteCommand(event, args);
            }
        }
    }

    private boolean shouldIgnoreMessage(MessageReceivedEvent event) {

        // Ignore bots
        if (Configuration.getConfig().discord.ignoresBots && event.getAuthor().isBot()) {
            return true;
        }

        // Ignore self
        if (event.getAuthor().getId().equals(DiscordClient.getInstance().getSelf().getId())) {
            return true;
        }

        // Ignore specified users
        return Configuration.getConfig().discord.isIgnoringUser(event.getAuthor());
    }

    private void broadcastMessageIngame(MessageReceivedEvent event, ArrayList<Integer> dimensions, boolean stripMinecraftCodes) {
        String content = event.getMessage().getContentDisplay().trim();
        MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();
        List<EntityPlayerMP> players;
        if (dimensions.size() == 0) {
            players = minecraftServer.getPlayerList().getPlayers();
        } else {
            players = minecraftServer.getPlayerList().getPlayers()
                .stream()
                .filter(player -> dimensions.contains(player.dimension))
                .collect(Collectors.toList());
        }

        if (stripMinecraftCodes) {
            content = Patterns.minecraftCodePattern.matcher(content).replaceAll("");
        }

        Message message = new Message()
            .setAuthor(event.getMember().getEffectiveName())
            .setMessage(Configuration.getConfig().discord.channels.generic.messages.chatMessage)
            .setArguments(buildMessageReceivedArgumentList(event));

        DiscordIntegrationLogger.Log(message.getFormattedTextMinecraft());
        for (EntityPlayerMP player : players) {
            player.sendMessage(new TextComponentString(message.getFormattedTextMinecraft()));
        }
    }

    private void emitIMCMessage(MessageReceivedEvent event) {
        NBTTagCompound eventTagCompound = new NBTTagCompound();
        eventTagCompound.setString("type", "chat");

        NBTTagCompound userTagComponent = new NBTTagCompound();
        userTagComponent.setString("id", event.getAuthor().getId());
        userTagComponent.setString("username", event.getAuthor().getName());
        userTagComponent.setString("discriminator", event.getAuthor().getDiscriminator());

        eventTagCompound.setTag("user", userTagComponent);
        eventTagCompound.setString("message", event.getMessage().getContentDisplay().trim());

        IMCHandler.emitMessage("event", eventTagCompound);
    }

    @NotNull
    private HashMap<String, String> buildMessageReceivedArgumentList(MessageReceivedEvent event) {
        HashMap<String, String> arguments = new HashMap<>();
        arguments.put(
            "MESSAGE",
            event.getMessage().getContentDisplay().trim()
        );
        arguments.put(
            "CHANNEL",
            event.getChannelType() == ChannelType.PRIVATE ? "DM" : event.getChannel().getName()
        );
        return arguments;
    }

    private void tryExecuteCommand(MessageReceivedEvent event, List<String> args) {
        String cmd = args.remove(0);

        if (Configuration.getConfig().discord.allowLinking) {
            if (cmd.equalsIgnoreCase("link")) {
                handleLinkCommand(event);
                return;
            }
            if (cmd.equalsIgnoreCase("unlink")) {
                handleUnlinkCommand(event);
                return;
            }
        }

        List<CommandConfig> commands = Configuration.getConfig().discord.getCommandConfigs();
        for (CommandConfig command : commands) {
            if (command.shouldExecute(cmd, event.getAuthor(), event.getChannel())) {
                FMLCommonHandler.instance().getMinecraftServerInstance().callFromMainThread(() -> {
                    FMLCommonHandler.instance().getMinecraftServerInstance().getCommandManager().executeCommand(
                        new DiscordCommandSender(event.getChannel(), event.getAuthor(), command),
                        command.buildCommand(args)
                    );
                    return 0;
                });
                return;
            }
        }
    }

    private void handleLinkCommand(MessageReceivedEvent event) {
        MinecraftServer minecraftServer = FMLCommonHandler.instance().getMinecraftServerInstance();

        UUID minecraftUUID = Configuration.getLinking().getMinecraftId(event.getAuthor().getIdLong());
        if (minecraftUUID != null) {
            GameProfile minecraftProfile = minecraftServer.getPlayerProfileCache().getProfileByUUID(minecraftUUID);
            event.getAuthor().openPrivateChannel()
                .queue(privateChannel -> privateChannel.sendMessage(
                    String.format(
                        "You are already linked to %s",
                        minecraftProfile == null ? "a Minecraft account" : minecraftProfile.getName()
                    )
                ).queue());
            return;
        }

        LinkingRequest request = Configuration.getLinking().getRequest(event.getAuthor().getIdLong());

        if (request.hasExpired()) {
            request.generateCode();
        }

        event.getAuthor().openPrivateChannel()
            .queue(privateChannel -> privateChannel.sendMessage(
                String.format(
                    "Use `/discord link %s` on the Minecraft server to link your Discord user with your Minecraft user.\nThe code expires in %s!",
                    request.getCode(),
                    request.expiresIn()
                )
            ).queue());

        if (event.getMember().getPermissions(event.getTextChannel()).contains(Permission.MESSAGE_MANAGE)) {
            event.getMessage().delete().queue();
        }

        Configuration.saveLinking();
    }

    private void handleUnlinkCommand(MessageReceivedEvent event) {
        UUID minecraftUUID = Configuration.getLinking().getMinecraftId(event.getAuthor().getIdLong());
        if (minecraftUUID == null) {
            event.getAuthor().openPrivateChannel()
                .queue(privateChannel -> privateChannel.sendMessage(
                    "You aren't linked"
                ).queue());
        } else {
            Configuration.getLinking().removeLink(minecraftUUID);
            event.getAuthor().openPrivateChannel()
                .queue(privateChannel -> privateChannel.sendMessage(
                    "Unlinked"
                ).queue());
        }

        if (event.getMember().getPermissions(event.getTextChannel()).contains(Permission.MESSAGE_MANAGE)) {
            event.getMessage().delete().queue();
        }
    }
}
