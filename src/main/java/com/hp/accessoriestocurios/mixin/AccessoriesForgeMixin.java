package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import io.wispforest.accessories.neoforge.AccessoriesForge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AccessoriesForge.class, remap = false)
public abstract class AccessoriesForgeMixin {
    @Inject(method = "registerCapabilities", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$registerEntityCapabilities(RegisterCapabilitiesEvent event, CallbackInfo ci) {
        BridgeCommonEvents.registerBridgedEntityCapabilities(event);
        ci.cancel();
    }
}
