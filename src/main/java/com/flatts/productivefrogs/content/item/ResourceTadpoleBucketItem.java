package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
        com.flatts.productivefrogs.data.FrogKind kind = readKind(stack);
        if (kind == null) {
            return Component.translatable(getDescriptionId());
        }
        // Kind-suffixed (#281): a Prowler tadpole bucket reads "Bucket of Prowler
        // Tadpole", not its anchor species - the bucket must never be visually
        // indistinguishable from a plain species bucket (review finding #6).
        return Component.translatable("item.productivefrogs.resource_tadpole_bucket." + kind.nameSuffix());
    }

    /**
     * Pull the stored kind out of the bucket's {@code BUCKET_ENTITY_DATA}
     * payload (#281): the {@code "Kind"} id the tadpole bucket now writes, with
     * the legacy {@code "Category"}(+{@code "Midas"}) fallback that slime buckets
     * and pre-Kind data still use. Returns {@code null} for an empty bucket.
     */
    /**
     * Fold {@code contained_kind} into {@code BUCKET_ENTITY_DATA} before vanilla
     * spawns the tadpole.
     *
     * <p>{@code MobBucketItem#spawn} is private and hands {@code loadFromBucketTag}
     * nothing but the tag, so without this a bucket carrying only the component -
     * the {@code /give}, quest-reward or pack-recipe case the component exists to
     * serve - would name itself, tint itself and satisfy a filter as one kind, then
     * release a DEFAULT tadpole. Silent identity loss on the path that matters
     * most, and worse than the divergence #385 set out to fix. The component is
     * only a display key until the release path agrees with it.
     */
    @Override
    public void checkExtraContent(@Nullable net.minecraft.world.entity.LivingEntity user,
                                  net.minecraft.world.level.Level level, ItemStack stack,
                                  net.minecraft.core.BlockPos pos) {
        String kindId = stack.get(PFDataComponents.CONTAINED_KIND.get());
        if (kindId != null) {
            CustomData existing = stack.get(DataComponents.BUCKET_ENTITY_DATA);
            boolean tagHasKind = existing != null && existing.copyTag().contains("Kind");
            if (!tagHasKind) {
                CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack,
                    tag -> tag.putString("Kind", kindId));
            }
        }
        super.checkExtraContent(user, level, stack, pos);
    }

    @Nullable
    public static com.flatts.productivefrogs.data.FrogKind readKind(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data != null) {
            com.flatts.productivefrogs.data.FrogKind fromTag =
                com.flatts.productivefrogs.data.FrogKind.readFromTag(data.copyTag()).orElse(null);
            if (fromTag != null) {
                return fromTag;
            }
        }
        // Fall back to the flat component (#385), so the two identity carriers are
        // self-healing in both directions. Once contained_kind is the advertised
        // identity key, a bucket can arrive with only that - from /give, a quest
        // reward, or a pack recipe built off the component - and without this it
        // would pass every component filter while still naming and tinting itself
        // as an empty bucket. byId returns null for an id this build does not
        // know, which is the same "unknown" answer the tag path gives.
        String kindId = stack.get(PFDataComponents.CONTAINED_KIND.get());
        return kindId == null ? null : com.flatts.productivefrogs.data.FrogKind.byId(kindId);
    }

    /**
     * Pull the stored category out of the bucket's {@code BUCKET_ENTITY_DATA}
     * payload - the kind's fallback category (#281: a Kind-written tadpole
     * bucket resolves through {@link #readKind}; a Category-written slime
     * bucket resolves through the same legacy read). Returns {@code null} if
     * the bucket doesn't carry either form.
     */
    @Nullable
    public static Category readCategory(ItemStack stack) {
        com.flatts.productivefrogs.data.FrogKind kind = readKind(stack);
        return kind == null ? null : kind.fallbackCategory();
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
    public static Identifier readVariant(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data != null) {
            CompoundTag tag = data.copyTag();
            if (tag.contains("Variant")) {
                String raw = tag.getStringOr("Variant", "");
                if (!raw.isEmpty()) {
                    return Identifier.tryParse(raw);
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
