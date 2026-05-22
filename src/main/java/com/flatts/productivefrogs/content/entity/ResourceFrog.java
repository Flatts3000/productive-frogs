package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFSensors;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Resource Frog — a Productive Frogs frog locked to one of the six
 * {@link Category} variants. Inherits vanilla {@link Frog} AI wholesale per
 * the locked Q8 decision; the only difference from vanilla is the category
 * field, which gates which slimes the frog will eventually consider tasty.
 *
 * <p>Category is synced as an integer ordinal on the entity's
 * {@link SynchedEntityData}. Stored as the enum {@code name()} string in
 * persistent save data for readable NBT.
 *
 * <p>Out of scope for this PR: tongue/prey AI changes (need Resource Slimes
 * to exist first) and per-category visuals (texture tinting comes with the
 * texture batch). Players see category via display name.
 */
public class ResourceFrog extends Frog {

    private static final EntityDataAccessor<Integer> DATA_CATEGORY =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);

    @SuppressWarnings("unchecked")
    public ResourceFrog(EntityType<? extends ResourceFrog> type, Level level) {
        // Java generics can't see that EntityType<ResourceFrog extends Frog
        // extends Animal> is a valid EntityType<? extends Animal>; the double
        // cast satisfies the type checker.
        super((EntityType<? extends Frog>) (EntityType<?>) type, level);
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
     * Category-aware display name — so Jade, the F3 entity readout, and any
     * other client surface that calls {@code getName()} reads "Metallic Frog"
     * instead of the generic "Resource Frog". Falls back to the custom name
     * (player-set via name tag) when present.
     *
     * <p>Lang key: {@code entity.productivefrogs.resource_frog.<id>}.
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
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("Category", getCategory().name());
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
    }

    /**
     * Player direct-feeding per design Q9: right-click a Resource Frog with
     * a category-matching Slime Bucket → frog instantly converts the bucketed
     * slime to a Froglight drop, the bucket transforms back to empty, and
     * the player effectively shortcuts the milk-fountain step.
     *
     * <p>Category-match check applies. Feeding a Gem Slime to a Metallic
     * Frog returns {@link InteractionResult#PASS} unchanged — the bucket is
     * not consumed, the frog ignores the interaction, vanilla
     * {@code Animal.mobInteract} runs (love-mode breeding via slimeballs,
     * name-tag, etc.).
     *
     * <p>Variant carries through when the bucketed slime carried one —
     * iron-variant slime → iron configurable_froglight drop, exactly like
     * the in-world tongue-eat path produces. Category-only slimes (no
     * variant) drop the broad-strokes category Froglight, again matching
     * the tongue-eat behavior.
     *
     * <p>The Slime Bucket is replaced with a vanilla empty bucket in
     * survival; creative players keep the slime bucket. No explicit tongue
     * cooldown is set — the player's natural action speed and the cost of
     * re-bucketing each slime gates throughput.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(PFItems.SLIME_BUCKET.get())) {
            return super.mobInteract(player, hand);
        }
        Category bucketCategory = ResourceTadpoleBucketItem.readCategory(stack);
        if (bucketCategory == null || bucketCategory != this.getCategory()) {
            // Bucket has no category (empty / vanilla slime bucket) or the
            // categories disagree — fail closed, defer to vanilla
            // mobInteract so slimeballs, name tags, etc. still work normally.
            return super.mobInteract(player, hand);
        }
        if (this.level().isClientSide()) {
            // Client-side returns SUCCESS so the player's swing arm animates
            // and the inventory updates roundtrip from the server's mutation.
            return InteractionResult.SUCCESS;
        }
        Identifier variantId = ResourceTadpoleBucketItem.readVariant(stack);
        FrogTongueDropHandler.dropFroglightAtFrog(this, bucketCategory, variantId);
        // Apply the same brain memory vanilla Frog uses to gate repeated
        // tongue use — keeps direct-feed cadence consistent with the natural
        // tongue cooldown so a player can't out-feed the tongue path's
        // pacing just by spamming buckets.
        this.getBrain().setMemoryWithExpiry(MemoryModuleType.IS_TEMPTED, true, 40L);
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Override the brain setup to add a category-aware lay-spawn behavior at
     * priority 2 of the LAY_SPAWN activity (vanilla's lay behavior runs at
     * priority 3). When a Resource Frog completes love-mode, our behavior
     * fires first, places a Primed Frog Egg block of the matching category,
     * and erases IS_PREGNANT — preventing vanilla's behavior from placing
     * a second (vanilla) frogspawn.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<Frog> brain = (Brain<Frog>) super.makeBrain(dynamic);
        brain.addActivityWithConditions(
            Activity.LAY_SPAWN,
            ImmutableList.of(Pair.of(2, LayCategoryFrogspawn.create())),
            ImmutableSet.of(Pair.of(MemoryModuleType.IS_PREGNANT, MemoryStatus.VALUE_PRESENT))
        );
        return brain;
    }

    /**
     * Override the brain provider sensor list to swap vanilla's
     * {@code FROG_ATTACKABLES} for our category-filtering equivalent. Everything
     * else (memory types, other sensors) reuses vanilla {@link Frog}'s
     * {@code protected static} lists verbatim — only the prey-detection sensor
     * changes, matching the Q8 "vanilla AI except for prey eligibility filter"
     * design constraint.
     */
    @Override
    public Brain.Provider<Frog> brainProvider() {
        ImmutableList<SensorType<? extends Sensor<? super Frog>>> sensors = ImmutableList.<SensorType<? extends Sensor<? super Frog>>>builder()
            .add(SensorType.NEAREST_LIVING_ENTITIES)
            .add(SensorType.HURT_BY)
            .add(PFSensors.RESOURCE_FROG_ATTACKABLES.get())
            .add(SensorType.FROG_TEMPTATIONS)
            .add(SensorType.IS_IN_WATER)
            .build();
        return Brain.provider(MEMORY_TYPES, sensors);
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        // ATTACK_DAMAGE 10.0 matches vanilla Frog#createAttributes. The previous
        // placeholder value of 0.0 (from before ResourceSlimes existed) meant
        // doHurtTarget triggered the hurt animation but never reduced the slime's
        // HP, so ShootTongue.eatEntity's isAlive() check never went false and the
        // entire production loop stalled at the tongue grab. Any non-zero value
        // would technically kill a 1-HP size-1 slime — we use the vanilla number
        // so larger / armored future prey still die in one tongue eat.
        return Animal.createAnimalAttributes()
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 1.0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 10.0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 10.0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT, 1.0);
    }
}
