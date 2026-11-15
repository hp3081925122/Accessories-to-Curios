package com.hp.accessoriestocurios.bridge;

import com.hp.accessoriestocurios.config.BridgeConfig;
import com.hp.accessoriestocurios.mixin.AccessoriesHolderImplAccessor;
import com.mojang.logging.LogUtils;
import io.wispforest.accessories.AccessoriesInternals;
import io.wispforest.accessories.endec.NbtMapCarrier;
import io.wispforest.accessories.impl.AccessoriesHolderImpl;
import io.wispforest.accessories.impl.PlayerEquipControl;
import io.wispforest.endec.util.MapCarrier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.slf4j.Logger;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public final class LegacyDataMigrator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LegacyDataMigrator() {
    }

    public static void migrateEntityIfNeeded(LivingEntity entity) {
        if (entity.level().isClientSide() || entity.isRemoved()) {
            return;
        }
        if (!(AccessoriesInternals.getHolder(entity) instanceof AccessoriesHolderImpl holder)) {
            return;
        }
        AccessoriesHolderImplAccessor accessor = (AccessoriesHolderImplAccessor) holder;

        drainInvalidStacks(entity, holder);

        MapCarrier carrier = accessor.accessoriesToCurios$getCarrier();
        if (!(carrier instanceof NbtMapCarrier nbtCarrier)) {
            if (accessor.accessoriesToCurios$isLoadedFromTag()) {
                accessor.accessoriesToCurios$setLoadedFromTag(false);
            }
            return;
        }

        CompoundTag root = nbtCarrier.compoundTag();
        if (root == null || root.isEmpty()) {
            accessor.accessoriesToCurios$setLoadedFromTag(false);
            return;
        }

        restoreHolderPreferences(holder, root);

        String containersKey = root.contains("accessories_containers", Tag.TAG_COMPOUND)
                ? "accessories_containers"
                : (root.contains("AccessoriesContainers", Tag.TAG_COMPOUND) ? "AccessoriesContainers" : null);

        if (containersKey == null) {
            accessor.accessoriesToCurios$setLoadedFromTag(false);
            accessor.accessoriesToCurios$setCarrier(NbtMapCarrier.of());
            return;
        }

        if (BridgeConfig.COMMON_SPEC.isLoaded() && !BridgeConfig.COMMON.migrateLegacyData.get()) {
            return;
        }

        Optional<ICuriosItemHandler> curiosInvOpt = CuriosApi.getCuriosInventory(entity);
        if (curiosInvOpt.isEmpty()) {
            return;
        }
        ICuriosItemHandler curiosInv = curiosInvOpt.get();
        if (curiosInv.getCurios().isEmpty() && !CuriosApi.getEntitySlots(entity).isEmpty()) {
            return;
        }

        CompoundTag containersTag = root.getCompound(containersKey);
        HolderLookup.Provider registries = entity.registryAccess();
        List<ItemStack> overflowStacks = new ArrayList<>();
        int migratedStacks = 0;
        int migratedModifiers = 0;

        for (String accSlotName : containersTag.getAllKeys()) {
            if (!containersTag.contains(accSlotName, Tag.TAG_COMPOUND)) {
                continue;
            }
            CompoundTag slotTag = containersTag.getCompound(accSlotName);
            if (slotTag.isEmpty()) {
                continue;
            }

            String targetCuriosSlot = SlotBridgeMap.toCuriosSlot(accSlotName);
            Optional<ICurioStacksHandler> handlerOpt = curiosInv.getStacksHandler(targetCuriosSlot);
            if (handlerOpt.isEmpty() && !"curio".equals(targetCuriosSlot)) {
                handlerOpt = curiosInv.getStacksHandler("curio");
            }

            ListTag persistentMods = getListTag(slotTag, "persistent_modifiers", "PersistentModifiers", Tag.TAG_COMPOUND);
            if (handlerOpt.isPresent() && !persistentMods.isEmpty()) {
                ICurioStacksHandler stacksHandler = handlerOpt.get();
                for (int i = 0; i < persistentMods.size(); i++) {
                    AttributeModifier modifier = AttributeModifier.load(persistentMods.getCompound(i));
                    if (modifier != null && !stacksHandler.getModifiers().containsKey(modifier.id())) {
                        stacksHandler.addPermanentModifier(modifier);
                        migratedModifiers++;
                    }
                }
                stacksHandler.update();
            }

            ListTag itemsList = getListTag(slotTag, "items", "Items", Tag.TAG_COMPOUND);
            ListTag cosmeticsList = getListTag(slotTag, "cosmetics", "Cosmetics", Tag.TAG_COMPOUND);
            List<Boolean> renderFlags = readRenderOptions(slotTag);

            if (handlerOpt.isPresent()) {
                ICurioStacksHandler stacksHandler = handlerOpt.get();
                migratedStacks += migrateStackList(itemsList, registries, stacksHandler.getStacks(), renderFlags, stacksHandler.getRenders(), overflowStacks);
                migratedStacks += migrateStackList(cosmeticsList, registries, stacksHandler.getCosmeticStacks(), List.of(), null, overflowStacks);
            } else {
                migratedStacks += collectAllDecodedStacks(itemsList, registries, overflowStacks);
                migratedStacks += collectAllDecodedStacks(cosmeticsList, registries, overflowStacks);
            }
        }

        for (ItemStack overflow : overflowStacks) {
            if (overflow.isEmpty()) {
                continue;
            }
            if (entity instanceof Player player) {
                ItemHandlerHelper.giveItemToPlayer(player, overflow);
            } else {
                entity.spawnAtLocation(overflow);
            }
        }

        root.remove("accessories_containers");
        root.remove("AccessoriesContainers");
        accessor.accessoriesToCurios$setLoadedFromTag(false);
        accessor.accessoriesToCurios$setCarrier(NbtMapCarrier.of());
        if (migratedStacks > 0 || migratedModifiers > 0) {
            LOGGER.info(
                    "[Accessories to Curios] Migrated {} legacy Accessories stack(s) and {} slot modifier(s) for entity {}",
                    migratedStacks,
                    migratedModifiers,
                    entity.getScoreboardName()
            );
        } else {
            LOGGER.debug(
                    "[Accessories to Curios] Cleared empty legacy Accessories container tag for entity {}",
                    entity.getScoreboardName()
            );
        }
    }

    private static void drainInvalidStacks(LivingEntity entity, AccessoriesHolderImpl holder) {
        if (holder.invalidStacks.isEmpty()) {
            return;
        }
        for (ItemStack invalid : holder.invalidStacks) {
            if (invalid.isEmpty()) {
                continue;
            }
            if (entity instanceof Player player) {
                ItemHandlerHelper.giveItemToPlayer(player, invalid);
            } else {
                entity.spawnAtLocation(invalid);
            }
        }
        holder.invalidStacks.clear();
    }

    private static void restoreHolderPreferences(AccessoriesHolderImpl holder, CompoundTag root) {
        String equipKey = root.contains("equip_control", Tag.TAG_STRING)
                ? "equip_control"
                : (root.contains("EquipControl", Tag.TAG_STRING) ? "EquipControl" : null);
        if (equipKey != null) {
            try {
                holder.equipControl(PlayerEquipControl.valueOf(root.getString(equipKey)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (root.contains("show_unused_slots", Tag.TAG_BYTE)) {
            holder.showUnusedSlots(root.getBoolean("show_unused_slots"));
        }
        if (root.contains("show_cosmetics", Tag.TAG_BYTE)) {
            holder.cosmeticsShown(root.getBoolean("show_cosmetics"));
        }
    }

    private static ListTag getListTag(CompoundTag tag, String primary, String legacy, int elementType) {
        if (tag.contains(primary, Tag.TAG_LIST)) {
            return tag.getList(primary, elementType);
        }
        if (tag.contains(legacy, Tag.TAG_LIST)) {
            return tag.getList(legacy, elementType);
        }
        return new ListTag();
    }

    private static List<Boolean> readRenderOptions(CompoundTag slotTag) {
        String key = slotTag.contains("render_options", Tag.TAG_LIST)
                ? "render_options"
                : (slotTag.contains("RenderOptions", Tag.TAG_LIST) ? "RenderOptions" : null);
        if (key == null) {
            return List.of();
        }
        ListTag list = slotTag.getList(key, Tag.TAG_BYTE);
        List<Boolean> result = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            Tag elem = list.get(i);
            result.add(elem instanceof net.minecraft.nbt.ByteTag byteTag ? byteTag.getAsByte() != 0 : true);
        }
        return result;
    }

    private static int migrateStackList(
            ListTag tagList,
            HolderLookup.Provider registries,
            IDynamicStackHandler targetHandler,
            List<Boolean> sourceRenderFlags,
            NonNullList<Boolean> targetRenders,
            List<ItemStack> overflow
    ) {
        int migrated = 0;
        for (int i = 0; i < tagList.size(); i++) {
            CompoundTag itemTag = tagList.getCompound(i);
            int savedSlot = itemTag.getInt("Slot");
            ItemStack decoded = decodeStack(registries, itemTag);
            if (decoded.isEmpty()) {
                continue;
            }
            migrated++;
            int placedSlot = -1;
            if (savedSlot >= 0 && savedSlot < targetHandler.getSlots() && targetHandler.getStackInSlot(savedSlot).isEmpty()) {
                targetHandler.setStackInSlot(savedSlot, decoded);
                placedSlot = savedSlot;
            } else {
                for (int s = 0; s < targetHandler.getSlots(); s++) {
                    if (targetHandler.getStackInSlot(s).isEmpty()) {
                        targetHandler.setStackInSlot(s, decoded);
                        placedSlot = s;
                        break;
                    }
                }
            }
            if (placedSlot >= 0) {
                if (targetRenders != null
                        && savedSlot >= 0
                        && savedSlot < sourceRenderFlags.size()
                        && placedSlot < targetRenders.size()) {
                    targetRenders.set(placedSlot, sourceRenderFlags.get(savedSlot));
                }
            } else {
                overflow.add(decoded);
            }
        }
        return migrated;
    }

    private static int collectAllDecodedStacks(
            ListTag tagList,
            HolderLookup.Provider registries,
            List<ItemStack> overflow
    ) {
        int count = 0;
        for (int i = 0; i < tagList.size(); i++) {
            ItemStack decoded = decodeStack(registries, tagList.getCompound(i));
            if (!decoded.isEmpty()) {
                overflow.add(decoded);
                count++;
            }
        }
        return count;
    }

    private static ItemStack decodeStack(HolderLookup.Provider registries, CompoundTag itemTag) {
        return ItemStack.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), itemTag)
                .resultOrPartial(err -> LOGGER.error("[Accessories to Curios] Failed decoding legacy stack {}: {}", itemTag, err))
                .orElse(ItemStack.EMPTY);
    }
}
