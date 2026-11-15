package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.common.inventory.CurioStacksHandler;

@Mixin(value = CurioStacksHandler.class, remap = false)
public abstract class CurioStacksHandlerMixin {
    @Shadow
    public abstract void update();

    @Inject(method = "update", at = @At("HEAD"), cancellable = true, remap = false)
    private void accessoriesToCurios$deferMidIterationResize(CallbackInfo ci) {
        if (BridgeCommonEvents.isInCuriosSlotIteration()) {
            ci.cancel();
        }
    }

    @Inject(method = "getSyncTag", at = @At("HEAD"), remap = false)
    private void accessoriesToCurios$flushDeferredResizeBeforeSync(CallbackInfoReturnable<CompoundTag> cir) {
        if (BridgeCommonEvents.isInCuriosSlotIteration()) {
            BridgeCommonEvents.runOutsideCuriosSlotIteration(this::update);
        }
    }
}
