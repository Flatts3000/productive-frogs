package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.PrimedFrogEggBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceTadpole;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A Primed Frog Egg block — vanilla frogspawn that's been primed with a
 * category-tagged material. One subclass instance per {@link Category}.
 *
 * <p>Behavior mirrors {@code minecraft:frogspawn} as closely as practical
 * (the "stay close to vanilla" project principle):
 * <ul>
 *   <li>Frogspawn voxel shape, no collision, instant break, frogspawn sound type.</li>
 *   <li>Must sit on a water source; gets destroyed if the water vanishes.</li>
 *   <li>On placement, schedules a tick at a <b>fixed, config-exposed</b> delay
 *       ({@link PFConfig#hatchTicks()}, default 3600 ticks / 3 min). Unlike
 *       vanilla {@code FrogspawnBlock}'s random {@code [3600, 12000)} window,
 *       primed-egg hatch timing is deterministic so packs can pace progression
 *       and automation predictably (docs/known_issues.md). Vanilla frogspawn is
 *       unaffected.</li>
 *   <li>On tick, spawns 1-3 {@link ResourceTadpole}s of this block's
 *       category and removes itself. Vanilla spawns 2-5 vanilla tadpoles;
 *       we tighten the range slightly because Resource Tadpoles mature into
 *       Resource Frogs (more valuable than vanilla frogs).</li>
 * </ul>
 */
public final class PrimedFrogEggBlock extends Block implements EntityBlock {

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.5, 16.0);

    private static final int MIN_TADPOLES_SPAWN = 1;
    private static final int MAX_TADPOLES_SPAWN = 3;

    private final Category category;
    /**
     * Equivalence lane (#253): a Midas egg. It still carries a {@link Category}
     * (VOID, the sentinel its tadpoles + frog use), but is its OWN block - natively
     * named "Midas Egg", not a VOID egg - and stamps the {@code midas} marker so it
     * hatches Midas. A standalone block, NOT a 7th Category.
     */
    private final boolean midas;

    public PrimedFrogEggBlock(Category category, Properties properties) {
        this(category, false, properties);
    }

    public PrimedFrogEggBlock(Category category, boolean midas, Properties properties) {
        super(properties);
        this.category = category;
        this.midas = midas;
    }

    public Category getCategory() {
        return category;
    }

    /** Whether this is the Midas egg block (#253). */
    public boolean isMidas() {
        return midas;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PrimedFrogEggBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return mayPlaceOn(level, pos.below());
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (level.isClientSide()) {
            return;
        }
        int delay = getHatchDelay();
        level.scheduleTick(pos, this, delay);
        // Stamp the absolute hatch time on the BE so the Jade tooltip can count
        // down to it (the scheduled tick lives in the level scheduler, which
        // exposes no "ticks remaining"). The BE is created before onPlace runs.
        if (level.getBlockEntity(pos) instanceof PrimedFrogEggBlockEntity egg) {
            egg.setHatchGameTime(level.getGameTime() + delay);
            if (midas) {
                egg.setMidas(true);
            }
        }
    }

    private static int getHatchDelay() {
        // Fixed, config-tunable delay (default 3600 ticks). Deterministic by
        // design - see the class javadoc and docs/known_issues.md.
        return PFConfig.hatchTicks();
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (!canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) {
            destroy(level, pos);
            return;
        }
        hatch(level, pos, random);
    }

    private void hatch(ServerLevel level, BlockPos pos, RandomSource random) {
        // Read the pending offspring stats off the BE BEFORE destroying the
        // block (destroy removes the BE). A non-bred egg (creative placement,
        // /setblock) has no stats; hatched tadpoles then mature into baseline
        // (1/1/1) frogs. This is the back half of the conception->egg->tadpole
        // stat carry (docs/frog_breeding.md).
        PrimedFrogEggBlockEntity eggBe = level.getBlockEntity(pos) instanceof PrimedFrogEggBlockEntity be ? be : null;
        boolean carryStats = eggBe != null && eggBe.hasStats();
        int appetite = carryStats ? eggBe.getAppetite() : 0;
        int bounty = carryStats ? eggBe.getBounty() : 0;
        int reach = carryStats ? eggBe.getReach() : 0;
        // Midas egg (#253): hatches Midas tadpoles regardless of the carrier block's category.
        boolean midas = eggBe != null && eggBe.isMidas();

        destroy(level, pos);
        level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);

        int count = random.nextInt(MIN_TADPOLES_SPAWN, MAX_TADPOLES_SPAWN + 1);
        for (int i = 0; i < count; i++) {
            ResourceTadpole tadpole = PFEntities.RESOURCE_TADPOLE.get().create(level);
            if (tadpole == null) {
                continue;
            }
            tadpole.setCategory(this.category);
            tadpole.setMidas(midas);
            if (carryStats) {
                tadpole.setPendingStats(appetite, bounty, reach);
            }

            double x = pos.getX() + clampedOffset(random);
            double z = pos.getZ() + clampedOffset(random);
            float yaw = random.nextInt(1, 361);

            tadpole.moveTo(x, pos.getY() - 0.5, z, yaw, 0.0F);
            tadpole.setPersistenceRequired();
            level.addFreshEntity(tadpole);
        }
    }

    /**
     * Uniform random offset inside the cell's central [0.2, 0.8) span — keeps
     * spawned tadpoles away from the block edges. Vanilla uses an
     * {@code Mth.clamp(nextDouble(), 0.2, 0.8)} pattern that biases toward
     * the endpoints; sampling directly avoids that.
     */
    private static double clampedOffset(RandomSource random) {
        return 0.2 + random.nextDouble() * 0.6;
    }

    private void destroy(Level level, BlockPos pos) {
        level.destroyBlock(pos, false);
    }

    private static boolean mayPlaceOn(LevelReader level, BlockPos below) {
        var belowFluid = level.getFluidState(below);
        return belowFluid.is(Fluids.WATER) && belowFluid.isSource();
    }
}
