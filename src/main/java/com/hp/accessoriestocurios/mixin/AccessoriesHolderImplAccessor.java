package com.hp.accessoriestocurios.mixin;

import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import io.wispforest.endec.util.MapCarrier;
import java.util.Map;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AccessoriesHolderImpl.class, remap = false)
public interface AccessoriesHolderImplAccessor {
    @Accessor("slotContainers")
    Map<String, AccessoriesContainer> accessoriesToCurios$getSlotContainers();

    @Accessor("carrier")
    MapCarrier accessoriesToCurios$getCarrier();

    @Accessor("carrier")
    void accessoriesToCurios$setCarrier(MapCarrier carrier);

    @Accessor("loadedFromTag")
    boolean accessoriesToCurios$isLoadedFromTag();

    @Accessor("loadedFromTag")
    void accessoriesToCurios$setLoadedFromTag(boolean loadedFromTag);
}
