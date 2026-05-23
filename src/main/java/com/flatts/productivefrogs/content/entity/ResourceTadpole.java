package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The Resource Tadpole — Productive Frogs' tadpole that matures into a
 * {@link ResourceFrog} of its locked {@link Category}, rather than into a
 * vanilla biome-determined Frog.
 *
 * <p>Inherits vanilla {@link Tadpole} behavior wholesale: brain, AI, bucket
 * tag, slimeball-acceleration, water-survival timer, sounds. Overrides:
 * <ul>
 *   <li>{@link #ageUp()} — exposed via access transformer; converts into
 *       ResourceFrog of matching category instead of vanilla Frog.</li>
 *   <li>{@link #getBucketItemStack()} — returns our Resource Tadpole bucket
 *       so vanilla {@code Bucketable.bucketMobPickup} routes through the
 *       category-preserving path.</li>
 *   <li>{@link #saveToBucketTag(ItemStack)} / {@link #loadFromBucketTag(CompoundTag)} —
 *       extend vanilla bucket NBT with the category field so it survives
 *       round-tripping through the bucket.</li>
 * </ul>
 */
public class ResourceTadpole extends Tadpole {

    private static final EntityDataAccessor<Integer> DATA_CATEGORY =
        SynchedEntityData.defineId(ResourceTadpole.class, EntityDataSerializers.INT);

    @SuppressWarnings("unchecked")
    public ResourceTadpole(EntityType<? extends ResourceTadpole> type, Level level) {
        // Java generics can't see that EntityType<ResourceTadpole extends Tadpole
        // extends AbstractFish> is a valid EntityType<? extends AbstractFish>;
        // the double cast satisfies the type checker.
        super((EntityType<? extends AbstractFish>) (EntityType<?>) type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CATEGORY, Category.METALLIC.ordinal());
    }

    public Category getCategory() {
        // Defensive: synced data can be set to any int via modded packets or
        // corrupted save data. Fall back to METALLIC (tier 1) rather than
        // crashing if the ordinal is out of range.
        int ordinal = this.entityData.get(DATA_CATEGORY);
        Category[] values = Category.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return Category.METALLIC;
        }
        return values[ordinal];
    }

    public void setCategory(Category category) {
        this.entityData.set(DATA_CATEGORY, category.ordinal());
    }

    /**
     * Category-aware display name. See {@code ResourceFrog#getName} for the
     * rationale — same pattern, different entity type id.
     */
    @Override
    public net.minecraft.network.chat.Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        return net.minecraft.network.chat.Component.translatable(
            getType().getDescriptionId() + "." + getCategory().id()
        );
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Category", getCategory().name());
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Category", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                setCategory(Category.valueOf(tag.getString("Category")));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in save data — leave default.
            }
        }
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(PFItems.RESOURCE_TADPOLE_BUCKET.get());
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        super.saveToBucketTag(stack);
        Category category = getCategory();
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag ->
            tag.putString("Category", category.name())
        );
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        super.loadFromBucketTag(tag);
        if (tag.contains("Category", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                setCategory(Category.valueOf(tag.getString("Category")));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in bucket NBT — leave default.
            }
        }
    }

    /**
     * Override the vanilla maturation hook (made overridable via access
     * transformer). Mirrors vanilla's logic but converts into a
     * {@link ResourceFrog} instead of {@code minecraft:frog}, carrying the
     * category forward.
     */
    @Override
    public void ageUp() {
        EntityType<ResourceFrog> target = PFEntities.RESOURCE_FROG.get();
        if (!EventHooks.canLivingConvert(this, target, timer -> {})) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        ResourceFrog frog = target.create(this.level());
        if (frog == null) return;
        EventHooks.onLivingConvert(this, frog);
        Category category = getCategory();
        frog.setCategory(category);
        frog.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
        frog.finalizeSpawn(
            serverLevel,
            serverLevel.getCurrentDifficultyAt(frog.blockPosition()),
            MobSpawnType.CONVERSION,
            null
        );
        frog.setNoAi(this.isNoAi());
        if (this.hasCustomName()) {
            frog.setCustomName(this.getCustomName());
            frog.setCustomNameVisible(this.isCustomNameVisible());
        }
        frog.setPersistenceRequired();
        this.playSound(SoundEvents.TADPOLE_GROW_UP, 0.15F, 1.0F);
        serverLevel.addFreshEntityWithPassengers(frog);
        this.discard();
    }
}
