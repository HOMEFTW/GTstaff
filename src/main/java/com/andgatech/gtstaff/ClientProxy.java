package com.andgatech.gtstaff;

import java.util.function.BooleanSupplier;

import com.andgatech.gtstaff.ui.FakePlayerManagerUI;
import com.cleanroommc.modularui.factory.GuiManager;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        registerFactoryIfMissing(
            () -> GuiManager.hasFactory(FakePlayerManagerUI.INSTANCE.getFactoryName()),
            () -> GuiManager.registerFactory(FakePlayerManagerUI.INSTANCE));
    }

    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);
    }

    static void registerFactoryIfMissing(BooleanSupplier hasFactory, Runnable registrar) {
        if (!hasFactory.getAsBoolean()) {
            registrar.run();
        }
    }

}
