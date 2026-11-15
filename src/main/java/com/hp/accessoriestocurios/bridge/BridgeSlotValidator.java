package com.hp.accessoriestocurios.bridge;

import com.hp.accessoriestocurios.AccessoriesToCurios;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.components.AccessoriesDataComponents;
import io.wispforest.accessories.api.components.AccessoryItemAttributeModifiers;
import io.wispforest.accessories.api.components.AccessorySlotValidationComponent;
import io.wispforest.accessories.api.data.AccessoriesBaseData;
import io.wispforest.accessories.api.data.AccessoriesTags;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.api.slot.SlotType;
import io.wispforest.accessories.api.slot.SlotTypeReference;
import io.wispforest.accessories.api.slot.UniqueSlotHandling;
import io.wispforest.accessories.data.SlotTypeLoader;
import io.wispforest.accessories.menu.ArmorSlotTypes;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;

public final class BridgeSlotValidator {
    public static final ResourceLocation PREDICATE_ID =
            ResourceLocation.fromNamespaceAndPath(AccessoriesToCurios.MOD_ID, "bridge");
    public static final TagKey<Item> LEGACY_ALL_TAG =
            TagKey.create(Registries.ITEM, Accessories.of("all"));
    public static final TagKey<Item> CURIOS_WILDCARD_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(CuriosApi.MODID, "curio"));

    private static final Set<String> INTERNAL_ARMOR_PROXY_SLOTS = Set.of(
            "accessories:head",
            "accessories:chest",
            "accessories:legs",
            "accessories:feet",
            "accessories:animal_body"
    );

    public static final Map<String, Integer> DEFAULT_ACCESSORIES_SLOT_AMOUNTS = Map.ofEntries(
            Map.entry("anklet", 1),
            Map.entry("back", 1),
            Map.entry("belt", 1),
            Map.entry("cape", 1),
            Map.entry("charm", 1),
            Map.entry("face", 1),
            Map.entry("hand", 2),
            Map.entry("hat", 1),
            Map.entry("necklace", 1),
            Map.entry("ring", 2),
            Map.entry("shoes", 1),
            Map.entry("wrist", 1)
    );

    private static final byte CLASS_BASE_FALSE = 0;
    private static final byte CLASS_DEFAULT_COMPONENT_TRUE = 1;
    private static final byte CLASS_INTRINSIC_TRUE = 2;

    private static final Map<String, TagKey<Item>> DEFAULT_SLOT_TAGS;
    private static final Map<Item, Byte> SERVER_ITEM_CACHE = new ConcurrentHashMap<>();
    private static final Map<Item, Byte> CLIENT_ITEM_CACHE = new ConcurrentHashMap<>();
    private static volatile Map<String, SlotType> lastServerSlotMap;
    private static volatile Map<String, SlotType> lastClientSlotMap;

    static {
        Map<String, TagKey<Item>> defaultTags = new LinkedHashMap<>();
        for (String defaultSlot : DEFAULT_ACCESSORIES_SLOT_AMOUNTS.keySet()) {
            defaultTags.put(defaultSlot, TagKey.create(Registries.ITEM, Accessories.of(defaultSlot)));
        }
        DEFAULT_SLOT_TAGS = Map.copyOf(defaultTags);
    }

    private BridgeSlotValidator() {
    }

    public static void invalidateCache() {
        SERVER_ITEM_CACHE.clear();
        CLIENT_ITEM_CACHE.clear();
        lastServerSlotMap = null;
        lastClientSlotMap = null;
    }

    public static boolean isInternalArmorProxySlot(@Nullable String slotName) {
        if (slotName == null || slotName.isEmpty()) {
            return false;
        }
        if (INTERNAL_ARMOR_PROXY_SLOTS.contains(slotName)) {
            return true;
        }
        SlotTypeReference head = ArmorSlotTypes.headSlot();
        if (head != null && ArmorSlotTypes.isArmorType(slotName)) {
            return true;
        }
        SlotTypeReference animalBody = ArmorSlotTypes.animalBody();
        return animalBody != null && slotName.equals(animalBody.slotName());
    }

    public static boolean isInternalArmorAccessory(@Nullable Accessory accessory) {
        if (accessory == null) {
            return false;
        }
        return accessory.getClass().getName().startsWith("io.wispforest.accessories.menu.ArmorSlotTypes");
    }

    public static boolean isRealCustomAccessory(@Nullable Accessory accessory) {
        return accessory != null
                && !AccessoriesAPI.isDefaultAccessory(accessory)
                && !isInternalArmorAccessory(accessory);
    }

    public static boolean isGenuineAccessoriesItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (isRealCustomAccessory(AccessoriesAPI.getAccessory(item))) {
            return true;
        }
        Level level = BridgeRuntimeAccess.resolveLevel(null);
        if (level != null) {
            byte classification = classifyBaseItem(item, level);
            if (classification == CLASS_INTRINSIC_TRUE) {
                return true;
            }
            if (classification == CLASS_DEFAULT_COMPONENT_TRUE) {
                return stack.isComponentsPatchEmpty() || hasAccessoryDataComponents(stack);
            }
            return !stack.isComponentsPatchEmpty() && hasAccessoryDataComponents(stack);
        }
        if (hasAccessoryDataComponents(stack)) {
            return true;
        }
        if (stack.is(AccessoriesTags.ANY_TAG) || stack.is(LEGACY_ALL_TAG)) {
            return true;
        }
        for (TagKey<Item> tagKey : DEFAULT_SLOT_TAGS.values()) {
            if (stack.is(tagKey)) {
                return true;
            }
        }
        return false;
    }

    private static byte classifyBaseItem(Item item, Level level) {
        Map<String, SlotType> slotMap = SlotTypeLoader.getSlotTypes(level);
        Map<Item, Byte> cache;
        if (level.isClientSide()) {
            if (lastClientSlotMap != slotMap) {
                lastClientSlotMap = slotMap;
                CLIENT_ITEM_CACHE.clear();
            }
            cache = CLIENT_ITEM_CACHE;
        } else {
            if (lastServerSlotMap != slotMap) {
                lastServerSlotMap = slotMap;
                SERVER_ITEM_CACHE.clear();
            }
            cache = SERVER_ITEM_CACHE;
        }
        Byte cached = cache.get(item);
        if (cached != null) {
            return cached;
        }
        byte computed = computeBaseClassification(item, slotMap);
        cache.put(item, computed);
        return computed;
    }

    private static byte computeBaseClassification(Item item, Map<String, SlotType> slotMap) {
        if (isRealCustomAccessory(AccessoriesAPI.getAccessory(item))) {
            return CLASS_INTRINSIC_TRUE;
        }
        ItemStack defaultStack = item.getDefaultInstance();
        if (defaultStack.isEmpty()) {
            return CLASS_BASE_FALSE;
        }
        if (defaultStack.is(AccessoriesTags.ANY_TAG) || defaultStack.is(LEGACY_ALL_TAG)) {
            return CLASS_INTRINSIC_TRUE;
        }
        for (SlotType slotType : slotMap.values()) {
            if (isInternalArmorProxySlot(slotType.name())) {
                continue;
            }
            if (defaultStack.is(AccessoriesAPI.getSlotTag(slotType))) {
                return CLASS_INTRINSIC_TRUE;
            }
        }
        if (hasAccessoryDataComponents(defaultStack)) {
            return CLASS_DEFAULT_COMPONENT_TRUE;
        }
        return CLASS_BASE_FALSE;
    }

    private static boolean hasAccessoryDataComponents(ItemStack stack) {
        if (stack.has(AccessoriesDataComponents.NESTED_ACCESSORIES)
                || stack.has(AccessoriesDataComponents.CUSTOM_RENDERER)
                || stack.has(AccessoriesDataComponents.RENDER_OVERRIDE)) {
            return true;
        }
        AccessorySlotValidationComponent slotValidation = stack.getOrDefault(
                AccessoriesDataComponents.SLOT_VALIDATION,
                AccessorySlotValidationComponent.EMPTY
        );
        for (String validOverride : slotValidation.validSlotOverrides()) {
            if (!isInternalArmorProxySlot(validOverride)) {
                return true;
            }
        }
        if (!slotValidation.invalidSlotOverrides().isEmpty()) {
            return true;
        }
        AccessoryItemAttributeModifiers attributes = stack.getOrDefault(
                AccessoriesDataComponents.ATTRIBUTES,
                AccessoryItemAttributeModifiers.EMPTY
        );
        for (AccessoryItemAttributeModifiers.Entry entry : attributes.modifiers()) {
            if (!isInternalArmorProxySlot(entry.slotName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isPureUniversalAccessoriesItem(ItemStack stack) {
        AccessorySlotValidationComponent validation = stack.getOrDefault(
                AccessoriesDataComponents.SLOT_VALIDATION,
                AccessorySlotValidationComponent.EMPTY
        );
        if (!validation.invalidSlotOverrides().isEmpty()) {
            return false;
        }
        if (validation.validSlotOverrides().contains("any")) {
            return true;
        }
        return stack.is(AccessoriesTags.ANY_TAG) || stack.is(LEGACY_ALL_TAG);
    }

    public static boolean onlyMatchesUniqueSlots(ItemStack stack, @Nullable Level level, @Nullable LivingEntity entity) {
        if (level == null || isPureUniversalAccessoriesItem(stack) || stack.is(CURIOS_WILDCARD_TAG)) {
            return false;
        }
        boolean matchedUnique = false;
        for (SlotType slotType : SlotTypeLoader.getSlotTypes(level).values()) {
            String name = slotType.name();
            if (isInternalArmorProxySlot(name)) {
                continue;
            }
            if (matchesSpecificSlotType(stack, level, entity, slotType, 0, false)) {
                if (!UniqueSlotHandling.isUniqueSlot(name)) {
                    return false;
                }
                matchedUnique = true;
            }
        }
        return matchedUnique;
    }

    public static boolean testCurioSlot(SlotResult slotResult) {
        ItemStack stack = slotResult.stack();
        if (stack.isEmpty() || !isGenuineAccessoriesItem(stack)) {
            return false;
        }
        SlotContext ctx = slotResult.slotContext();
        String curiosSlot = ctx.identifier();
        LivingEntity entity = ctx.entity();
        Level level = BridgeRuntimeAccess.resolveLevel(entity);
        boolean isClient = level == null || level.isClientSide();
        boolean hasCurioWildcardSlot = CuriosApi.getSlots(isClient).containsKey("curio");

        if ("curio".equals(curiosSlot)) {
            if (isPureUniversalAccessoriesItem(stack)) {
                return checkEntityCanEquip(stack, entity, "curio", ctx.index());
            }
            Accessory explicit = AccessoriesAPI.getAccessory(stack);
            if (isRealCustomAccessory(explicit) && !matchesAnySpecificSlot(stack, level, entity)) {
                return checkEntityCanEquip(stack, entity, "curio", ctx.index());
            }
            return false;
        }

        if (hasCurioWildcardSlot && isPureUniversalAccessoriesItem(stack)) {
            return false;
        }

        if (level == null) {
            return matchesWithoutLevel(stack, curiosSlot);
        }

        Map<String, SlotType> slotTypes = SlotTypeLoader.getSlotTypes(level);
        List<String> candidates = SlotBridgeMap.getCandidateAccessoriesSlots(curiosSlot, level);
        int index = Math.max(0, ctx.index());

        for (String accSlotName : candidates) {
            if (isInternalArmorProxySlot(accSlotName)) {
                continue;
            }
            SlotType slotType = slotTypes.get(accSlotName);
            if (slotType != null) {
                if (matchesSpecificSlotType(stack, level, entity, slotType, index, !hasCurioWildcardSlot)) {
                    if (entity == null || AccessoriesAPI.canEquip(stack, SlotReference.of(entity, accSlotName, index))) {
                        return true;
                    }
                }
            } else if (matchesFallbackTagOrComponent(stack, accSlotName, !hasCurioWildcardSlot)) {
                if (checkEntityCanEquip(stack, entity, accSlotName, index)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean matchesSpecificSlotWithoutWildcard(ItemStack stack, Level level, @Nullable LivingEntity entity, SlotType slotType) {
        if (isInternalArmorProxySlot(slotType.name())) {
            return false;
        }
        return matchesSpecificSlotType(stack, level, entity, slotType, 0, false);
    }

    private static boolean matchesAnySpecificSlot(ItemStack stack, @Nullable Level level, @Nullable LivingEntity entity) {
        if (level == null) {
            for (String slot : DEFAULT_ACCESSORIES_SLOT_AMOUNTS.keySet()) {
                if (matchesFallbackTagOrComponent(stack, slot, false)) {
                    return true;
                }
            }
            return false;
        }
        for (SlotType slotType : SlotTypeLoader.getSlotTypes(level).values()) {
            if (isInternalArmorProxySlot(slotType.name())) {
                continue;
            }
            if (matchesSpecificSlotType(stack, level, entity, slotType, 0, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSpecificSlotType(
            ItemStack stack,
            Level level,
            @Nullable LivingEntity entity,
            SlotType slotType,
            int index,
            boolean allowAnyTag
    ) {
        if (isInternalArmorProxySlot(slotType.name())) {
            return false;
        }
        AccessorySlotValidationComponent validation = stack.getOrDefault(
                AccessoriesDataComponents.SLOT_VALIDATION,
                AccessorySlotValidationComponent.EMPTY
        );
        if (validation.invalidSlotOverrides().contains(slotType.name())) {
            return false;
        }

        Set<ResourceLocation> nonWildcardValidators = new HashSet<>();
        boolean hadTagValidator = false;
        boolean hadComponentValidator = false;
        for (ResourceLocation validator : slotType.validators()) {
            if (AccessoriesBaseData.TAG_PREDICATE_ID.equals(validator)) {
                hadTagValidator = true;
            } else if (AccessoriesBaseData.COMPONENT_PREDICATE_ID.equals(validator)) {
                hadComponentValidator = true;
            } else {
                nonWildcardValidators.add(validator);
            }
        }

        if (hadComponentValidator) {
            if (validation.validSlotOverrides().contains(slotType.name())) {
                return true;
            }
            if (allowAnyTag && validation.validSlotOverrides().contains("any")) {
                return true;
            }
        }

        if (hadTagValidator) {
            if (stack.is(AccessoriesAPI.getSlotTag(slotType))) {
                return true;
            }
            if (allowAnyTag && (stack.is(AccessoriesTags.ANY_TAG) || stack.is(LEGACY_ALL_TAG))) {
                return true;
            }
        }

        if (!nonWildcardValidators.isEmpty()) {
            return AccessoriesAPI.getPredicateResults(nonWildcardValidators, level, entity, slotType, index, stack);
        }
        return false;
    }

    private static boolean matchesWithoutLevel(ItemStack stack, String curiosSlot) {
        String accSlot = SlotBridgeMap.toAccessoriesSlot(curiosSlot);
        if (isInternalArmorProxySlot(accSlot)) {
            return false;
        }
        return matchesFallbackTagOrComponent(stack, accSlot, true)
                || (!accSlot.equals(curiosSlot) && !isInternalArmorProxySlot(curiosSlot) && matchesFallbackTagOrComponent(stack, curiosSlot, true));
    }

    private static boolean matchesFallbackTagOrComponent(ItemStack stack, String accSlotName, boolean allowAny) {
        if (isInternalArmorProxySlot(accSlotName)) {
            return false;
        }
        AccessorySlotValidationComponent validation = stack.getOrDefault(
                AccessoriesDataComponents.SLOT_VALIDATION,
                AccessorySlotValidationComponent.EMPTY
        );
        if (validation.invalidSlotOverrides().contains(accSlotName)) {
            return false;
        }
        if (validation.validSlotOverrides().contains(accSlotName)
                || (allowAny && validation.validSlotOverrides().contains("any"))) {
            return true;
        }
        ResourceLocation tagId = UniqueSlotHandling.isUniqueSlot(accSlotName)
                ? ResourceLocation.tryParse(accSlotName)
                : Accessories.of(accSlotName);
        if (tagId != null && stack.is(TagKey.create(Registries.ITEM, tagId))) {
            return true;
        }
        return allowAny && (stack.is(AccessoriesTags.ANY_TAG) || stack.is(LEGACY_ALL_TAG));
    }

    private static boolean checkEntityCanEquip(ItemStack stack, @Nullable LivingEntity entity, String slotName, int index) {
        if (entity == null) {
            return true;
        }
        return AccessoriesAPI.canEquip(stack, SlotReference.of(entity, slotName, Math.max(0, index)));
    }
}
