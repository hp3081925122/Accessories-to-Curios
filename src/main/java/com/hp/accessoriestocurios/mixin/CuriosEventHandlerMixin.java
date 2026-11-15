package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.theillusivec4.curios.common.event.CuriosEventHandler;

@Mixin(value = CuriosEventHandler.class, remap = false)
public abstract class CuriosEventHandlerMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void accessoriesToCurios$beginCuriosTick(EntityTickEvent.Post evt, CallbackInfo ci) {
        BridgeCommonEvents.enterCuriosSlotIteration(evt.getEntity());
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private void accessoriesToCurios$endCuriosTick(EntityTickEvent.Post evt, CallbackInfo ci) {
        BridgeCommonEvents.exitCuriosSlotIteration(evt.getEntity());
    }
}
