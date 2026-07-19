package com.flatts.productivefrogs.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.TestRegistryUtil;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bare-inventory coverage for the Virtual Terrarium's slot model, for the logic
 * that needs no datapack tags: the frog-slot rejection, the upgrade counters, and
 * the output-row bookkeeping (pushOutput bypassing the isItemValid guard, capacity
 * / fullness / distribution). The tag-gated upgrade validation + Smelter/Melter
 * exclusion need the loaded datapack, so they are the {@code vt_upgrade_slot_validation}
 * GameTest; the level/entity production paths are the other in-world GameTests.
 */
class VirtualTerrariumInventoryTest {

    @BeforeAll
    static void bindComponents() {
        TestRegistryUtil.bindComponents();
    }

    private static VirtualTerrariumInventory inv() {
        return new VirtualTerrariumInventory(slot -> { });
    }

    // NOTE: the bare JUnit bootstrap binds EMPTY component maps (TestRegistryUtil),
    // so every item reports getMaxStackSize() == 1 here. That happens to match real
    // Froglights (max-stack-1), and still exercises every pushOutput branch (empty
    // fill, merge-rejection, full detection, leftover). Batch-stacking of 64-stackable
    // predator loot is covered in-world by the production GameTests.
    private static ItemStack one() {
        return new ItemStack(Items.COBBLESTONE, 1);
    }

    // -- frog slot --

    @Test
    void frogSlotRejectsEmptyNetAndPlainItems() {
        VirtualTerrariumInventory inv = inv();
        assertFalse(inv.isItemValid(VirtualTerrariumInventory.FROG_SLOT, new ItemStack(PFItems.FROG_NET.get())),
            "an EMPTY Frog Net is not a loaded frog");
        assertFalse(inv.isItemValid(VirtualTerrariumInventory.FROG_SLOT, new ItemStack(Items.COBBLESTONE)),
            "a non-net item is not a frog");
    }

    // -- upgrade counters (set via setStackInSlot, which bypasses the tag-gated isItemValid) --

    @Test
    void countAndHasUpgradeTrackInstalledStacks() {
        VirtualTerrariumInventory inv = inv();
        assertFalse(inv.hasUpgrade(PFItems.VT_UPGRADE_BOUNTY.get()));
        assertEquals(0, inv.countUpgrade(PFItems.VT_UPGRADE_BOUNTY.get()));

        inv.setStackInSlot(VirtualTerrariumInventory.UPGRADE_START, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get()));
        inv.setStackInSlot(VirtualTerrariumInventory.UPGRADE_START + 1, new ItemStack(PFItems.VT_UPGRADE_BOUNTY.get()));

        assertTrue(inv.hasUpgrade(PFItems.VT_UPGRADE_BOUNTY.get()));
        assertEquals(2, inv.countUpgrade(PFItems.VT_UPGRADE_BOUNTY.get()));
        assertEquals(0, inv.countUpgrade(PFItems.VT_UPGRADE_OVERCLOCK.get()));
    }

    // -- output row bookkeeping --

    @Test
    void outputSlotsRejectGuiInsertButPushOutputBypasses() {
        VirtualTerrariumInventory inv = inv();
        assertFalse(inv.isItemValid(VirtualTerrariumInventory.OUTPUT_START, one()),
            "output slots must reject GUI / hopper inserts");
        ItemStack leftover = inv.pushOutput(one());
        assertTrue(leftover.isEmpty(), "pushOutput must place the item past the isItemValid guard");
        assertEquals(1, inv.getStackInSlot(VirtualTerrariumInventory.OUTPUT_START).getCount());
        assertEquals(VirtualTerrariumInventory.OUTPUT_COUNT - 1, inv.emptyOutputSlots());
    }

    @Test
    void freshInventoryHasFullOutputRoom() {
        VirtualTerrariumInventory inv = inv();
        assertEquals(VirtualTerrariumInventory.OUTPUT_COUNT, inv.emptyOutputSlots());
        assertFalse(inv.outputFull(one()), "a fresh inventory is not output-full");
        assertEquals(VirtualTerrariumInventory.OUTPUT_COUNT, inv.outputCapacity(one()),
            "a fresh inventory holds one per output slot");
    }

    @Test
    void jammedOutputReportsFull() {
        VirtualTerrariumInventory inv = inv();
        for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
            inv.setStackInSlot(VirtualTerrariumInventory.OUTPUT_START + i, new ItemStack(Items.GRAVEL, 1));
        }
        assertEquals(0, inv.emptyOutputSlots());
        assertTrue(inv.outputFull(one()), "every slot occupied by a non-mergeable item -> full");
        assertEquals(0, inv.outputCapacity(one()), "no room for a non-mergeable item");
    }

    @Test
    void pushOutputFillsEverySlotThenReturnsLeftover() {
        VirtualTerrariumInventory inv = inv();
        for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
            assertTrue(inv.pushOutput(one()).isEmpty(), "item " + i + " fits an empty slot");
        }
        assertEquals(0, inv.emptyOutputSlots(), "all six output slots occupied");
        ItemStack leftover = inv.pushOutput(one());
        assertFalse(leftover.isEmpty(), "a full output row must return the overflow rather than void it");
        assertEquals(1, leftover.getCount());
    }

    // -- item-agnostic backpressure gate (outputFull) --

    @Test
    void outputFullTrueOnlyWhenEverySlotAtMax() {
        VirtualTerrariumInventory inv = inv();
        assertFalse(inv.outputFull(), "a fresh grid is not full");
        for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
            inv.setStackInSlot(VirtualTerrariumInventory.OUTPUT_START + i, new ItemStack(Items.GRAVEL, 1));
        }
        assertTrue(inv.outputFull(), "every slot at max (1 here) -> full");
        inv.setStackInSlot(VirtualTerrariumInventory.OUTPUT_START, ItemStack.EMPTY);
        assertFalse(inv.outputFull(), "one empty slot -> not full");
    }

    // -- merge-aware multi-drop fit (canFitAll) --

    @Test
    void canFitAllAccountsForEmptySlotsAndMultipleDrops() {
        VirtualTerrariumInventory inv = inv();
        // 6 empty slots, drops are max-stack-1 in the bare test context.
        assertTrue(inv.canFitAll(java.util.List.of(one(), one(), one())), "3 drops fit 6 empty slots");
        assertTrue(inv.canFitAll(java.util.List.of(one(), one(), one(), one(), one(), one())), "6 drops fill 6 slots");
        assertFalse(inv.canFitAll(java.util.List.of(one(), one(), one(), one(), one(), one(), one())),
            "7 max-stack-1 drops do not fit 6 slots");
    }

    @Test
    void canFitAllRejectsNonMergeableWhenNoEmptySlot() {
        VirtualTerrariumInventory inv = inv();
        for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
            inv.setStackInSlot(VirtualTerrariumInventory.OUTPUT_START + i, new ItemStack(Items.GRAVEL, 1));
        }
        assertFalse(inv.canFitAll(java.util.List.of(one())),
            "a cobblestone drop cannot fit a grid full of maxed gravel stacks");
        inv.setStackInSlot(VirtualTerrariumInventory.OUTPUT_START, ItemStack.EMPTY);
        assertTrue(inv.canFitAll(java.util.List.of(one())), "one empty slot accepts the drop");
    }
}
