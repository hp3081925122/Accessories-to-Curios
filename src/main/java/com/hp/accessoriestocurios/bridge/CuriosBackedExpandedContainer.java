package com.hp.accessoriestocurios.bridge;

import com.mojang.datafixers.util.Pair;
import io.wispforest.accessories.impl.ExpandedSimpleContainer;
import java.util.Iterator;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public final class CuriosBackedExpandedContainer extends ExpandedSimpleContainer {
    private final Supplier<@Nullable IDynamicStackHandler> handlerSupplier;

    public CuriosBackedExpandedContainer(
            CuriosBackedAccessoriesContainer parent,
            String name,
            Supplier<@Nullable IDynamicStackHandler> handlerSupplier
    ) {
        super(parent, 0, name, false);
        this.handlerSupplier = handlerSupplier;
    }

    @Nullable
    private IDynamicStackHandler handler() {
        return this.handlerSupplier != null ? this.handlerSupplier.get() : null;
    }

    @Override
    public int getContainerSize() {
        IDynamicStackHandler h = handler();
        return h != null ? h.getSlots() : 0;
    }

    @Override
    public boolean validIndex(int slot) {
        return slot >= 0 && slot < getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        IDynamicStackHandler h = handler();
        if (h == null) {
            return true;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            if (!h.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        IDynamicStackHandler h = handler();
        if (h == null || slot < 0 || slot >= h.getSlots()) {
            return ItemStack.EMPTY;
        }
        return h.getStackInSlot(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        IDynamicStackHandler h = handler();
        if (h == null || slot < 0 || slot >= h.getSlots()) {
            return;
        }
        ItemStack safe = stack == null ? ItemStack.EMPTY : stack;
        int limit = getMaxStackSize(safe);
        if (!safe.isEmpty() && safe.getCount() > limit) {
            safe = safe.copyWithCount(limit);
        }
        h.setStackInSlot(slot, safe);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        IDynamicStackHandler h = handler();
        if (h == null || slot < 0 || slot >= h.getSlots() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack current = h.getStackInSlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = current.copy();
        ItemStack split = copy.split(amount);
        h.setStackInSlot(slot, copy.isEmpty() ? ItemStack.EMPTY : copy);
        return split;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        IDynamicStackHandler h = handler();
        if (h == null || slot < 0 || slot >= h.getSlots()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = h.getStackInSlot(slot);
        if (current.isEmpty()) {
            return ItemStack.EMPTY;
        }
        h.setStackInSlot(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public ItemStack addItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        IDynamicStackHandler h = handler();
        if (h == null) {
            return stack.copy();
        }
        ItemStack remaining = stack.copy();
        int maxStack = getMaxStackSize(remaining);
        for (int i = 0; i < h.getSlots() && !remaining.isEmpty(); i++) {
            ItemStack inSlot = h.getStackInSlot(i);
            if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, remaining)) {
                int transferable = Math.min(remaining.getCount(), maxStack - inSlot.getCount());
                if (transferable > 0) {
                    ItemStack updated = inSlot.copyWithCount(inSlot.getCount() + transferable);
                    h.setStackInSlot(i, updated);
                    remaining.shrink(transferable);
                }
            }
        }
        for (int i = 0; i < h.getSlots() && !remaining.isEmpty(); i++) {
            if (h.getStackInSlot(i).isEmpty()) {
                int toMove = Math.min(remaining.getCount(), maxStack);
                h.setStackInSlot(i, remaining.split(toMove));
            }
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    @Override
    public boolean canAddItem(ItemStack stack) {
        IDynamicStackHandler h = handler();
        if (h == null) {
            return false;
        }
        int maxStack = getMaxStackSize(stack);
        for (int i = 0; i < h.getSlots(); i++) {
            ItemStack inSlot = h.getStackInSlot(i);
            if (inSlot.isEmpty()) {
                return true;
            }
            if (ItemStack.isSameItemSameComponents(inSlot, stack) && inSlot.getCount() < maxStack) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void clearContent() {
        IDynamicStackHandler h = handler();
        if (h == null) {
            return;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            h.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        IDynamicStackHandler h = handler();
        if (h == null) {
            return NonNullList.create();
        }
        NonNullList<ItemStack> list = NonNullList.withSize(h.getSlots(), ItemStack.EMPTY);
        for (int i = 0; i < h.getSlots(); i++) {
            list.set(i, h.getStackInSlot(i));
        }
        return list;
    }

    @Override
    public ItemStack getPreviousItem(int slot) {
        IDynamicStackHandler h = handler();
        if (h == null || slot < 0 || slot >= h.getSlots()) {
            return ItemStack.EMPTY;
        }
        return h.getPreviousStackInSlot(slot);
    }

    @Override
    public void setPreviousItem(int slot, ItemStack stack) {
        IDynamicStackHandler h = handler();
        if (h == null || slot < 0 || slot >= h.getSlots()) {
            return;
        }
        h.setPreviousStackInSlot(slot, stack == null ? ItemStack.EMPTY : stack);
    }

    @Override
    public @NotNull Iterator<Pair<Integer, ItemStack>> iterator() {
        return new Iterator<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < getContainerSize();
            }

            @Override
            public Pair<Integer, ItemStack> next() {
                Pair<Integer, ItemStack> pair = Pair.of(index, getItem(index));
                index++;
                return pair;
            }
        };
    }

    @Override
    public ListTag createTag(HolderLookup.Provider provider) {
        ListTag listTag = new ListTag();
        for (int i = 0; i < getContainerSize(); i++) {
            ItemStack itemStack = getItem(i);
            if (!itemStack.isEmpty()) {
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.putInt("Slot", i);
                listTag.add(itemStack.save(provider, compoundTag));
            }
        }
        return listTag;
    }

    @Override
    public void fromTag(ListTag containerNbt, HolderLookup.Provider provider) {
        IDynamicStackHandler h = handler();
        if (h == null) {
            return;
        }
        for (int i = 0; i < h.getSlots(); i++) {
            h.setStackInSlot(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < containerNbt.size(); i++) {
            CompoundTag compoundTag = containerNbt.getCompound(i);
            int slot = compoundTag.getInt("Slot");
            ItemStack stack = parseOptional(provider, compoundTag);
            if (slot >= 0 && slot < h.getSlots()) {
                h.setStackInSlot(slot, stack);
            }
        }
    }
}
