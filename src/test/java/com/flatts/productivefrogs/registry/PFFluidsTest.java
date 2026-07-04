package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.TestRegistryUtil;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.fluid.SlimeMilkFluid;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FlowingFluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The single component-carrying Slime Milk fluid stack (26.1 R-1). There are no
 * per-variant milk fluids any more ({@code PFVariantMilk}, deleted): one
 * {@code slime_milk} FluidType + source/flowing fluid + source block + bucket, and
 * the variant rides the {@code SLIME_VARIANT} component (the 26.1 transfer API
 * preserves it through automation). This pins the single registrations and that a
 * bucket stamped with a variant reads it back.
 */
class PFFluidsTest {

    private static final Identifier IRON =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

    @BeforeAll
    static void bindComponents() {
        TestRegistryUtil.bindComponents();
    }

    @Test
    void singleFluidTypeIsRegistered() {
        FluidType type = NeoForgeRegistries.FLUID_TYPES.getValue(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk"));
        assertNotNull(type, "slime_milk FluidType must be registered");
        assertSame(PFFluidTypes.SLIME_MILK_TYPE.get(), type, "accessor and registry must agree");
    }

    @Test
    void sourceAndFlowingPointAtEachOther() {
        FlowingFluid source = PFFluids.SLIME_MILK.get();
        FlowingFluid flowing = (FlowingFluid) source.getFlowing();
        assertTrue(source instanceof BaseFlowingFluid.Source, "source must be a BaseFlowingFluid.Source");
        assertTrue(source instanceof SlimeMilkFluid.Source, "source must be a SlimeMilkFluid.Source");
        assertTrue(flowing instanceof BaseFlowingFluid.Flowing, "flowing must be a BaseFlowingFluid.Flowing");
        assertSame(source, source.getSource(), "source.getSource() must return itself");
        assertSame(source, flowing.getSource(), "flowing.getSource() must return the source");
        assertSame(flowing, flowing.getFlowing(), "flowing.getFlowing() must return itself");
        assertSame(PFFluids.SLIME_MILK_FLOWING.get(), flowing, "flowing accessor and registry must agree");
    }

    @Test
    void singleSourceBlockIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk_source");
        assertTrue(PFBlocks.SLIME_MILK_SOURCE.get() instanceof SlimeMilkSourceBlock,
            "slime_milk_source block must be a SlimeMilkSourceBlock");
        assertSame(PFBlocks.SLIME_MILK_SOURCE.get(), BuiltInRegistries.BLOCK.getValue(id),
            "slime_milk_source block must resolve to the PFBlocks holder");
    }

    @Test
    void singleBucketItemIsRegistered() {
        Identifier id = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "slime_milk_bucket");
        assertSame(PFItems.SLIME_MILK_BUCKET.get(), BuiltInRegistries.ITEM.getValue(id),
            "slime_milk_bucket must resolve to the PFItems holder");
        assertTrue(PFItems.SLIME_MILK_BUCKET.get() instanceof SlimeMilkBucketItem,
            "the milk bucket must be a SlimeMilkBucketItem");
    }

    @Test
    void bucketRoundTripsVariantThroughComponent() {
        // The variant rides the SLIME_VARIANT component (not the fluid/item identity),
        // so a bucket stamped for iron must read iron back - the mechanism the whole
        // single-fluid collapse rests on.
        ItemStack bucket = SlimeMilkBucketItem.forVariant(IRON);
        assertSame(PFItems.SLIME_MILK_BUCKET.get(), bucket.getItem(),
            "forVariant must mint the single slime_milk_bucket item");
        assertEquals(IRON, SlimeMilkBucketItem.variantOf(bucket),
            "the stamped variant must read back off the bucket");
    }
}
