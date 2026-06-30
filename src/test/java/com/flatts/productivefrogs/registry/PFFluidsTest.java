package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.Test;

/**
 * Per-variant Slime Milk fluid stack (v1.8). There is no single {@code slime_milk}
 * fluid any more: each variant gets its own FluidType + source/flowing fluid +
 * source block + bucket, minted at mod-init by {@link PFVariantMilk}. A variant's
 * milk being a distinct {@code Fluid} is what lets tank/pipe mods preserve the
 * variant through automation. This pins the relationships for a representative
 * first-party variant (iron); {@link PFVariantMilkTest} covers discovery + counts.
 */
class PFFluidsTest {

    private static final Identifier IRON =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

    @Test
    void perVariantFluidTypeIsRegistered() {
        FluidType type = NeoForgeRegistries.FLUID_TYPES.getValue(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk"));
        assertNotNull(type, "iron_slime_milk FluidType must be registered");
        assertSame(PFVariantMilk.fluidType(IRON), type, "accessor and registry must agree");
    }

    @Test
    void sourceAndFlowingPointAtEachOther() {
        FlowingFluid source = PFVariantMilk.sourceFluid(IRON);
        FlowingFluid flowing = (FlowingFluid) source.getFlowing();
        assertTrue(source instanceof BaseFlowingFluid.Source, "source must be a BaseFlowingFluid.Source");
        assertTrue(flowing instanceof BaseFlowingFluid.Flowing, "flowing must be a BaseFlowingFluid.Flowing");
        assertSame(source, source.getSource(), "source.getSource() must return itself");
        assertSame(source, flowing.getSource(), "flowing.getSource() must return the source variant");
        assertSame(flowing, flowing.getFlowing(), "flowing.getFlowing() must return itself");
    }

    @Test
    void sourceBlockIsRegisteredAndCarriesVariant() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk");
        SlimeMilkSourceBlock block = PFVariantMilk.block(IRON);
        assertSame(block, BuiltInRegistries.BLOCK.getValue(id), "iron_slime_milk block must resolve to the PFVariantMilk holder");
        assertEquals(IRON, block.blockVariant(), "block must carry its variant baked in at registration");
    }

    @Test
    void bucketItemIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk_bucket");
        assertSame(PFVariantMilk.bucket(IRON), BuiltInRegistries.ITEM.getValue(id),
            "iron_slime_milk_bucket must resolve to the PFVariantMilk holder");
        assertTrue(PFVariantMilk.bucket(IRON) instanceof SlimeMilkBucketItem,
            "per-variant milk bucket must be a SlimeMilkBucketItem");
    }
}
