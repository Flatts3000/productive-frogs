package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFSensors;
import com.flatts.productivefrogs.util.PFDebug;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;

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

    // Breeding stats (docs/frog_breeding.md). Synced so the Jade tooltip and
    // the cosmetic render tier can read them client-side. Persisted in NBT.
    // Clamped to [STAT_MIN, statCap] on every read and write.
    private static final EntityDataAccessor<Integer> DATA_APPETITE =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOUNTY =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REACH =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);

    // Set true once stats are established (baseline or inheritance) so a
    // re-fired finalizeSpawn (vanilla can call it more than once) doesn't
    // re-apply baseline over an established frog. Transient: a loaded frog has
    // stats in NBT and never re-applies anyway.
    private boolean statsInitialized;

    // Pending offspring stats captured at conception (spawnChildFromBreeding),
    // carried to the laid egg by LayCategoryFrogspawn, then cleared. Server-side
    // payload only; persisted in NBT so a chunk unload between conception and
    // the lay behavior firing doesn't lose the roll.
    private boolean hasPendingOffspring;
    private int pendingOffspringAppetite;
    private int pendingOffspringBounty;
    private int pendingOffspringReach;

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
        builder.define(DATA_CATEGORY, Category.BOG.ordinal());
        builder.define(DATA_APPETITE, FrogStats.STAT_MIN);
        builder.define(DATA_BOUNTY, FrogStats.STAT_MIN);
        builder.define(DATA_REACH, FrogStats.STAT_MIN);
    }

    private static int statCap() {
        return PFConfig.statCap();
    }

    public int getAppetite() {
        return FrogStats.clamp(this.entityData.get(DATA_APPETITE), statCap());
    }

    public int getBounty() {
        return FrogStats.clamp(this.entityData.get(DATA_BOUNTY), statCap());
    }

    public int getReach() {
        return FrogStats.clamp(this.entityData.get(DATA_REACH), statCap());
    }

    public void setAppetite(int value) {
        this.entityData.set(DATA_APPETITE, FrogStats.clamp(value, statCap()));
    }

    public void setBounty(int value) {
        this.entityData.set(DATA_BOUNTY, FrogStats.clamp(value, statCap()));
    }

    public void setReach(int value) {
        this.entityData.set(DATA_REACH, FrogStats.clamp(value, statCap()));
    }

    /** Set all three stats at once and mark stats as established (no starter re-roll). */
    public void setStats(int appetite, int bounty, int reach) {
        setAppetite(appetite);
        setBounty(bounty);
        setReach(reach);
        this.statsInitialized = true;
    }

    /** Sum of the three stats (range {@code 3 * STAT_MIN .. 3 * statCap}). Drives the cosmetic tier. */
    public int statTotal() {
        return getAppetite() + getBounty() + getReach();
    }

    /** The configured per-stat cap. Public so the Jade readout can render {@code value/cap}. */
    public int getStatCap() {
        return statCap();
    }

    public Category getCategory() {
        // Defensive: synced data can be set to any int via modded packets or
        // corrupted save data. fromOrdinalOrDefault falls back to BOG rather
        // than crashing if the ordinal is out of range.
        return Category.fromOrdinalOrDefault(this.entityData.get(DATA_CATEGORY));
    }

    public void setCategory(Category category) {
        this.entityData.set(DATA_CATEGORY, category.ordinal());
    }

    /**
     * Category-aware display name — so Jade, the F3 entity readout, and any
     * other client surface that calls {@code getName()} reads "Bog Frog"
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
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Category", getCategory().name());
        tag.putInt("Appetite", getAppetite());
        tag.putInt("Bounty", getBounty());
        tag.putInt("Reach", getReach());
        if (hasPendingOffspring) {
            tag.putBoolean("HasPendingOffspring", true);
            tag.putInt("PendingOffspringAppetite", pendingOffspringAppetite);
            tag.putInt("PendingOffspringBounty", pendingOffspringBounty);
            tag.putInt("PendingOffspringReach", pendingOffspringReach);
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
        if (tag.contains("Appetite", net.minecraft.nbt.Tag.TAG_INT)) {
            // A frog persisted with stats is established - getters clamp, so a
            // tampered save can't inject out-of-range values.
            setStats(tag.getInt("Appetite"), tag.getInt("Bounty"), tag.getInt("Reach"));
        }
        hasPendingOffspring = tag.getBoolean("HasPendingOffspring");
        if (hasPendingOffspring) {
            pendingOffspringAppetite = tag.getInt("PendingOffspringAppetite");
            pendingOffspringBounty = tag.getInt("PendingOffspringBounty");
            pendingOffspringReach = tag.getInt("PendingOffspringReach");
        }
    }

    /**
     * Apply <b>baseline</b> stats to a non-bred frog and config-gated persistence.
     * A non-bred frog - one matured from crafted / Spawnery / non-bred frogspawn -
     * starts at the floor ({@link FrogStats#STAT_MIN} across all three stats, i.e.
     * {@code 1/1/1}); breeding is the only way to climb above baseline
     * (docs/known_issues.md, docs/frog_breeding.md). A frog matured from a bred
     * egg has its inherited stats applied by {@link ResourceTadpole#ageUp()} AFTER
     * this runs, overriding the baseline. A frog loaded from disk has
     * {@code statsInitialized} set by readAdditionalSaveData and skips this. The
     * {@code statsInitialized} guard also makes a double finalizeSpawn (vanilla
     * can call it more than once) idempotent.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData spawnData) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, spawnData);
        if (!statsInitialized) {
            applyBaselineStats();
        }
        applyPersistence();
        return result;
    }

    private void applyBaselineStats() {
        setStats(FrogStats.STAT_MIN, FrogStats.STAT_MIN, FrogStats.STAT_MIN);
        PFDebug.log(PFDebug.Area.LIFECYCLE, () -> String.format(
            "baseline stats: non-bred frog category=%s -> A%d/B%d/R%d",
            getCategory(), getAppetite(), getBounty(), getReach()));
    }

    /** Config-gated persistence (frogs.persistent, default true) so a bred line isn't lost to despawn. */
    private void applyPersistence() {
        if (PFConfig.frogsPersistent()) {
            setPersistenceRequired();
        }
    }

    /**
     * Player direct-feeding per design Q9: right-click a Resource Frog with
     * a category-matching Slime Bucket → frog instantly converts the bucketed
     * slime to a Froglight drop, the bucket transforms back to empty, and
     * the player effectively shortcuts the milk-fountain step.
     *
     * <p>Category-match check applies. Mismatched (or non-Slime-Bucket)
     * interactions fall through to {@code super.mobInteract} — i.e. vanilla
     * {@link Frog#mobInteract} → {@link Animal#mobInteract}. That preserves
     * slimeball-driven love-mode, name-tag application, lead attachment,
     * and every other vanilla animal interaction without our handler
     * having to enumerate them. The bucket is not consumed on mismatch.
     *
     * <p>Variant carries through when the bucketed slime carried one —
     * iron-variant slime → iron configurable_froglight drop, exactly like
     * the in-world tongue-eat path produces. Category-only slimes (no
     * variant) drop the broad-strokes category Froglight, again matching
     * the tongue-eat behavior.
     *
     * <p>The Slime Bucket is replaced with a vanilla empty bucket in
     * survival; creative players keep the slime bucket. After a successful
     * match we start the Appetite-scaled hunting cooldown via
     * {@link #startEatCooldown()} (the {@link MemoryModuleType#HAS_HUNTING_COOLDOWN}
     * gate our brain registers), so a direct-feed is paced exactly like a
     * natural tongue eat and spamming buckets can't outrun it. (Players are
     * also rate-limited by needing to re-bucket each slime.)
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
        // V1.5: direct-feed requires a variant-stamped bucket. Category-only
        // buckets (a Bucket of <Species> Slime with no variant) are
        // intermediates — frogs only "produce" from variant slimes.
        ResourceLocation variantId = ResourceTadpoleBucketItem.readVariant(stack);
        if (variantId == null) {
            return super.mobInteract(player, hand);
        }
        if (this.level().isClientSide()) {
            // Client-side returns SUCCESS so the player's swing arm animates
            // and the inventory updates roundtrip from the server's mutation.
            return InteractionResult.SUCCESS;
        }
        FrogTongueDropHandler.dropFroglightAtFrog(this, variantId);
        // Start the Appetite-scaled hunting cooldown so a direct-feed is paced
        // exactly like a natural tongue eat - a player can't out-produce the
        // tongue loop just by spamming buckets (they're also gated by needing
        // to re-bucket each slime).
        startEatCooldown();
        if (!player.getAbilities().instabuild) {
            player.setItemInHand(hand, new ItemStack(Items.BUCKET));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Breeding food is the dedicated {@link PFItems#SWEETSLIME} treat, NOT the
     * vanilla slime ball (D5: slime balls are ubiquitous in a slime farm and
     * would trigger accidental breeding). Vanilla {@link Animal#mobInteract}
     * reads this to drive the feed -> love-mode interaction, so overriding it is
     * the only hook needed to make Sweetslime the breeding trigger. This also
     * replaces the vanilla {@code ItemTags.FROG_FOOD} (slime ball) temptation
     * food for Resource Frogs.
     */
    @Override
    public boolean isFood(ItemStack stack) {
        return stack.is(PFItems.SWEETSLIME.get());
    }

    /**
     * Same-species breeding gate (D4): a Resource Frog only mates with another
     * Resource Frog of the same {@link Category}. Config-gated via
     * {@code breeding.sameSpeciesOnly} (default true); when disabled the gate
     * falls back to vanilla "same EntityType + both in love". The class check
     * (via super) already restricts partners to other Resource Frogs since
     * ResourceFrog is its own class; the category check narrows that to the same
     * species.
     */
    @Override
    public boolean canMate(Animal other) {
        if (!super.canMate(other)) {
            return false;
        }
        boolean sameSpeciesOnly = PFConfig.sameSpeciesOnly();
        if (sameSpeciesOnly && other instanceof ResourceFrog partner) {
            return this.getCategory() == partner.getCategory();
        }
        return true;
    }

    /**
     * Conception hook - the one moment both parents are guaranteed present.
     * Vanilla {@code AnimalMakeLove} calls this on the initiating frog when the
     * love timer fires; the Frog override sets {@code IS_PREGNANT} (which later
     * drives {@link LayCategoryFrogspawn}). We compute the offspring's inherited
     * stats from both parents HERE and stash them on this (the pregnant) frog,
     * because at lay time the partner may be gone. The lay behavior reads them
     * back and stamps them onto the egg. See docs/frog_breeding.md.
     */
    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        if (partner instanceof ResourceFrog mate) {
            captureOffspringStats(mate, level.getRandom());
        }
        super.spawnChildFromBreeding(level, partner);
        // Vanilla's finalizeSpawnChildFromBreeding sets both parents' age to a
        // fixed 6000-tick re-breed cooldown. Re-apply our config-exposed,
        // deterministic value (default 6000 = vanilla) so packs can tune the
        // breeding cadence (docs/known_issues.md). setAge on an adult is the
        // re-breed cooldown countdown.
        int cooldown = PFConfig.breedingCooldownTicks();
        this.setAge(cooldown);
        partner.setAge(cooldown);
    }

    private void captureOffspringStats(ResourceFrog mate, RandomSource random) {
        double improvement = PFConfig.improvementChance();
        double regression = PFConfig.regressionChance();
        int cap = PFConfig.statCap();
        this.pendingOffspringAppetite =
            FrogStats.inheritStat(getAppetite(), mate.getAppetite(), improvement, regression, cap, random);
        this.pendingOffspringBounty =
            FrogStats.inheritStat(getBounty(), mate.getBounty(), improvement, regression, cap, random);
        this.pendingOffspringReach =
            FrogStats.inheritStat(getReach(), mate.getReach(), improvement, regression, cap, random);
        this.hasPendingOffspring = true;
        PFDebug.log(PFDebug.Area.EGG, () -> String.format(
            "conception: %s parents A(%d,%d) B(%d,%d) R(%d,%d) -> offspring A%d/B%d/R%d",
            getCategory(), getAppetite(), mate.getAppetite(), getBounty(), mate.getBounty(),
            getReach(), mate.getReach(),
            pendingOffspringAppetite, pendingOffspringBounty, pendingOffspringReach));
    }

    /** Whether this (pregnant) frog carries a captured offspring stat roll for the lay behavior. */
    public boolean hasPendingOffspring() {
        return hasPendingOffspring;
    }

    public int getPendingOffspringAppetite() {
        return pendingOffspringAppetite;
    }

    public int getPendingOffspringBounty() {
        return pendingOffspringBounty;
    }

    public int getPendingOffspringReach() {
        return pendingOffspringReach;
    }

    /** Clear the captured offspring stats once the lay behavior has stamped them onto the egg. */
    public void clearPendingOffspring() {
        hasPendingOffspring = false;
    }

    /**
     * Override the brain setup to add (1) a same-species {@code AnimalMakeLove}
     * keyed to the Resource Frog EntityType - vanilla's is keyed to
     * {@code EntityType.FROG} and would never pair two Resource Frogs - and
     * (2) a category-aware lay-spawn behavior at priority 2 of the LAY_SPAWN
     * activity (vanilla's lay behavior runs at priority 3). When a Resource Frog
     * completes love-mode, our lay behavior fires first, places a Primed Frog Egg
     * block of the matching category (stamping the captured offspring stats),
     * and erases IS_PREGNANT - preventing vanilla's behavior from placing a
     * second (vanilla) frogspawn.
     */
    @Override
    @SuppressWarnings("unchecked")
    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        Brain<Frog> brain = (Brain<Frog>) super.makeBrain(dynamic);
        // Pair Resource Frogs with each other. Vanilla FrogAi installs
        // AnimalMakeLove(EntityType.FROG) in IDLE/SWIM at priority 0; that
        // partnerType never matches a ResourceFrog, so without this a pair of
        // Resource Frogs in love would never produce a child. Added at the same
        // priority in both activities so it runs alongside the vanilla pacing.
        AnimalMakeLove resourceMakeLove =
            new AnimalMakeLove((EntityType<? extends Animal>) (EntityType<?>) PFEntities.RESOURCE_FROG.get());
        // Re-add IDLE/SWIM via addActivityWithConditions, NOT addActivity. In
        // 1.21.1 Brain#addActivity(activity, list) routes to
        // addActivityAndRemoveMemoriesWhenStopped with an EMPTY condition set,
        // and that does activityRequirements.put(activity, conditions) - a PUT
        // that would WIPE the land/water gating FrogAi already installed (IDLE
        // requires IS_IN_WATER absent, SWIM requires it present). With SWIM's
        // requirements wiped to "always met" and FrogAi.updateActivity checking
        // SWIM before IDLE, a frog on land gets stuck in SWIM. Re-supplying
        // vanilla's exact requirement sets adds resourceMakeLove while keeping
        // the gating intact (the behavior list is merged, not replaced).
        brain.addActivityWithConditions(
            Activity.IDLE,
            ImmutableList.of(Pair.of(0, resourceMakeLove)),
            ImmutableSet.of(
                Pair.of(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT),
                Pair.of(MemoryModuleType.IS_IN_WATER, MemoryStatus.VALUE_ABSENT)
            )
        );
        brain.addActivityWithConditions(
            Activity.SWIM,
            ImmutableList.of(Pair.of(0, resourceMakeLove)),
            ImmutableSet.of(
                Pair.of(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT),
                Pair.of(MemoryModuleType.IS_IN_WATER, MemoryStatus.VALUE_PRESENT)
            )
        );
        // LAY_SPAWN: include LONG_JUMP_MID_JUMP-absent too (vanilla's full set),
        // so a pregnant frog doesn't try to lay mid-jump.
        brain.addActivityWithConditions(
            Activity.LAY_SPAWN,
            ImmutableList.of(Pair.of(2, LayCategoryFrogspawn.create())),
            ImmutableSet.of(
                Pair.of(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT),
                Pair.of(MemoryModuleType.IS_PREGNANT, MemoryStatus.VALUE_PRESENT)
            )
        );
        return brain;
    }

    /**
     * Override the brain provider to make three targeted changes to vanilla
     * {@link Frog}'s sensor and memory lists, leaving everything else intact:
     * <ul>
     *   <li>swap {@code FROG_ATTACKABLES} for our category-filtering prey sensor;</li>
     *   <li>swap {@code FROG_TEMPTATIONS} (slime-ball / {@code FROG_FOOD}) for the
     *       Sweetslime temptation sensor, so frogs are lured by the item they
     *       actually breed on;</li>
     *   <li>register {@code HAS_HUNTING_COOLDOWN} (absent from vanilla
     *       {@code Frog.MEMORY_TYPES}) so it becomes our Appetite eat-cooldown.</li>
     * </ul>
     * The first two keep the Q8 "vanilla AI except for prey eligibility + the
     * breeding-item swap" constraint.
     *
     * <p>Both source lists are made directly readable via the access transformer
     * (see {@code META-INF/accesstransformer.cfg}). Starting from vanilla's lists
     * means a new sensor Mojang adds to the frog brain is inherited automatically.
     */
    @Override
    public Brain.Provider<Frog> brainProvider() {
        ImmutableList<SensorType<? extends Sensor<? super Frog>>> sensors = SENSOR_TYPES.stream()
            .<SensorType<? extends Sensor<? super Frog>>>map(t -> {
                if (t == SensorType.FROG_ATTACKABLES) {
                    return PFSensors.RESOURCE_FROG_ATTACKABLES.get();
                }
                // Swap the slime-ball temptation for our Sweetslime one so frogs
                // follow the item they actually breed on, not loose slime balls.
                if (t == SensorType.FROG_TEMPTATIONS) {
                    return PFSensors.RESOURCE_FROG_TEMPTATIONS.get();
                }
                return t;
            })
            .collect(ImmutableList.toImmutableList());
        // Register HAS_HUNTING_COOLDOWN, which vanilla Frog.MEMORY_TYPES omits.
        // FrogAttackablesSensor already refuses to surface prey while this
        // memory is present, but a Frog brain never registers it, so
        // setMemoryWithExpiry on it is a no-op on a vanilla frog. Registering
        // it here turns it into our Appetite-scaled eat cooldown (set in
        // startEatCooldown after every eat). See docs/frog_breeding.md.
        ImmutableList<MemoryModuleType<?>> memories = ImmutableList.<MemoryModuleType<?>>builder()
            .addAll(MEMORY_TYPES)
            .add(MemoryModuleType.HAS_HUNTING_COOLDOWN)
            .build();
        return Brain.provider(memories, sensors);
    }

    /**
     * Begin the post-eat hunting cooldown. Appetite controls its length: at
     * Appetite 1 it is the configured max (slow), at the cap it is the min
     * (fast), via {@link FrogStats#appetiteCooldownTicks}. Implemented with the
     * vanilla {@link MemoryModuleType#HAS_HUNTING_COOLDOWN} memory our brain
     * registers; {@link com.flatts.productivefrogs.content.entity.ai.ResourceFrogAttackablesSensor}
     * (and vanilla {@code FrogAttackablesSensor}) already refuse to target while
     * it is present, and the expiry auto-decrements in {@code Brain#tick}, so no
     * countdown behavior is needed. Called from {@code FrogTongueDropHandler} on
     * a tongue kill and from {@link #mobInteract} on a direct-feed.
     */
    public void startEatCooldown() {
        int ticks = FrogStats.appetiteCooldownTicks(
            getAppetite(), PFConfig.appetiteCooldownMin(), PFConfig.appetiteCooldownMax(), PFConfig.statCap());
        getBrain().setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, ticks);
    }

    public static net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder createAttributes() {
        // ATTACK_DAMAGE 10.0 matches vanilla Frog#createAttributes. The previous
        // placeholder value of 0.0 (from before ResourceSlimes existed) meant
        // doHurtTarget triggered the hurt animation but never reduced the slime's
        // HP, so ShootTongue.eatEntity's isAlive() check never went false and the
        // entire production loop stalled at the tongue grab. Any non-zero value
        // would technically kill a 1-HP size-1 slime — we use the vanilla number
        // so larger / armored future prey still die in one tongue eat.
        return Mob.createMobAttributes()
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED, 1.0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH, 10.0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, 10.0)
            .add(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT, 1.0);
    }
}
