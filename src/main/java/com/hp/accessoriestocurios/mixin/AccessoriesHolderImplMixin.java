package com.hp.accessoriestocurios.mixin;

import com.hp.accessoriestocurios.bridge.ContainerViewManager;
import com.hp.accessoriestocurios.bridge.LegacyDataMigrator;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.endec.NbtMapCarrier;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.util.MapCarrier;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AccessoriesHolderImpl.class, remap = false)
public abstract class AccessoriesHolderImplMixin {
    @Shadow
    protected boolean loadedFromTag;

    @Shadow
    private MapCarrier carrier;

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$initContainers(AccessoriesCapability capability, CallbackInfo ci) {
        if (capability != null && capability.entity() != null) {
            if (!capability.entity().level().isClientSide()) {
                LegacyDataMigrator.migrateEntityIfNeeded(capability.entity());
            } else {
                this.loadedFromTag = false;
            }
            ContainerViewManager.refreshHolderContainers((AccessoriesHolderImpl) (Object) this, capability);
        }
        ci.cancel();
    }

    @Inject(method = "write", at = @At("HEAD"), cancellable = true)
    private void accessoriesToCurios$preserveUnmigratedCarrier(
            MapCarrier outCarrier,
            SerializationContext ctx,
            CallbackInfo ci
    ) {
        if (this.loadedFromTag
                && this.carrier instanceof NbtMapCarrier unmigrated
                && outCarrier instanceof NbtMapCarrier target) {
            CompoundTag raw = unmigrated.compoundTag();
            if (raw != null && !raw.isEmpty()) {
                target.compoundTag().merge(raw.copy());
                ci.cancel();
            }
        }
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void accessoriesToCurios$stripEmptyContainerShells(
            MapCarrier outCarrier,
            SerializationContext ctx,
            CallbackInfo ci
    ) {
        if (!this.loadedFromTag && outCarrier instanceof NbtMapCarrier target) {
            CompoundTag tag = target.compoundTag();
            if (tag != null) {
                tag.remove("accessories_containers");
                tag.remove("AccessoriesContainers");
            }
        }
    }
}
