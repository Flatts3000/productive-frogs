package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.ConversionType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The Resource Slime — Productive Frogs' category-locked slime variant. Mirrors
 * vanilla {@link Slime} behavior wholesale (movement, jump, push, sound,
 * spawn-rule eligibility) and adds a {@link Category} field that propagates
 * through split children so a {@code METALLIC} resource slime always splits into
 * smaller {@code METALLIC} resource slimes.
 *
 * <p>V1 simplification: one entity type, six categories (Category-keyed, not
 * variant-keyed). Per-resource variants within a category — {@code iron_slime}
 * vs {@code copper_slime} both inside {@code METALLIC} — layer in later via the
 * data-driven {@code SlimeVariant} registry described in
 * {@code docs/architecture.md}. We can do that without changing this entity,
 * the same way ItemTintSources layer on top of the flat Category enum on the
 * Frog Egg side.
 *
 * <p>Direct-kill drops (per design Q10): slimeballs only, vanilla parity.
 * Behavior lives in
 * {@code data/productivefrogs/loot_table/entities/resource_slime.json}, which
 * mirrors vanilla {@code minecraft:entities/slime}'s "size-1 only" condition.
 * Loot table customization has to go through JSON in 1.21.x — {@code Mob#getLootTable}
 * is {@code final}, so an override-by-subclass path isn't available.
 */
public class ResourceSlime extends Slime implements Bucketable {

    private static final EntityDataAccessor<Integer> DATA_CATEGORY =
        SynchedEntityData.defineId(ResourceSlime.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FROM_BUCKET =
        SynchedEntityData.defineId(ResourceSlime.class, EntityDataSerializers.BOOLEAN);

    public ResourceSlime(EntityType<? extends ResourceSlime> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CATEGORY, Category.METALLIC.ordinal());
        builder.define(DATA_FROM_BUCKET, false);
    }

    public Category getCategory() {
        // Defensive: synced data is an int and could be corrupted by modded
        // packets or save migration. Fall back to METALLIC (tier 1) rather
        // than crashing on out-of-range ordinals.
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

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.getString("Category").ifPresent(name -> {
            try {
                setCategory(Category.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in save data — leave default.
            }
        });
        setFromBucket(in.getBooleanOr("FromBucket", false));
    }

    /**
     * Re-implement vanilla {@link Slime#remove} so split offspring inherit the
     * parent's category. Vanilla's split runs in {@code Slime.remove}'s
     * {@code convertTo} lambda which only sets size + position — by overriding
     * here and zeroing size before delegating to {@code super}, we suppress the
     * vanilla split (its {@code size > 1} guard fails) while preserving the
     * rest of {@code Mob#remove}'s cleanup chain.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        int originalSize = this.getSize();
        if (!this.level().isClientSide() && originalSize > 1 && this.isDeadOrDying()) {
            splitWithCategory(originalSize);
            this.setSize(1, false);
        }
        super.remove(reason);
    }

    private void splitWithCategory(int originalSize) {
        float width = this.getDimensions(this.getPose()).width();
        float halfWidth = width / 2.0F;
        int childSize = originalSize / 2;
        int childCount = 2 + this.random.nextInt(3);
        PlayerTeam team = this.getTeam();
        Category category = getCategory();

        for (int l = 0; l < childCount; l++) {
            float xOff = (l % 2 - 0.5F) * halfWidth;
            float zOff = (l / 2 - 0.5F) * halfWidth;
            this.convertTo(
                this.getType(),
                new ConversionParams(ConversionType.SPLIT_ON_DEATH, false, false, team),
                EntitySpawnReason.TRIGGERED,
                child -> {
                    child.setSize(childSize, true);
                    if (child instanceof ResourceSlime resource) {
                        resource.setCategory(category);
                    }
                    child.snapTo(
                        this.getX() + xOff,
                        this.getY() + 0.5,
                        this.getZ() + zOff,
                        this.random.nextFloat() * 360.0F,
                        0.0F
                    );
                }
            );
        }
    }

    // Direct-kill drops are handled via the entity's default loot table at
    // data/productivefrogs/loot_table/entities/resource_slime.json — a copy
    // of vanilla slime's loot (slimeballs from size-1 only). Per design Q10,
    // the resource conversion lives in the frog tongue path, not in direct
    // combat. We can't override getLootTable() in code because Mob declares
    // it final in 1.21.x.

    // ---------------------------------------------------------------------
    // Bucketable — size-1 capture/release with category preservation
    // ---------------------------------------------------------------------

    @Override
    public boolean fromBucket() {
        return this.entityData.get(DATA_FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.entityData.set(DATA_FROM_BUCKET, fromBucket);
    }

    /**
     * Right-click handler — bridge for {@link Bucketable#bucketMobPickup} since
     * vanilla {@link Slime} doesn't extend AbstractFish (which is where the
     * vanilla fish/axolotl/tadpole pattern wires this up). Only size-1 slimes
     * are bucketable per design; larger slimes split via the standard slime
     * mechanic and the player buckets the offspring.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.getSize() == 1) {
            return Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
        }
        return super.mobInteract(player, hand);
    }

    @Override
    public void saveToBucketTag(ItemStack stack) {
        Bucketable.saveDefaultDataToBucketTag(this, stack);
        Category category = getCategory();
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag ->
            tag.putString("Category", category.name())
        );
    }

    @Override
    public void loadFromBucketTag(CompoundTag tag) {
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        tag.getString("Category").ifPresent(name -> {
            try {
                setCategory(Category.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in bucket NBT — leave default.
            }
        });
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(PFItems.SLIME_BUCKET.get());
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_FISH;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("Category", getCategory().name());
        // The category save is handled above; persist the fromBucket flag too
        // so a slime released from a bucket doesn't despawn on chunk reload.
        out.putBoolean("FromBucket", fromBucket());
    }
    // NOTE: the readAdditional override above intentionally lives in this class
    // unchanged from the original — the FromBucket flag is restored from
    // bucket NBT via loadFromBucketTag, which is wired by MobBucketItem on
    // release. World-saved slimes don't need it (they spawn normally).
}
