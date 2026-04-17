package com.andgatech.gtstaff;

import java.io.File;

import com.andgatech.gtstaff.command.CommandGTstaff;
import com.andgatech.gtstaff.command.CommandPlayer;
import com.andgatech.gtstaff.config.Config;
import com.andgatech.gtstaff.fakeplayer.FakePlayerRegistry;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import net.minecraft.server.MinecraftServer;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        GTstaff.LOG.info(GTstaff.MOD_NAME + " at version " + GTstaff.VERSION);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void complete(FMLLoadCompleteEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandGTstaff());
        event.registerServerCommand(new CommandPlayer());
        GTstaff.LOG.info("Ok, " + GTstaff.MOD_NAME + " at version " + GTstaff.VERSION + " load success.");
    }

    public void serverStarted(FMLServerStartedEvent event) {
        FakePlayerRegistry.load(getRegistryFile());
        FakePlayerRegistry.restorePersisted(MinecraftServer.getServer());
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        FakePlayerRegistry.save(getRegistryFile());
    }

    private File getRegistryFile() {
        MinecraftServer server = MinecraftServer.getServer();
        return server == null ? new File("data", "gtstaff_registry.dat") : server.getFile("data/gtstaff_registry.dat");
    }

}
