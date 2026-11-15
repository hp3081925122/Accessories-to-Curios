package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.SlotBridgeMap;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.SlotTypeLoader;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.api.CuriosApi;

@Mixin(value = SlotTypeLoader.class, remap = false)
public abstract class SlotTypeLoaderMixin {
    @Inject(
            method = "getSlotType(Lnet/minecraft/world/level/Level;Ljava/lang/String;)Lio/wispforest/accessories/api/slot/SlotType;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void accessoriesToCurios$fallbackByLevel(
            Level level,
            String slotName,
            CallbackInfoReturnable<SlotType> cir
    ) {
        if (cir.getReturnValue() != null || level == null || slotName == null) {
            return;
        }
        String curiosSlot = SlotBridgeMap.toCuriosSlot(slotName);
        if (CuriosApi.getSlot(curiosSlot, level.isClientSide()).isPresent()) {
            cir.setReturnValue(SlotBridgeMap.resolveOrSynthesizeSlotType(slotName, curiosSlot, level));
        } else if (CuriosApi.getSlot(slotName, level.isClientSide()).isPresent()) {
            cir.setReturnValue(SlotBridgeMap.resolveOrSynthesizeSlotType(slotName, slotName, level));
        }
    }

    @Inject(
            method = "getSlotType(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/String;)Lio/wispforest/accessories/api/slot/SlotType;",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void accessoriesToCurios$fallbackByEntity(
            LivingEntity entity,
            String slotName,
            CallbackInfoReturnable<SlotType> cir
    ) {
        if (cir.getReturnValue() != null || entity == null || slotName == null) {
            return;
        }
        Level level = entity.level();
        String curiosSlot = SlotBridgeMap.toCuriosSlot(slotName);
        if (CuriosApi.getSlot(curiosSlot, level.isClientSide()).isPresent()) {
            cir.setReturnValue(SlotBridgeMap.resolveOrSynthesizeSlotType(slotName, curiosSlot, level));
        } else if (CuriosApi.getSlot(slotName, level.isClientSide()).isPresent()) {
            cir.setReturnValue(SlotBridgeMap.resolveOrSynthesizeSlotType(slotName, slotName, level));
        }
    }
}
