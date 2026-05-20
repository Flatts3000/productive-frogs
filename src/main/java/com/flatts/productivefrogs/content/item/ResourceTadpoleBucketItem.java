package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluid;
import org.jspecify.annotations.Nullable;

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
    public static Category readCategory(ItemStack stack) {
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag();
        Optional<String> name = tag.getString("Category");
        if (name.isEmpty()) {
            return null;
        }
        try {
            return Category.valueOf(name.get());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
