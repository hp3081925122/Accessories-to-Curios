package com.hp.accessoriestocurios.bridge;

import com.hp.accessoriestocurios.config.BridgeConfig;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.Accessories;
import io.wispforest.accessories.api.AccessoriesAPI;
import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.Accessory;
import io.wispforest.accessories.api.AccessoryNest;
import io.wispforest.accessories.api.DropRule;
import io.wispforest.accessories.api.attributes.AccessoryAttributeBuilder;
import io.wispforest.accessories.api.attributes.AttributeModificationData;
import io.wispforest.accessories.api.components.AccessoryNestContainerContents;
import io.wispforest.accessories.api.events.AccessoryChangeCallback;
import io.wispforest.accessories.api.events.OnDeathCallback;
import io.wispforest.accessories.api.events.OnDropCallback;
import io.wispforest.accessories.api.events.SlotStateChange;
import io.wispforest.accessories.api.menu.AccessoriesBasedSlot;
import io.wispforest.accessories.api.slot.SlotEntryReference;
import io.wispforest.accessories.api.slot.SlotReference;
import io.wispforest.accessories.impl.AccessoryNestUtils;
import io.wispforest.accessories.menu.AccessoriesInternalSlot;
import io.wispforest.accessories.menu.SlotTypeAccessible;
import io.wispforest.accessories.menu.variants.AccessoriesMenuBase;
import io.wispforest.accessories.neoforge.AccessoriesForge;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.event.CurioDropsEvent;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
import top.theillusivec4.curios.common.inventory.container.CuriosContainer;
import top.theillusivec4.curios.common.inventory.container.CuriosContainerProvider;
import top.theillusivec4.curios.mixin.CuriosImplMixinHooks;

public final class BridgeCommonEvents {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Boolean> IN_LOOTING_CALC = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Integer> CURIOS_TICK_DEPTH = ThreadLocal.withInitial(() -> 0);

    private BridgeCommonEvents() {
    }

    public static void enterCuriosSlotIteration(@Nullable Entity entity) {
        int depth = CURIOS_TICK_DEPTH.get();
        if (depth == 0 && entity instanceof LivingEntity living) {
            CuriosApi.getCuriosInventory(living).ifPresent(inv -> {
                for (ICurioStacksHandler sh : List.copyOf(inv.getCurios().values())) {
                    sh.update();
                }
            });
        }
        CURIOS_TICK_DEPTH.set(depth + 1);
    }

    public static void exitCuriosSlotIteration(@Nullable Entity entity) {
        int next = Math.max(0, CURIOS_TICK_DEPTH.get() - 1);
        CURIOS_TICK_DEPTH.set(next);
        if (next == 0 && entity instanceof LivingEntity living) {
            CuriosApi.getCuriosInventory(living).ifPresent(inv -> {
                for (ICurioStacksHandler sh : List.copyOf(inv.getCurios().values())) {
                    sh.update();
                }
            });
        }
    }

    public static boolean isInCuriosSlotIteration() {
        return CURIOS_TICK_DEPTH.get() > 0;
    }

    public static void runOutsideCuriosSlotIteration(Runnable action) {
        int saved = CURIOS_TICK_DEPTH.get();
        if (saved == 0) {
            action.run();
            return;
        }
        CURIOS_TICK_DEPTH.set(0);
        try {
            action.run();
        } finally {
            CURIOS_TICK_DEPTH.set(saved);
        }
    }

    public static void enterLootingCalculation() {
        IN_LOOTING_CALC.set(true);
    }

    public static void exitLootingCalculation() {
        IN_LOOTING_CALC.set(false);
    }

    public static boolean isInLootingCalculation() {
        return IN_LOOTING_CALC.get();
    }

    public static void registerBridgedEntityCapabilities(RegisterCapabilitiesEvent event) {
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            if (event.isEntityRegistered(AccessoriesForge.CAPABILITY, entityType)) {
                continue;
            }
            event.registerEntity(
                    AccessoriesForge.CAPABILITY,
                    entityType,
                    (entity, ctx) -> entity instanceof LivingEntity living ? AccessoriesCapability.get(living) : null
            );
        }
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BridgeCommonEvents::onCommonSetup);
        modEventBus.addListener(EventPriority.LOWEST, BridgeCommonEvents::onRegisterCapabilities);

        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(BridgeCommonEvents::onCurioChange);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BridgeCommonEvents::onDropRules);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, BridgeCommonEvents::onCurioDrops);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CuriosApi.registerCurioPredicate(
                BridgeSlotValidator.PREDICATE_ID,
                BridgeSlotValidator::testCurioSlot
        ));
    }

    @Nullable
    public static MenuProvider wrapMenuProviderIfNeeded(
            ServerPlayer player,
            @Nullable MenuProvider provider,
            boolean hasExtraDataWriter
    ) {
        if (provider == null
                || hasExtraDataWriter
                || provider instanceof CuriosContainerProvider
                || provider instanceof MenuRedirectingProvider) {
            return provider;
        }
        if (BridgeConfig.COMMON_SPEC.isLoaded() && !BridgeConfig.COMMON.disableAccessoriesMenu.get()) {
            return provider;
        }
        return new MenuRedirectingProvider(provider);
    }

    public static boolean shouldRedirectOpenedMenu(Player player, @Nullable AbstractContainerMenu menu) {
        if (menu == null || menu instanceof CuriosContainer) {
            return false;
        }
        if (BridgeConfig.COMMON_SPEC.isLoaded() && !BridgeConfig.COMMON.disableAccessoriesMenu.get()) {
            return false;
        }
        if (menu instanceof AccessoriesMenuBase base) {
            return base.targetEntity() == null || base.targetEntity() == player;
        }
        if (menu instanceof InventoryMenu) {
            for (Slot slot : menu.slots) {
                if (slot instanceof AccessoriesBasedSlot
                        || slot instanceof AccessoriesInternalSlot
                        || slot instanceof SlotTypeAccessible
                        || slot.container instanceof CuriosBackedExpandedContainer
                        || slot.getClass().getName().contains("Accessories")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        int wiredItemCount = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            event.registerItem(
                    CuriosCapability.ITEM,
                    (stack, ctx) -> {
                        Item stackItem = stack.getItem();
                        if (stackItem instanceof ICurioItem || CuriosImplMixinHooks.getCurioFromRegistry(stackItem).isPresent()) {
                            return null;
                        }
                        return BridgeSlotValidator.isGenuineAccessoriesItem(stack) ? new BridgedCurio(stack) : null;
                    },
                    item
            );
            wiredItemCount++;
        }
        LOGGER.debug("[Accessories to Curios] Registered fallback CuriosCapability.ITEM provider across {} items", wiredItemCount);

    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.SERVER_DATA_LOAD) {
            SlotSynchronizer.syncServerSlotsAndEntities(null);
        }
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        SlotSynchronizer.syncServerSlotsAndEntities(null);
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer singlePlayer = event.getPlayer();
        SlotSynchronizer.syncServerSlotsAndEntities(singlePlayer != null ? singlePlayer.level() : null);
    }

    private static void onCurioChange(CurioChangeEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        ItemStack from = event.getFrom();
        ItemStack to = event.getTo();
        String accSlotName = SlotBridgeMap.resolveContainerSlotName(entity, event.getIdentifier());
        SlotReference slotReference = SlotReference.of(entity, accSlotName, Math.max(0, event.getSlotIndex()));

        boolean isReplacement = !ItemStack.isSameItem(from, to);
        if (isReplacement && entity instanceof ServerPlayer serverPlayer) {
            if (!to.isEmpty() && Accessories.ACCESSORY_EQUIPPED != null) {
                Accessories.ACCESSORY_EQUIPPED.trigger(serverPlayer, to, slotReference, false);
            }
            if (!from.isEmpty() && Accessories.ACCESSORY_UNEQUIPPED != null) {
                Accessories.ACCESSORY_UNEQUIPPED.trigger(serverPlayer, from, slotReference, false);
            }
        }

        SlotStateChange stateChange = isReplacement ? SlotStateChange.REPLACEMENT : SlotStateChange.MUTATION;
        AccessoryChangeCallback.EVENT.invoker().onChange(from, to, slotReference, stateChange);
        fireRecursiveNestChanges(slotReference, AccessoryNestUtils.getData(from), AccessoryNestUtils.getData(to));

        if (!from.isEmpty()) {
            reapplyRemainingExclusiveModifiers(entity, from, slotReference);
        }
    }

    private static void reapplyRemainingExclusiveModifiers(LivingEntity entity, ItemStack removedStack, SlotReference removedRef) {
        AccessoryAttributeBuilder removedBuilder = AccessoriesAPI.getAttributeModifiers(removedStack, removedRef);
        if (removedBuilder.exclusiveAttributes().isEmpty()) {
            return;
        }
        AccessoriesCapability capability = AccessoriesCapability.get(entity);
        if (capability == null) {
            return;
        }
        for (SlotEntryReference equipped : capability.getAllEquipped()) {
            ItemStack stack = equipped.stack();
            if (stack.isEmpty()) {
                continue;
            }
            AccessoryAttributeBuilder activeBuilder = AccessoriesAPI.getAttributeModifiers(stack, equipped.reference());
            for (Map.Entry<Holder<Attribute>, Map<ResourceLocation, AttributeModificationData>> attrEntry : activeBuilder.exclusiveAttributes().entrySet()) {
                Holder<Attribute> attribute = attrEntry.getKey();
                AttributeInstance instance = entity.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                for (AttributeModificationData modData : attrEntry.getValue().values()) {
                    AttributeModifier mod = modData.modifier();
                    if (!instance.hasModifier(mod.id())) {
                        instance.addTransientModifier(mod);
                    }
                }
            }
        }
    }

    private static void fireRecursiveNestChanges(
            SlotReference slotReference,
            @Nullable AccessoryNestContainerContents lastNestData,
            @Nullable AccessoryNestContainerContents currentNestData
    ) {
        if (lastNestData == null && currentNestData == null) {
            return;
        }
        Map<Integer, SlotStateChange> currentNestChanges = currentNestData != null
                ? currentNestData.slotChanges()
                : new HashMap<>();

        List<Map.Entry<SlotEntryReference, Accessory>> lastInner = lastNestData != null
                ? List.copyOf(lastNestData.getMap(slotReference).entrySet())
                : List.of();
        List<Map.Entry<SlotEntryReference, Accessory>> currentInner = currentNestData != null
                ? List.copyOf(currentNestData.getMap(slotReference).entrySet())
                : List.of();

        int maxLen = Math.max(lastInner.size(), currentInner.size());
        for (int i = 0; i < maxLen; i++) {
            Map.Entry<SlotEntryReference, Accessory> lastEntry = i < lastInner.size() ? lastInner.get(i) : null;
            Map.Entry<SlotEntryReference, Accessory> currentEntry = i < currentInner.size() ? currentInner.get(i) : null;
            SlotStateChange changeType = currentNestChanges.getOrDefault(i, SlotStateChange.REPLACEMENT);

            if (lastEntry == null && currentEntry != null) {
                SlotEntryReference curRef = currentEntry.getKey();
                fireInnerStackChange(curRef.reference(), ItemStack.EMPTY, curRef.stack(), changeType);
                fireRecursiveNestChanges(slotReference, null, AccessoryNestUtils.getData(curRef.stack()));
            } else if (currentEntry == null && lastEntry != null) {
                SlotEntryReference prevRef = lastEntry.getKey();
                fireInnerStackChange(prevRef.reference(), prevRef.stack(), ItemStack.EMPTY, changeType);
                fireRecursiveNestChanges(slotReference, AccessoryNestUtils.getData(prevRef.stack()), null);
            } else if (lastEntry != null) {
                SlotEntryReference prevRef = lastEntry.getKey();
                SlotEntryReference curRef = currentEntry.getKey();
                if (!ItemStack.matches(prevRef.stack(), curRef.stack())) {
                    fireInnerStackChange(prevRef.reference(), prevRef.stack(), curRef.stack(), changeType);
                    fireRecursiveNestChanges(
                            slotReference,
                            AccessoryNestUtils.getData(prevRef.stack()),
                            AccessoryNestUtils.getData(curRef.stack())
                    );
                }
            }
        }
        currentNestChanges.clear();
    }

    private static void fireInnerStackChange(
            SlotReference ref,
            ItemStack prev,
            ItemStack cur,
            SlotStateChange changeType
    ) {
        if (ref.entity() instanceof ServerPlayer serverPlayer) {
            if (!cur.isEmpty() && Accessories.ACCESSORY_EQUIPPED != null) {
                Accessories.ACCESSORY_EQUIPPED.trigger(serverPlayer, cur, ref, false);
            }
            if (!prev.isEmpty() && Accessories.ACCESSORY_UNEQUIPPED != null) {
                Accessories.ACCESSORY_UNEQUIPPED.trigger(serverPlayer, prev, ref, false);
            }
        }
        AccessoryChangeCallback.EVENT.invoker().onChange(prev, cur, ref, changeType);
    }

    private static void onDropRules(DropRulesEvent event) {
        LivingEntity entity = event.getEntity();
        AccessoriesCapability capability = AccessoriesCapability.get(entity);
        if (capability == null) {
            return;
        }
        List<ItemStack> equipped = new ArrayList<>();
        for (ICurioStacksHandler handler : event.getCurioHandler().getCurios().values()) {
            IDynamicStackHandler stacks = handler.getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                ItemStack stack = stacks.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    equipped.add(stack);
                }
            }
            IDynamicStackHandler cosmetics = handler.getCosmeticStacks();
            for (int i = 0; i < cosmetics.getSlots(); i++) {
                ItemStack stack = cosmetics.getStackInSlot(i);
                if (!stack.isEmpty()) {
                    equipped.add(stack);
                }
            }
        }
        TriState deathResult = OnDeathCallback.EVENT.invoker().shouldDrop(
                TriState.DEFAULT,
                entity,
                capability,
                event.getSource(),
                equipped
        );
        if (!deathResult.orElse(true)) {
            event.addOverride(stack -> true, ICurio.DropRule.ALWAYS_KEEP);
        }
    }

    private static void onCurioDrops(CurioDropsEvent event) {
        LivingEntity entity = event.getEntity();
        DamageSource source = event.getSource();
        for (ItemEntity itemEntity : event.getDrops()) {
            ItemStack droppedStack = itemEntity.getItem();
            if (droppedStack.isEmpty() || !(AccessoriesAPI.getOrDefaultAccessory(droppedStack) instanceof AccessoryNest nest)) {
                continue;
            }
            SlotReference ref = SlotReference.of(entity, "curio", 0);
            List<Pair<DropRule, ItemStack>> innerRules = nest.getDropRules(droppedStack, ref, source);
            boolean modified = false;
            for (int i = 0; i < innerRules.size(); i++) {
                Pair<DropRule, ItemStack> pair = innerRules.get(i);
                ItemStack innerStack = pair.right();
                if (innerStack.isEmpty()) {
                    continue;
                }
                DropRule resolved = OnDropCallback.getAlternativeRule(pair.left(), innerStack, ref, source);
                if (resolved == DropRule.DESTROY
                        || (resolved == DropRule.DEFAULT && EnchantmentHelper.has(innerStack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP))) {
                    nest.setInnerStack(droppedStack, i, ItemStack.EMPTY);
                    modified = true;
                }
            }
            if (modified) {
                itemEntity.setItem(droppedStack);
            }
        }
    }
}
