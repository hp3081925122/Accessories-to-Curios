package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.BridgeCommonEvents;
import com.hp.accessoriestocurios.bridge.ContainerViewManager;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesHolder;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories.pond.AccessoriesAPIAccess;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = LivingEntity.class, priority = 1500, remap = false)
public abstract class LivingEntityBridgeMixin implements AccessoriesAPIAccess {
    @Unique
    private AccessoriesCapabilityImpl accessoriesToCurios$capability;

    @Unique
    private AccessoriesCapabilityImpl accessoriesToCurios$getOrCreateCapability() {
        if (this.accessoriesToCurios$capability == null) {
            this.accessoriesToCurios$capability = new AccessoriesCapabilityImpl((LivingEntity) (Object) this);
        }
        return this.accessoriesToCurios$capability;
    }

    @Override
    @Nullable
    public AccessoriesCapability accessoriesCapability() {
        if (BridgeCommonEvents.isInLootingCalculation()) {
            return null;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (!ContainerViewManager.hasAnyEntitySlots(self)) {
            return null;
        }
        AccessoriesCapabilityImpl cap = this.accessoriesToCurios$getOrCreateCapability();
        cap.getHolder();
        return cap;
    }

    @Override
    @Nullable
    public AccessoriesHolder accessoriesHolder() {
        return this.accessoriesToCurios$getOrCreateCapability().getHolder();
    }
}
