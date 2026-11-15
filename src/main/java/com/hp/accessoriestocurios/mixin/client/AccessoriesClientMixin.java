package com.hp.accessoriestocurios.mixin.client;

import com.hp.accessoriestocurios.client.BridgeClientEvents;
import io.wispforest.accessories.client.AccessoriesClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AccessoriesClient.class, remap = false)
public abstract class AccessoriesClientMixin {
    @Inject(method = "initLayer", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$hideInventoryButtons(CallbackInfo ci) {
        if (BridgeClientEvents.shouldHideAccessoriesUi()) {
            ci.cancel();
        }
    }

    @Inject(method = "attemptToOpenScreen(Z)Z", at = @At("HEAD"), cancellable = true)
    private static void accessoriesToCurios$redirectOpenScreen(
            boolean targetingLookingEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (BridgeClientEvents.shouldRedirectOpenToCurios()) {
            if (!targetingLookingEntity) {
                cir.setReturnValue(BridgeClientEvents.openCuriosScreen());
            } else if (BridgeClientEvents.shouldHideAccessoriesUi()) {
                cir.setReturnValue(false);
            }
        }
    }
}
