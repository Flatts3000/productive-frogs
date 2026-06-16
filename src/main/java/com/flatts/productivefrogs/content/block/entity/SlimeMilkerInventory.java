package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Two-slot inventory backing the Slime Milker BlockEntity. Built on
 * {@link ItemStackHandler} (the legacy NeoForge 1.21.1 item-handler API) so
 * the BE can expose its storage as the {@link IItemHandler} capability and
 * hoppers / pipe mods can push/pull through {@code Capabilities.Item.BLOCK}.
 *
 * <p>Slot semantics:
 * <ul>
 *   <li>{@code INPUT_SLOT} accepts only {@code productivefrogs:slime_bucket}.</li>
 *   <li>{@code OUTPUT_SLOT} accepts no inserts; the cook loop writes via
 *       {@link #setStackInSlot(int, ItemStack)} which bypasses the validity
 *       check on the parent.</li>
 * </ul>
 *
 * <p>Side-restricted views via {@link #inputView()} / {@link #outputView()}
 * back the side-aware capability provider in {@code PFModBusEvents}: top +
 * horizontal sides return the insert-only INPUT view, bottom returns the
 * extract-only OUTPUT view. Mirrors the vanilla furnace's hopper convention.
 */
public class SlimeMilkerInventory extends ItemStackHandler {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    private final Runnable onChanged;
    private final IItemHandler inputView;
    private final IItemHandler outputView;

    public SlimeMilkerInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new SidedView(this, INPUT_SLOT, true, false);
        this.outputView = new SidedView(this, OUTPUT_SLOT, false, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        // Captured Slime Bucket, or (Equivalence lane, #253) a captured Mimic
        // Slime Bucket - the Milker turns either into its milk.
        return slot == INPUT_SLOT
            && (stack.is(PFItems.SLIME_BUCKET.get()) || stack.is(PFItems.MIMIC_SLIME_BUCKET.get()));
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

    /**
     * Snapshot the inventory state into a passed CompoundTag using the
     * RegistryAccess.EMPTY provider. The BE owns the parent tag's key
     * ("Inventory") so we just write our handler's NBT directly.
     */
    public void serialize(CompoundTag tag) {
        tag.merge(serializeNBT(net.minecraft.core.RegistryAccess.EMPTY));
    }

    public void deserialize(CompoundTag tag) {
        deserializeNBT(net.minecraft.core.RegistryAccess.EMPTY, tag);
    }

    /**
     * Single-slot delegating view that restricts insert and/or extract.
     */
    private static final class SidedView implements IItemHandler {

        private final SlimeMilkerInventory delegate;
        private final int targetIndex;
        private final boolean allowInsert;
        private final boolean allowExtract;

        SidedView(SlimeMilkerInventory delegate, int targetIndex, boolean allowInsert, boolean allowExtract) {
            this.delegate = delegate;
            this.targetIndex = targetIndex;
            this.allowInsert = allowInsert;
            this.allowExtract = allowExtract;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(targetIndex);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!allowInsert) return stack;
            return delegate.insertItem(targetIndex, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!allowExtract) return ItemStack.EMPTY;
            return delegate.extractItem(targetIndex, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(targetIndex);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!allowInsert) return false;
            return delegate.isItemValid(targetIndex, stack);
        }
    }
}
