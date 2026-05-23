package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.material.Fluid;

/**
 * Bucket variant for {@link com.flatts.productivefrogs.content.entity.ResourceSlime}.
 * Inherits everything functional from {@link MobBucketItem} (capture/release
 * sound, fluid placement, NBT round-trip via {@code ResourceSlime#saveToBucketTag});
 * the only addition is a per-variant / per-category display name pulled from
 * the bucket's {@code BUCKET_ENTITY_DATA} payload.
 *
 * <p>Name resolution mirrors the tint pipeline in
 * {@code BucketedCategoryTint}:
 *
 * <ol>
 *   <li><b>Variant</b> — read the {@code Variant} id from the bucket NBT via
 *       {@link ResourceTadpoleBucketItem#readVariant} (works on slime buckets
 *       too — same NBT layout). Falls through to the
 *       {@code item.productivefrogs.slime_bucket.<variant_path>} translation
 *       key (e.g. {@code .iron}, {@code .copper}). Most specific match — a
 *       captured iron Resource Slime reads "Bucket of Iron Slime".</li>
 *   <li><b>Category</b> — fall back to the broader category via
 *       {@link ResourceTadpoleBucketItem#readCategory}. Catches the rare
 *       category-only slime (no variant assigned) plus the
 *       {@code BUCKET_ENTITY_DATA}-without-variant edge cases. Translation
 *       key {@code .<category_id>}.</li>
 *   <li><b>Default</b> — empty bucket / pre-component save data. Falls
 *       through to the base translation
 *       {@code item.productivefrogs.slime_bucket}.</li>
 * </ol>
 *
 * <p>The lang file ({@code assets/productivefrogs/lang/en_us.json}) ships a
 * translation for the base key + every category + every shipped variant; a
 * variant that lacks a translation will surface as the raw key (Minecraft's
 * standard fallback), which is the visible failure mode that prompts a
 * lang-entry addition.
 *
 * <p>Why a custom class: vanilla {@link MobBucketItem#getName(ItemStack)} just
 * returns the base description id. To distinguish the 12 stamped subtypes in
 * JEI search and tooltips, we need the per-stack lookup. Mirrors what
 * {@link ResourceTadpoleBucketItem} already does for tadpole buckets.
 */
public final class SlimeBucketItem extends MobBucketItem {

    public SlimeBucketItem(EntityType<? extends Mob> type, Fluid fluid,
                           SoundEvent emptySound, Properties properties) {
        super(type, fluid, emptySound, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier variantId = ResourceTadpoleBucketItem.readVariant(stack);
        if (variantId != null) {
            return Component.translatable(
                "item.productivefrogs.slime_bucket." + variantId.getPath());
        }
        Category category = ResourceTadpoleBucketItem.readCategory(stack);
        if (category != null) {
            return Component.translatable(
                "item.productivefrogs.slime_bucket." + category.id());
        }
        return Component.translatable(getDescriptionId());
    }
}
