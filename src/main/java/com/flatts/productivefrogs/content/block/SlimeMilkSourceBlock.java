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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
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
 * Slime Milk's placeable form. As of v1.8 each variant has its own source block
 * ({@code <variant>_slime_milk}, minted by
 * {@link com.flatts.productivefrogs.registry.PFVariantMilk}), so the block carries
 * its variant baked in at registration ({@link #blockVariant()}). Subclasses
 * {@link LiquidBlock} for vanilla flow and is an {@link EntityBlock} so its
 * {@link SlimeMilkSourceBlockEntity} can store the spawn economy + catalyst upgrades.
 *
 * <p>The variant is authoritative on the block ({@link #effectiveVariant}); the BE
 * keeps a mirror, seeded in {@link #onPlace} so a tank-mod raw {@code setBlock}
 * placement still spawns the right variant. Catalyst/budget upgrades are written to
 * the BE on placement (by
 * {@link com.flatts.productivefrogs.content.item.SlimeMilkBucketItem#checkExtraContent})
 * and read back when re-bucketing ({@link #pickupBlock}). Only a source block with a
 * variant spawns slimes + tints; milk that spread from a source (fluid spreading does
 * not copy BlockEntities) carries no variant and is inert decoration.
 *
 * <p><b>Spawn economy (v1.7):</b> remaining-spawn count, speed level, quantity
 * level, and the infinite flag all live on the {@link SlimeMilkSourceBlockEntity}
 * (the counter used to be a blockstate property capped at 16 - moved off to let
 * Count catalysts raise it without bound). Players buff a placed source by
 * dropping catalyst items into the pool; {@link #entityInside} consumes them. See
 * {@code docs/slime_milk_catalysts.md}.
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
    private static final ResourceLocation VANILLA_SENTINEL =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "vanilla");
    private static final ResourceLocation MAGMA_SENTINEL =
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "magma");

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

    /**
     * The variant this block produces, baked in at registration (per-variant
     * fluids, v1.8). Authoritative over the BE's mirror, so a source placed by a
     * tank/pipe mod that never wrote the BE (e.g. JDT's raw {@code setBlock})
     * still spawns the right variant. Null for the legacy single source block.
     */
    @Nullable
    private final ResourceLocation blockVariant;

    public SlimeMilkSourceBlock(FlowingFluid fluid, Properties properties) {
        this(fluid, null, properties);
    }

    public SlimeMilkSourceBlock(FlowingFluid fluid, @Nullable ResourceLocation variant, Properties properties) {
        super(fluid, properties);
        this.blockVariant = variant;
    }

    /** The variant baked into this block at registration, or null for the legacy block. */
    @Nullable
    public ResourceLocation blockVariant() {
        return blockVariant;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlimeMilkSourceBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Only source blocks spawn. Don't schedule on the client. The spawn
        // budget is seeded onto the BE when its variant is set (see
        // SlimeMilkSourceBlockEntity#setVariantId), so onPlace just kicks off
        // the spawn-tick loop.
        if (!(level instanceof ServerLevel serverLevel) || !level.getFluidState(pos).isSource()) {
            return;
        }
        // Per-variant block: seed the BE from the block's baked-in variant so a
        // vanilla-bucket placement (no checkExtraContent) or a tank-mod setBlock
        // still spawns + tints correctly. setVariantId is idempotent (seedIfUnset),
        // so a re-bucketed source that already restored its budget is untouched.
        if (blockVariant != null && getSourceBE(level, pos) instanceof SlimeMilkSourceBlockEntity be
                && be.getVariantId() == null) {
            be.setVariantId(blockVariant);
        }
        scheduleNextSpawnTick(serverLevel, pos, level.getRandom(), 0);
    }

    /**
     * Reject every fluid except this source's own Slime Milk (#235). A milk source is
     * a {@link LiquidBlock} with no collision, so vanilla's
     * {@code FlowingFluid#canHoldFluid} (which returns {@code !blocksMotion()} for a
     * plain block) would otherwise let any neighbouring water, lava, or modded fluid
     * flow in and overwrite it - destroying the production pool. Implementing
     * {@link LiquidBlockContainer} routes that gate through here: we allow only our
     * own milk family and refuse all others, so a foreign fluid treats the source as
     * a wall instead of washing it away.
     *
     * <p>"Own milk" is matched by {@code FluidType} identity, not {@code Fluid#isSame}
     * (which is reference equality, so the milk's own <i>flowing</i> form would fail a
     * source-vs-flowing check): a variant's source and flowing fluids share one
     * per-variant FluidType ({@code PFVariantMilk}), and water / lava / a different
     * variant's milk each have a distinct type. This is the victim-side counterpart to
     * {@link com.flatts.productivefrogs.content.fluid.SlimeMilkFluid}'s aggressor-side
     * {@code canSpreadTo} guard.
     */
    @Override
    public boolean canPlaceLiquid(@Nullable Player player, BlockGetter level, BlockPos pos,
                                  BlockState state, Fluid fluid) {
        return fluid.getFluidType() == this.fluid.getFluidType();
    }

    /**
     * Only our own milk fluid ever reaches here ({@link #canPlaceLiquid} gates the rest
     * out). Mirror vanilla {@code FlowingFluid#spreadTo}'s non-container branch for that
     * case so milk's own flow into an existing flowing-milk neighbour keeps levelling:
     * write the milk fluid's legacy block and report {@code true} (placed).
     *
     * <p>Never overwrite a real source position (it owns the spawn-economy
     * {@link SlimeMilkSourceBlockEntity}); return {@code false} there, honouring the
     * {@link LiquidBlockContainer} contract ({@code true} == liquid was placed) so a
     * caller that checks the result (e.g. a modded pipe deciding whether to drain its
     * tank) isn't told a placement happened. Vanilla {@code spreadTo} / {@code BucketItem}
     * ignore the return, and fluid spread never targets a source cell, so this is a
     * defensive, contract-correct guard rather than a flow change.
     */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (state.getFluidState().isSource()) {
            return false;
        }
        level.setBlock(pos, fluidState.createLegacyBlock(), Block.UPDATE_ALL);
        return true;
    }

    /**
     * The variant this source produces: the block's baked-in variant (per-variant
     * fluids) wins, falling back to the BE mirror (legacy single block + the
     * re-bucket round-trip). Null = inert spread milk.
     */
    @Nullable
    private ResourceLocation effectiveVariant(@Nullable SlimeMilkSourceBlockEntity be) {
        if (blockVariant != null) {
            return blockVariant;
        }
        return be != null ? be.getVariantId() : null;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (!level.getFluidState(pos).isSource()) {
            return;
        }
        // Inert unless this source carries a variant. Milk that spread from a
        // bucket-placed source has no BE variant and just sits as decoration:
        // no spawn, no depletion, no reschedule.
        SlimeMilkSourceBlockEntity be = getSourceBE(level, pos);
        ResourceLocation variantId = effectiveVariant(be);
        if (variantId == null || be == null) {
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
        // Boss-tier altar gate (#184, docs/boss_catalyst_altar.md): a variant
        // with spawn_catalyst spawns nothing until the matching catalyst block
        // is on all six faces of the source. Pause WITHOUT spending the budget
        // (like the density cap above) and reschedule, so the altar can be
        // completed later without the source draining in the meantime.
        if (variantRequiresCatalyst(level, variantId) && catalystFaceCount(level, pos, variantId) < 6) {
            PFDebug.logOnce(PFDebug.Area.MILK_SOURCE, "altar#" + pos, () -> String.format(
                "source @%s: paused, %s altar incomplete (needs 6 catalyst faces)", pos, variantId));
            scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
            return;
        }
        int spawned = spawnBatch(level, pos, random, variantId, be);
        if (spawned == 0) {
            // No slime could be placed - an altar-gated boss source whose neighbour
            // cells are all blocked, where spawning into the source's own (sealed)
            // cell is refused. Pause WITHOUT spending the budget (like the density
            // cap) and retry on the next scheduled tick.
            scheduleNextSpawnTick(level, pos, random, be.getSpeedLevel());
            return;
        }
        if (depleting) {
            be.decrementSpawns();
            // If that spawn was this source's last, drain in the SAME tick rather
            // than rescheduling and draining one full interval later. Otherwise the
            // counter visibly hits 0 (Jade reads "0 / cap") while the block lingers
            // and fires one more scheduled tick before disappearing - an off-by-one
            // where the source looks empty yet is still standing.
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
    private static void drainToAir(ServerLevel level, BlockPos pos, ResourceLocation variantId) {
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
     */
    private static boolean isAreaCrowded(ServerLevel level, BlockPos pos, ResourceLocation variantId) {
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
    private static SlimeVariant variantFor(net.minecraft.world.level.Level level, ResourceLocation variantId) {
        var registry = level.registryAccess().registry(PFRegistries.SLIME_VARIANT).orElse(null);
        return registry == null ? null : registry.get(variantId);
    }

    @Nullable
    private static Category categoryForVariant(ServerLevel level, ResourceLocation variantId) {
        SlimeVariant variant = variantFor(level, variantId);
        return variant == null ? null : variant.category();
    }

    /**
     * Whether this variant is altar-gated ({@code spawn_catalyst}, #184). The
     * server gate (tick) and the Jade readout share this one lookup.
     */
    public static boolean variantRequiresCatalyst(net.minecraft.world.level.Level level, ResourceLocation variantId) {
        SlimeVariant variant = variantFor(level, variantId);
        return variant != null && variant.spawnCatalyst();
    }

    /**
     * Completed catalyst faces (0-6): how many of the six neighbours are the
     * catalyst block that arms this variant ({@link PFBlocks#catalystForVariant()}).
     * 6 means the altar is complete. A missing mapping (shouldn't happen for a
     * shipped boss variant) reads 0 - the gate fails closed, the source stays
     * paused rather than spawning ungated.
     */
    public static int catalystFaceCount(net.minecraft.world.level.Level level, BlockPos pos, ResourceLocation variantId) {
        Block catalyst = PFBlocks.catalystForVariant().get(variantId);
        if (catalyst == null) {
            return 0;
        }
        int count = 0;
        for (Direction dir : Direction.values()) {
            if (level.getBlockState(pos.relative(dir)).is(catalyst)) {
                count++;
            }
        }
        return count;
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
                           ResourceLocation variantId, SlimeMilkSourceBlockEntity be) {
        // An altar-gated boss source must never spawn into its own (sealed) cell.
        boolean avoidSourceCell = variantRequiresCatalyst(level, variantId);
        int quantity = MilkSpawnEconomy.batchQuantity(be.getQuantityLevel());
        int spawned = 0;
        for (int i = 0; i < quantity; i++) {
            if (spawn(level, pos, random, variantId, avoidSourceCell)) {
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
    private boolean spawn(ServerLevel level, BlockPos pos, RandomSource random, ResourceLocation variantId,
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
        slime.moveTo(spawnPos.getX() + 0.5,
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
     * overlaps the block, so it costs nothing for idle sources. A dropper aimed
     * into the pool feeds catalysts the same way (a small taste of automation).
     *
     * <p>Gates, in order: server-side only; catalysts globally enabled; the entity
     * is a catalyst {@link ItemEntity}; this is an actual source (not spread milk)
     * with a variant; and Count/Infinite only apply when depletion is on (else they
     * would be no-ops). An upgrade that's already maxed is left unconsumed so the
     * item floats for the player to retrieve rather than being silently eaten.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (level.isClientSide || !PFConfig.milkCatalystsEnabled()) {
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
        if (be == null || effectiveVariant(be) == null) {
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
        // glint); Count/Speed/Quantity are server-only, so a block update on
        // those consumes would re-render the fluid for no client-visible change.
        // Push the update only when an Infinite catalyst just landed.
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
     * slow-rising mote so the "never runs dry" state reads at a glance. Plain
     * sources and the other upgrade tiers emit nothing. Reads the infinite flag
     * off the BE, synced from {@code SlimeMilkSourceBlockEntity#getUpdateTag}.
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
    public static Slime createSlimeForVariant(ServerLevel level, ResourceLocation variantId) {
        // The two built-in specials are NOT registry variants (no primer, no
        // froglight, no spawn egg), so they are matched by sentinel id here
        // rather than via the registry - a deliberate, contained seam.
        if (VANILLA_SENTINEL.equals(variantId)) {
            return EntityType.SLIME.create(level);
        }
        if (MAGMA_SENTINEL.equals(variantId)) {
            return EntityType.MAGMA_CUBE.create(level);
        }
        // A real variant can opt into a custom spawn entity (a modded Slime
        // subtype) via its slime_variant JSON's optional spawn_entity field.
        Slime custom = createCustomSpawnEntity(level, variantId);
        if (custom != null) {
            return custom;
        }
        ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level);
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
    private static Slime createCustomSpawnEntity(ServerLevel level, ResourceLocation variantId) {
        var registry = level.registryAccess().registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) {
            return null;
        }
        SlimeVariant variant = registry.get(variantId);
        if (variant == null || variant.spawnEntity().isEmpty()) {
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getOptional(variant.spawnEntity().get())
            .map(type -> type.create(level))
            .filter(Slime.class::isInstance)
            .map(Slime.class::cast)
            .orElse(null);
    }

    /**
     * Re-bucketing, the inverse of placement: read the source's variant + the full
     * upgrade set from its BE (before super removes the block) and stamp them onto
     * the filled bucket so they survive the world -> bucket round-trip. Carrying
     * the upgrades stops a buffed source from resetting to a plain full source just
     * by re-bucketing it.
     */
    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        SlimeMilkSourceBlockEntity be = getSourceBE(level, pos);
        ResourceLocation variantId = effectiveVariant(be);
        int remaining = be != null ? be.getSpawnsRemaining() : 0;
        int capacity = be != null ? be.getSpawnsCapacity() : 0;
        int speed = be != null ? be.getSpeedLevel() : 0;
        int quantity = be != null ? be.getQuantityLevel() : 0;
        boolean infinite = be != null && be.isInfinite();
        // super returns the per-variant bucket (this block's fluid.getBucket()),
        // which already carries the variant via its item identity (v1.8) - no
        // SLIME_VARIANT component needed. Stamp the catalyst/budget upgrades so a
        // buffed source survives the world -> bucket round-trip. An inert
        // spread-milk grab has no variant and must not stamp misleading values.
        ItemStack bucket = super.pickupBlock(player, level, pos, state);
        if (variantId != null && !bucket.isEmpty()) {
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
     * fallback would spawn the slime <i>inside the milk source block</i>, trapping
     * it in the altar. A normal open pool keeps the fallback (its own cell is open
     * milk the slime can sit in). See docs/boss_catalyst_altar.md.
     */
    @Nullable
    private static BlockPos chooseSpawnPos(ServerLevel level, BlockPos source, boolean avoidSourceCell) {
        for (int[] off : NEIGHBOUR_OFFSETS) {
            BlockPos neighbour = source.offset(off[0], off[1], off[2]);
            BlockPos above = neighbour.above();
            // For an altar-gated boss source, never land in the source's own cell.
            // This guards not just the fallback below but the block directly beneath
            // the source, whose .above() IS the source cell - otherwise the slime
            // would still spawn inside the sealed milk source block via that neighbour.
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
