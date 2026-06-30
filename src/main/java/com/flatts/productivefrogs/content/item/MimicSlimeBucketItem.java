package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Bucket for the {@link com.flatts.productivefrogs.content.entity.MimicSlime}
 * (#253) - the front of the EE lane. Mirrors {@link SlimeBucketItem}: inherits
 * {@link MobBucketItem}'s capture/release round-trip, releases the slime
 * <b>without</b> placing water, and reads its display name + tint off the
 * top-level {@link PFDataComponents#SYNTHESIZED_ITEM} component (stamped by
 * {@code MimicSlime#saveToBucketTag} and by the Alembic via {@link #forItem}).
 */
public final class MimicSlimeBucketItem extends MobBucketItem {

    public MimicSlimeBucketItem(EntityType<? extends Mob> type, Fluid fluid,
                                SoundEvent emptySound, Properties properties) {
        super(type, fluid, emptySound, properties);
    }

    /** Release the slime without dumping the carrier fluid (see {@link SlimeBucketItem}). */
    @Override
    public boolean emptyContents(@Nullable LivingEntity user, Level level, BlockPos pos,
                                 @Nullable BlockHitResult result, @Nullable ItemStack container) {
        this.playEmptySound(user, level, pos);
        return true;
    }

    /**
     * Mint a Mimic Slime Bucket for an item id <b>without</b> a live entity - the
     * Alembic produces these. Stamps the top-level component (tint/name) and the
     * {@code BUCKET_ENTITY_DATA} key (so release spawns a Mimic Slime carrying it).
     */
    public static ItemStack forItem(Identifier itemId) {
        ItemStack stack = new ItemStack(PFItems.MIMIC_SLIME_BUCKET.get());
        stack.set(PFDataComponents.SYNTHESIZED_ITEM.get(), itemId);
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack,
            tag -> tag.putString("SynthesizedItem", itemId.toString()));
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier itemId = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
        if (itemId != null) {
            net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            Component itemName = item != null
                ? Component.translatable(item.getDescriptionId())
                : Component.literal(itemId.toString());
            return Component.translatable("item.productivefrogs.mimic_slime_bucket.item", itemName);
        }
        return Component.translatable(getDescriptionId());
    }

    /** Convenience: the captured item id (for the Milker/Churn branches + JEI). */
    @Nullable
    public static Identifier readItem(ItemStack stack) {
        return stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
    }

    // The released entity type is fixed to the Mimic Slime; kept explicit so the
    // registry wiring reads cleanly at the call site.
    public static EntityType<?> entityType() {
        return PFEntities.MIMIC_SLIME.get();
    }
}
