package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Locks in the J-series fluid stack wiring across every shipped Slime Milk
 * variant. Parameterized on {@link PFFluidTypes#VARIANTS} so adding a new
 * variant only requires editing that list — the tests automatically extend
 * coverage to the new entry.
 *
 * <p>Per-variant asserts: FluidType registration, Source / Flowing pair,
 * source↔flowing pairing (so scoop + flow-decay work), LiquidBlock wrapping
 * the source, BucketItem present.
 *
 * <p>J3+ (milker block, source-block spawning, depletion) gets its own test
 * file; this one stays scoped to registration correctness.
 */
class PFFluidsTest {

    /** Provides variant names for {@link ParameterizedTest} via {@link MethodSource}. */
    static java.util.stream.Stream<String> variants() {
        return PFFluidTypes.VARIANTS.stream();
    }

    @ParameterizedTest
    @MethodSource("variants")
    void milkFluidTypeIsRegistered(String variant) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant + "_slime_milk");
        FluidType type = NeoForgeRegistries.FLUID_TYPES.get(id);
        assertNotNull(type, id + " must be registered as a FluidType");
        assertSame(PFFluidTypes.BY_VARIANT.get(variant).get(), type,
            "DeferredHolder must resolve to the registered FluidType");
    }

    @ParameterizedTest
    @MethodSource("variants")
    void sourceAndFlowingFluidsAreRegistered(String variant) {
        ResourceLocation sourceId = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant + "_slime_milk");
        ResourceLocation flowingId = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant + "_slime_milk_flowing");

        Fluid source = BuiltInRegistries.FLUID.get(sourceId);
        Fluid flowing = BuiltInRegistries.FLUID.get(flowingId);

        assertNotNull(source, sourceId + " must be registered as a Fluid");
        assertNotNull(flowing, flowingId + " must be registered as a Fluid");
        assertTrue(source instanceof BaseFlowingFluid.Source,
            "source must be a BaseFlowingFluid.Source");
        assertTrue(flowing instanceof BaseFlowingFluid.Flowing,
            "flowing must be a BaseFlowingFluid.Flowing");
    }

    @ParameterizedTest
    @MethodSource("variants")
    void sourceAndFlowingPointAtEachOther(String variant) {
        FlowingFluid source = PFFluids.BY_VARIANT.get(variant).source().get();
        FlowingFluid flowing = PFFluids.BY_VARIANT.get(variant).flowing().get();

        assertSame(source, source.getSource(), "source.getSource() must return itself");
        assertSame(flowing, source.getFlowing(), "source.getFlowing() must return the flowing variant");
        assertSame(source, flowing.getSource(), "flowing.getSource() must return the source variant");
        assertSame(flowing, flowing.getFlowing(), "flowing.getFlowing() must return itself");
    }

    @ParameterizedTest
    @MethodSource("variants")
    void liquidBlockIsRegistered(String variant) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant + "_slime_milk");
        // BuiltInRegistries.BLOCK is defaulted (returns AIR for missing ids),
        // so assertNotNull alone wouldn't catch a missing registration. Compare
        // identity against the deferred holder to confirm the id actually
        // resolves to OUR block.
        assertSame(PFBlocks.MILK_BLOCKS.get(variant).get(),
            BuiltInRegistries.BLOCK.get(id),
            id + " must be registered to the matching PFBlocks holder (not vanilla default)");
        assertTrue(PFBlocks.MILK_BLOCKS.get(variant).get() instanceof LiquidBlock,
            variant + " milk block must be a LiquidBlock");
    }

    @ParameterizedTest
    @MethodSource("variants")
    void bucketItemIsRegistered(String variant) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variant + "_slime_milk_bucket");
        // BuiltInRegistries.ITEM is defaulted (returns AIR item for missing ids).
        // Compare identity to catch a missing registration.
        assertSame(PFItems.MILK_BUCKETS.get(variant).get(),
            BuiltInRegistries.ITEM.get(id),
            id + " must be registered to the matching PFItems holder (not vanilla default)");
        assertTrue(PFItems.MILK_BUCKETS.get(variant).get() instanceof BucketItem,
            variant + " milk bucket must be a BucketItem");
    }

    @Test
    void allShippedVariantsAreCovered() {
        // Sanity-check the size of the variant family. If someone removes an
        // entry from VARIANTS this catches it before the per-variant tests
        // silently lose coverage. Update this count when intentionally adding
        // a new variant; v1.1 ships 37 (12 v1.0 + 23 v1.1 resource variants +
        // vanilla + magma specials).
        assertEquals(37, PFFluidTypes.VARIANTS.size(),
            "Slime Milk family must have 37 variants (see docs/farming.md)");
    }
}
