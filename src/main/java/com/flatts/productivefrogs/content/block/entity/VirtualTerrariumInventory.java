package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.EntityNetItem;
import com.flatts.productivefrogs.content.item.FrogNetItem;
import com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler;
import com.flatts.productivefrogs.registry.PFItemTags;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Item inventory backing the Virtual Terrarium Processor: one frog slot (a filled
 * Frog Net) and a row of output slots (Froglights / smelted results / mob loot).
 * The feedstock (Slime Milk / Mimic Milk / Mob Slurry) and any product fluids live
 * in separate {@code FluidTank}s on the BlockEntity, not here.
 *
 * <p>Slice 1 wires the Resource path only; the frog slot accepts a filled Frog Net,
 * the output slots reject inserts (the eat loop writes via {@code setStackInSlot}).
 */
public class VirtualTerrariumInventory extends net.neoforged.neoforge.items.ItemStackHandler {

    public static final int FROG_SLOT = 0;
    public static final int OUTPUT_START = 1;
    public static final int OUTPUT_COUNT = 6;
    public static final int UPGRADE_START = OUTPUT_START + OUTPUT_COUNT;
    public static final int UPGRADE_COUNT = 4;
    public static final int SLOT_COUNT = UPGRADE_START + UPGRADE_COUNT;

    private final java.util.function.IntConsumer onChanged;

    public VirtualTerrariumInventory(java.util.function.IntConsumer onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (slot == FROG_SLOT) {
            return stack.getItem() instanceof FrogNetItem && EntityNetItem.isFilled(stack);
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

    /** Count of completely empty output slots (conservative backpressure for multi-drop loot). */
    public int emptyOutputSlots() {
        int free = 0;
        for (int i = OUTPUT_START; i < UPGRADE_START; i++) {
            if (getStackInSlot(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** True when no output slot can accept the sample (production backpressure). */
    public boolean outputFull(ItemStack sample) {
        for (int i = OUTPUT_START; i < UPGRADE_START; i++) {
            ItemStack existing = getStackInSlot(i);
            if (existing.isEmpty()) {
                return false;
            }
            if (ItemStack.isSameItemSameComponents(existing, sample)
                    && existing.getCount() < Math.min(existing.getMaxStackSize(), getSlotLimit(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * True when the output grid is completely jammed - every slot occupied AND at its
     * limit. A cheap, item-agnostic backpressure gate the eat loop checks BEFORE any
     * expensive roll (loot table / smelt / melt recipe) so a full output doesn't re-run
     * that work every tick.
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

    /**
     * Whether every drop in the list fits the output grid, accounting for merging into
     * existing same-item stacks and empty slots consumed by earlier drops in the list.
     * Merge-aware backpressure for the multi-item predator loot path.
     */
    public boolean canFitAll(java.util.List<ItemStack> drops) {
        ItemStack[] sim = new ItemStack[OUTPUT_COUNT];
        for (int i = 0; i < OUTPUT_COUNT; i++) {
            sim[i] = getStackInSlot(OUTPUT_START + i).copy();
        }
        for (ItemStack drop : drops) {
            int remaining = drop.getCount();
            for (int i = 0; i < OUTPUT_COUNT && remaining > 0; i++) {
                int slotMax = Math.min(drop.getMaxStackSize(), getSlotLimit(OUTPUT_START + i));
                if (sim[i].isEmpty()) {
                    int move = Math.min(remaining, slotMax);
                    sim[i] = drop.copyWithCount(move);
                    remaining -= move;
                } else if (ItemStack.isSameItemSameComponents(sim[i], drop)) {
                    int move = Math.min(remaining, Math.max(0, slotMax - sim[i].getCount()));
                    sim[i].grow(move);
                    remaining -= move;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return true;
    }

    // -- 26.1 Capabilities.Item.BLOCK views (cached: one handler = one SnapshotJournal) --

    private ResourceHandler<ItemResource> outputResourceCached;
    private ResourceHandler<ItemResource> frogResourceCached;

    /** DOWN face: extract-only over the output slots. */
    public ResourceHandler<ItemResource> outputResource() {
        if (outputResourceCached == null) {
            int[] outSlots = new int[OUTPUT_COUNT];
            for (int i = 0; i < OUTPUT_COUNT; i++) {
                outSlots[i] = OUTPUT_START + i;
            }
            outputResourceCached = new RestrictedItemResourceHandler(this, outSlots, false, true);
        }
        return outputResourceCached;
    }

    /** Other faces: insert/extract the frog slot (load/retrieve a netted frog). */
    public ResourceHandler<ItemResource> frogResource() {
        if (frogResourceCached == null) {
            frogResourceCached = new RestrictedItemResourceHandler(this, new int[] {FROG_SLOT}, true, true);
        }
        return frogResourceCached;
    }

    public void serialize(ValueOutput output) {
        super.serialize(output);
    }

    public void deserialize(ValueInput input) {
        super.deserialize(input);
    }

    /** True for a Frog Net item (filled or empty) - used by the menu's quick-move. */
    public static boolean isFrogNet(ItemStack stack) {
        return stack.getItem() instanceof FrogNetItem || stack.is(PFItems.FROG_NET.get());
    }
}
