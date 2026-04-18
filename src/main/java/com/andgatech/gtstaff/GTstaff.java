package com.andgatech.gtstaff;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

@Mod(
    modid = GTstaff.MODID,
    version = GTstaff.VERSION,
    name = GTstaff.MOD_NAME,
    dependencies = "required-after:gregtech;required-after:gtnhlib;required-after:modularui;",
    acceptedMinecraftVersions = "[1.7.10]")
public class GTstaff {

    public static final String MODID = "gtstaff";
    public static final String MOD_ID = MODID;
    public static final String MOD_NAME = "GTstaff";
    public static final String VERSION = Tags.VERSION;
    public static final String RESOURCE_ROOT_ID = "gtstaff";

    public static final Logger LOG = LogManager.getLogger(MODID);

    @Mod.Instance
    public static GTstaff instance;

    @SidedProxy(clientSide = "com.andgatech.gtstaff.ClientProxy", serverSide = "com.andgatech.gtstaff.CommonProxy")
    public static CommonProxy proxy;

    // region FML Events
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void completeInit(FMLLoadCompleteEvent event) {
        proxy.complete(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        proxy.serverStarted(event);
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        proxy.serverStopping(event);
    }
    // endregion

}
