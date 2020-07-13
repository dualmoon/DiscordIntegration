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

package luna.discord.command;

import luna.discord.core.MinecraftFormattingCodes;
import luna.discord.core.config.Configuration;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
class SubCommandUnlink extends CommandBase {
    @Override
    public String getName() {
        return "unlink";
    }

    @Override
    public String getUsage(ICommandSender iCommandSender) {
        return "/discord unlink";
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] strings) throws CommandException {
        if (!Configuration.getConfig().discord.allowLinking) {
            sender.sendMessage(new TextComponentString(MinecraftFormattingCodes.DARK_RED + "Linking is disabled on this server."));
            return;
        }

        if (!(sender instanceof EntityPlayer)) {
            sender.sendMessage(new TextComponentString(MinecraftFormattingCodes.DARK_RED + "You need to be a player"));
            return;
        }

        UUID minecraftUUID = ((EntityPlayer) sender).getGameProfile().getId();

        if (Configuration.getLinking().getDiscordId(minecraftUUID) == null) {
            sender.sendMessage(new TextComponentString(MinecraftFormattingCodes.DARK_RED + "You aren't linked"));
            return;
        }

        Configuration.getLinking().removeLink(minecraftUUID);
        sender.sendMessage(new TextComponentString(MinecraftFormattingCodes.DARK_RED + "Unlinked"));
    }
}
