package com.hp.accessoriestocurios.mixin.client;

import com.hp.accessoriestocurios.client.BridgeClientEvents;
import io.wispforest.accessories.neoforge.client.AccessoriesClientForge;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AccessoriesClientForge.class, remap = false)
public abstract class AccessoriesClientForgeMixin {
    @Inject(method = "initKeybindings", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$omitKeybindingRegistration(RegisterKeyMappingsEvent event, CallbackInfo ci) {
        if (BridgeClientEvents.shouldHideAccessoriesUi()) {
            AccessoriesClientForge.OPEN_SCREEN = BridgeClientEvents.createUnboundOpenKey();
            ci.cancel();
        }
    }
}
