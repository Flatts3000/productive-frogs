package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Bucket variant for Resource Tadpoles — same release-on-water-block behavior
 * as vanilla {@code MobBucketItem} (we just inherit it), with a per-category
 * dynamic display name driven by the bucket's stored category NBT.
 *
 * <p>The category itself is persisted on the bucket's {@code bucket_entity_data}
 * data component via overrides in {@code ResourceTadpole.saveToBucketTag} /
 * {@code loadFromBucketTag}. This item only needs to:
 *
 * <ul>
 *   <li>Be the canonical bucket type for {@link com.flatts.productivefrogs.content.entity.ResourceTadpole}
 *       (so vanilla {@code Bucketable.bucketMobPickup} picks it up).</li>
 *   <li>Render a per-category display name when picked up.</li>
 * </ul>
 */
public final class ResourceTadpoleBucketItem extends MobBucketItem {

    public ResourceTadpoleBucketItem(EntityType<? extends Mob> type, Fluid fluid,
                                     SoundEvent emptySound, Properties properties) {
        super(type, fluid, emptySound, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        Category category = readCategory(stack);
        if (category == null) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("item.productivefrogs.resource_tadpole_bucket." + category.id());
    }

    /**
     * Pull the stored category out of the bucket's {@code BUCKET_ENTITY_DATA}
     * payload. Returns {@code null} if the bucket doesn't have one (legacy
     * data, corrupted save, etc.).
     */
    @Nullable
    /**
     * Fold {@code contained_category} into {@code BUCKET_ENTITY_DATA} before vanilla
     * spawns the tadpole.
     *
     * <p>{@code MobBucketItem#spawn} hands {@code loadFromBucketTag} nothing but the
     * tag, so without this a bucket carrying only the component - the {@code /give},
     * quest-reward or pack-recipe case the component exists to serve - would name
     * itself, tint itself and satisfy a filter as one category, then release a
     * DEFAULT tadpole. Silent identity loss on the path that matters most, and
     * worse than the divergence #385 set out to fix.
     */
    @Override
    public void checkExtraContent(@Nullable net.minecraft.world.entity.player.Player player,
                                  net.minecraft.world.level.Level level, ItemStack stack,
                                  net.minecraft.core.BlockPos pos) {
        Category category = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
        if (category != null) {
            CustomData existing = stack.get(DataComponents.BUCKET_ENTITY_DATA);
            boolean tagHasCategory = existing != null
                && existing.copyTag().contains("Category", net.minecraft.nbt.Tag.TAG_STRING);
            if (!tagHasCategory) {
                CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack,
                    tag -> tag.putString("Category", category.name()));
            }
        }
        super.checkExtraContent(player, level, stack, pos);
    }

    public static Category readCategory(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("Category", net.minecraft.nbt.Tag.TAG_STRING)) {
                String name = tag.getString("Category");
                if (name != null && !name.isEmpty()) {
                    try {
                        return Category.valueOf(name);
                    } catch (IllegalArgumentException ignored) {
                        // fall through to the component
                    }
                }
            }
        }
        // Fall back to the flat component (#385), so the two identity carriers are
        // self-healing in both directions. Once contained_category is the advertised
        // identity key, a bucket can arrive with only that - from /give, a quest
        // reward, or a pack recipe built off the component - and without this it
        // would pass every component filter while still reading as an empty bucket
        // for its name and tint.
        return stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
    }

    /**
     * Pull the stored slime variant id out of the bucket's
     * {@code BUCKET_ENTITY_DATA} payload. Returns {@code null} if the bucket
     * is empty, the entity didn't store a Variant tag (Tadpole Buckets
     * never do; only Slime Buckets that captured a variant-stamped
     * ResourceSlime), or the stored id is malformed.
     *
     * <p>Used by the bucket tint sources to prefer the variant's primary
     * color over the broader category tint when both are present.
     */
    @Nullable
    public static ResourceLocation readVariant(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("Variant", net.minecraft.nbt.Tag.TAG_STRING)) {
                String raw = tag.getString("Variant");
                if (raw != null && !raw.isEmpty()) {
                    return ResourceLocation.tryParse(raw);
                }
            }
        }
        // Fall back to the flat component (#357). A slime bucket now carries its
        // variant in BOTH places, and this keeps the two carriers self-healing:
        // once slime_variant is the advertised identity key, a bucket can arrive
        // with only that - from /give, a quest reward, or a pack recipe built off
        // the component - and without this it would pass every component filter
        // while the Milker fail-closed on it, feeding a frog fell through to
        // vanilla, and it rendered and named itself as a plain Slime Bucket.
        // Harmless for tadpole buckets, which never carry the component.
        return stack.get(PFDataComponents.SLIME_VARIANT.get());
    }
}
