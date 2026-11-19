package com.hp.accessoriestocurios.bridge;

import com.google.common.collect.ImmutableMap;
import com.hp.accessoriestocurios.config.BridgeConfig;
import com.hp.accessoriestocurios.mixin.CuriosEntityManagerAccessor;
import com.hp.accessoriestocurios.mixin.CuriosSlotManagerAccessor;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.components.AccessoryItemAttributeModifiers;
import io.wispforest.accessories.api.components.AccessorySlotValidationComponent;
import io.wispforest.accessories.api.data.AccessoriesBaseData;
import io.wispforest.accessories.api.data.AccessoriesTags;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.data.EntitySlotLoader;
import io.wispforest.accessories.data.SlotTypeLoader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.common.data.CuriosEntityManager;
import top.theillusivec4.curios.common.data.CuriosSlotManager;

public final class SlotSynchronizer {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int UNIVERSAL_CURIO_MIN_ORDER = 10000;

    private static final ResourceLocation CURIOS_TAG_VALIDATOR =
            ResourceLocation.fromNamespaceAndPath(CuriosApi.MODID, "tag");
    private static final ResourceLocation DEFAULT_CURIO_ICON =
            ResourceLocation.fromNamespaceAndPath(CuriosApi.MODID, "slot/empty_curio_slot");
    private static volatile boolean pendingLevelRescan = false;

    private SlotSynchronizer() {
    }

    public static boolean consumePendingLevelRescan() {
        boolean pending = pendingLevelRescan;
        pendingLevelRescan = false;
        return pending;
    }

    public static void syncServerSlotsAndEntities(@Nullable Level fallbackLevel) {
        if (BridgeConfig.COMMON_SPEC.isLoaded() && !BridgeConfig.COMMON.autoRegisterUsedSlots.get()) {
            pendingLevelRescan = false;
            return;
        }

        Map<String, SlotType> accSlots = SlotTypeLoader.INSTANCE.getSlotTypes(false);
        Map<EntityType<?>, Map<String, SlotType>> accEntitySlots = EntitySlotLoader.INSTANCE.getEntitySlotData(false);

        Set<String> usedAccSlots = new LinkedHashSet<>();
        boolean hasCustomSlotValidators = false;
        for (Map.Entry<String, SlotType> entry : accSlots.entrySet()) {
            String name = entry.getKey();
            if (BridgeSlotValidator.isInternalArmorProxySlot(name)) {
                continue;
            }
            SlotType slotType = entry.getValue();
            if (slotType.amount() <= 0) {
                continue;
            }
            for (ResourceLocation validator : slotType.validators()) {
                if (!AccessoriesBaseData.TAG_PREDICATE_ID.equals(validator)
                        && !AccessoriesBaseData.COMPONENT_PREDICATE_ID.equals(validator)) {
                    hasCustomSlotValidators = true;
                    break;
                }
            }
            Integer defaultAmount = BridgeSlotValidator.DEFAULT_ACCESSORIES_SLOT_AMOUNTS.get(name);
            if (UniqueSlotHandling.isUniqueSlot(name)
                    || defaultAmount == null
                    || slotType.amount() > defaultAmount) {
                usedAccSlots.add(name);
            }
        }

        boolean[] needsUniversalCurioSlot = new boolean[]{false};
        Level scanLevel = fallbackLevel != null ? fallbackLevel : BridgeRuntimeAccess.resolveLevel(null);
        pendingLevelRescan = scanLevel == null && hasCustomSlotValidators;

        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(AccessoriesTags.ANY_TAG) || stack.is(BridgeSlotValidator.LEGACY_ALL_TAG)) {
                needsUniversalCurioSlot[0] = true;
            }
            AccessorySlotValidationComponent validation = stack.getOrDefault(
                    AccessoriesDataComponents.SLOT_VALIDATION,
                    AccessorySlotValidationComponent.EMPTY
            );
            if (validation.validSlotOverrides().contains("any")) {
                needsUniversalCurioSlot[0] = true;
            }
            for (String validOverride : validation.validSlotOverrides()) {
                if (!"any".equals(validOverride)
                        && !BridgeSlotValidator.isInternalArmorProxySlot(validOverride)
                        && accSlots.containsKey(validOverride)) {
                    usedAccSlots.add(validOverride);
                }
            }
            AccessoryItemAttributeModifiers attrMods = stack.getOrDefault(
                    AccessoriesDataComponents.ATTRIBUTES,
                    AccessoryItemAttributeModifiers.EMPTY
            );
            for (AccessoryItemAttributeModifiers.Entry modEntry : attrMods.modifiers()) {
                String targetSlot = modEntry.slotName();
                if ("any".equals(targetSlot)) {
                    needsUniversalCurioSlot[0] = true;
                } else if (!BridgeSlotValidator.isInternalArmorProxySlot(targetSlot) && accSlots.containsKey(targetSlot)) {
                    usedAccSlots.add(targetSlot);
                }
            }

            boolean matchedSpecific = false;
            for (Map.Entry<String, SlotType> slotEntry : accSlots.entrySet()) {
                String slotName = slotEntry.getKey();
                if (BridgeSlotValidator.isInternalArmorProxySlot(slotName)) {
                    continue;
                }
                SlotType slotType = slotEntry.getValue();
                if (stack.is(AccessoriesAPI.getSlotTag(slotType))) {
                    usedAccSlots.add(slotName);
                    matchedSpecific = true;
                } else if (scanLevel != null
                        && BridgeSlotValidator.matchesSpecificSlotWithoutWildcard(stack, scanLevel, null, slotType)) {
                    usedAccSlots.add(slotName);
                    matchedSpecific = true;
                }
            }

            Accessory explicit = AccessoriesAPI.getAccessory(item);
            if (BridgeSlotValidator.isRealCustomAccessory(explicit) && !matchedSpecific) {
                needsUniversalCurioSlot[0] = true;
            }
        }

        CuriosSlotManagerAccessor slotAccessor = (CuriosSlotManagerAccessor) CuriosSlotManager.SERVER;
        Map<String, ISlotType> updatedCuriosSlots = new LinkedHashMap<>();
        Map<String, ResourceLocation> updatedIcons = new LinkedHashMap<>(CuriosSlotManager.SERVER.getIcons());

        for (Map.Entry<String, ISlotType> entry : slotAccessor.accessoriesToCurios$getSlots().entrySet()) {
            ISlotType withBridgeValidator = ensureValidatorAndOrder(entry.getValue(), BridgeSlotValidator.PREDICATE_ID);
            updatedCuriosSlots.put(entry.getKey(), withBridgeValidator);
            updatedIcons.putIfAbsent(entry.getKey(), withBridgeValidator.getIcon());
        }

        for (String accSlotName : usedAccSlots) {
            if (BridgeSlotValidator.isInternalArmorProxySlot(accSlotName)) {
                continue;
            }
            SlotType accSlot = accSlots.get(accSlotName);
            if (accSlot == null || accSlot.amount() <= 0) {
                continue;
            }
            String curiosId = SlotBridgeMap.toCuriosSlot(accSlotName);
            ISlotType existing = updatedCuriosSlots.get(curiosId);
            ISlotType merged = buildOrMergeCuriosSlot(curiosId, accSlot, existing);
            updatedCuriosSlots.put(curiosId, merged);
            updatedIcons.put(curiosId, merged.getIcon());
        }

        if (needsUniversalCurioSlot[0]) {
            ISlotType existingCurio = updatedCuriosSlots.get("curio");
            top.theillusivec4.curios.common.slottype.SlotType.Builder builder =
                    new top.theillusivec4.curios.common.slottype.SlotType.Builder("curio");
            if (existingCurio != null) {
                copyExistingToBuilder(builder, existingCurio);
                builder.size(Math.max(1, existingCurio.getSize()), true);
                builder.order(Math.max(existingCurio.getOrder(), UNIVERSAL_CURIO_MIN_ORDER), true);
            } else {
                builder.size(1, true)
                        .order(UNIVERSAL_CURIO_MIN_ORDER, true)
                        .icon(DEFAULT_CURIO_ICON)
                        .useNativeGui(true, true)
                        .renderToggle(true, true)
                        .validator(CURIOS_TAG_VALIDATOR);
            }
            builder.hasCosmetic(true, true);
            builder.validator(BridgeSlotValidator.PREDICATE_ID);
            top.theillusivec4.curios.common.slottype.SlotType builtCurio = builder.build();
            updatedCuriosSlots.put("curio", builtCurio);
            updatedIcons.put("curio", builtCurio.getIcon());
        }

        slotAccessor.accessoriesToCurios$setSlots(ImmutableMap.copyOf(updatedCuriosSlots));
        CuriosSlotManager.SERVER.setIcons(updatedIcons);

        CuriosEntityManagerAccessor entityAccessor = (CuriosEntityManagerAccessor) CuriosEntityManager.SERVER;
        Map<EntityType<?>, Map<String, ISlotType>> mutableEntityMap = new LinkedHashMap<>();

        for (Map.Entry<EntityType<?>, Map<String, ISlotType>> entry : entityAccessor.accessoriesToCurios$getEntitySlots().entrySet()) {
            Map<String, ISlotType> refreshedInner = new LinkedHashMap<>();
            for (Map.Entry<String, ISlotType> slotEntry : entry.getValue().entrySet()) {
                ISlotType latest = updatedCuriosSlots.getOrDefault(slotEntry.getKey(), slotEntry.getValue());
                refreshedInner.put(slotEntry.getKey(), latest);
            }
            mutableEntityMap.put(entry.getKey(), refreshedInner);
        }

        if (!accEntitySlots.isEmpty()) {
            for (Map.Entry<EntityType<?>, Map<String, SlotType>> entry : accEntitySlots.entrySet()) {
                EntityType<?> entityType = entry.getKey();
                Map<String, SlotType> boundAccSlots = entry.getValue();
                for (String accSlotName : boundAccSlots.keySet()) {
                    if (!usedAccSlots.contains(accSlotName) || BridgeSlotValidator.isInternalArmorProxySlot(accSlotName)) {
                        continue;
                    }
                    String curiosId = SlotBridgeMap.toCuriosSlot(accSlotName);
                    ISlotType curiosSlotType = updatedCuriosSlots.get(curiosId);
                    if (curiosSlotType != null && curiosSlotType.getSize() > 0) {
                        mutableEntityMap
                                .computeIfAbsent(entityType, k -> new LinkedHashMap<>())
                                .put(curiosId, curiosSlotType);
                    }
                }
            }
        } else if (!usedAccSlots.isEmpty()) {
            Map<String, ISlotType> playerMap = mutableEntityMap.computeIfAbsent(EntityType.PLAYER, k -> new LinkedHashMap<>());
            for (String accSlotName : usedAccSlots) {
                if (BridgeSlotValidator.isInternalArmorProxySlot(accSlotName)) {
                    continue;
                }
                String curiosId = SlotBridgeMap.toCuriosSlot(accSlotName);
                ISlotType curiosSlotType = updatedCuriosSlots.get(curiosId);
                if (curiosSlotType != null && curiosSlotType.getSize() > 0) {
                    playerMap.put(curiosId, curiosSlotType);
                }
            }
        }

        if (needsUniversalCurioSlot[0]) {
            ISlotType curioSlotType = updatedCuriosSlots.get("curio");
            if (curioSlotType != null && curioSlotType.getSize() > 0) {
                mutableEntityMap
                        .computeIfAbsent(EntityType.PLAYER, k -> new LinkedHashMap<>())
                        .put("curio", curioSlotType);
            }
        }

        ImmutableMap.Builder<EntityType<?>, Map<String, ISlotType>> finalEntityMap = ImmutableMap.builder();
        for (Map.Entry<EntityType<?>, Map<String, ISlotType>> entry : mutableEntityMap.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                finalEntityMap.put(entry.getKey(), ImmutableMap.copyOf(entry.getValue()));
            }
        }
        entityAccessor.accessoriesToCurios$setEntitySlots(finalEntityMap.build());
        AccessoriesCompat.clearValidationCache(false);
        BridgeSlotValidator.invalidateCache();
        ContainerViewManager.bumpStructureGeneration();
        LOGGER.debug(
                "[Accessories to Curios] Synchronized {} used Accessories slots into Curios (universalCurioSlot={}): {}",
                usedAccSlots.size(),
                needsUniversalCurioSlot[0],
                usedAccSlots
        );
    }

    private static ISlotType ensureValidatorAndOrder(ISlotType existing, ResourceLocation validatorId) {
        boolean hasValidator = existing.getValidators() != null && existing.getValidators().contains(validatorId);
        boolean needsOrderBump = "curio".equals(existing.getIdentifier()) && existing.getOrder() < UNIVERSAL_CURIO_MIN_ORDER;
        if (hasValidator && !needsOrderBump) {
            return existing;
        }
        top.theillusivec4.curios.common.slottype.SlotType.Builder builder =
                new top.theillusivec4.curios.common.slottype.SlotType.Builder(existing.getIdentifier());
        copyExistingToBuilder(builder, existing);
        if (needsOrderBump) {
            builder.order(UNIVERSAL_CURIO_MIN_ORDER, true);
        }
        if (!hasValidator) {
            builder.validator(validatorId);
        }
        return builder.build();
    }

    private static ISlotType buildOrMergeCuriosSlot(
            String curiosId,
            SlotType accSlot,
            @Nullable ISlotType existing
    ) {
        top.theillusivec4.curios.common.slottype.SlotType.Builder builder =
                new top.theillusivec4.curios.common.slottype.SlotType.Builder(curiosId);

        if (existing != null) {
            copyExistingToBuilder(builder, existing);
            builder.size(Math.max(existing.getSize(), accSlot.amount()), true);
            if ("curio".equals(curiosId)) {
                builder.order(Math.max(existing.getOrder(), UNIVERSAL_CURIO_MIN_ORDER), true);
            }
            if (existing.getDropRule() == ICurio.DropRule.DEFAULT) {
                ICurio.DropRule mappedRule = SlotBridgeMap.toCuriosDropRule(accSlot.dropRule());
                if (mappedRule != ICurio.DropRule.DEFAULT) {
                    builder.dropRule(mappedRule);
                }
            }
            if (DEFAULT_CURIO_ICON.equals(existing.getIcon()) && accSlot.icon() != null) {
                builder.icon(accSlot.icon());
            }
        } else {
            int order = "curio".equals(curiosId) ? Math.max(accSlot.order(), UNIVERSAL_CURIO_MIN_ORDER) : accSlot.order();
            builder.size(accSlot.amount(), true)
                    .order(order, true)
                    .icon(accSlot.icon() != null ? accSlot.icon() : DEFAULT_CURIO_ICON)
                    .useNativeGui(true, true)
                    .renderToggle(true, true)
                    .dropRule(SlotBridgeMap.toCuriosDropRule(accSlot.dropRule()))
                    .validator(CURIOS_TAG_VALIDATOR);
        }

        builder.hasCosmetic(true, true);
        builder.validator(BridgeSlotValidator.PREDICATE_ID);
        return builder.build();
    }

    private static void copyExistingToBuilder(
            top.theillusivec4.curios.common.slottype.SlotType.Builder builder,
            ISlotType existing
    ) {
        builder.order(existing.getOrder(), true)
                .size(existing.getSize(), true)
                .icon(existing.getIcon())
                .useNativeGui(existing.useNativeGui(), true)
                .hasCosmetic(existing.hasCosmetic(), true)
                .renderToggle(existing.canToggleRendering(), true)
                .dropRule(existing.getDropRule());
        if (existing.getValidators() != null) {
            for (ResourceLocation validator : existing.getValidators()) {
                builder.validator(validator);
            }
        }
    }
}
