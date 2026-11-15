package com.hp.accessoriestocurios;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import com.hp.accessoriestocurios.client.BridgeClientEvents;
import com.hp.accessoriestocurios.config.BridgeConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(AccessoriesToCurios.MOD_ID)
public final class AccessoriesToCurios {
    public static final String MOD_ID = "accessories_to_curios";

    public AccessoriesToCurios() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BridgeConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BridgeConfig.CLIENT_SPEC);
        BridgeCommonEvents.register(modEventBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BridgeClientEvents.register(modEventBus));
    }
}
