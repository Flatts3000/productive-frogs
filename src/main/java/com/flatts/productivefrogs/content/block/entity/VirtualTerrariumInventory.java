package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.EntityNetItem;
import com.flatts.productivefrogs.content.item.FrogNetItem;
import com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler;
import com.flatts.productivefrogs.registry.PFItems;
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
    public static final int SLOT_COUNT = OUTPUT_START + OUTPUT_COUNT;

    private final Runnable onChanged;

    public VirtualTerrariumInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        // Only the frog slot accepts inserts, and only a filled Frog Net.
        return slot == FROG_SLOT && !stack.isEmpty()
            && stack.getItem() instanceof FrogNetItem && EntityNetItem.isFilled(stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    /** The filled Frog Net in the frog slot, or EMPTY. */
    public ItemStack getFrog() {
        return getStackInSlot(FROG_SLOT);
    }

    /** Insert one produced stack into the first free/mergeable output slot; returns the leftover. */
    public ItemStack pushOutput(ItemStack produced) {
        ItemStack remaining = produced;
        for (int i = OUTPUT_START; i < SLOT_COUNT && !remaining.isEmpty(); i++) {
            remaining = insertItem(i, remaining, false);
        }
        return remaining;
    }

    /** Count of completely empty output slots (conservative backpressure for multi-drop loot). */
    public int emptyOutputSlots() {
        int free = 0;
        for (int i = OUTPUT_START; i < SLOT_COUNT; i++) {
            if (getStackInSlot(i).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** True when every output slot is full (production backpressure). */
    public boolean outputFull(ItemStack sample) {
        ItemStack probe = sample.copy();
        for (int i = OUTPUT_START; i < SLOT_COUNT; i++) {
            probe = insertItem(i, probe, true);
            if (probe.isEmpty()) {
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
