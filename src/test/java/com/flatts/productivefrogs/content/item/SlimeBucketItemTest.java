package com.flatts.productivefrogs.content.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests {@link SlimeBucketItem#getName(ItemStack)} resolution. Variant wins
 * over category, category beats empty bucket, empty bucket falls through to
 * the base description key.
 *
 * <p>We assert against the translation key (not the resolved English string)
 * so the test doesn't depend on the lang file loading inside the JUnit
 * harness — the lang round-trip is its own concern; here we pin the
 * Java-side resolution chain.
 */
class SlimeBucketItemTest {

    @Test
    void emptyBucketUsesBaseDescriptionId() {
        ItemStack stack = new ItemStack(PFItems.SLIME_BUCKET.get());
        // The BUCKET_ENTITY_DATA component is set to CustomData.EMPTY by
        // PFItems.SLIME_BUCKET's default properties — present but empty.
        // readVariant / readCategory return null because the inner tag has
        // no "Variant" / "Category" entry, so the resolver falls through
        // to the base description key.
        assertKey("item.productivefrogs.slime_bucket", stack);
    }

    @ParameterizedTest
    @EnumSource(Category.class)
    void categoryOnlyBucketResolvesPerCategoryKey(Category category) {
        ItemStack stack = new ItemStack(PFItems.SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putString("Category", category.name());
        });
        assertKey("item.productivefrogs.slime_bucket." + category.id(), stack);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "iron", "copper", "gold",
        "redstone", "lapis", "coal",
        "diamond", "emerald",
        "prismarine", "sponge",
        "magma_cream", "ender_pearl"
    })
    void variantStampedBucketResolvesPerVariantKey(String variantName) {
        ItemStack stack = new ItemStack(PFItems.SLIME_BUCKET.get());
        Identifier variantId = Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, variantName);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            // Real captured buckets also carry Category, but the resolver
            // reads Variant first — write only Variant here to pin the
            // variant-wins-over-category contract.
            tag.putString("Variant", variantId.toString());
        });
        assertKey("item.productivefrogs.slime_bucket." + variantName, stack);
    }

    @Test
    void variantWinsOverCategoryWhenBothPresent() {
        // Real captured buckets carry both. The resolver must prefer the
        // more specific variant key — otherwise iron / copper / gold
        // METALLIC slimes would all read "Bucket of Metallic Slime".
        ItemStack stack = new ItemStack(PFItems.SLIME_BUCKET.get());
        Identifier variantId =
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "copper");
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putString("Category", Category.METALLIC.name());
            tag.putString("Variant", variantId.toString());
        });
        assertKey("item.productivefrogs.slime_bucket.copper", stack);
    }

    private static void assertKey(String expectedKey, ItemStack stack) {
        Component name = PFItems.SLIME_BUCKET.get().getName(stack);
        if (!(name instanceof MutableComponent mutable
            && mutable.getContents() instanceof TranslatableContents translatable)) {
            throw new AssertionError("expected a translatable component, got " + name.getClass().getName());
        }
        assertEquals(expectedKey, translatable.getKey());
    }
}
