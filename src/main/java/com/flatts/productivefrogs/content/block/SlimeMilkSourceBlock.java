package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * Slime Milk's placeable form. As of the 26.1 re-implementation (R-1) there is a
 * <b>single</b> {@code slime_milk_source} block (not per-variant): the variant it
 * spawns rides on its {@link SlimeMilkSourceBlockEntity}, seeded from the placing
 * bucket's {@code SLIME_VARIANT} component
 * ({@link com.flatts.productivefrogs.content.item.SlimeMilkBucketItem#checkExtraContent}).
 * Subclasses {@link LiquidBlock} for the fluid render and is an {@link EntityBlock}
 * so the BE can store the spawn economy + catalyst upgrades. This mirrors the Mimic
 * Milk source block; see {@code docs/port_mc_26_1_reimplementation.md} (R-1).
 *
 * <p><b>Source-only / never spreads</b> (maintainer decision 2026-06-29): the
 * {@code slime_milk} fluid refuses all spread ({@link com.flatts.productivefrogs.content.fluid.SlimeMilkFluid}),
 * so every Slime Milk block is a BE-backed source. A source placed without a
 * variant (e.g. {@code /setblock}, or a tank-mod raw {@code setBlock}) has a null
 * BE variant and is inert decoration - no spawn, no tint.
 *
 * <p><b>Spawn economy (v1.7):</b> remaining-spawn count, speed level, quantity
 * level, and the infinite flag all live on the {@link SlimeMilkSourceBlockEntity}.
 * Players buff a placed source by dropping catalyst items into the pool;
 * {@link #entityInside} consumes them. See {@code docs/slime_milk_catalysts.md}.
 */
public class SlimeMilkSourceBlock extends LiquidBlock implements EntityBlock, LiquidBlockContainer {

    /**
     * Fallback starting spawn budget used only before COMMON config loads (the
     * real default is {@link PFConfig#DEPLETION_COUNT}). Was the blockstate
     * property's max in pre-v1.7 builds; kept as the pre-config fallback.
     */
    public static final int MAX_SPAWNS_REMAINING = 16;

    /**
     * Sentinel variant ids that spawn a vanilla Slime / MagmaCube instead of a
     * {@link ResourceSlime}. Matched as full ResourceLocations (namespace + path),
     * so a datapack variant that happens to use the path {@code vanilla} or
     * {@code magma} in its own namespace is NOT mistaken for these.
     */
    private static final Identifier VANILLA_SENTINEL =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "vanilla");
    private static final Identifier MAGMA_SENTINEL =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "magma");

    /**
     * Offsets to the 26 neighbours, ordered to prefer natural "rim" spawn spots:
     * same-y plane (cardinals then diagonals), then below plane, then above.
     */
    private static final int[][] NEIGHBOUR_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
        {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
        {0, -1, 0},
        {1, -1, 1}, {1, -1, -1}, {-1, -1, 1}, {-1, -1, -1},
        {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
        {0, 1, 0},
        {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
    };

    /**
     * Test-only override for {@link PFConfig#DEPLETION_ENABLED}. Volatile so a
     * test thread's write is visible to the server thread running the tick.
     */
    @Nullable
    public static volatile Boolean depletionEnabledOverride = null;

    /**
     * Test-only override for {@link PFConfig#maxNearbySlimes()} so a GameTest can
     * exercise the density cap with a small count instead of spawning 30 entities.
     * Volatile for cross-thread visibility, like {@link #depletionEnabledOverride}.
     */
    @Nullable
    public static volatile Integer spawnCapOverride = null;

    public SlimeMilkSourceBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlimeMilkSourceBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Only source blocks spawn, and only server-side. The variant + spawn
        // budget are seeded onto the BE by the placing bucket's checkExtraContent
        // (which runs right after onPlace); onPlace just kicks off the spawn-tick
        // loop. A source with no variant stays inert (its tick short-circuits).
        if (level instanceof ServerLevel serverLevel && level.getFluidState(pos).isSource()) {
            scheduleNextSpawnTick(serverLevel, pos, level.getRandom(), 0);
        }
    }

    /**
     * Reject every fluid (#235). A milk source is a {@link LiquidBlock} with no
     * collision, so vanilla's {@code canHoldFluid} would otherwise let a neighbouring
     * water / lava / modded fluid flow in and overwrite it. Milk is source-only and
     * never spreads (placed via the bucket's {@code setBlock}, not fluid flow), so no
     * fluid should ever flow into this cell - returning {@code false} uniformly keeps
     * this consistent with {@link #placeLiquid} (which always refuses) and protects
     * the source's spawn-economy BE from any displacement.
     */
    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos,
                                  BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        // The Slime Milk fluid never spreads, so this is only reached defensively;
        // never overwrite a real source cell (it owns the spawn-economy BE).
        return false;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getFluidState(pos).isSource()) {
            return;
        }
        // Inert unless this source carries a variant on its BE. A source placed
        // without one (e.g. /setblock) just sits as decoration: no spawn, no
        // depletion, no reschedule.
        SlimeMilkSourceBlockEntity be = getSourceBE(level, pos);
        Identifier variantId = be == null ? null : be.getVariantId();
        if (variantId == null) {
            return;
        }

        boolean depleting = depletionEnabled() && !be.isInfinite();
        if (depleting && be.getSpawnsRemaining() <= 0) {
            // Already exhausted before this tick (e.g. placed from a re-bucketed
            // empty source, or loaded at 0): drain without spawning.
            drainToAir(level, pos, variantId);
            return;
        }
        // Density cap: pause (WITHOUT spending the budget) when this source's own
        // species already crowds the area, so an automated / Endless / Rapid source
        // can't flood the server with slimes faster than frogs eat them. Reschedule
        // so it resumes once the count drops back below the cap. (chooseSpawnPos also
        // skips when no adjacent block is free, a second emergent limit.)
        if (PFConfig.spawnCapEnabled() && isAreaCrowded(level, pos, variantId)) {
            PFDebug.logOnce(PFDebug.Area.MILK_SOURCE, "capped#" + pos, () -> String.format(
                "source @%s: paused, >= %d nearby %s slimes (cap)", pos, PFConfig.maxNearbySlimes(), variantId));
            scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
            return;
        }
        int spawned = spawnBatch(level, pos, random, variantId, be);
        if (spawned == 0) {
            // No slime could be placed (all neighbour cells blocked). Pause WITHOUT
            // spending the budget (like the density cap) and retry on the next
            // scheduled tick.
            scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
            return;
        }
        if (depleting) {
            be.decrementSpawns();
            // If that spawn was this source's last, drain in the SAME tick rather
            // than rescheduling and draining one full interval later.
            if (be.getSpawnsRemaining() <= 0) {
                drainToAir(level, pos, variantId);
                return;
            }
        }
        scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
    }

    /**
     * Drain a source by swapping it to air. A plain {@code removeBlock} on a fluid
     * block round-trips the fluid back to a default-state source, so the drain must
     * set {@link Blocks#AIR} explicitly.
     */
    private static void drainToAir(ServerLevel level, BlockPos pos, Identifier variantId) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
            "source @%s: depleted, drained to air (variant=%s)", pos, variantId));
    }

    /**
     * True when at least {@link PFConfig#maxNearbySlimes()} Resource Slimes of this
     * source's own species are already within {@link PFConfig#spawnCapRadius()} of
     * it. Counts only PF {@link ResourceSlime}s of the matching {@link Category}
     * (vanilla slimes don't count); if the variant's category can't be resolved
     * (e.g. a sentinel source), counts any ResourceSlime so the cap still bounds it.
     * Public since Phase 3: the Slime Milk Basin shares this exact check.
     */
    public static boolean isAreaCrowded(ServerLevel level, BlockPos pos, Identifier variantId) {
        Category category = categoryForVariant(level, variantId);
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(pos).inflate(PFConfig.spawnCapRadius());
        java.util.List<ResourceSlime> nearby = level.getEntitiesOfClass(
            ResourceSlime.class, box,
            category == null ? slime -> true : slime -> slime.getCategory() == category);
        Integer capOverride = spawnCapOverride;
        int cap = capOverride != null ? capOverride : PFConfig.maxNearbySlimes();
        return nearby.size() >= cap;
    }

    /** Resolve a variant from the registry, or null (registry absent / unknown id). */
    @Nullable
    private static SlimeVariant variantFor(net.minecraft.world.level.Level level, Identifier variantId) {
        return PFRegistries.variant(level.registryAccess(), variantId);
    }

    @Nullable
    private static Category categoryForVariant(ServerLevel level, Identifier variantId) {
        SlimeVariant variant = variantFor(level, variantId);
        return variant == null ? null : variant.category();
    }


    private static boolean depletionEnabled() {
        Boolean override = depletionEnabledOverride;
        return override != null ? override : PFConfig.DEPLETION_ENABLED.get();
    }

    /**
     * Spawn {@code 1 + quantityLevel} slimes this spawn event (Quantity catalyst).
     * Quantity multiplies yield per event; the remaining-spawn counter is still
     * decremented once per event by the caller, so Quantity is strictly additive
     * to throughput and does not burn extra count.
     */
    private int spawnBatch(ServerLevel level, BlockPos pos, RandomSource random,
                           Identifier variantId, SlimeMilkSourceBlockEntity be) {
        int quantity = MilkSpawnEconomy.batchQuantity(be.getQuantityLevel());
        int spawned = 0;
        for (int i = 0; i < quantity; i++) {
            if (spawn(level, pos, random, variantId, false)) {
                spawned++;
            }
        }
        return spawned;
    }

    /**
     * Reschedule the next spawn tick, applying the Speed catalyst: the base
     * {@code [min, max]} interval is reduced by {@code speedLevel *
     * speedReductionPerLevel}, clamped down to {@code minIntervalFloorTicks} so
     * stacked Speed levels can't drive the cadence to zero.
     */
    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random, int speedLevel) {
        int delay = MilkSpawnEconomy.intervalTicks(speedLevel, random);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    /** Spawn one slime; returns false (no spawn) when {@code avoidSourceCell} and no free neighbour exists. */
    private boolean spawn(ServerLevel level, BlockPos pos, RandomSource random, Identifier variantId,
                          boolean avoidSourceCell) {
        BlockPos spawnPos = chooseSpawnPos(level, pos, avoidSourceCell);
        if (spawnPos == null) {
            // Altar-gated boss source with no free neighbour cell: refuse to spawn
            // inside the (enclosed) milk source block. The caller pauses without
            // spending the spawn budget.
            PFDebug.logOnce(PFDebug.Area.MILK_SOURCE, "noroom#" + pos, () -> String.format(
                "source @%s: no free cell for %s, skipped (would spawn inside the milk source block)", pos, variantId));
            return false;
        }
        Slime slime = createSlimeForVariant(level, variantId);
        if (slime == null) {
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "source @%s: slime create failed for variant=%s (skip)", pos, variantId));
            return false;
        }
        slime.setSize(1, true);
        slime.snapTo(spawnPos.getX() + 0.5,
                     spawnPos.getY(),
                     spawnPos.getZ() + 0.5,
                     random.nextFloat() * 360F,
                     0F);
        level.addFreshEntity(slime);
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
            "source @%s: spawned %s slime at %s (%s)", pos, variantId, spawnPos,
            spawnPos.equals(pos) ? "inside-fluid fallback" : "on neighbour"));
        return true;
    }

    /**
     * Consume a Slime Milk catalyst dropped into a real (variant-carrying) source
     * pool, applying its upgrade to the BlockEntity. Fires only while an entity
     * overlaps the block, so it costs nothing for idle sources.
     *
     * <p>Gates, in order: server-side only; catalysts globally enabled; the entity
     * is a catalyst {@link ItemEntity}; this is an actual source (not spread milk)
     * with a variant; and Count/Infinite only apply when depletion is on (else they
     * would be no-ops). An upgrade that's already maxed is left unconsumed.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
                                InsideBlockEffectApplier effectApplier, boolean isPrecise) {
        super.entityInside(state, level, pos, entity, effectApplier, isPrecise);
        if (level.isClientSide() || !PFConfig.milkCatalystsEnabled()) {
            return;
        }
        if (!(entity instanceof ItemEntity itemEntity) || !state.getFluidState().isSource()) {
            return;
        }
        ItemStack stack = itemEntity.getItem();
        MilkCatalyst catalyst = MilkCatalyst.fromStack(stack);
        if (catalyst == null) {
            return;
        }
        // Per-catalyst gate (#201): a config-disabled catalyst is inert - leave the
        // item floating for the player rather than consuming it for no effect.
        if (!catalyst.isEnabled()) {
            return;
        }
        SlimeMilkSourceBlockEntity be = getSourceBE(level, pos);
        if (be == null || be.getVariantId() == null) {
            return;
        }
        // Count / Infinite are meaningless when depletion is globally off; leave
        // those catalysts unconsumed rather than burning them for no effect.
        if ((catalyst == MilkCatalyst.COUNT || catalyst == MilkCatalyst.INFINITE) && !depletionEnabled()) {
            return;
        }
        if (!be.applyCatalyst(catalyst)) {
            return;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(stack);
        }
        // Only the infinite flag is client-synced (it drives the animateTick
        // glint); Count/Speed/Quantity are server-only. Push the update only when
        // an Infinite catalyst just landed.
        if (catalyst == MilkCatalyst.INFINITE) {
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS,
            0.7F, 1.3F + level.getRandom().nextFloat() * 0.2F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.0);
        }
        PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
            "source @%s: consumed %s catalyst (speed=%d quantity=%d infinite=%s remaining=%d)",
            pos, catalyst, be.getSpeedLevel(), be.getQuantityLevel(), be.isInfinite(), be.getSpawnsRemaining()));
    }

    @Nullable
    private static SlimeMilkSourceBlockEntity getSourceBE(LevelAccessor level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SlimeMilkSourceBlockEntity be ? be : null;
    }

    /**
     * Client-side ambient glint for an Infinite (Endless catalyst) source: a faint
     * slow-rising mote so the "never runs dry" state reads at a glance. Reads the
     * infinite flag off the BE, synced from {@code SlimeMilkSourceBlockEntity#getUpdateTag}.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (!state.getFluidState().isSource() || random.nextInt(6) != 0) {
            return;
        }
        SlimeMilkSourceBlockEntity be = getSourceBE(level, pos);
        if (be != null && be.isInfinite()) {
            level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.25 + random.nextDouble() * 0.5,
                pos.getY() + 0.9,
                pos.getZ() + 0.25 + random.nextDouble() * 0.5,
                0.0, 0.015, 0.0);
        }
    }

    /**
     * Map the variant id to the slime it produces. The {@code productivefrogs:vanilla}
     * / {@code productivefrogs:magma} sentinels spawn a vanilla Slime / MagmaCube;
     * every other id is looked up as a {@link ResourceSlime} variant (the slime
     * resolves its category from the {@code slime_variant} registry on setVariant).
     */
    @Nullable
    public static Slime createSlimeForVariant(ServerLevel level, Identifier variantId) {
        if (VANILLA_SENTINEL.equals(variantId)) {
            return EntityType.SLIME.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        }
        if (MAGMA_SENTINEL.equals(variantId)) {
            return EntityType.MAGMA_CUBE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        }
        // A real variant can opt into a custom spawn entity (a modded Slime
        // subtype) via its slime_variant JSON's optional spawn_entity field.
        Slime custom = createCustomSpawnEntity(level, variantId);
        if (custom != null) {
            return custom;
        }
        ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
        if (resource == null) {
            return null;
        }
        resource.setVariant(variantId);
        return resource;
    }

    /**
     * Spawn the variant's optional {@code spawn_entity} (a modded Slime subtype)
     * if it declares one, else null so the caller falls back to a
     * {@link ResourceSlime}. The data-driven extension point for cross-mod
     * variants whose parent is a modded slime.
     */
    @Nullable
    private static Slime createCustomSpawnEntity(ServerLevel level, Identifier variantId) {
        SlimeVariant variant = PFRegistries.variant(level.registryAccess(), variantId);
        if (variant == null || variant.spawnEntity().isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(variant.spawnEntity().get())
            .map(type -> type.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED))
            .filter(Slime.class::isInstance)
            .map(Slime.class::cast)
            .orElse(null);
    }

    /**
     * Re-bucketing, the inverse of placement: read the source's variant + the full
     * upgrade set from its BE (before super removes the block) and stamp them onto
     * the filled bucket so they survive the world -> bucket round-trip. The variant
     * now rides the {@code SLIME_VARIANT} component (R-1, single fluid), so it must
     * be stamped explicitly here (the bucket no longer carries it by item identity).
     */
    @Override
    public ItemStack pickupBlock(@Nullable LivingEntity player, LevelAccessor level, BlockPos pos, BlockState state) {
        SlimeMilkSourceBlockEntity be = getSourceBE(level, pos);
        Identifier variantId = be != null ? be.getVariantId() : null;
        int remaining = be != null ? be.getSpawnsRemaining() : 0;
        int capacity = be != null ? be.getSpawnsCapacity() : 0;
        int speed = be != null ? be.getSpeedLevel() : 0;
        int quantity = be != null ? be.getQuantityLevel() : 0;
        boolean infinite = be != null && be.isInfinite();
        // super returns the single slime_milk bucket (this block's fluid.getBucket()).
        // Stamp the variant + the catalyst/budget upgrades so a variant-carrying,
        // possibly-buffed source survives the world -> bucket round-trip. An inert
        // source with no variant must not stamp misleading values.
        ItemStack bucket = super.pickupBlock(player, level, pos, state);
        if (variantId != null && !bucket.isEmpty()) {
            bucket.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
            bucket.set(PFDataComponents.SPAWNS_REMAINING.get(), remaining);
            bucket.set(PFDataComponents.MILK_CAPACITY.get(), capacity);
            if (speed > 0) {
                bucket.set(PFDataComponents.MILK_SPEED.get(), speed);
            }
            if (quantity > 0) {
                bucket.set(PFDataComponents.MILK_QUANTITY.get(), quantity);
            }
            if (infinite) {
                bucket.set(PFDataComponents.MILK_INFINITE.get(), true);
            }
        }
        return bucket;
    }

    /**
     * Pick spawn slot: first 3x3x3 neighbour whose top face is sturdy and whose
     * block-above is non-motion-blocking. Otherwise fall back to the source's own
     * (no-collision milk) cell - <b>except</b> when {@code avoidSourceCell} is set,
     * in which case return {@code null} to skip the spawn entirely.
     *
     * <p>{@code avoidSourceCell} is true for an altar-gated boss source: its milk
     * source block is sealed inside the 6-face catalyst altar, so the source-cell
     * fallback would spawn the slime <i>inside the milk source block</i>. See
     * docs/boss_catalyst_altar.md.
     */
    @Nullable
    private static BlockPos chooseSpawnPos(ServerLevel level, BlockPos source, boolean avoidSourceCell) {
        for (int[] off : NEIGHBOUR_OFFSETS) {
            BlockPos neighbour = source.offset(off[0], off[1], off[2]);
            BlockPos above = neighbour.above();
            // For an altar-gated boss source, never land in the source's own cell.
            if (avoidSourceCell && above.equals(source)) {
                continue;
            }
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, Direction.UP)
                && !level.getBlockState(above).blocksMotion()) {
                return above;
            }
        }
        return avoidSourceCell ? null : source;
    }
}
