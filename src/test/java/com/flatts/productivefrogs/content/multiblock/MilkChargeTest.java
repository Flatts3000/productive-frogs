package com.flatts.productivefrogs.content.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.TestRegistryUtil;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pure unit coverage for {@link MilkCharge} - the bucket-equivalent the Terrarium
 * Controller buffers. The NBT round-trip and the component read/defaulting are
 * level-free; {@code MilkSpawnEconomy.defaultSpawnCount()} returns the config
 * default (or its compile-time fallback in the bare test JVM).
 */
class MilkChargeTest {

    @BeforeAll
    static void bindComponents() {
        TestRegistryUtil.bindComponents();
    }

    @Test
    void tagRoundTripPreservesAllFields() {
        MilkCharge charge = new MilkCharge(8, 16, 2, 1, true);
        assertEquals(charge, MilkCharge.fromTag(charge.toTag()), "toTag/fromTag must round-trip");
    }

    @Test
    void bucketWithoutComponentsTakesDefaults() {
        MilkCharge charge = MilkCharge.fromBucket(new ItemStack(Items.BUCKET));
        int def = MilkSpawnEconomy.defaultSpawnCount();
        assertEquals(def, charge.spawnsRemaining(), "absent remaining -> default spawn count");
        assertEquals(def, charge.capacity(), "absent capacity -> defaults to remaining");
        assertEquals(0, charge.speed());
        assertEquals(0, charge.quantity());
        assertFalse(charge.infinite());
    }

    @Test
    void fluidReadsCatalystComponents() {
        FluidStack fluid = new FluidStack(Fluids.WATER, 1000);
        fluid.set(PFDataComponents.SPAWNS_REMAINING.get(), 5);
        fluid.set(PFDataComponents.MILK_CAPACITY.get(), 12);
        fluid.set(PFDataComponents.MILK_SPEED.get(), 3);
        fluid.set(PFDataComponents.MILK_QUANTITY.get(), 2);
        fluid.set(PFDataComponents.MILK_INFINITE.get(), true);

        MilkCharge charge = MilkCharge.fromFluid(fluid);
        assertEquals(5, charge.spawnsRemaining());
        assertEquals(12, charge.capacity());
        assertEquals(3, charge.speed());
        assertEquals(2, charge.quantity());
        assertTrue(charge.infinite());
    }

    @Test
    void capacityNeverBelowRemaining() {
        FluidStack fluid = new FluidStack(Fluids.WATER, 1000);
        fluid.set(PFDataComponents.SPAWNS_REMAINING.get(), 10); // capacity absent
        MilkCharge charge = MilkCharge.fromFluid(fluid);
        assertEquals(10, charge.spawnsRemaining());
        assertEquals(10, charge.capacity(), "capacity floors at remaining when absent");
    }
}
