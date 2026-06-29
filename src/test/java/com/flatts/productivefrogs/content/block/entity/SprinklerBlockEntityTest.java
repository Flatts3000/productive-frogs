package com.flatts.productivefrogs.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

/**
 * Bare-BlockEntity coverage for the Sprinkler's milk budget (the placed-source
 * economy state it shares with a hand-placed source). No level: {@code setChanged}
 * no-ops, and {@code PFConfig} returns its compile-time defaults (maxSpeed=4).
 */
class SprinklerBlockEntityTest {

    private static final Identifier IRON =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");
    private static final Identifier COPPER =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");

    private static SprinklerBlockEntity newSprinkler() {
        return new SprinklerBlockEntity(BlockPos.ZERO, PFBlocks.SPRINKLER.get().defaultBlockState());
    }

    @Test
    void emptySprinklerAcceptsAFreshCharge() {
        SprinklerBlockEntity s = newSprinkler();
        assertTrue(s.isEmpty());
        assertTrue(s.acceptsFreshCharge());
    }

    @Test
    void loadChargeStampsVariantAndClampsCatalysts() {
        SprinklerBlockEntity s = newSprinkler();
        s.loadCharge(IRON, new MilkCharge(8, 8, 99, 99, true)); // speed/quantity above the caps
        assertEquals(IRON, s.getVariantId());
        assertFalse(s.acceptsFreshCharge(), "a loaded Sprinkler is no longer fresh");
        assertEquals(PFConfig.catalystMaxSpeedLevel(), s.getSpeedLevel(), "speed clamps to the config max");
        assertTrue(s.isInfinite());
        assertEquals(8, s.getSpawnsRemaining());
    }

    @Test
    void mergeChargePoolsBudgetTakesBetterStatsOrsInfinite() {
        SprinklerBlockEntity s = newSprinkler();
        s.loadCharge(IRON, new MilkCharge(4, 4, 1, 0, false));
        s.mergeCharge(new MilkCharge(8, 8, 3, 2, true));
        assertEquals(12, s.getSpawnsRemaining(), "remaining is summed");
        assertEquals(3, s.getSpeedLevel(), "speed takes the max");
        assertTrue(s.isInfinite(), "infinite is OR'd in");
    }

    @Test
    void wantsTopUpGatesOnVariantThresholdAndInfinite() {
        SprinklerBlockEntity s = newSprinkler();
        s.loadCharge(IRON, new MilkCharge(2, 8, 0, 0, false));
        assertTrue(s.wantsTopUp(IRON, 4), "matching variant, draining below threshold");
        assertFalse(s.wantsTopUp(COPPER, 4), "wrong variant never tops up");
        assertFalse(s.wantsTopUp(IRON, 1), "remaining above the threshold");

        SprinklerBlockEntity infinite = newSprinkler();
        infinite.loadCharge(IRON, new MilkCharge(0, 8, 0, 0, true));
        assertFalse(infinite.wantsTopUp(IRON, 4), "infinite never needs topping up");
    }

    @Test
    void drainToBucketReturnsMilkAndClears() {
        SprinklerBlockEntity s = newSprinkler();
        s.loadCharge(IRON, new MilkCharge(8, 8, 2, 0, false));
        ItemStack bucket = s.drainToBucket(null, BlockPos.ZERO, PFBlocks.SPRINKLER.get().defaultBlockState());
        assertFalse(bucket.isEmpty(), "drain returns the per-variant milk bucket");
        assertTrue(s.isEmpty(), "draining clears the Sprinkler");
    }

    @Test
    void applyCatalystUpgradesHeldMilkAndRefusesMaxed() {
        SprinklerBlockEntity s = newSprinkler();
        s.loadCharge(IRON, new MilkCharge(8, 8, 0, 0, false));
        assertTrue(s.applyCatalyst(MilkCatalyst.SPEED), "speed applies");
        assertEquals(1, s.getSpeedLevel());
        assertTrue(s.applyCatalyst(MilkCatalyst.QUANTITY), "quantity applies");
        assertEquals(1, s.getQuantityLevel());
        assertTrue(s.applyCatalyst(MilkCatalyst.INFINITE), "infinite applies");
        assertTrue(s.isInfinite());
        assertFalse(s.applyCatalyst(MilkCatalyst.INFINITE), "already infinite -> refused");
    }

    @Test
    void applyCatalystIsNoOpOnAnEmptySprinkler() {
        SprinklerBlockEntity s = newSprinkler();
        assertFalse(s.applyCatalyst(MilkCatalyst.SPEED), "nothing to upgrade when empty");
    }

    @Test
    void nbtRoundTripPreservesBudget() {
        SprinklerBlockEntity s = newSprinkler();
        s.loadCharge(IRON, new MilkCharge(7, 9, 2, 1, false));
        CompoundTag tag = new CompoundTag();
        s.saveAdditional(tag, null);

        SprinklerBlockEntity reloaded = newSprinkler();
        reloaded.loadAdditional(tag, null);
        assertEquals(IRON, reloaded.getVariantId());
        assertEquals(7, reloaded.getSpawnsRemaining());
        assertEquals(2, reloaded.getSpeedLevel());
    }
}
