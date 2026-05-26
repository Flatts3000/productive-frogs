package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFItemTags;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Four-slot inventory backing the Spawnery BlockEntity, on {@link ItemStackHandler}
 * so the BE can expose it as the {@code Capabilities.ItemHandler.BLOCK} capability
 * for hoppers / pipe mods (1.21.1 API).
 *
 * <p>Slot semantics:
 * <ul>
 *   <li>{@link #BOTTLE_SLOT} accepts only {@code minecraft:glass_bottle} (the
 *       container, consumed and returned as the filled Frog Egg).</li>
 *   <li>{@link #FUEL_SLOT} accepts only {@code minecraft:slime_ball} (burn fuel,
 *       1 ball = 1 bottle).</li>
 *   <li>{@link #PRIMER_SLOT} accepts any item in a {@code spawnery_primer/<species>}
 *       tag (see {@link PFItemTags}); selects the output species.</li>
 *   <li>{@link #OUTPUT_SLOT} rejects inserts; the production loop writes via
 *       {@link #setStackInSlot(int, ItemStack)} which bypasses the validity check.</li>
 * </ul>
 *
 * <p>Side-restricted views via {@link #inputView()} / {@link #outputView()} back the
 * side-aware capability provider in {@code PFModBusEvents}: the bottom face returns
 * the extract-only OUTPUT view; every other face returns the insert-only view over
 * the three input slots, which routes each pushed item to the slot whose
 * {@link #isItemValid} accepts it.
 */
public class SpawneryInventory extends ItemStackHandler {

    public static final int BOTTLE_SLOT = 0;
    public static final int FUEL_SLOT = 1;
    public static final int PRIMER_SLOT = 2;
    public static final int OUTPUT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    private final Runnable onChanged;
    private final IItemHandler inputView;
    private final IItemHandler outputView;

    public SpawneryInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new MultiSlotView(this, new int[] {BOTTLE_SLOT, FUEL_SLOT, PRIMER_SLOT}, true, false);
        this.outputView = new MultiSlotView(this, new int[] {OUTPUT_SLOT}, false, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            case BOTTLE_SLOT -> stack.is(Items.GLASS_BOTTLE);
            case FUEL_SLOT -> stack.is(Items.SLIME_BALL);
            case PRIMER_SLOT -> PFItemTags.primerCategory(stack) != null;
            default -> false; // OUTPUT_SLOT rejects inserts
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
     * Delegating view exposing a fixed subset of the parent's slots, with insert
     * and/or extract gated. Hoppers iterate the view's slots and call
     * {@code insertItem(slot, ...)} per slot; routing to {@code delegate.insertItem}
     * preserves the per-slot {@link #isItemValid} filter, so a bottle lands in the
     * bottle slot, a slime ball in the fuel slot, a tagged primer in the primer
     * slot, and anything else bounces.
     */
    private static final class MultiSlotView implements IItemHandler {

        private final SpawneryInventory delegate;
        private final int[] indices;
        private final boolean allowInsert;
        private final boolean allowExtract;

        MultiSlotView(SpawneryInventory delegate, int[] indices, boolean allowInsert, boolean allowExtract) {
            this.delegate = delegate;
            this.indices = indices;
            this.allowInsert = allowInsert;
            this.allowExtract = allowExtract;
        }

        @Override
        public int getSlots() {
            return indices.length;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(indices[slot]);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!allowInsert) {
                return stack;
            }
            return delegate.insertItem(indices[slot], stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!allowExtract) {
                return ItemStack.EMPTY;
            }
            return delegate.extractItem(indices[slot], amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(indices[slot]);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (!allowInsert) {
                return false;
            }
            return delegate.isItemValid(indices[slot], stack);
        }
    }
}
