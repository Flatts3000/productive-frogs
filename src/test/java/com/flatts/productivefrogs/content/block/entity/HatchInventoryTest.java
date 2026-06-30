package com.flatts.productivefrogs.content.block.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.TestRegistryUtil;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bare-BlockEntity coverage for the Hatch's froglight output inventory. The
 * direct-to-Hatch override + the eat-backpressure are level/entity behaviors
 * covered by the in-world GameTests; this pins the inventory bookkeeping that
 * drives the {@link HatchBlockEntity#isFull()} backpressure signal.
 */
class HatchInventoryTest {

    @BeforeAll
    static void bindComponents() {
        TestRegistryUtil.bindComponents();
    }

    private static HatchBlockEntity newHatch() {
        return new HatchBlockEntity(BlockPos.ZERO, PFBlocks.HATCH.get().defaultBlockState());
    }

    private static ItemStack froglight() {
        return new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
    }

    @Test
    void startsEmpty() {
        HatchBlockEntity h = newHatch();
        assertTrue(h.isEmpty());
        assertFalse(h.isFull());
        assertEquals(0, h.fillCount());
    }

    @Test
    void insertAcceptsFroglights() {
        HatchBlockEntity h = newHatch();
        assertTrue(h.insert(froglight()), "a froglight fits in an empty Hatch");
        assertFalse(h.isEmpty());
        assertEquals(1, h.fillCount());
    }

    @Test
    void fillForTestSaturatesEverySlot() {
        HatchBlockEntity h = newHatch();
        h.fillForTest();
        assertTrue(h.isFull(), "every slot occupied -> full (the backpressure signal)");
        assertEquals(HatchBlockEntity.SLOTS, h.fillCount());
    }
}
