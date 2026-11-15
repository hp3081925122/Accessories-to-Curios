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
import io.wispforest.accessories.client.AccessoriesMenu;
import io.wispforest.accessories.client.gui.AccessoriesInternalSlot;
import io.wispforest.accessories.impl.AccessoryNestUtils;
import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.event.CurioChangeEvent;
import top.theillusivec4.curios.api.event.CurioDropsEvent;
import top.theillusivec4.curios.api.event.DropRulesEvent;
import top.theillusivec4.curios.api.type.ICuriosMenu;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;
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

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BridgeCommonEvents::onCommonSetup);
        modEventBus.addListener(BridgeCommonEvents::onConfigLoading);
        modEventBus.addListener(BridgeCommonEvents::onConfigReloading);

        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, EventPriority.LOWEST, BridgeCommonEvents::onAttachItemStackCapabilities);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onTagsUpdated);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, BridgeCommonEvents::onDatapackSync);
        MinecraftForge.EVENT_BUS.addListener(BridgeCommonEvents::onCurioChange);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, BridgeCommonEvents::onDropRules);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, BridgeCommonEvents::onCurioDrops);
    }

    private static void onConfigLoading(ModConfigEvent.Loading event) {
        SlotBridgeMap.invalidateCache();
        BridgeSlotValidator.invalidateCache();
        ContainerViewManager.bumpStructureGeneration();
    }

    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        SlotBridgeMap.invalidateCache();
        BridgeSlotValidator.invalidateCache();
        ContainerViewManager.bumpStructureGeneration();
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> CuriosApi.registerCurioPredicate(
                BridgeSlotValidator.PREDICATE_ID,
                BridgeSlotValidator::testCurioSlot
        ));
    }

    private static void onAttachItemStackCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        Item item = stack.getItem();
        if (item instanceof ICurioItem
                || CuriosImplMixinHooks.getCurioFromRegistry(item).isPresent()
                || event.getCapabilities().containsKey(CuriosCapability.ID_ITEM)) {
            return;
        }
        if (BridgeSlotValidator.isRealCustomAccessory(AccessoriesAPI.getAccessory(item))) {
            event.addCapability(CuriosCapability.ID_ITEM, CuriosApi.createCurioProvider(new BridgedCurio(stack)));
        }
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
        if (menu == null || menu instanceof ICuriosMenu) {
            return false;
        }
        if (BridgeConfig.COMMON_SPEC.isLoaded() && !BridgeConfig.COMMON.disableAccessoriesMenu.get()) {
            return false;
        }
        if (menu instanceof AccessoriesMenu accMenu) {
            return accMenu.targetEntity() == null || accMenu.targetEntity() == player;
        }
        if (menu instanceof InventoryMenu) {
            for (Slot slot : menu.slots) {
                if (slot instanceof AccessoriesBasedSlot
                        || slot instanceof AccessoriesInternalSlot
                        || slot.container instanceof CuriosBackedExpandedContainer
                        || slot.getClass().getName().contains("Accessories")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        BridgeSlotValidator.invalidateCache();
        ContainerViewManager.bumpStructureGeneration();
    }

    private static void onServerAboutToStart(ServerAboutToStartEvent event) {
        SlotSynchronizer.syncServerSlotsAndEntities(null);
    }

    private static void onServerStarted(ServerStartedEvent event) {
        if (SlotSynchronizer.consumePendingLevelRescan()) {
            SlotSynchronizer.syncServerSlotsAndEntities(event.getServer().overworld());
        }
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            return;
        }
        SlotSynchronizer.syncServerSlotsAndEntities(event.getPlayerList().getServer().overworld());
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
    }

    public static void reapplyRemainingExclusiveModifiers(
            LivingEntity entity,
            ItemStack removedStack,
            String removedCuriosSlot,
            int removedSlotIndex
    ) {
        if (entity == null || entity.level().isClientSide() || removedStack.isEmpty()) {
            return;
        }
        String accSlotName = SlotBridgeMap.resolveContainerSlotName(entity, removedCuriosSlot);
        SlotReference removedRef = SlotReference.of(entity, accSlotName, Math.max(0, removedSlotIndex));
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
            SlotReference eqRef = equipped.reference();
            String eqCuriosSlot = eqRef.slotContainer() instanceof CuriosBackedAccessoriesContainer backed
                    ? backed.getCuriosSlotId()
                    : SlotBridgeMap.toCuriosSlot(eqRef.slotName());
            if (eqRef.slot() == removedSlotIndex && eqCuriosSlot.equals(removedCuriosSlot)) {
                continue;
            }
            AccessoryAttributeBuilder activeBuilder = AccessoriesAPI.getAttributeModifiers(stack, eqRef);
            for (Map.Entry<Attribute, Map<ResourceLocation, AttributeModificationData>> attrEntry : activeBuilder.exclusiveAttributes().entrySet()) {
                Attribute attribute = attrEntry.getKey();
                AttributeInstance instance = entity.getAttribute(attribute);
                if (instance == null) {
                    continue;
                }
                for (AttributeModificationData modData : attrEntry.getValue().values()) {
                    AttributeModifier mod = modData.modifier();
                    if (!instance.hasModifier(mod)) {
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

            if (lastEntry == null && currentEntry != null) {
                SlotEntryReference curRef = currentEntry.getKey();
                fireInnerStackChange(curRef.reference(), ItemStack.EMPTY, curRef.stack(), SlotStateChange.REPLACEMENT);
                fireRecursiveNestChanges(slotReference, null, AccessoryNestUtils.getData(curRef.stack()));
            } else if (currentEntry == null && lastEntry != null) {
                SlotEntryReference prevRef = lastEntry.getKey();
                fireInnerStackChange(prevRef.reference(), prevRef.stack(), ItemStack.EMPTY, SlotStateChange.REPLACEMENT);
                fireRecursiveNestChanges(slotReference, AccessoryNestUtils.getData(prevRef.stack()), null);
            } else if (lastEntry != null) {
                SlotEntryReference prevRef = lastEntry.getKey();
                SlotEntryReference curRef = currentEntry.getKey();
                if (!ItemStack.matches(prevRef.stack(), curRef.stack())) {
                    SlotStateChange innerChange = !ItemStack.isSameItem(prevRef.stack(), curRef.stack())
                            ? SlotStateChange.REPLACEMENT
                            : SlotStateChange.MUTATION;
                    fireInnerStackChange(prevRef.reference(), prevRef.stack(), curRef.stack(), innerChange);
                    fireRecursiveNestChanges(
                            slotReference,
                            AccessoryNestUtils.getData(prevRef.stack()),
                            AccessoryNestUtils.getData(curRef.stack())
                    );
                }
            }
        }
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
                        || (resolved == DropRule.DEFAULT && EnchantmentHelper.hasVanishingCurse(innerStack))) {
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
