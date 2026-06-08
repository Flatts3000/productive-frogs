package com.flatts.productivefrogs.content.block.entity;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Delegating {@link IItemHandler} view exposing a fixed subset of a parent
 * {@link ItemStackHandler}'s slots, with insert and/or extract gated. Backs
 * the side-aware {@code Capabilities.ItemHandler.BLOCK} providers in
 * {@code PFModBusEvents}: hoppers iterate the view's slots and call
 * {@code insertItem(slot, ...)} per slot; routing to
 * {@code delegate.insertItem} preserves the parent's per-slot
 * {@code isItemValid} filter, so each pushed item lands in the slot that
 * accepts it and everything else bounces.
 *
 * <p>Shared by {@link SpawneryInventory} and {@link SlimeChurnInventory}
 * (extracted from their identical private copies - one fix site for hopper
 * routing, not N).
 */
final class MultiSlotItemView implements IItemHandler {

    private final ItemStackHandler delegate;
    private final int[] indices;
    private final boolean allowInsert;
    private final boolean allowExtract;

    MultiSlotItemView(ItemStackHandler delegate, int[] indices, boolean allowInsert, boolean allowExtract) {
        this.delegate = delegate;
        this.indices = indices;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
    }

    @Override
    public int getSlots() {
        return indices.length;
    }

    /** Map a view-local slot to its delegate index, or -1 if out of range. */
    private int target(int slot) {
        return (slot >= 0 && slot < indices.length) ? indices[slot] : -1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        int i = target(slot);
        return i < 0 ? ItemStack.EMPTY : delegate.getStackInSlot(i);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        int i = target(slot);
        if (!allowInsert || i < 0) {
            return stack;
        }
        return delegate.insertItem(i, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        int i = target(slot);
        if (!allowExtract || i < 0) {
            return ItemStack.EMPTY;
        }
        return delegate.extractItem(i, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        int i = target(slot);
        return i < 0 ? 0 : delegate.getSlotLimit(i);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        int i = target(slot);
        return allowInsert && i >= 0 && delegate.isItemValid(i, stack);
    }
}
