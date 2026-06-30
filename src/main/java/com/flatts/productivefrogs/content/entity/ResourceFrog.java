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
import java.util.List;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalMakeLove;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogAi;
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

    // Midas marker (Equivalence lane, #253). A Midas frog is a ResourceFrog whose
    // ONLY divergences are diet (eats Mimic Slimes, not its category's Resource
    // Slimes - branched in ResourceFrogAttackablesSensor) and drop (a Prismatic
    // Froglight stamped with the eaten Mimic Slime's item - MidasTongueDropHandler).
    // Threaded through the egg -> tadpole -> frog pipeline like the stats, and
    // breeds true. A flag (not a 7th Category) so the six-species machinery and
    // every Category.values() surface stay exactly as they are.
    private static final EntityDataAccessor<Boolean> DATA_MIDAS =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.BOOLEAN);

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

    // Sweetslimed lily pad perch (#214, docs/lily_pad_perch.md). Server-only: the
    // pad this frog is pinned to and the level game-time the claim lapses at. The
    // pad's BlockEntity re-asserts this on a short interval; if the pad is broken it
    // stops re-asserting and the claim expires, freeing the frog. Persisted so a
    // relog keeps a pinned frog pinned until the (short) claim naturally lapses and
    // the pad re-claims it. Not synced - perching has no client-visual in v1.
    @Nullable
    private net.minecraft.core.BlockPos perchPad;
    private long perchValidUntil;

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
        builder.define(DATA_MIDAS, false);
    }

    /** True if this is a Midas frog (Equivalence lane, #253). */
    public boolean isMidas() {
        return this.entityData.get(DATA_MIDAS);
    }

    public void setMidas(boolean midas) {
        this.entityData.set(DATA_MIDAS, midas);
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
        if (isMidas()) {
            return net.minecraft.network.chat.Component.translatable(getType().getDescriptionId() + ".midas");
        }
        return net.minecraft.network.chat.Component.translatable(
            getType().getDescriptionId() + "." + getCategory().id()
        );
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Category", getCategory().name());
        output.putInt("Appetite", getAppetite());
        output.putInt("Bounty", getBounty());
        output.putInt("Reach", getReach());
        if (isMidas()) {
            output.putBoolean("Midas", true);
        }
        if (hasPendingOffspring) {
            output.putBoolean("HasPendingOffspring", true);
            output.putInt("PendingOffspringAppetite", pendingOffspringAppetite);
            output.putInt("PendingOffspringBounty", pendingOffspringBounty);
            output.putInt("PendingOffspringReach", pendingOffspringReach);
        }
        if (perchPad != null) {
            output.putLong("PerchPad", perchPad.asLong());
            output.putLong("PerchValidUntil", perchValidUntil);
        }
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("Category").ifPresent(name -> {
            try {
                setCategory(Category.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in save data — leave default.
            }
        });
        input.getInt("Appetite").ifPresent(appetite ->
            // A frog persisted with stats is established - getters clamp, so a
            // tampered save can't inject out-of-range values.
            setStats(appetite, input.getIntOr("Bounty", 0), input.getIntOr("Reach", 0)));
        setMidas(input.getBooleanOr("Midas", false));
        hasPendingOffspring = input.getBooleanOr("HasPendingOffspring", false);
        if (hasPendingOffspring) {
            pendingOffspringAppetite = input.getIntOr("PendingOffspringAppetite", 0);
            pendingOffspringBounty = input.getIntOr("PendingOffspringBounty", 0);
            pendingOffspringReach = input.getIntOr("PendingOffspringReach", 0);
        }
        input.getLong("PerchPad").ifPresent(packed -> {
            perchPad = net.minecraft.core.BlockPos.of(packed);
            perchValidUntil = input.getLongOr("PerchValidUntil", 0L);
        });
    }

    // ---- Sweetslimed lily pad perch (#214) ----

    /** The pad this frog is currently pinned to, or null if unclaimed / the claim has lapsed. */
    @Nullable
    public net.minecraft.core.BlockPos getActivePerch() {
        return perchPad != null && this.level().getGameTime() < perchValidUntil ? perchPad : null;
    }

    /** Whether this frog is currently held by a pad (its claim is still valid). */
    public boolean hasActivePerch() {
        return getActivePerch() != null;
    }

    /** Pin this frog to {@code pad} until {@code validUntil} (a level game-time); the pad BE re-asserts it. */
    public void setPerch(net.minecraft.core.BlockPos pad, long validUntil) {
        this.perchPad = pad;
        this.perchValidUntil = validUntil;
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
                                        EntitySpawnReason reason, @Nullable SpawnGroupData spawnData) {
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
        Identifier variantId = ResourceTadpoleBucketItem.readVariant(stack);
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
        // With the stat layer off (#202) Sweetslime is not a breeding food, so
        // there is no breeding minigame at all (Sweetslime is also uncraftable).
        return PFConfig.frogStatsEnabled() && stack.is(PFItems.SWEETSLIME.get());
    }

    // ---- effective stats (#202) ----
    // Behavior reads these, not the raw getters: when the stat layer is config-off
    // every frog acts at the baseline (stat 1) regardless of its stored stats, so
    // there is no per-frog variance. The stored stats are left untouched (the
    // getters/NBT still hold them) - a freeze, not a delete, so flipping the flag
    // back on restores each frog's bred behavior. See docs/frog_breeding.md.

    /** Appetite as applied to the eat cooldown - stored value when the layer is on, else baseline. */
    public int effectiveAppetite() {
        return PFConfig.frogStatsEnabled() ? getAppetite() : FrogStats.STAT_MIN;
    }

    /** Bounty as applied to the Froglight drop count - stored value when the layer is on, else baseline. */
    public int effectiveBounty() {
        return PFConfig.frogStatsEnabled() ? getBounty() : FrogStats.STAT_MIN;
    }

    /** Reach as applied to the prey-scan radius - stored value when the layer is on, else baseline. */
    public int effectiveReach() {
        return PFConfig.frogStatsEnabled() ? getReach() : FrogStats.STAT_MIN;
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
        if (other instanceof ResourceFrog partner) {
            // Midas (Equivalence lane, #253) is its own breeding line: Midas x
            // Midas only, and never with a species frog (even one sharing Midas's
            // fallback category). So the colony you grow from one Kiss stays Midas.
            if (this.isMidas() || partner.isMidas()) {
                return this.isMidas() && partner.isMidas();
            }
            if (PFConfig.sameSpeciesOnly()) {
                return this.getCategory() == partner.getCategory();
            }
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
        // Stat layer off (#202): stamp baseline so a frog bred by any path (another
        // mod, a command) carries default stats rather than a hidden rolled value.
        if (!PFConfig.frogStatsEnabled()) {
            this.pendingOffspringAppetite = FrogStats.STAT_MIN;
            this.pendingOffspringBounty = FrogStats.STAT_MIN;
            this.pendingOffspringReach = FrogStats.STAT_MIN;
            this.hasPendingOffspring = true;
            return;
        }
        // Two-layer roll (docs/frog_breeding.md): blend (parent average) then climb,
        // with a guaranteed +1 on at least one stat so a breed is never wasted.
        int[] offspring = FrogStats.inheritStats(
            new int[] { getAppetite(), getBounty(), getReach() },
            new int[] { mate.getAppetite(), mate.getBounty(), mate.getReach() },
            PFConfig.improvementChance(), PFConfig.guaranteedImprovement(), PFConfig.statCap(), random);
        this.pendingOffspringAppetite = offspring[0];
        this.pendingOffspringBounty = offspring[1];
        this.pendingOffspringReach = offspring[2];
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

    // Lazily-built brain provider (26.1). Vanilla Frog now declares a static
    // Brain.Provider with an inline sensor list + a Brain.ActivitySupplier
    // (FrogAi.getActivities()); the old overridable brainProvider() / mutate-
    // after-super makeBrain(Dynamic) hooks are gone. We build our own provider
    // with the two swapped sensors + the extra HAS_HUNTING_COOLDOWN memory, and
    // an activity supplier that augments vanilla's activity list. Lazy because
    // the sensor list resolves PFSensors DeferredHolders via get(): deferring to
    // the first makeBrain (entity construction) keeps it well after registration
    // rather than at class-init.
    @Nullable
    private static Brain.Provider<Frog> resourceBrainProvider;

    private static Brain.Provider<Frog> resourceBrainProvider() {
        Brain.Provider<Frog> provider = resourceBrainProvider;
        if (provider == null) {
            // Vanilla's frog sensor list with two swaps: FROG_ATTACKABLES -> our
            // category-filtering prey sensor (Q8 prey eligibility), FROG_TEMPTATIONS
            // -> our Sweetslime temptation sensor (frogs follow the item they breed
            // on, not loose slime balls).
            List<SensorType<? extends Sensor<? super Frog>>> sensors = List.of(
                SensorType.NEAREST_LIVING_ENTITIES,
                SensorType.HURT_BY,
                PFSensors.RESOURCE_FROG_ATTACKABLES.get(),
                PFSensors.RESOURCE_FROG_TEMPTATIONS.get(),
                SensorType.IS_IN_WATER
            );
            // HAS_HUNTING_COOLDOWN is not required by any vanilla frog behavior, so
            // the auto-memory-registration the 2-arg provider relies on never adds
            // it. Inject it explicitly (the deprecated 3-arg provider form) so it
            // becomes our Appetite-scaled eat cooldown (set in startEatCooldown);
            // ResourceFrogAttackablesSensor refuses to surface prey while present.
            provider = Brain.<Frog>provider(
                List.of(MemoryModuleType.HAS_HUNTING_COOLDOWN),
                sensors,
                body -> resourceActivities()
            );
            resourceBrainProvider = provider;
        }
        return provider;
    }

    @Override
    protected Brain<Frog> makeBrain(Brain.Packed packed) {
        return resourceBrainProvider().makeBrain(this, packed);
    }

    /**
     * Augment vanilla's frog activity list with our two additions, preserving
     * every other activity (and every vanilla condition / erase-set) intact:
     * <ul>
     *   <li>IDLE / SWIM gain a same-species {@code AnimalMakeLove} keyed to the
     *       Resource Frog EntityType - vanilla's is keyed to {@code EntityType.FROG}
     *       and would never pair two Resource Frogs - at priority 0, alongside the
     *       vanilla pacing.</li>
     *   <li>LAY_SPAWN gains our category-aware lay behavior at priority 2 (vanilla's
     *       runs at priority 3). When love-mode completes our behavior fires first,
     *       places a Primed Frog Egg block of the matching category (stamping the
     *       captured offspring stats), and erases IS_PREGNANT - preventing vanilla's
     *       behavior from placing a second (vanilla) frogspawn.</li>
     * </ul>
     * Rebuilding the matching {@link ActivityData} (rather than appending a second
     * ActivityData for the same activity) keeps vanilla's gating conditions: a bare
     * extra IDLE/SWIM/LAY_SPAWN entry would overwrite the land/water requirements
     * and strand a frog in the wrong activity. {@code FrogAi.getActivities()} is
     * exposed via the access transformer.
     */
    @SuppressWarnings("unchecked")
    private static List<ActivityData<Frog>> resourceActivities() {
        AnimalMakeLove resourceMakeLove =
            new AnimalMakeLove((EntityType<? extends Animal>) (EntityType<?>) PFEntities.RESOURCE_FROG.get());
        List<ActivityData<Frog>> out = new java.util.ArrayList<>();
        for (ActivityData<Frog> data : FrogAi.getActivities()) {
            Activity activity = data.activityType();
            if (activity == Activity.IDLE || activity == Activity.SWIM) {
                out.add(withExtraBehavior(data, 0, resourceMakeLove));
            } else if (activity == Activity.LAY_SPAWN) {
                out.add(withExtraBehavior(data, 2, LayCategoryFrogspawn.create()));
            } else {
                out.add(data);
            }
        }
        return out;
    }

    /** Copy {@code data} with one extra prioritized behavior merged into its list. */
    private static ActivityData<Frog> withExtraBehavior(
            ActivityData<Frog> data, int priority, BehaviorControl<? super Frog> behavior) {
        ImmutableList<Pair<Integer, ? extends BehaviorControl<? super Frog>>> merged =
            ImmutableList.<Pair<Integer, ? extends BehaviorControl<? super Frog>>>builder()
                .addAll(data.behaviorPriorityPairs())
                .add(Pair.of(priority, behavior))
                .build();
        return ActivityData.create(
            data.activityType(), merged, data.conditions(), data.memoriesToEraseWhenStopped());
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
            effectiveAppetite(), PFConfig.appetiteCooldownMin(), PFConfig.appetiteCooldownMax(), PFConfig.statCap());
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
