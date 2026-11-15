package com.hp.accessoriestocurios.bridge;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.impl.AccessoriesContainerImpl;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import io.wispforest.endec.SerializationContext;
import io.wispforest.endec.util.MapCarrier;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class CuriosBackedAccessoriesContainer extends AccessoriesContainerImpl {
    private final String accessoriesSlotName;
    private final String curiosSlotId;
    private final SlotType fallbackSlotType;
    private final CuriosBackedExpandedContainer liveAccessories;
    private final CuriosBackedExpandedContainer liveCosmetics;

    public CuriosBackedAccessoriesContainer(
            AccessoriesCapability capability,
            SlotType slotType,
            String curiosSlotId
    ) {
        super(capability, slotType);
        this.accessoriesSlotName = slotType.name();
        this.curiosSlotId = curiosSlotId;
        this.fallbackSlotType = slotType;
        this.liveAccessories = new CuriosBackedExpandedContainer(
                this,
                "accessories",
                () -> resolveStacksHandler().map(ICurioStacksHandler::getStacks).orElse(null)
        );
        this.liveCosmetics = new CuriosBackedExpandedContainer(
                this,
                "cosmetic_accessories",
                () -> resolveStacksHandler().map(ICurioStacksHandler::getCosmeticStacks).orElse(null)
        );
    }

    public String getCuriosSlotId() {
        return this.curiosSlotId;
    }

    public void rebindCapability(AccessoriesCapability capability) {
        this.capability = capability;
    }

    public Optional<ICurioStacksHandler> resolveStacksHandler() {
        LivingEntity entity = this.capability != null ? this.capability.entity() : null;
        if (entity == null) {
            return Optional.empty();
        }
        return CuriosApi.getCuriosInventory(entity)
                .flatMap(inv -> inv.getStacksHandler(this.curiosSlotId));
    }

    @Override
    public @Nullable SlotType slotType() {
        SlotType superType = super.slotType();
        if (superType != null) {
            return superType;
        }
        LivingEntity entity = this.capability != null ? this.capability.entity() : null;
        return entity != null
                ? SlotBridgeMap.resolveOrSynthesizeSlotType(this.accessoriesSlotName, this.curiosSlotId, entity.level())
                : this.fallbackSlotType;
    }

    @Override
    public String getSlotName() {
        return this.accessoriesSlotName;
    }

    @Override
    public @Nullable Integer getBaseSize() {
        LivingEntity entity = this.capability != null ? this.capability.entity() : null;
        boolean isClient = entity == null || entity.level().isClientSide();
        return CuriosApi.getSlot(this.curiosSlotId, isClient)
                .map(ISlotType::getSize)
                .orElseGet(this.fallbackSlotType::amount);
    }

    @Override
    public int getSize() {
        return resolveStacksHandler().map(ICurioStacksHandler::getSlots).orElse(0);
    }

    @Override
    public ExpandedSimpleContainer getAccessories() {
        return this.liveAccessories;
    }

    @Override
    public ExpandedSimpleContainer getCosmeticAccessories() {
        return this.liveCosmetics;
    }

    @Override
    public List<Boolean> renderOptions() {
        Optional<ICurioStacksHandler> handlerOpt = resolveStacksHandler();
        if (handlerOpt.isEmpty()) {
            return Collections.emptyList();
        }
        NonNullList<Boolean> renders = handlerOpt.get().getRenders();
        return renders;
    }

    @Override
    public void containerChanged(Container container) {
    }

    @Override
    public void markChanged(boolean resizingUpdate) {
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public void update() {
        resolveStacksHandler().ifPresent(ICurioStacksHandler::update);
    }

    @Override
    public Map<ResourceLocation, AttributeModifier> getModifiers() {
        return resolveStacksHandler().map(ICurioStacksHandler::getModifiers).orElse(Collections.emptyMap());
    }

    @Override
    public Set<AttributeModifier> getCachedModifiers() {
        return resolveStacksHandler().map(ICurioStacksHandler::getCachedModifiers).orElse(Collections.emptySet());
    }

    @Override
    public Collection<AttributeModifier> getModifiersForOperation(AttributeModifier.Operation operation) {
        return resolveStacksHandler()
                .map(h -> h.getModifiersByOperation(operation))
                .orElse(Collections.emptyList());
    }

    @Override
    public void addTransientModifier(AttributeModifier modifier) {
        resolveStacksHandler().ifPresent(h -> h.addTransientModifier(modifier));
    }

    @Override
    public void addPersistentModifier(AttributeModifier modifier) {
        resolveStacksHandler().ifPresent(h -> h.addPermanentModifier(modifier));
    }

    @Override
    public boolean hasModifier(ResourceLocation location) {
        return resolveStacksHandler().map(h -> h.getModifiers().containsKey(location)).orElse(false);
    }

    @Override
    public void removeModifier(ResourceLocation location) {
        resolveStacksHandler().ifPresent(h -> h.removeModifier(location));
    }

    @Override
    public void clearModifiers() {
        resolveStacksHandler().ifPresent(ICurioStacksHandler::clearModifiers);
    }

    @Override
    public void removeCachedModifiers(AttributeModifier modifier) {
        resolveStacksHandler().ifPresent(h -> h.getCachedModifiers().remove(modifier));
    }

    @Override
    public void clearCachedModifiers() {
        resolveStacksHandler().ifPresent(ICurioStacksHandler::clearCachedModifiers);
    }

    @Override
    public void write(MapCarrier carrier, SerializationContext ctx, boolean sync) {
        if (sync) {
            carrier.put(SLOT_NAME_KEY, this.accessoriesSlotName);
            super.write(carrier, ctx, true);
        }
    }

    @Override
    public void read(MapCarrier carrier, SerializationContext ctx, boolean sync) {
        if (sync) {
            super.read(carrier, ctx, true);
        }
    }
}
