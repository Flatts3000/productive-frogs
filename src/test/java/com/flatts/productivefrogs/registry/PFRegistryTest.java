package com.flatts.productivefrogs.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.data.Category;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Integration tests for our registry wiring — runs inside moddev's JUnit
 * integration so vanilla registries are loaded, our DeferredRegisters have
 * fired, and {@code BuiltInRegistries} can resolve our IDs.
 *
 * <p>These guard against the silent failure mode where a DeferredRegister
 * isn't attached to the mod event bus or an ID has a typo — both surface as
 * runtime crashes in the dev client but at well-known places that an
 * integration test catches before a manual playtest.
 */
class PFRegistryTest {

    @Test
    void frogEggItemIsRegistered() {
        Item item = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "frog_egg")
        );
        assertNotNull(item, "productivefrogs:frog_egg must be registered");
        assertEquals(PFItems.FROG_EGG.get(), item);
    }

    @Test
    void resourceTadpoleBucketIsRegistered() {
        Item item = BuiltInRegistries.ITEM.get(
            ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "resource_tadpole_bucket")
        );
        assertNotNull(item, "productivefrogs:resource_tadpole_bucket must be registered");
        assertEquals(PFItems.RESOURCE_TADPOLE_BUCKET.get(), item);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void primedFrogEggBlockRegisteredForEachCategory(Category cat) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, cat.primedEggItemName());
        Block block = BuiltInRegistries.BLOCK.get(id);
        assertNotNull(block, id + " must be registered");
        assertTrue(block instanceof PrimedFrogEggBlock, id + " must be a PrimedFrogEggBlock");
        assertEquals(cat, ((PrimedFrogEggBlock) block).getCategory());
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void primedFrogEggBlockItemRegisteredForEachCategory(Category cat) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, cat.primedEggItemName());
        Item item = BuiltInRegistries.ITEM.get(id);
        assertNotNull(item, id + " item form must be registered");
        assertTrue(item instanceof BlockItem, id + " must be a BlockItem");
    }

    @Test
    void blockAndItemBijectionHoldsForEachCategory() {
        for (Category cat : Category.values()) {
            Block block = PFBlocks.primedEgg(cat);
            assertEquals(
                block,
                ((BlockItem) PFItems.PRIMED_FROG_EGG_ITEMS.get(cat).get()).getBlock(),
                cat + " block↔item bijection must hold"
            );
        }
    }

    /**
     * Each of the 12 variant slime spawn eggs must carry the {@code SLIME_VARIANT}
     * data component on its default stack so the {@code slime_variant} ItemTintSource
     * can resolve the variant's primary colour from the datapack registry. Without
     * this component the spawn eggs would all render the (gray) JSON-default
     * fallback — the bug captured in {@code docs/known_issues.md} under the
     * "Per-variant + per-category items need JEI subtype interpreters" entry
     * (tint half).
     */
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "iron", "copper", "gold",
        "redstone", "lapis", "coal",
        "diamond", "emerald",
        "prismarine", "sponge",
        "magma_cream", "ender_pearl"
    })
    void variantSlimeSpawnEggCarriesSlimeVariantComponent(String variantName) {
        assertNotNull(PFItems.RESOURCE_SLIME_SPAWN_EGG, "the resource slime spawn egg must be registered");
        ResourceLocation expected = ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantName);
        net.minecraft.world.item.ItemStack stack = PFItems.resourceSlimeSpawnEgg(expected);
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        assertNotNull(variantId,
            variantName + " spawn-egg stack must carry SLIME_VARIANT");
        assertEquals(expected, variantId,
            variantName + " spawn-egg stack's SLIME_VARIANT id must match the variant");
    }
}
