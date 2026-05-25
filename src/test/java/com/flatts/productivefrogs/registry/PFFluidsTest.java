package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;

/**
 * Locks the <b>single</b> Slime Milk fluid stack: one {@code slime_milk}
 * FluidType, one Source + Flowing pair, one source block, one bucket item.
 *
 * <p>There is deliberately no per-variant parameterization here: variant
 * identity is data-driven (carried on the bucket's {@code SLIME_VARIANT}
 * component and the source block's BlockEntity), not on distinct registrations.
 * That collapse is what lets a datapack-added variant get milk with no Java edit
 * - see {@code docs/refactor_data_driven_variants.md}.
 */
class PFFluidsTest {

    @Test
    void milkFluidTypeIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk");
        FluidType type = NeoForgeRegistries.FLUID_TYPES.get(id);
        assertNotNull(type, "slime_milk must be registered as a FluidType");
        assertSame(PFFluidTypes.SLIME_MILK.get(), type, "holder must resolve to the registered FluidType");
    }

    @Test
    void sourceAndFlowingFluidsAreRegistered() {
        Fluid source = BuiltInRegistries.FLUID.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk"));
        Fluid flowing = BuiltInRegistries.FLUID.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk_flowing"));
        assertTrue(source instanceof BaseFlowingFluid.Source, "source must be a BaseFlowingFluid.Source");
        assertTrue(flowing instanceof BaseFlowingFluid.Flowing, "flowing must be a BaseFlowingFluid.Flowing");
    }

    @Test
    void sourceAndFlowingPointAtEachOther() {
        FlowingFluid source = PFFluids.SLIME_MILK_SOURCE.get();
        FlowingFluid flowing = PFFluids.SLIME_MILK_FLOWING.get();
        assertSame(source, source.getSource(), "source.getSource() must return itself");
        assertSame(flowing, source.getFlowing(), "source.getFlowing() must return the flowing variant");
        assertSame(source, flowing.getSource(), "flowing.getSource() must return the source variant");
        assertSame(flowing, flowing.getFlowing(), "flowing.getFlowing() must return itself");
    }

    @Test
    void sourceBlockIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk");
        assertSame(PFBlocks.SLIME_MILK_SOURCE.get(), BuiltInRegistries.BLOCK.get(id),
            "slime_milk block must resolve to the PFBlocks holder (not vanilla default)");
        assertTrue(PFBlocks.SLIME_MILK_SOURCE.get() instanceof SlimeMilkSourceBlock,
            "slime_milk block must be a SlimeMilkSourceBlock");
    }

    @Test
    void bucketItemIsRegistered() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk_bucket");
        assertSame(PFItems.SLIME_MILK_BUCKET.get(), BuiltInRegistries.ITEM.get(id),
            "slime_milk_bucket must resolve to the PFItems holder (not vanilla default)");
        assertTrue(PFItems.SLIME_MILK_BUCKET.get() instanceof SlimeMilkBucketItem,
            "slime_milk_bucket must be a SlimeMilkBucketItem");
    }
}
