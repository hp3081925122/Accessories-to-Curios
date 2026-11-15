package com.hp.accessoriestocurios.mixin.client;

import com.hp.accessoriestocurios.client.BridgeClientEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.common.inventory.CurioSlot;

@Mixin(value = CurioSlot.class, remap = false)
public abstract class CurioSlotMixin {
    @Shadow
    @Final
    private String identifier;

    @Shadow
    private boolean isCosmetic;

    @Inject(method = "getSlotName", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$fallbackAccessoriesSlotName(CallbackInfoReturnable<String> cir) {
        String fallback = BridgeClientEvents.resolveFallbackSlotName(this.identifier, this.isCosmetic);
        if (fallback != null) {
            cir.setReturnValue(fallback);
        }
    }
}
