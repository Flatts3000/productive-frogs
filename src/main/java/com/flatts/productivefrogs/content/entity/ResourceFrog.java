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

    // Frog stats - the two-layer Talent + Training model (docs/frog_stats_redesign.md).
    // LIVE trained stats: what all behavior reads (eat cadence, drop count, scan
    // radius). Clamped to [STAT_MIN, talent] on every read/write. Synced for Jade
    // + the cosmetic render tier; persisted in NBT.
    private static final EntityDataAccessor<Integer> DATA_APPETITE =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_BOUNTY =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REACH =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);

    // TALENT ceilings: the max each live stat can be trained to. Set by breeding
    // (inheritance) or the non-bred default; raised only by breeding a higher
    // bloodline. Clamped to [STAT_MIN, statCap]. Synced so Jade shows live/talent.
    private static final EntityDataAccessor<Integer> DATA_TALENT_APPETITE =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TALENT_BOUNTY =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_TALENT_REACH =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);

    // Cumulative training XP (earned by eating slimes) and the Training Focus
    // (which stat earned points pour into). Synced for the Jade level/focus lines.
    private static final EntityDataAccessor<Integer> DATA_TRAINING_XP =
        SynchedEntityData.defineId(ResourceFrog.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_FOCUS =
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
        builder.define(DATA_TALENT_APPETITE, FrogStats.STAT_MIN);
        builder.define(DATA_TALENT_BOUNTY, FrogStats.STAT_MIN);
        builder.define(DATA_TALENT_REACH, FrogStats.STAT_MIN);
        builder.define(DATA_TRAINING_XP, 0);
        builder.define(DATA_FOCUS, FrogFocus.AUTO.ordinal());
    }

    private static int statCap() {
        return PFConfig.statCap();
    }

    // ---- talent ceilings (bred; clamped to [STAT_MIN, statCap]) ----

    public int getTalentAppetite() {
        return FrogStats.clamp(this.entityData.get(DATA_TALENT_APPETITE), statCap());
    }

    public int getTalentBounty() {
        return FrogStats.clamp(this.entityData.get(DATA_TALENT_BOUNTY), statCap());
    }

    public int getTalentReach() {
        return FrogStats.clamp(this.entityData.get(DATA_TALENT_REACH), statCap());
    }

    public void setTalentAppetite(int value) {
        this.entityData.set(DATA_TALENT_APPETITE, FrogStats.clamp(value, statCap()));
    }

    public void setTalentBounty(int value) {
        this.entityData.set(DATA_TALENT_BOUNTY, FrogStats.clamp(value, statCap()));
    }

    public void setTalentReach(int value) {
        this.entityData.set(DATA_TALENT_REACH, FrogStats.clamp(value, statCap()));
    }

    // ---- live trained stats (behavior reads these; clamped to [STAT_MIN, talent]) ----

    public int getAppetite() {
        return FrogStats.clamp(this.entityData.get(DATA_APPETITE), getTalentAppetite());
    }

    public int getBounty() {
        return FrogStats.clamp(this.entityData.get(DATA_BOUNTY), getTalentBounty());
    }

    public int getReach() {
        return FrogStats.clamp(this.entityData.get(DATA_REACH), getTalentReach());
    }

    public void setAppetite(int value) {
        this.entityData.set(DATA_APPETITE, FrogStats.clamp(value, getTalentAppetite()));
    }

    public void setBounty(int value) {
        this.entityData.set(DATA_BOUNTY, FrogStats.clamp(value, getTalentBounty()));
    }

    public void setReach(int value) {
        this.entityData.set(DATA_REACH, FrogStats.clamp(value, getTalentReach()));
    }

    /**
     * Set the three <b>talent</b> ceilings and mark stats as established. Live
     * trained stats are left untouched (they stay clamped to the new talents on
     * next read). Used by the bred-lineage maturation paths
     * ({@link ResourceTadpole#ageUp()}, the Incubator) to give a freshly matured
     * frog its inherited ceiling while it starts at live baseline.
     */
    public void setTalents(int appetite, int bounty, int reach) {
        setTalentAppetite(appetite);
        setTalentBounty(bounty);
        setTalentReach(reach);
        this.statsInitialized = true;
    }

    /** Set the three live trained stats (each clamped to its talent). */
    public void setLiveStats(int appetite, int bounty, int reach) {
        setAppetite(appetite);
        setBounty(bounty);
        setReach(reach);
    }

    /**
     * Convenience: a <b>fully-realized</b> frog at exactly these stats - talent
     * AND live set to each value, so the frog behaves at them immediately. Used
     * by tests, commands, and any path that wants a frog at known effective stats
     * without running the training grind. (The breeding/training paths use
     * {@link #setTalents} + training instead.)
     */
    public void setStats(int appetite, int bounty, int reach) {
        setTalents(appetite, bounty, reach);
        setLiveStats(appetite, bounty, reach);
    }

    // ---- training XP + level + focus ----

    /** Cumulative training XP earned by eating slimes (never negative). */
    public int getTrainingXp() {
        return Math.max(0, this.entityData.get(DATA_TRAINING_XP));
    }

    public void setTrainingXp(int value) {
        this.entityData.set(DATA_TRAINING_XP, Math.max(0, value));
    }

    /** Training level = total stat points earned (one per {@code trainingXpPerLevel}). Drives the Jade headline. */
    public int getTrainingLevel() {
        return FrogStats.trainingLevel(getTrainingXp(), PFConfig.trainingXpPerLevel());
    }

    public FrogFocus getFocus() {
        return FrogFocus.byOrdinal(this.entityData.get(DATA_FOCUS));
    }

    public void setFocus(FrogFocus focus) {
        this.entityData.set(DATA_FOCUS, focus.ordinal());
    }

    /** Cycle to the next Training Focus (sneak-interact). Returns the new focus. */
    public FrogFocus cycleFocus() {
        FrogFocus next = getFocus().next();
        setFocus(next);
        return next;
    }

    /** Sum of the three live trained stats (range {@code 3 * STAT_MIN .. 3 * statCap}). Drives the cosmetic tier. */
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
        // Live trained stats.
        tag.putInt("Appetite", getAppetite());
        tag.putInt("Bounty", getBounty());
        tag.putInt("Reach", getReach());
        // Talent ceilings (their presence is the migration marker on read).
        tag.putInt("TalentAppetite", getTalentAppetite());
        tag.putInt("TalentBounty", getTalentBounty());
        tag.putInt("TalentReach", getTalentReach());
        tag.putInt("TrainingXp", getTrainingXp());
        tag.putInt("Focus", getFocus().ordinal());
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
            // A frog persisted with stats is established. Read talents first so
            // the live-stat setters clamp against the right ceiling. Getters clamp,
            // so a tampered save can't inject out-of-range values.
            if (tag.contains("TalentAppetite", net.minecraft.nbt.Tag.TAG_INT)) {
                // Post-redesign save: talents and live stats are stored separately.
                setTalents(tag.getInt("TalentAppetite"), tag.getInt("TalentBounty"), tag.getInt("TalentReach"));
                setLiveStats(tag.getInt("Appetite"), tag.getInt("Bounty"), tag.getInt("Reach"));
                setTrainingXp(tag.getInt("TrainingXp"));
                setFocus(FrogFocus.byOrdinal(tag.getInt("Focus")));
            } else {
                // Migration (R7): a pre-redesign save holds only the old "final"
                // stats. Keep the frog's earned power (live = stored), set its
                // talent ceiling to where it already is (>= the non-bred default),
                // and seed training XP so its level reads as fully-trained-for-talent
                // rather than 0. Non-breaking: the frog plays exactly as before.
                int default_ = PFConfig.nonBredTalentDefault();
                int liveA = tag.getInt("Appetite");
                int liveB = tag.getInt("Bounty");
                int liveR = tag.getInt("Reach");
                setTalents(Math.max(liveA, default_), Math.max(liveB, default_), Math.max(liveR, default_));
                setLiveStats(liveA, liveB, liveR);
                int points = (getAppetite() - FrogStats.STAT_MIN)
                    + (getBounty() - FrogStats.STAT_MIN)
                    + (getReach() - FrogStats.STAT_MIN);
                setTrainingXp(points * PFConfig.trainingXpPerLevel());
                setFocus(FrogFocus.AUTO);
            }
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
        // A non-bred frog starts at live baseline (1/1/1) with the non-bred talent
        // ceiling, trainable up to that ceiling by eating slimes; breeding raises
        // the ceiling further (R5, docs/frog_stats_redesign.md).
        int talent = PFConfig.nonBredTalentDefault();
        setTalents(talent, talent, talent);
        setLiveStats(FrogStats.STAT_MIN, FrogStats.STAT_MIN, FrogStats.STAT_MIN);
        PFDebug.log(PFDebug.Area.LIFECYCLE, () -> String.format(
            "baseline stats: non-bred frog category=%s -> live A%d/B%d/R%d talent %d",
            getCategory(), getAppetite(), getBounty(), getReach(), talent));
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
        // Training Focus cycle (R4): sneak + empty main hand cycles which stat this
        // frog's earned training points pour into. Gated by the stat layer and the
        // focus toggle; off -> falls through to vanilla (sneak-empty does nothing
        // on a frog). Main hand only so it doesn't double-fire across both hands.
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && stack.isEmpty()
                && PFConfig.frogStatsEnabled() && PFConfig.trainingFocusEnabled()) {
            if (this.level().isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            FrogFocus focus = cycleFocus();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "productivefrogs.frog.focus_set",
                net.minecraft.network.chat.Component.translatable("productivefrogs.frog.focus." + focus.id())), true);
            return InteractionResult.SUCCESS;
        }
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
        // A direct-feed is an eat - it trains the frog exactly like a tongue kill.
        addTrainingXp(PFConfig.trainingXpPerEat());
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
        // The pendingOffspring* payload is the offspring's inherited TALENTS (the
        // ceilings it can be trained to); it matures at live baseline and trains up.
        // Stat layer off (#202): stamp the non-bred default talent so a frog bred by
        // any path carries a usable ceiling rather than a hidden rolled value.
        if (!PFConfig.frogStatsEnabled()) {
            int t = PFConfig.nonBredTalentDefault();
            this.pendingOffspringAppetite = t;
            this.pendingOffspringBounty = t;
            this.pendingOffspringReach = t;
            this.hasPendingOffspring = true;
            return;
        }
        double improvement = PFConfig.improvementChance();
        int cap = PFConfig.statCap();
        // Inherit TALENTS from the parents' talents - no regression (R2): the
        // offspring's ceiling is never below the better parent's.
        this.pendingOffspringAppetite =
            FrogStats.inheritTalent(getTalentAppetite(), mate.getTalentAppetite(), improvement, cap, random);
        this.pendingOffspringBounty =
            FrogStats.inheritTalent(getTalentBounty(), mate.getTalentBounty(), improvement, cap, random);
        this.pendingOffspringReach =
            FrogStats.inheritTalent(getTalentReach(), mate.getTalentReach(), improvement, cap, random);
        this.hasPendingOffspring = true;
        PFDebug.log(PFDebug.Area.EGG, () -> String.format(
            "conception: %s parent talents A(%d,%d) B(%d,%d) R(%d,%d) -> offspring talent A%d/B%d/R%d",
            getCategory(), getTalentAppetite(), mate.getTalentAppetite(),
            getTalentBounty(), mate.getTalentBounty(), getTalentReach(), mate.getTalentReach(),
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
     * Award training XP (earned by eating a slime) and allocate any newly-earned
     * stat points into live stats per the Training Focus, each climbing toward its
     * talent ceiling (docs/frog_stats_redesign.md). No-op when the stat layer is
     * off or {@code amount <= 0}. Called from {@link FrogTongueDropHandler} on a
     * tongue kill and from {@link #mobInteract} on a direct-feed.
     */
    public void addTrainingXp(int amount) {
        if (!PFConfig.frogStatsEnabled() || amount <= 0) {
            return;
        }
        setTrainingXp(getTrainingXp() + amount);
        allocateTrainingPoints();
    }

    /** Allocate every earned-but-unspent point (earned levels minus points already in live stats). */
    private void allocateTrainingPoints() {
        int earned = getTrainingLevel();
        int allocated = (getAppetite() - FrogStats.STAT_MIN)
            + (getBounty() - FrogStats.STAT_MIN)
            + (getReach() - FrogStats.STAT_MIN);
        int available = earned - allocated;
        boolean grew = false;
        while (available > 0 && allocateOnePoint()) {
            available--;
            grew = true;
        }
        if (grew && this.level() instanceof ServerLevel server) {
            server.playSound(null, blockPosition(), net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.NEUTRAL, 0.4F, 1.4F);
            PFDebug.log(PFDebug.Area.LIFECYCLE, () -> String.format(
                "training: %s -> live A%d/B%d/R%d (talent %d/%d/%d) lvl=%d",
                getCategory(), getAppetite(), getBounty(), getReach(),
                getTalentAppetite(), getTalentBounty(), getTalentReach(), getTrainingLevel()));
        }
    }

    /**
     * Raise one live stat by 1 toward its talent. Targets the Training Focus stat
     * when it has headroom; otherwise (Auto, or a focused stat already at its
     * talent) fills the stat with the most remaining headroom, so a point is never
     * wasted while any stat can still grow. Returns false when every live stat is
     * already at its talent (the frog is fully trained for its talent).
     */
    private boolean allocateOnePoint() {
        int headroomA = getTalentAppetite() - getAppetite();
        int headroomB = getTalentBounty() - getBounty();
        int headroomR = getTalentReach() - getReach();
        if (headroomA <= 0 && headroomB <= 0 && headroomR <= 0) {
            return false;
        }
        FrogFocus focus = PFConfig.trainingFocusEnabled() ? getFocus() : FrogFocus.AUTO;
        switch (focus) {
            case APPETITE -> { if (headroomA > 0) { setAppetite(getAppetite() + 1); return true; } }
            case BOUNTY -> { if (headroomB > 0) { setBounty(getBounty() + 1); return true; } }
            case REACH -> { if (headroomR > 0) { setReach(getReach() + 1); return true; } }
            default -> { }
        }
        // Auto, or the focused stat is already capped: fill the largest headroom.
        if (headroomA > 0 && headroomA >= headroomB && headroomA >= headroomR) {
            setAppetite(getAppetite() + 1);
        } else if (headroomB > 0 && headroomB >= headroomR) {
            setBounty(getBounty() + 1);
        } else {
            setReach(getReach() + 1);
        }
        return true;
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
