package com.flatts.productivefrogs.content.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;

/**
 * Variant-resolution unit tests for {@link SlimeMilkerBlock}. The milker reads
 * the input Slime Bucket's variant via {@link SlimeMilkerBlock#readBucketVariant}
 * (path, for legacy callers) / {@link SlimeMilkerBlock#readBucketVariantId} (full
 * id, used to stamp the single output milk bucket's {@code SLIME_VARIANT}
 * component). If parsing breaks, the milker silently fails closed (the player
 * keeps the slime bucket and gets nothing) — a no-op masquerading as a bug.
 * These tests pin the parsing contract so a future refactor of the NBT shape
 * can't quietly regress the appliance.
 *
 * <p>In-world behavior (player swings the bucket, block consumes it, sound
 * plays) is covered by the GameTest in {@code PFGameTests}; here we focus on
 * the data-shape contract.
 */
class SlimeMilkerBlockTest {

    @Test
    void readsVariantPathFromBucketEntityData() {
        // Mirrors what ResourceSlime.saveToBucketTag writes when the captured
        // slime carries a registered SlimeVariant: a full Identifier string
        // under the "Variant" key inside BUCKET_ENTITY_DATA. The milker pulls
        // just the path back out because PFFluidTypes.VARIANTS is keyed by
        // bare variant names (e.g. "iron", not "productivefrogs:iron").
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket,
            tag -> tag.putString("Variant", "productivefrogs:iron"));

        assertEquals("iron", SlimeMilkerBlock.readBucketVariant(bucket));
    }

    @Test
    void returnsNullWhenBucketHasNoEntityData() {
        // Empty Slime Bucket (right after crafting, before pickup) has no
        // BUCKET_ENTITY_DATA. Milker must treat this as "no variant" and
        // fail closed — the player keeps the bucket.
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        assertNull(SlimeMilkerBlock.readBucketVariant(bucket),
            "an empty Slime Bucket must not resolve to any variant");
    }

    @Test
    void returnsNullWhenVariantTagIsAbsent() {
        // Category-only slime (older save data, or a Resource Slime that
        // never received a SlimeVariant): BUCKET_ENTITY_DATA exists and
        // carries Category, but no Variant key. The milker depends on
        // Variant specifically, so this must miss — falls through to the
        // CONSUME no-op rather than picking some arbitrary default milk.
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket,
            tag -> tag.putString("Category", "BOG"));

        assertNull(SlimeMilkerBlock.readBucketVariant(bucket),
            "a bucket with Category but no Variant must not resolve");
    }

    @Test
    void returnsNullWhenVariantTagIsEmptyString() {
        // Defensive: empty-string Variant should be treated identically to
        // absent. Identifier.tryParse would barf on an empty string, but the
        // explicit isEmpty() check in readBucketVariant short-circuits before
        // that — pin the behavior so the short-circuit isn't dropped in a
        // future refactor.
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket,
            tag -> tag.putString("Variant", ""));

        assertNull(SlimeMilkerBlock.readBucketVariant(bucket));
    }

    @Test
    void returnsNullWhenVariantIdIsMalformed() {
        // "::" is unparseable as an Identifier (too many colons). Identifier
        // .tryParse returns null on malformed input; the milker must propagate
        // that as null, not throw.
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket,
            tag -> tag.putString("Variant", "::not::a::valid::id"));

        assertNull(SlimeMilkerBlock.readBucketVariant(bucket));
    }

    @Test
    void returnsNullForNonSlimeBucketItem() {
        // Sanity: a plain vanilla item with no BUCKET_ENTITY_DATA component
        // resolves to null. The milker's first check rejects non-Slime-Bucket
        // items before this is even called, but the parser itself should be
        // safe on any ItemStack — the regression risk is someone calling it
        // from a different surface (creative-tab tooltip, JEI hover) and the
        // helper exploding on unexpected input.
        ItemStack stack = new ItemStack(Items.STICK);
        assertNull(SlimeMilkerBlock.readBucketVariant(stack));
    }

    @Test
    void readsFullVariantIdForMilkStamping() {
        // The milker stamps the single output Slime Milk bucket with the FULL
        // variant id (namespace preserved) via readBucketVariantId, so a
        // cross-namespace datapack variant (e.g. mymod:adamantite) survives the
        // slime-bucket -> milk-bucket conversion. The path-only readBucketVariant
        // above would lose the namespace.
        ItemStack bucket = new ItemStack(PFItems.SLIME_BUCKET.get());
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket,
            tag -> tag.putString("Variant", "mymod:adamantite"));

        assertEquals(net.minecraft.resources.Identifier.parse("mymod:adamantite"),
            SlimeMilkerBlock.readBucketVariantId(bucket));
        assertNull(SlimeMilkerBlock.readBucketVariantId(new ItemStack(PFItems.SLIME_BUCKET.get())),
            "an empty Slime Bucket has no variant id");
    }
}
