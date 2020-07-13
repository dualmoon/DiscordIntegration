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

import luna.discord.core.config.Configuration;
import mcp.MethodsReturnNonnullByDefault;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.server.command.CommandTreeBase;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CommandDiscord extends CommandTreeBase {
    public CommandDiscord() {
        this.addSubcommand(new SubCommandConfig());
        this.addSubcommand(new SubCommandOnline());
        this.addSubcommand(new SubCommandTps());
        this.addSubcommand(new SubCommandUnstuck());
        this.addSubcommand(new SubCommandUptime());
        this.addSubcommand(new SubCommandLink());
        this.addSubcommand(new SubCommandUnlink());
    }

    @Override
    public String getName() {
        return "discord";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/discord <config|online" + (Configuration.getConfig().discord.allowLinking ? "|link|unlink" : "") + "|tps|unstuck|uptime> [options]";
    }
}
