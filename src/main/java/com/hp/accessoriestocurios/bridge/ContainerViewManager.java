package com.hp.accessoriestocurios.bridge;

import com.hp.accessoriestocurios.mixin.AccessoriesHolderImplAccessor;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.entity.LivingEntity;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

public final class ContainerViewManager {
    private static final AtomicInteger STRUCTURE_GENERATION = new AtomicInteger(1);

    private ContainerViewManager() {
    }

    public static void bumpStructureGeneration() {
        STRUCTURE_GENERATION.incrementAndGet();
    }

    public static int currentStructureGeneration() {
        return STRUCTURE_GENERATION.get();
    }

    public static int getCuriosHandlerCount(LivingEntity entity) {
        ICuriosItemHandler inv = entity.getCapability(CuriosCapability.INVENTORY);
        return inv != null ? inv.getCurios().size() : 0;
    }

    public static boolean hasAnyEntitySlots(LivingEntity entity) {
        if (!EntitySlotLoader.getEntitySlots(entity).isEmpty()) {
            return true;
        }
        if (!CuriosApi.getEntitySlots(entity).isEmpty()) {
            return true;
        }
        ICuriosItemHandler inv = entity.getCapability(CuriosCapability.INVENTORY);
        return inv != null && !inv.getCurios().isEmpty();
    }

    public static void rebindContainers(AccessoriesHolderImpl holder, AccessoriesCapability capability) {
        AccessoriesHolderImplAccessor accessor = (AccessoriesHolderImplAccessor) holder;
        for (AccessoriesContainer container : accessor.accessoriesToCurios$getSlotContainers().values()) {
            if (container instanceof CuriosBackedAccessoriesContainer backed) {
                backed.rebindCapability(capability);
            }
        }
    }

    public static void refreshHolderContainers(AccessoriesHolderImpl holder, AccessoriesCapability capability) {
        LivingEntity entity = capability.entity();
        if (entity == null) {
            return;
        }
        AccessoriesHolderImplAccessor accessor = (AccessoriesHolderImplAccessor) holder;
        Map<String, AccessoriesContainer> backingMap = accessor.accessoriesToCurios$getSlotContainers();

        Map<String, AccessoriesContainer> desired = new LinkedHashMap<>();
        Set<String> coveredCuriosIds = new HashSet<>();

        Map<String, SlotType> accEntitySlots = EntitySlotLoader.getEntitySlots(entity);
        for (Map.Entry<String, SlotType> entry : accEntitySlots.entrySet()) {
            String accSlotName = entry.getKey();
            SlotType slotType = entry.getValue();
            String curiosId = SlotBridgeMap.toCuriosSlot(accSlotName);
            if (!coveredCuriosIds.add(curiosId)) {
                curiosId = SlotBridgeMap.sanitizeCuriosId("unmapped__" + accSlotName);
            }
            desired.put(accSlotName, reuseOrCreate(backingMap, capability, slotType, curiosId));
        }

        Map<String, ISlotType> curiosEntitySlots = CuriosApi.getEntitySlots(entity);
        for (String curiosId : curiosEntitySlots.keySet()) {
            if (coveredCuriosIds.add(curiosId)) {
                String containerName = pickUniqueContainerName(desired, entity, curiosId);
                SlotType synthetic = SlotBridgeMap.resolveOrSynthesizeSlotType(containerName, curiosId, entity.level());
                desired.put(containerName, reuseOrCreate(backingMap, capability, synthetic, curiosId));
            }
        }

        CuriosApi.getCuriosInventory(entity).ifPresent(inv -> {
            for (Map.Entry<String, ICurioStacksHandler> handlerEntry : inv.getCurios().entrySet()) {
                String curiosId = handlerEntry.getKey();
                if (coveredCuriosIds.add(curiosId)) {
                    String containerName = pickUniqueContainerName(desired, entity, curiosId);
                    SlotType synthetic = SlotBridgeMap.resolveOrSynthesizeSlotType(containerName, curiosId, entity.level());
                    desired.put(containerName, reuseOrCreate(backingMap, capability, synthetic, curiosId));
                }
            }
        });

        boolean structureChanged = !backingMap.keySet().equals(desired.keySet());
        if (!structureChanged) {
            for (Map.Entry<String, AccessoriesContainer> entry : desired.entrySet()) {
                if (backingMap.get(entry.getKey()) != entry.getValue()) {
                    structureChanged = true;
                    break;
                }
            }
        }

        if (structureChanged) {
            backingMap.clear();
            backingMap.putAll(desired);
        }
        accessor.accessoriesToCurios$setValidSlotTypes(null);
    }

    private static String pickUniqueContainerName(
            Map<String, AccessoriesContainer> existing,
            LivingEntity entity,
            String curiosId
    ) {
        String candidate = SlotBridgeMap.resolveContainerSlotName(entity, curiosId);
        if (!existing.containsKey(candidate)) {
            return candidate;
        }
        if (!existing.containsKey(curiosId)) {
            return curiosId;
        }
        return "curios_" + curiosId;
    }

    private static CuriosBackedAccessoriesContainer reuseOrCreate(
            Map<String, AccessoriesContainer> existingMap,
            AccessoriesCapability capability,
            SlotType slotType,
            String curiosId
    ) {
        AccessoriesContainer prev = existingMap.get(slotType.name());
        if (prev instanceof CuriosBackedAccessoriesContainer backed
                && backed.getCuriosSlotId().equals(curiosId)) {
            backed.rebindCapability(capability);
            return backed;
        }
        return new CuriosBackedAccessoriesContainer(capability, slotType, curiosId);
    }
}
