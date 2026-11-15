package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.ContainerViewManager;
import com.hp.accessoriestocurios.bridge.LegacyDataMigrator;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.impl.AccessoriesCapabilityImpl;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import java.util.Map;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AccessoriesCapabilityImpl.class, remap = false)
public abstract class AccessoriesCapabilityImplMixin {
    @Shadow
    @Final
    private LivingEntity entity;

    @Unique
    private int accessoriesToCurios$syncedGeneration = -1;

    @Inject(method = "getContainers", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$getContainers(CallbackInfoReturnable<Map<String, AccessoriesContainer>> cir) {
        AccessoriesCapability self = (AccessoriesCapability) this;
        AccessoriesHolderImpl holder = (AccessoriesHolderImpl) self.getHolder();
        int generation = ContainerViewManager.currentStructureGeneration();
        if (this.accessoriesToCurios$syncedGeneration != generation
                || ((AccessoriesHolderImplAccessor) holder).accessoriesToCurios$getSlotContainers().isEmpty()) {
            ContainerViewManager.refreshHolderContainers(holder, self);
            this.accessoriesToCurios$syncedGeneration = generation;
        } else {
            ContainerViewManager.rebindContainers(holder, self);
        }
        cir.setReturnValue(holder.getSlotContainers());
    }

    @Inject(method = "reset", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$reset(boolean loadedFromTag, CallbackInfo ci) {
        if (!this.entity.level().isClientSide()) {
            LegacyDataMigrator.migrateEntityIfNeeded(this.entity);
        }
        if (AccessoriesInternals.getHolder(this.entity) instanceof AccessoriesHolderImpl holder) {
            ContainerViewManager.refreshHolderContainers(holder, (AccessoriesCapability) this);
            this.accessoriesToCurios$syncedGeneration = ContainerViewManager.currentStructureGeneration();
        }
        ci.cancel();
    }
}
