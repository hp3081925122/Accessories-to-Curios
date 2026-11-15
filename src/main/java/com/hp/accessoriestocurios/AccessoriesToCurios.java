package com.hp.accessoriestocurios;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import com.hp.accessoriestocurios.config.BridgeConfig;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(AccessoriesToCurios.MOD_ID)
public final class AccessoriesToCurios {
    public static final String MOD_ID = "accessories_to_curios";

    public AccessoriesToCurios(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, BridgeConfig.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, BridgeConfig.CLIENT_SPEC);
        BridgeCommonEvents.register(modEventBus);
    }
}
