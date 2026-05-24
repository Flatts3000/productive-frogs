package com.flatts.productivefrogs.content.block;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A Primed Frog Egg block — vanilla frogspawn that's been primed with a
 * category-tagged material. One subclass instance per {@link Category}.
 *
 * <p>Behavior mirrors {@code minecraft:frogspawn} as closely as practical
 * (the "stay close to vanilla" project principle):
 * <ul>
 *   <li>Frogspawn voxel shape, no collision, instant break, frogspawn sound type.</li>
 *   <li>Must sit on a water source; gets destroyed if the water vanishes.</li>
 *   <li>On placement, schedules a tick at {@code [3600, 12000)} ticks later
 *       (3-10 minutes wall-clock at 20 TPS, upper bound exclusive). Matches
 *       vanilla {@code FrogspawnBlock}'s hatch range exactly.</li>
 *   <li>On tick, spawns 1-3 {@link ResourceTadpole}s of this block's
 *       category and removes itself. Vanilla spawns 2-5 vanilla tadpoles;
 *       we tighten the range slightly because Resource Tadpoles mature into
 *       Resource Frogs (more valuable than vanilla frogs).</li>
 * </ul>
 */
public final class PrimedFrogEggBlock extends Block {

    private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 1.5, 16.0);

    private static final int MIN_HATCH_TICK_DELAY = 3600;
    private static final int MAX_HATCH_TICK_DELAY = 12000;
    private static final int MIN_TADPOLES_SPAWN = 1;
    private static final int MAX_TADPOLES_SPAWN = 3;

    private final Category category;

    public PrimedFrogEggBlock(Category category, Properties properties) {
        super(properties);
        this.category = category;
    }

    public Category getCategory() {
        return category;
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
        level.scheduleTick(pos, this, getHatchDelay(level.getRandom()));
    }

    private static int getHatchDelay(RandomSource random) {
        return random.nextInt(MIN_HATCH_TICK_DELAY, MAX_HATCH_TICK_DELAY);
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
        destroy(level, pos);
        level.playSound(null, pos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 1.0F, 1.0F);

        int count = random.nextInt(MIN_TADPOLES_SPAWN, MAX_TADPOLES_SPAWN + 1);
        for (int i = 0; i < count; i++) {
            ResourceTadpole tadpole = PFEntities.RESOURCE_TADPOLE.get().create(level);
            if (tadpole == null) {
                continue;
            }
            tadpole.setCategory(this.category);

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
