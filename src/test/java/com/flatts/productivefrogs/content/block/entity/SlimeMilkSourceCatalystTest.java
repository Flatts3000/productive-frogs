package com.flatts.productivefrogs.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the catalyst-driven spawn economy on
 * {@link SlimeMilkSourceBlockEntity} (docs/slime_milk_catalysts.md). The counter
 * moved off the blockstate onto the BE so Count catalysts can raise it without
 * bound; these tests pin the seed/apply/cap/restore arithmetic directly on a
 * bare BE (no level needed - {@code setChanged()} is a no-op without one).
 *
 * <p>Config isn't loaded in the bare unit JVM, so the {@code PFConfig} accessors
 * return their compile-time defaults (countPer=16, maxSpeed=4, maxQuantity=3) -
 * which is exactly the deterministic baseline these assertions encode.
 */
class SlimeMilkSourceCatalystTest {

    private static SlimeMilkSourceBlockEntity newSource() {
        // The single Slime Milk source block's default state (26.1 R-1); the BE
        // economy is variant-agnostic, so no variant needs to be stamped here.
        return new SlimeMilkSourceBlockEntity(
            BlockPos.ZERO,
            PFBlocks.SLIME_MILK_SOURCE.get().defaultBlockState());
    }

    @Test
    void seedsConfiguredDefaultCount() {
        SlimeMilkSourceBlockEntity be = newSource();
        be.seedIfUnset();
        int expected = PFConfig.SPEC.isLoaded()
            ? PFConfig.DEPLETION_COUNT.get()
            : com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING;
        assertEquals(expected, be.getSpawnsRemaining(), "fresh source seeds the configured default budget");
    }

    @Test
    void countCatalystAddsConfiguredAmountAndIsUncapped() {
        SlimeMilkSourceBlockEntity be = newSource();
        be.seedIfUnset();
        int before = be.getSpawnsRemaining();
        assertTrue(be.applyCatalyst(MilkCatalyst.COUNT), "count always applies below the storage ceiling");
        assertEquals(before + PFConfig.catalystCountPer(), be.getSpawnsRemaining(), "count adds the configured chunk");
        // Keep stacking - count has no design cap, only the overflow-guard ceiling.
        assertTrue(be.applyCatalyst(MilkCatalyst.COUNT));
        assertEquals(before + 2 * PFConfig.catalystCountPer(), be.getSpawnsRemaining());
    }

    @Test
    void speedCatalystCapsAtMaxAndStopsConsuming() {
        SlimeMilkSourceBlockEntity be = newSource();
        int max = PFConfig.catalystMaxSpeedLevel();
        for (int i = 1; i <= max; i++) {
            assertTrue(be.applyCatalyst(MilkCatalyst.SPEED), "speed applies up to the cap (level " + i + ")");
            assertEquals(i, be.getSpeedLevel());
        }
        assertFalse(be.applyCatalyst(MilkCatalyst.SPEED), "a maxed speed catalyst is not consumed");
        assertEquals(max, be.getSpeedLevel(), "speed stays at the cap");
    }

    @Test
    void quantityCatalystCapsAtMaxAndStopsConsuming() {
        SlimeMilkSourceBlockEntity be = newSource();
        int max = PFConfig.catalystMaxQuantityLevel();
        for (int i = 1; i <= max; i++) {
            assertTrue(be.applyCatalyst(MilkCatalyst.QUANTITY));
            assertEquals(i, be.getQuantityLevel());
        }
        assertFalse(be.applyCatalyst(MilkCatalyst.QUANTITY), "a maxed quantity catalyst is not consumed");
        assertEquals(max, be.getQuantityLevel());
    }

    @Test
    void infiniteCatalystAppliesOnceThenIsIdempotent() {
        SlimeMilkSourceBlockEntity be = newSource();
        assertFalse(be.isInfinite());
        assertTrue(be.applyCatalyst(MilkCatalyst.INFINITE), "first infinite applies");
        assertTrue(be.isInfinite());
        assertFalse(be.applyCatalyst(MilkCatalyst.INFINITE), "already-infinite source doesn't consume another");
    }

    @Test
    void infiniteSkipsDepletion() {
        SlimeMilkSourceBlockEntity be = newSource();
        be.applyCatalyst(MilkCatalyst.INFINITE);
        be.setSpawnsRemaining(5);
        be.decrementSpawns();
        assertEquals(5, be.getSpawnsRemaining(), "infinite sources never decrement");
    }

    @Test
    void decrementFloorsAtZero() {
        SlimeMilkSourceBlockEntity be = newSource();
        be.setSpawnsRemaining(1);
        be.decrementSpawns();
        assertEquals(0, be.getSpawnsRemaining());
        be.decrementSpawns();
        assertEquals(0, be.getSpawnsRemaining(), "decrement never goes negative");
    }

    @Test
    void restoreUpgradesClampsToConfigBounds() {
        SlimeMilkSourceBlockEntity be = newSource();
        be.restoreUpgrades(999, 1500, 999, 999, true);
        assertEquals(999, be.getSpawnsRemaining(), "remaining restored as-is (below the storage ceiling)");
        assertEquals(1500, be.getSpawnsCapacity(), "capacity restored as-is");
        assertEquals(PFConfig.catalystMaxSpeedLevel(), be.getSpeedLevel(), "speed clamped to max");
        assertEquals(PFConfig.catalystMaxQuantityLevel(), be.getQuantityLevel(), "quantity clamped to max");
        assertTrue(be.isInfinite());
    }

    @Test
    void capacityTracksHighWaterMarkNotRemaining() {
        SlimeMilkSourceBlockEntity be = newSource();
        be.seedIfUnset();
        int base = be.getSpawnsRemaining();
        assertEquals(base, be.getSpawnsCapacity(), "fresh source: capacity == seeded budget");
        // A Count catalyst raises capacity alongside remaining.
        be.applyCatalyst(MilkCatalyst.COUNT);
        int grown = base + PFConfig.catalystCountPer();
        assertEquals(grown, be.getSpawnsRemaining());
        assertEquals(grown, be.getSpawnsCapacity(), "count catalyst raises capacity too");
        // Draining lowers remaining but NOT capacity (the denominator stays put).
        be.decrementSpawns();
        be.decrementSpawns();
        assertEquals(grown - 2, be.getSpawnsRemaining());
        assertEquals(grown, be.getSpawnsCapacity(), "capacity holds as the source drains");
    }
}
