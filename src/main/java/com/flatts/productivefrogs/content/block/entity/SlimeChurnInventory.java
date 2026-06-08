package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Four-slot inventory backing the Slime Churn BlockEntity, on
 * {@link ItemStackHandler} so the BE can expose it as the
 * {@code Capabilities.ItemHandler.BLOCK} capability for hoppers / pipe mods
 * (1.21.1 API). Two inputs, two outputs (#187).
 *
 * <p>Slot semantics:
 * <ul>
 *   <li>{@link #MILK_SLOT} accepts only a per-variant Slime Milk bucket
 *       ({@link SlimeMilkBucketItem}) - the variant + spawn-budget source.</li>
 *   <li>{@link #BUCKET_SLOT} accepts only empty {@code minecraft:bucket}s -
 *       the capture containers, one consumed per slime produced.</li>
 *   <li>{@link #SLIME_OUTPUT_SLOT} rejects inserts; the churn loop writes the
 *       produced variant Slime Buckets via {@link #setStackInSlot}.</li>
 *   <li>{@link #EMPTY_OUTPUT_SLOT} rejects inserts; receives the spent empty
 *       milk container when an input milk bucket depletes.</li>
 * </ul>
 *
 * <p>Side-restricted views via {@link #inputView()} / {@link #outputView()}
 * back the side-aware capability provider in {@code PFModBusEvents}: the
 * bottom face returns the extract-only view over both output slots; every
 * other face returns the insert-only view over both input slots, which routes
 * each pushed item to the slot whose {@link #isItemValid} accepts it (milk
 * buckets to the milk slot, empties to the bucket slot).
 */
public class SlimeChurnInventory extends ItemStackHandler {

    public static final int MILK_SLOT = 0;
    public static final int BUCKET_SLOT = 1;
    public static final int SLIME_OUTPUT_SLOT = 2;
    public static final int EMPTY_OUTPUT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    private final Runnable onChanged;
    private final IItemHandler inputView;
    private final IItemHandler outputView;

    public SlimeChurnInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new MultiSlotView(this, new int[] {MILK_SLOT, BUCKET_SLOT}, true, false);
        this.outputView = new MultiSlotView(this, new int[] {SLIME_OUTPUT_SLOT, EMPTY_OUTPUT_SLOT}, false, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case MILK_SLOT -> stack.getItem() instanceof SlimeMilkBucketItem;
            case BUCKET_SLOT -> stack.is(Items.BUCKET);
            default -> false; // output slots reject inserts
        };
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    public IItemHandler inputView() {
        return inputView;
    }

    public IItemHandler outputView() {
        return outputView;
    }

    public void serialize(CompoundTag tag) {
        tag.merge(serializeNBT(RegistryAccess.EMPTY));
    }

    public void deserialize(CompoundTag tag) {
        deserializeNBT(RegistryAccess.EMPTY, tag);
    }

    /**
     * Delegating view exposing a fixed subset of the parent's slots, with
     * insert and/or extract gated. Hoppers iterate the view's slots and call
     * {@code insertItem(slot, ...)} per slot; routing to
     * {@code delegate.insertItem} preserves the per-slot {@link #isItemValid}
     * filter, so a milk bucket lands in the milk slot, an empty bucket in the
     * bucket slot, and anything else bounces. (Same shape as
     * {@code SpawneryInventory.MultiSlotView}.)
     */
    private static final class MultiSlotView implements IItemHandler {

        private final SlimeChurnInventory delegate;
        private final int[] indices;
        private final boolean allowInsert;
        private final boolean allowExtract;

        MultiSlotView(SlimeChurnInventory delegate, int[] indices, boolean allowInsert, boolean allowExtract) {
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
}
