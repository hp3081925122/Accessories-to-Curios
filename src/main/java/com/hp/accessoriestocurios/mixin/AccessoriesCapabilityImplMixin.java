package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.ContainerViewManager;
import com.hp.accessoriestocurios.bridge.LegacyDataMigrator;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.EntitySlotLoader;
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
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;

@Mixin(value = AccessoriesCapabilityImpl.class, remap = false)
public abstract class AccessoriesCapabilityImplMixin {
    @Shadow
    @Final
    private LivingEntity entity;

    @Unique
    private int accessoriesToCurios$syncedGeneration = -1;

    @Unique
    private Map<String, SlotType> accessoriesToCurios$syncedAccSlots;

    @Unique
    private Map<String, ISlotType> accessoriesToCurios$syncedCuriosSlots;

    @Unique
    private int accessoriesToCurios$syncedCuriosCount = -1;

    @Unique
    private AccessoriesCapability accessoriesToCurios$boundCapability;

    @Inject(method = "getContainers", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$getContainers(CallbackInfoReturnable<Map<String, AccessoriesContainer>> cir) {
        AccessoriesCapability self = (AccessoriesCapability) this;
        AccessoriesHolderImpl holder = (AccessoriesHolderImpl) self.getHolder();
        int generation = ContainerViewManager.currentStructureGeneration();
        Map<String, SlotType> accSlots = EntitySlotLoader.getEntitySlots(this.entity);
        Map<String, ISlotType> curiosSlots = CuriosApi.getEntitySlots(this.entity);
        int curiosCount = ContainerViewManager.getCuriosHandlerCount(this.entity);
        boolean expectsContainers = !accSlots.isEmpty() || !curiosSlots.isEmpty() || curiosCount > 0;
        if (this.accessoriesToCurios$syncedGeneration != generation
                || this.accessoriesToCurios$syncedAccSlots != accSlots
                || this.accessoriesToCurios$syncedCuriosSlots != curiosSlots
                || this.accessoriesToCurios$syncedCuriosCount != curiosCount
                || (expectsContainers && ((AccessoriesHolderImplAccessor) holder).accessoriesToCurios$getSlotContainers().isEmpty())) {
            ContainerViewManager.refreshHolderContainers(holder, self);
            this.accessoriesToCurios$syncedGeneration = generation;
            this.accessoriesToCurios$syncedAccSlots = accSlots;
            this.accessoriesToCurios$syncedCuriosSlots = curiosSlots;
            this.accessoriesToCurios$syncedCuriosCount = curiosCount;
            this.accessoriesToCurios$boundCapability = self;
        } else if (this.accessoriesToCurios$boundCapability != self) {
            ContainerViewManager.rebindContainers(holder, self);
            this.accessoriesToCurios$boundCapability = self;
        }
        cir.setReturnValue(holder.getSlotContainers());
    }

    @Inject(method = "reset", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$reset(boolean loadedFromTag, CallbackInfo ci) {
        if (!this.entity.level().isClientSide()) {
            LegacyDataMigrator.migrateEntityIfNeeded(this.entity);
        }
        if (AccessoriesInternals.getHolder(this.entity) instanceof AccessoriesHolderImpl holder) {
            AccessoriesCapability self = (AccessoriesCapability) this;
            ContainerViewManager.refreshHolderContainers(holder, self);
            this.accessoriesToCurios$syncedGeneration = ContainerViewManager.currentStructureGeneration();
            this.accessoriesToCurios$syncedAccSlots = EntitySlotLoader.getEntitySlots(this.entity);
            this.accessoriesToCurios$syncedCuriosSlots = CuriosApi.getEntitySlots(this.entity);
            this.accessoriesToCurios$syncedCuriosCount = ContainerViewManager.getCuriosHandlerCount(this.entity);
            this.accessoriesToCurios$boundCapability = self;
        }
        ci.cancel();
    }
}
