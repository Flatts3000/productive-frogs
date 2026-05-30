package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.fluid.SlimeMilkFluid;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.Item;
import org.junit.jupiter.api.Test;

/**
 * Keystone spike (v1.8 per-variant milk): proves that variant ids discovered at
 * mod-init by {@link com.flatts.productivefrogs.setup.VariantFluidDiscovery} are
 * dynamically minted as real per-variant fluids/blocks/buckets in the frozen
 * built-in registries. If this passes, constructor-time dynamic registration
 * works and the rest of the refactor rests on a verified mechanism.
 */
class PFVariantMilkTest {

    private static final ResourceLocation IRON =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron");

    @Test
    void ironVariantMilkFluidIsDynamicallyRegistered() {
        assertTrue(PFVariantMilk.isRegistered(IRON),
            "iron must be discovered + registered as a per-variant milk fluid");
        Fluid source = BuiltInRegistries.FLUID.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk"));
        assertTrue(source instanceof SlimeMilkFluid.Source,
            "iron_slime_milk must be a SlimeMilkFluid.Source in BuiltInRegistries.FLUID");
        assertSame(PFVariantMilk.sourceFluid(IRON), source, "accessor and registry must agree");
    }

    @Test
    void ironVariantMilkBlockCarriesItsVariant() {
        Block block = BuiltInRegistries.BLOCK.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk"));
        assertTrue(block instanceof SlimeMilkSourceBlock, "iron_slime_milk block must be a SlimeMilkSourceBlock");
        assertEquals(IRON, ((SlimeMilkSourceBlock) block).blockVariant(),
            "block must know its variant from registration (so JDT-placed sources spawn correctly)");
    }

    @Test
    void ironVariantMilkBucketIsRegistered() {
        Item bucket = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "iron_slime_milk_bucket"));
        assertNotNull(bucket, "iron_slime_milk_bucket must be registered");
        assertTrue(bucket instanceof BucketItem, "per-variant bucket must be a vanilla BucketItem");
        assertSame(PFVariantMilk.bucket(IRON), bucket, "accessor and registry must agree");
    }

    @Test
    void registeredVariantCountIsNonTrivial() {
        // At least the 40 first-party variants (no mod_loaded gate) must register
        // in the bare test JVM, where cross-mod source mods are absent.
        assertTrue(PFVariantMilk.registeredVariants().size() >= 40,
            "expected >=40 first-party milk fluids, got " + PFVariantMilk.registeredVariants().size());
    }
}
