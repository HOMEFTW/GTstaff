package com.andgatech.gtstaff;

import com.andgatech.gtstaff.command.CommandPlayer;
import com.andgatech.gtstaff.config.Config;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        GTstaff.LOG.info(Tags.MODNAME + " at version " + Tags.VERSION);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void complete(FMLLoadCompleteEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandPlayer());
        GTstaff.LOG.info("Ok, " + Tags.MODNAME + " at version " + Tags.VERSION + " load success.");
    }

    public void serverStarted(FMLServerStartedEvent event) {}

}
