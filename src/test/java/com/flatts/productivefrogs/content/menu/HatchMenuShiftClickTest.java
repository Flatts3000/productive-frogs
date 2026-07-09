package com.flatts.productivefrogs.content.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

/**
 * Regression coverage for the Hatch shift-click dupe: shift-clicking a stackable
 * item (a Froglight) in the Hatch GUI doubled the stack because
 * {@link HatchMenu#quickMoveStack} moved the stack into a destination range that
 * INCLUDED the source slot, so vanilla {@code moveItemStackTo} merged the stack
 * into its own slot ({@code count + count}).
 *
 * <p>These are pure-arithmetic tests over {@link HatchMenu#shiftClickDestRange}
 * (no Minecraft bootstrap - matches {@code FrogStatsTest}). The load-bearing
 * invariant is {@link #destRangeNeverContainsTheSourceSlot()}: a destination
 * range that never contains the source is exactly what makes the self-merge
 * impossible. The end-to-end player shift-click is pinned by the in-world
 * GameTest.
 *
 * <p>Layout: slots 0..17 are the output Hatch inventory, 18..44 the player main
 * rows, 45..53 the hotbar (54 slots total).
 */
class HatchMenuShiftClickTest {

    private static final int HATCH_SLOTS = 18;
    private static final int MAIN_END = HATCH_SLOTS + 27; // 45
    private static final int PLAYER_END = HATCH_SLOTS + 36; // 54

    /**
     * The dupe invariant: for EVERY slot, the shift-click destination range must
     * exclude the source slot itself. A source inside its own destination range
     * is what lets {@code moveItemStackTo} merge the stack into itself and double
     * it.
     */
    @Test
    void destRangeNeverContainsTheSourceSlot() {
        for (int source = 0; source < PLAYER_END; source++) {
            int[] range = HatchMenu.shiftClickDestRange(source);
            int start = range[0];
            int end = range[1];
            boolean sourceInRange = source >= start && source < end;
            assertFalse(sourceInRange,
                "shift-click from slot " + source + " must not target a range containing itself,"
                    + " but got [" + start + ", " + end + ") - this is the froglight dupe");
        }
    }

    /** A Hatch (output) slot shift-clicks into the whole player region. */
    @Test
    void hatchSlotMovesIntoWholePlayerRegion() {
        for (int source = 0; source < HATCH_SLOTS; source++) {
            assertArrayEquals(new int[] {HATCH_SLOTS, PLAYER_END},
                HatchMenu.shiftClickDestRange(source),
                "hatch slot " + source + " -> whole player region [18, 54)");
        }
    }

    /** A main-inventory slot shuffles into the hotbar (never itself). */
    @Test
    void mainInventorySlotMovesIntoHotbar() {
        for (int source = HATCH_SLOTS; source < MAIN_END; source++) {
            assertArrayEquals(new int[] {MAIN_END, PLAYER_END},
                HatchMenu.shiftClickDestRange(source),
                "main slot " + source + " -> hotbar [45, 54)");
        }
    }

    /** A hotbar slot shuffles into the main inventory rows (never itself). */
    @Test
    void hotbarSlotMovesIntoMainInventory() {
        for (int source = MAIN_END; source < PLAYER_END; source++) {
            assertArrayEquals(new int[] {HATCH_SLOTS, MAIN_END},
                HatchMenu.shiftClickDestRange(source),
                "hotbar slot " + source + " -> main rows [18, 45)");
        }
    }
}
