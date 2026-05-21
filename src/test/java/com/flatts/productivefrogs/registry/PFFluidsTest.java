package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;

/**
 * Locks in the J1 fluid stack wiring for the iron_slime_milk variant. Asserts
 * the FluidType registration, the Source / Flowing pair, the LiquidBlock that
 * wraps the source, and the BucketItem — so a future refactor that drops any
 * one of these layers fails CI before a player runs into a "fluid is purple
 * and black" or "bucket can't scoop the fluid back" regression.
 *
 * <p>J2 expansion to the other 13 variants should mirror this test shape so
 * the same coverage applies across the whole milk family.
 */
class PFFluidsTest {

    @Test
    void ironSlimeMilkFluidTypeIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk");
        FluidType type = NeoForgeRegistries.FLUID_TYPES.getValue(id);
        assertNotNull(type, id + " must be registered as a FluidType");
        assertSame(PFFluidTypes.IRON_SLIME_MILK.get(), type, "DeferredHolder must resolve to the registered FluidType");
    }

    @Test
    void ironSlimeMilkSourceAndFlowingFluidsAreRegistered() {
        Identifier sourceId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk");
        Identifier flowingId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk_flowing");

        Fluid source = BuiltInRegistries.FLUID.getValue(sourceId);
        Fluid flowing = BuiltInRegistries.FLUID.getValue(flowingId);

        assertNotNull(source, sourceId + " must be registered as a Fluid");
        assertNotNull(flowing, flowingId + " must be registered as a Fluid");
        assertTrue(source instanceof BaseFlowingFluid.Source,
            "source must be a BaseFlowingFluid.Source (NeoForge helper for FlowingFluid wiring)");
        assertTrue(flowing instanceof BaseFlowingFluid.Flowing,
            "flowing must be a BaseFlowingFluid.Flowing");
    }

    @Test
    void sourceAndFlowingPointAtEachOther() {
        // The BaseFlowingFluid.Properties wires getSource/getFlowing via lazy
        // Suppliers — this asserts the two-way pairing landed correctly so a
        // bucket scoop on the source produces the right fluid back, and a
        // tick on the flowing variant resolves to the right source for the
        // "flow back to source" decay logic.
        FlowingFluid source = PFFluids.IRON_SLIME_MILK_SOURCE.get();
        FlowingFluid flowing = PFFluids.IRON_SLIME_MILK_FLOWING.get();

        assertSame(source, source.getSource(), "source.getSource() must return itself");
        assertSame(flowing, source.getFlowing(), "source.getFlowing() must return the flowing variant");
        assertSame(source, flowing.getSource(), "flowing.getSource() must return the source variant");
        assertSame(flowing, flowing.getFlowing(), "flowing.getFlowing() must return itself");
    }

    @Test
    void ironSlimeMilkLiquidBlockIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk");
        assertNotNull(BuiltInRegistries.BLOCK.getValue(id), id + " must be registered as a Block");
        assertTrue(PFBlocks.IRON_SLIME_MILK.get() instanceof LiquidBlock,
            "iron_slime_milk block must be a LiquidBlock — that's the in-world wrapper for a fluid");
    }

    @Test
    void ironSlimeMilkBucketIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk_bucket");
        assertNotNull(BuiltInRegistries.ITEM.getValue(id), id + " must be registered as an Item");
        assertTrue(PFItems.IRON_SLIME_MILK_BUCKET.get() instanceof BucketItem,
            "iron_slime_milk_bucket item must be a BucketItem so vanilla pickup/place behavior works");
    }
}
