package com.flatts.productivefrogs.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

/**
 * Bare-BlockEntity coverage for the Incubator's seed/growth bookkeeping. The
 * release + frog-cap path needs a live level + TerrariumManager and is covered by
 * the in-world GameTests; this pins the level-free state machine.
 */
class IncubatorBlockEntityTest {

    private static IncubatorBlockEntity newIncubator() {
        return new IncubatorBlockEntity(BlockPos.ZERO, PFBlocks.INCUBATOR.get().defaultBlockState());
    }

    @Test
    void seedSetsCategoryAndStartsTheGrowthTimer() {
        IncubatorBlockEntity i = newIncubator();
        assertTrue(i.hasRoom());
        assertTrue(i.seedBaseline(Category.CAVE));
        assertFalse(i.hasRoom(), "seeded -> no room");
        assertEquals(Category.CAVE, i.getCategory());
        assertEquals(PFConfig.hatchTicks() + PFConfig.tadpoleGrowthTicks(), i.growthTotal(),
            "growth timer = full egg->frog lifecycle (hatch + maturation), both from config");
        assertEquals(i.growthTotal(), i.growthRemaining(), "starts at the full timer");
        assertFalse(i.isWaitingForSpace());
    }

    @Test
    void onlyIncubatesOneAtATime() {
        IncubatorBlockEntity i = newIncubator();
        assertTrue(i.seedFromBreeding(Category.CAVE, 5, 7, 3));
        assertFalse(i.seedBaseline(Category.GEODE), "a second seed is refused while incubating");
        assertEquals(Category.CAVE, i.getCategory(), "the first seed is untouched");
    }

    @Test
    void sweetslimeShavesTenPercentOffWhileIncubating() {
        IncubatorBlockEntity i = newIncubator();
        i.seedBaseline(Category.CAVE);
        int before = i.growthRemaining();
        int expectedCut = Math.max(1, i.growthTotal() / 10);
        assertTrue(i.accelerateWithSweetslime(), "a sweetslime accelerates an active incubation");
        assertEquals(before - expectedCut, i.growthRemaining(), "shaves 10% of the full lifecycle");
    }

    @Test
    void sweetslimeIsNoOpWhenEmpty() {
        IncubatorBlockEntity i = newIncubator();
        assertFalse(i.accelerateWithSweetslime(), "nothing to accelerate when empty");
    }

    @Test
    void nbtRoundTripPreservesSeed() {
        IncubatorBlockEntity i = newIncubator();
        i.seedFromBreeding(Category.INFERNAL, 5, 7, 3);
        CompoundTag tag = new CompoundTag();
        i.saveAdditional(tag, null);

        IncubatorBlockEntity reloaded = newIncubator();
        reloaded.loadAdditional(tag, null);
        assertEquals(Category.INFERNAL, reloaded.getCategory());
        assertEquals(i.growthTotal(), reloaded.growthTotal());
        assertFalse(reloaded.hasRoom());
    }
}
