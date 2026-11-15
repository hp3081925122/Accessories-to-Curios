package com.hp.accessoriestocurios.mixin;

import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.common.data.CuriosSlotManager;

@Mixin(value = CuriosSlotManager.class, remap = false)
public interface CuriosSlotManagerAccessor {
    @Accessor("slots")
    Map<String, ISlotType> accessoriesToCurios$getSlots();

    @Accessor("slots")
    void accessoriesToCurios$setSlots(Map<String, ISlotType> slots);
}
