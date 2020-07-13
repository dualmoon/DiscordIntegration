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

package luna.discord;

import luna.discord.command.CommandDiscord;
import luna.discord.core.CoreConstants;
import luna.discord.core.CoreUtils;
import luna.discord.core.DiscordClient;
import luna.discord.core.Proxy;
import luna.discord.listener.DiscordListener;
import luna.discord.listener.MinecraftListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;

@Mod(modid = CoreConstants.MODID, name = CoreConstants.MODNAME, version = CoreConstants.VERSION, serverSideOnly = true, acceptableRemoteVersions = "*")
public class DiscordIntegration {
    @Mod.Instance
    static DiscordIntegration instance;

    private static Proxy proxy = new Proxy();
    private static Thread updateThread;

    @Mod.EventHandler
    public void onPreInit(FMLPreInitializationEvent event) {
        proxy.onPreInit(event.getModConfigurationDirectory());

        CoreUtils.addPatterns();

        MinecraftForge.EVENT_BUS.register(new MinecraftListener());
    }

    @Mod.EventHandler
    public void onPostInit(FMLPostInitializationEvent event) {
        event.buildSoftDependProxy("dynmap", "luna.discord.integration.DynmapIntegration");
    }

    @Mod.EventHandler
    public void onServerAboutToStart(FMLServerAboutToStartEvent event) {
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        proxy.onServerStarting();

        DiscordClient.getInstance().addEventListener(new DiscordListener());

        event.registerServerCommand(new CommandDiscord());

        updateThread = new Thread(new DiscordThread());
        updateThread.start();
    }

    @Mod.EventHandler
    public void onServerStarted(FMLServerStartedEvent event) {
        proxy.onServerStarted();
    }

    @Mod.EventHandler
    public void onServerStopping(FMLServerStoppingEvent event) {
        updateThread.interrupt();
        proxy.onServerStopping();
    }

    @Mod.EventHandler
    public void onServerStopped(FMLServerStoppedEvent event) {
        proxy.onServerStopped();
    }

    @Mod.EventHandler
    public void imcReceived(FMLInterModComms.IMCEvent event) {
        for (FMLInterModComms.IMCMessage imcMessage : event.getMessages()) {
            IMCHandler.onMessageReceived(imcMessage);
        }
    }
}
