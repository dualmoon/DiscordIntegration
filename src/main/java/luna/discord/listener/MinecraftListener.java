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

import luna.discord.core.CoreUtils;
import luna.discord.core.DiscordClient;
import luna.discord.core.Message;
import luna.discord.core.config.Configuration;
import luna.discord.core.config.minecraft.MinecraftConfig;
import luna.discord.core.config.minecraft.MinecraftDimensionConfig;
import luna.discord.core.config.types.MessageConfig;
import com.google.common.base.Joiner;
import net.dv8tion.jda.core.entities.User;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.translation.I18n;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;

public class MinecraftListener {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCommand(CommandEvent event) {
        if (event.isCanceled()) return;

        String commandName = event.getCommand().getName();

        if (commandName.equalsIgnoreCase("say") || commandName.equalsIgnoreCase("me")) {
            broadcastMeOrSayToDiscord(event);
        } else {
            String commandWithParameters = event.getCommand().getName() + " " + Joiner.on(" ").join(event.getParameters());
            boolean commandIgnored = Configuration.getConfig().minecraft.dimensions.generic.isCommandIgnored(commandWithParameters);
            boolean isLinkCommand = commandName.equalsIgnoreCase("discord")
                && event.getParameters().length > 0
                && event.getParameters()[0].equalsIgnoreCase("link");

            if (!commandIgnored && !isLinkCommand) {
                // Do not relay link commands
                broadcastCommandToDiscord(event);
            }
        }
    }

    private void broadcastMeOrSayToDiscord(CommandEvent event) {
        ICommandSender sender = event.getSender();
        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;
        MessageConfig messageConfig;
        boolean isSayCommand = event.getCommand().getName().equalsIgnoreCase("say");

        if (isSayCommand && !Configuration.getConfig().minecraft.dimensions.generic.relaySayCommand) {
            return;
        }

        if (!isSayCommand && !Configuration.getConfig().minecraft.dimensions.generic.relayMeCommand) {
            return;
        }

        if (Configuration.getConfig().minecraft.dimensions.generic.ignoreFakePlayerChat && sender instanceof FakePlayer) {
            return;
        }

        String message = Joiner.on(" ").join(event.getParameters());

        if (Configuration.getConfig().minecraft.dimensions.generic.isMessageIgnored(message)) {
            return;
        }

        HashMap<String, String> arguments = new HashMap<>();
        arguments.put("MESSAGE", isSayCommand ? message : "_" + message + "_");

        String prefix = minecraftConfig.dimensions.generic.chatPrefix;
        messageConfig = minecraftConfig.dimensions.generic.messages.chatMessage;

        ArrayList<Long> channels;

        if (sender != null) {
            Entity entity = sender.getCommandSenderEntity();

            if (entity != null) {
                MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entity.dimension);

                if (dimensionConfig.chatPrefix != null && dimensionConfig.chatPrefix.trim().length() > 0) {
                    prefix = dimensionConfig.chatPrefix;
                }

                if (dimensionConfig.messages.chatMessage != null) {
                    messageConfig = dimensionConfig.messages.chatMessage;
                }

                channels = dimensionConfig.relayChat.getChannels(
                    genericConfig.relayChat.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                );
            } else {
                channels = genericConfig.relayChat.getChannels(genericConfig.discordChannel);
            }
        } else {
            channels = genericConfig.relayChat.getChannels(genericConfig.discordChannel);
        }

        GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(sender).invoke();
        String authorName = getEventAuthorNameAndAvatar.getAuthorName();
        String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig)
                .setArguments(arguments)
                .setPrefix(prefix),
            channels
        );
    }

    private void broadcastCommandToDiscord(CommandEvent event) {
        ICommandSender sender = event.getSender();
        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;
        MessageConfig messageConfig;
        ArrayList<Long> channels;

        messageConfig = minecraftConfig.dimensions.generic.messages.command;

        if (sender != null) {
            Entity entity = sender.getCommandSenderEntity();

            if (entity != null) {
                MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entity.dimension);

                if (dimensionConfig.messages.command != null) {
                    messageConfig = dimensionConfig.messages.command;
                }

                channels = dimensionConfig.relayCommands.getChannels(
                    genericConfig.relayCommands.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                );
            } else {
                channels = genericConfig.relayCommands.getChannels(genericConfig.discordChannel);
            }
        } else {
            channels = genericConfig.relayCommands.getChannels(genericConfig.discordChannel);
        }

        HashMap<String, String> arguments = new HashMap<>();
        arguments.put("COMMAND", event.getCommand().getName());
        arguments.put("ARGUMENTS", Joiner.on(" ").join(event.getParameters()));

        GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(sender).invoke();
        String authorName = getEventAuthorNameAndAvatar.getAuthorName();
        String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig)
                .setArguments(arguments)
                .setParsing(false),
            channels
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChatMessage(ServerChatEvent event) {
        if (event.isCanceled() || event.getPlayer() == null) return;

        if (Configuration.getConfig().minecraft.dimensions.generic.ignoreFakePlayerChat && event.getPlayer() instanceof FakePlayer) {
            return;
        }

        if (Configuration.getConfig().minecraft.dimensions.generic.isMessageIgnored(event.getMessage())) {
            return;
        }

        HashMap<String, String> arguments = new HashMap<>();
        arguments.put("MESSAGE", event.getMessage());

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(event.getPlayer().dimension);
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig = dimensionConfig.messages.chatMessage != null ? dimensionConfig.messages.chatMessage : genericConfig.messages.chatMessage;

        GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(event).invoke();
        String authorName = getEventAuthorNameAndAvatar.getAuthorName();
        String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig)
                .setArguments(arguments)
                .setPrefix(dimensionConfig.chatPrefix != null && dimensionConfig.chatPrefix.trim().length() > 0 ? dimensionConfig.chatPrefix : genericConfig.chatPrefix),
            dimensionConfig.relayChat.getChannels(
                genericConfig.relayChat.getChannels(
                    dimensionConfig.discordChannel.getChannels(
                        genericConfig.discordChannel
                    )
                )
            )
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerAchievement(AdvancementEvent event) {
        EntityPlayer entityPlayer = event.getEntityPlayer();

        if (entityPlayer instanceof EntityPlayerMP) {
            Advancement advancement = event.getAdvancement();
            DisplayInfo displayInfo = advancement.getDisplay();

            if (displayInfo == null || !displayInfo.shouldAnnounceToChat()) {
                return;
            }

            HashMap<String, String> arguments = new HashMap<>();
            arguments.put("ACHIEVEMENT", displayInfo.getTitle().getUnformattedText());
            // TODO: Figure out what to do with I18n - Might remove the description...
            //noinspection deprecation
            arguments.put("DESCRIPTION", I18n.translateToLocalFormatted(displayInfo.getDescription().getUnformattedText(), "KEY"));

            MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
            MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entityPlayer.dimension);
            MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

            MessageConfig messageConfig = dimensionConfig.messages.achievement != null ? dimensionConfig.messages.achievement : genericConfig.messages.achievement;

            GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(entityPlayer).invoke();
            String authorName = getEventAuthorNameAndAvatar.getAuthorName();
            String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

            DiscordClient.getInstance().broadcast(
                new Message()
                    .setAuthor(authorName)
                    .setAvatarUrl(avatarUrl)
                    .setMessage(messageConfig)
                    .setArguments(arguments),
                dimensionConfig.relayAchievements.getChannels(
                    genericConfig.relayAchievements.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                )
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.isCanceled() || event.player == null) return;

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(event.player.dimension);
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig = dimensionConfig.messages.playerJoin != null ? dimensionConfig.messages.playerJoin : genericConfig.messages.playerJoin;

        GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(event.player).invoke();
        String authorName = getEventAuthorNameAndAvatar.getAuthorName();
        String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig),
            dimensionConfig.relayPlayerJoin.getChannels(
                genericConfig.relayPlayerJoin.getChannels(
                    dimensionConfig.discordChannel.getChannels(
                        genericConfig.discordChannel
                    )
                )
            )
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.isCanceled() || event.player == null) return;

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(event.player.dimension);
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig = dimensionConfig.messages.playerLeave != null ? dimensionConfig.messages.playerLeave : genericConfig.messages.playerLeave;

        GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(event.player).invoke();
        String authorName = getEventAuthorNameAndAvatar.getAuthorName();
        String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig),
            dimensionConfig.relayPlayerLeave.getChannels(
                genericConfig.relayPlayerLeave.getChannels(
                    dimensionConfig.discordChannel.getChannels(
                        genericConfig.discordChannel
                    )
                )
            )
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        EntityLivingBase entityLiving = event.getEntityLiving();

        if (event.isCanceled() || entityLiving == null) return;

        if (entityLiving instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) entityLiving;

            HashMap<String, String> arguments = new HashMap<>();
            arguments.put("REASON", entityPlayer.getCombatTracker().getDeathMessage().getUnformattedText().replace(entityPlayer.getDisplayNameString(), "").trim());

            MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
            MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entityLiving.dimension);
            MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

            MessageConfig messageConfig = dimensionConfig.messages.playerDeath != null ? dimensionConfig.messages.playerDeath : genericConfig.messages.playerDeath;

            GetEventAuthorNameAndAvatar getEventAuthorNameAndAvatar = new GetEventAuthorNameAndAvatar(entityPlayer).invoke();
            String authorName = getEventAuthorNameAndAvatar.getAuthorName();
            String avatarUrl = getEventAuthorNameAndAvatar.getAvatarUrl();

            DiscordClient.getInstance().broadcast(
                new Message()
                    .setAuthor(authorName)
                    .setAvatarUrl(avatarUrl)
                    .setMessage(messageConfig)
                    .setArguments(arguments),
                dimensionConfig.relayPlayerDeath.getChannels(
                    genericConfig.relayPlayerDeath.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                )
            );
        }
    }

    private class GetEventAuthorNameAndAvatar {
        private Long discordId;
        private String authorName;
        private String avatarUrl;

        GetEventAuthorNameAndAvatar(ServerChatEvent event) {
            if (event == null) {
                return;
            }
            this.discordId = Configuration.getLinking().getDiscordId(event.getPlayer().getGameProfile().getId());
            this.authorName = event.getUsername();
            this.avatarUrl = CoreUtils.getAvatarUrl(this.authorName);
        }

        GetEventAuthorNameAndAvatar(EntityPlayer entityPlayer) {
            if (entityPlayer == null) {
                return;
            }
            this.discordId = Configuration.getLinking().getDiscordId(entityPlayer.getGameProfile().getId());
            this.authorName = entityPlayer.getDisplayNameString();
            this.avatarUrl = CoreUtils.getAvatarUrl(this.authorName);
        }

        GetEventAuthorNameAndAvatar(ICommandSender sender) {
            if (sender == null) {
                return;
            }

            this.authorName = sender.getName();
            if (sender instanceof EntityPlayer) {
                this.discordId = Configuration.getLinking().getDiscordId(((EntityPlayer) sender).getGameProfile().getId());
                this.avatarUrl = CoreUtils.getAvatarUrl(sender.getName());
            }
        }

        String getAuthorName() {
            return this.authorName;
        }

        String getAvatarUrl() {
            return this.avatarUrl;
        }

        GetEventAuthorNameAndAvatar invoke() {
            if (this.discordId != null) {
                User discordUser = DiscordClient.getInstance().getUser(this.discordId);
                if (discordUser != null) {
                    this.authorName = discordUser.getName();
                    this.avatarUrl = discordUser.getAvatarUrl();
                }
            }
            return this;
        }
    }
}
