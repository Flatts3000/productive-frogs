package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.FrogNetItem;
import com.flatts.productivefrogs.registry.PFItemTags;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Item inventory backing the Virtual Terrarium Processor: one frog slot (a filled
 * Frog Net), a row of output slots (Froglights / smelted results), and a column of
 * upgrade slots. The feedstock (Slime Milk / Mimic Milk) and the molten product live
 * in separate {@code FluidTank}s on the BlockEntity, not here.
 *
 * <p>The frog slot accepts a filled Frog Net; the output slots reject GUI/hopper
 * inserts (the eat loop writes via {@code setStackInSlot}, bypassing the validity
 * check); the upgrade slots accept the {@code virtual_terrarium_upgrade} tag under
 * a per-type cap, with Smelter and Melter mutually exclusive.
 */
public class VirtualTerrariumInventory extends ItemStackHandler {

    public static final int FROG_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = 6;
    public static final int UPGRADE_START = OUTPUT_START + OUTPUT_COUNT;
    public static final int UPGRADE_COUNT = 4;
    public static final int SLOT_COUNT = UPGRADE_START + UPGRADE_COUNT;

    private final java.util.function.IntConsumer onChanged;
    private final IItemHandler outputView;
    private final IItemHandler frogView;

    public VirtualTerrariumInventory(java.util.function.IntConsumer onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.outputView = new SidedView(this, OUTPUT_START, OUTPUT_COUNT, false, true);
        this.frogView = new SidedView(this, FROG_SLOT, 1, true, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == FROG_SLOT) {
            return stack.getItem() instanceof FrogNetItem && FrogNetItem.isFilled(stack);
        }
        if (slot >= UPGRADE_START && slot < UPGRADE_START + UPGRADE_COUNT) {
            if (!stack.is(PFItemTags.VIRTUAL_TERRARIUM_UPGRADE)) {
                return false;
            }
            // Smelter and Melter are mutually exclusive.
            if (stack.is(PFItems.VT_UPGRADE_SMELTER.get()) && countUpgrade(PFItems.VT_UPGRADE_MELTER.get()) > 0) {
                return false;
            }
            if (stack.is(PFItems.VT_UPGRADE_MELTER.get()) && countUpgrade(PFItems.VT_UPGRADE_SMELTER.get()) > 0) {
                return false;
            }
            // Per-upgrade cap: reject once the machine already holds the max of this type.
            return countUpgrade(stack.getItem()) < upgradeCap(stack.getItem());
        }
        return false;
    }

    /**
     * Max total of a given upgrade the machine accepts: 8 Bounty / 8 Appetite, 3 Overclock,
     * and only 1 Smelter OR Melter (they are also mutually exclusive).
     */
    public int upgradeCap(Item upgrade) {
        if (upgrade == PFItems.VT_UPGRADE_BOUNTY.get() || upgrade == PFItems.VT_UPGRADE_APPETITE.get()) {
            return 8;
        }
        if (upgrade == PFItems.VT_UPGRADE_OVERCLOCK.get()) {
            return 3;
        }
        if (upgrade == PFItems.VT_UPGRADE_SMELTER.get() || upgrade == PFItems.VT_UPGRADE_MELTER.get()) {
            return 1;
        }
        return 0;
    }

    /** Total installed count of an upgrade item across the upgrade slots. */
    public int countUpgrade(Item upgrade) {
        int total = 0;
        for (int i = UPGRADE_START; i < UPGRADE_START + UPGRADE_COUNT; i++) {
            ItemStack stack = getStackInSlot(i);
            if (stack.is(upgrade)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    public boolean hasUpgrade(Item upgrade) {
        return countUpgrade(upgrade) > 0;
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.accept(slot);
    }

    /** The filled Frog Net in the frog slot, or EMPTY. */
    public ItemStack getFrog() {
        return getStackInSlot(FROG_SLOT);
    }

    /**
     * Insert one produced stack into the first free/mergeable output slot; returns
     * the leftover. Writes via {@code setStackInSlot} rather than {@code insertItem}
     * because the output slots reject {@link #isItemValid} inserts (that guard blocks
     * GUI / hopper deposits into the output row); the internal eat loop must bypass it.
     */
    public ItemStack pushOutput(ItemStack produced) {
        ItemStack remaining = produced.copy();
        for (int i = OUTPUT_START; i < UPGRADE_START && !remaining.isEmpty(); i++) {
            ItemStack existing = getStackInSlot(i);
            if (existing.isEmpty()) {
                int move = Math.min(remaining.getCount(), Math.min(remaining.getMaxStackSize(), getSlotLimit(i)));
                setStackInSlot(i, remaining.copyWithCount(move));
                remaining.shrink(move);
            } else if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int room = Math.min(existing.getMaxStackSize(), getSlotLimit(i)) - existing.getCount();
                int move = Math.min(Math.max(0, room), remaining.getCount());
                if (move > 0) {
                    ItemStack merged = existing.copy();
                    merged.grow(move);
                    setStackInSlot(i, merged);
                    remaining.shrink(move);
                }
            }
        }
        return remaining;
    }

    /** Total room across the output slots for the given unit item (distributes a batch across slots). */
    public int outputCapacity(ItemStack unit) {
        int cap = 0;
        for (int i = OUTPUT_START; i < UPGRADE_START; i++) {
            ItemStack existing = getStackInSlot(i);
            int slotMax = Math.min(unit.getMaxStackSize(), getSlotLimit(i));
            if (existing.isEmpty()) {
                cap += slotMax;
            } else if (ItemStack.isSameItemSameComponents(existing, unit)) {
                cap += Math.max(0, slotMax - existing.getCount());
            }
        }
        return cap;
    }

    /**
     * True when the output grid is completely jammed - every slot occupied AND at its
     * limit. A cheap, item-agnostic backpressure gate the eat loop checks BEFORE any
     * expensive recipe lookup so a full output doesn't re-run smelt/melt every tick.
     */
    public boolean outputFull() {
        for (int i = OUTPUT_START; i < UPGRADE_START; i++) {
            ItemStack s = getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < Math.min(s.getMaxStackSize(), getSlotLimit(i))) {
                return false;
            }
        }
        return true;
    }

    // -- side-aware IItemHandler views (wired in PFModBusEvents) --

    /** DOWN face: extract-only over the output slots. */
    public IItemHandler outputView() {
        return outputView;
    }

    /** Other faces: insert/extract the frog slot (load/retrieve a netted frog). */
    public IItemHandler frogView() {
        return frogView;
    }

    public void serialize(CompoundTag tag) {
        tag.merge(serializeNBT(RegistryAccess.EMPTY));
    }

    public void deserialize(CompoundTag tag) {
        deserializeNBT(RegistryAccess.EMPTY, tag);
    }

    /** True for a Frog Net item (filled or empty) - used by the menu's quick-move. */
    public static boolean isFrogNet(ItemStack stack) {
        return stack.getItem() instanceof FrogNetItem || stack.is(PFItems.FROG_NET.get());
    }

    /**
     * Delegating multi-slot view that restricts insert and/or extract - the Milker's
     * SidedView shape, widened to a contiguous slot range so the DOWN face can pull
     * from every output slot.
     */
    private static final class SidedView implements IItemHandler {

        private final VirtualTerrariumInventory delegate;
        private final int start;
        private final int count;
        private final boolean allowInsert;
        private final boolean allowExtract;

        SidedView(VirtualTerrariumInventory delegate, int start, int count, boolean allowInsert, boolean allowExtract) {
            this.delegate = delegate;
            this.start = start;
            this.count = count;
            this.allowInsert = allowInsert;
            this.allowExtract = allowExtract;
        }

        @Override
        public int getSlots() {
            return count;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(start + slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return allowInsert ? delegate.insertItem(start + slot, stack, simulate) : stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return allowExtract ? delegate.extractItem(start + slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(start + slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return allowInsert && delegate.isItemValid(start + slot, stack);
        }
    }
}
