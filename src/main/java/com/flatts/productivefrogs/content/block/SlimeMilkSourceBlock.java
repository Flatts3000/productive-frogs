package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FlowingFluid;
import org.jetbrains.annotations.Nullable;

/**
 * Slime Milk's placeable form: the single source block for the one generic
 * {@code slime_milk} fluid. Subclasses {@link LiquidBlock} for vanilla flow and
 * is an {@link EntityBlock} so its {@link SlimeMilkSourceBlockEntity} can store
 * the variant: collapsed from the former one-block-per-variant model so a
 * datapack-added variant gets milk with no Java edit (see
 * {@code docs/refactor_data_driven_variants.md}).
 *
 * <p>The variant is written to the BE on placement (by
 * {@link com.flatts.productivefrogs.content.item.SlimeMilkBucketItem#checkExtraContent})
 * and read back when re-bucketing ({@link #pickupBlock}). Only a source block
 * with a non-null variant spawns slimes + tints per-variant; milk that spread
 * from a source (fluid spreading does not copy BlockEntities) carries no variant
 * and is inert decoration.
 *
 * <p>Edge case: placing a bucket where the fluid engine will not sustain a source
 * (e.g. an isolated spot the engine immediately drains to flowing/air) creates a
 * momentary variant source that the engine then removes, discarding the variant.
 * That is intended - a milk source only persists where the engine keeps it a
 * source, the same constraint as any vanilla fluid.
 *
 * <p>Spawn cadence + depletion are unchanged from the per-variant design:
 * uniform {@code [PFConfig.MIN_SPAWN_INTERVAL_TICKS, MAX_SPAWN_INTERVAL_TICKS]}
 * per spawn; {@link #SPAWNS_REMAINING} decrements per spawn and drains to air at
 * zero when {@link PFConfig#DEPLETION_ENABLED}.
 */
public class SlimeMilkSourceBlock extends LiquidBlock implements EntityBlock {

    public static final int MAX_SPAWNS_REMAINING = 16;

    /**
     * Depletion counter persisted into the blockstate. {@code N} means "N more
     * spawns until this source drains". Read only when
     * {@link PFConfig#DEPLETION_ENABLED}.
     */
    public static final IntegerProperty SPAWNS_REMAINING =
        IntegerProperty.create("spawns_remaining", 0, MAX_SPAWNS_REMAINING);

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

    public SlimeMilkSourceBlock(FlowingFluid fluid, Properties properties) {
        super(fluid, properties);
        registerDefaultState(defaultBlockState().setValue(SPAWNS_REMAINING, MAX_SPAWNS_REMAINING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlimeMilkSourceBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SPAWNS_REMAINING);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        // Only source blocks spawn. Don't schedule on the client.
        if (!(level instanceof ServerLevel serverLevel) || !level.getFluidState(pos).isSource()) {
            return;
        }
        int configured = PFConfig.DEPLETION_COUNT.get();
        if (oldState.getBlock() != state.getBlock()
            && state.getValue(SPAWNS_REMAINING) == MAX_SPAWNS_REMAINING
            && configured < MAX_SPAWNS_REMAINING) {
            serverLevel.setBlock(pos, state.setValue(SPAWNS_REMAINING, configured), Block.UPDATE_CLIENTS);
        }
        scheduleNextSpawnTick(serverLevel, pos, level.getRandom());
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
        ResourceLocation variantId = readVariant(level, pos);
        if (variantId == null) {
            return;
        }

        if (depletionEnabled()) {
            int remaining = state.getValue(SPAWNS_REMAINING);
            if (remaining <= 0) {
                // True air swap to drain: removeBlock on a fluid block would
                // reset the source to its default state (counter back to MAX).
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                    "source @%s: depleted, drained to air (variant=%s)", pos, variantId));
                return;
            }
            spawn(level, pos, random, variantId);
            level.setBlock(pos, state.setValue(SPAWNS_REMAINING, remaining - 1), Block.UPDATE_CLIENTS);
        } else {
            spawn(level, pos, random, variantId);
        }
        scheduleNextSpawnTick(level, pos, random);
    }

    private static boolean depletionEnabled() {
        Boolean override = depletionEnabledOverride;
        return override != null ? override : PFConfig.DEPLETION_ENABLED.get();
    }

    private static void scheduleNextSpawnTick(ServerLevel level, BlockPos pos, RandomSource random) {
        int min = PFConfig.MIN_SPAWN_INTERVAL_TICKS.get();
        int max = PFConfig.MAX_SPAWN_INTERVAL_TICKS.get();
        int delay = max <= min ? min : min + random.nextInt(max - min + 1);
        level.scheduleTick(pos, level.getBlockState(pos).getBlock(), delay);
    }

    private void spawn(ServerLevel level, BlockPos pos, RandomSource random, ResourceLocation variantId) {
        BlockPos spawnPos = chooseSpawnPos(level, pos);
        Slime slime = createSlimeForVariant(level, variantId);
        if (slime == null) {
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "source @%s: slime create failed for variant=%s (skip)", pos, variantId));
            return;
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
    }

    @Nullable
    private static ResourceLocation readVariant(LevelAccessor level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SlimeMilkSourceBlockEntity milkBe ? milkBe.getVariantId() : null;
    }

    /**
     * Map the variant id to the slime it produces. The {@code productivefrogs:vanilla}
     * / {@code productivefrogs:magma} sentinels spawn a vanilla Slime / MagmaCube;
     * every other id is looked up as a {@link ResourceSlime} variant (the slime
     * resolves its category from the {@code slime_variant} registry on setVariant).
     */
    @Nullable
    private static Slime createSlimeForVariant(ServerLevel level, ResourceLocation variantId) {
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
     * Re-bucketing, the inverse of placement: read the source's variant and
     * depletion counter from its BE / blockstate (before super removes the block)
     * and stamp both onto the filled bucket so they survive the world -> bucket
     * round-trip. Carrying SPAWNS_REMAINING stops a partially-depleted source from
     * refilling to full just by re-bucketing it (docs/known_issues.md).
     */
    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        ResourceLocation variantId = readVariant(level, pos);
        int remaining = state.getValue(SPAWNS_REMAINING);
        ItemStack bucket = super.pickupBlock(player, level, pos, state);
        // Only a variant-carrying source actually spawns + depletes, so stamp
        // both the variant and the counter only for one. An inert spread-milk
        // grab has no variant and must not stamp a misleading count.
        if (variantId != null && bucket.is(PFItems.SLIME_MILK_BUCKET.get())) {
            bucket.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
            bucket.set(PFDataComponents.SPAWNS_REMAINING.get(), remaining);
        }
        return bucket;
    }

    /**
     * Pick spawn slot: first 3x3x3 neighbour whose top face is sturdy and whose
     * block-above is non-motion-blocking; else fall back to the source's own
     * position (milk has noCollision, so this always works).
     */
    private static BlockPos chooseSpawnPos(ServerLevel level, BlockPos source) {
        for (int[] off : NEIGHBOUR_OFFSETS) {
            BlockPos neighbour = source.offset(off[0], off[1], off[2]);
            BlockPos above = neighbour.above();
            if (level.getBlockState(neighbour).isFaceSturdy(level, neighbour, Direction.UP)
                && !level.getBlockState(above).blocksMotion()) {
                return above;
            }
        }
        return source;
    }
}
