package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.PFConfig;
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
        builder.define(DATA_CATEGORY, Category.BOG.ordinal());
    }

    // Pending breeding stats inherited at conception, carried from the hatched
    // egg through to the matured ResourceFrog (docs/frog_breeding.md). Not
    // synced - the tadpole never displays them; they are server-side payload
    // consumed in ageUp(). Absent (hasPendingStats false) for a non-bred
    // tadpole, which matures into a baseline (1/1/1) frog instead.
    private boolean hasPendingStats;
    private int pendingAppetite;
    private int pendingBounty;
    private int pendingReach;

    // Fractional carry for the config-tunable growth accelerator (see aiStep).
    // Transient: losing the sub-tick remainder across a reload is negligible.
    private double growthCarry;

    public Category getCategory() {
        // Defensive: synced data can be set to any int via modded packets or
        // corrupted save data. fromOrdinalOrDefault falls back to BOG rather
        // than crashing if the ordinal is out of range.
        return Category.fromOrdinalOrDefault(this.entityData.get(DATA_CATEGORY));
    }

    /**
     * Honor the config-exposed {@link PFConfig#tadpoleGrowthTicks()} for modded
     * tadpoles without touching vanilla's shared {@code Tadpole.ticksToBeFrog}
     * static (vanilla tadpoles keep stock pacing).
     *
     * <p>Vanilla {@code Tadpole.aiStep()} (run via {@code super}) increments the
     * age counter by 1 each server tick and matures the tadpole when age reaches
     * {@code ticksToBeFrog} (24000 / 20 min). To mature in a shorter configured
     * window we add extra age per tick so the counter reaches 24000 in
     * {@code tadpoleGrowthTicks} ticks. This is purely additive, so slime-ball
     * feeding (which also advances age) still accelerates growth on top.
     *
     * <p>Only acceleration is supported: a configured value at or above vanilla's
     * 24000-tick ceiling leaves the stock pace untouched (raising the ceiling
     * would require mutating the shared static). The default (24000) is a no-op.
     */
    @Override
    public void aiStep() {
        super.aiStep();
        int vanilla = Tadpole.ticksToBeFrog;
        // Cheap early-outs before the config read: never on the client, and once
        // age has reached vanilla's ceiling super.aiStep already matured us.
        if (this.level().isClientSide() || this.age >= vanilla) {
            return;
        }
        int target = PFConfig.tadpoleGrowthTicks();
        if (target >= vanilla) {
            return;
        }
        // Extra age units to add per tick so total age (1 from super + extra)
        // reaches `vanilla` in `target` ticks.
        this.growthCarry += (double) vanilla / target - 1.0;
        int extra = (int) this.growthCarry;
        if (extra <= 0) {
            return;
        }
        this.growthCarry -= extra;
        this.age = Math.min(vanilla, this.age + extra);
        if (this.age >= vanilla) {
            this.ageUp();
        }
    }

    public void setCategory(Category category) {
        this.entityData.set(DATA_CATEGORY, category.ordinal());
    }

    /**
     * Stamp the inherited offspring stats onto this tadpole. Called at hatch
     * time by {@link com.flatts.productivefrogs.content.block.PrimedFrogEggBlock}
     * when the egg carried bred stats. Consumed in {@link #ageUp()} when the
     * tadpole matures into a {@link ResourceFrog}.
     */
    public void setPendingStats(int appetite, int bounty, int reach) {
        this.hasPendingStats = true;
        this.pendingAppetite = appetite;
        this.pendingBounty = bounty;
        this.pendingReach = reach;
    }

    public boolean hasPendingStats() {
        return hasPendingStats;
    }

    /**
     * The inherited stats this tadpole will mature into (only meaningful when
     * {@link #hasPendingStats()} is true). Server-side payload - exposed so the
     * Jade look-at tooltip can preview the offspring before it grows up.
     */
    public int getPendingAppetite() {
        return pendingAppetite;
    }

    public int getPendingBounty() {
        return pendingBounty;
    }

    public int getPendingReach() {
        return pendingReach;
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
        if (hasPendingStats) {
            tag.putBoolean("HasPendingStats", true);
            tag.putInt("PendingAppetite", pendingAppetite);
            tag.putInt("PendingBounty", pendingBounty);
            tag.putInt("PendingReach", pendingReach);
        }
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
        hasPendingStats = tag.getBoolean("HasPendingStats");
        if (hasPendingStats) {
            pendingAppetite = tag.getInt("PendingAppetite");
            pendingBounty = tag.getInt("PendingBounty");
            pendingReach = tag.getInt("PendingReach");
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
        // Apply inherited stats AFTER finalizeSpawn so they override the
        // baseline stats finalizeSpawn would otherwise apply. A non-bred tadpole
        // (no pending stats) keeps the baseline (1/1/1). This is the final hop of
        // the conception->egg->tadpole->frog stat carry.
        if (hasPendingStats) {
            frog.setStats(pendingAppetite, pendingBounty, pendingReach);
        }
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
