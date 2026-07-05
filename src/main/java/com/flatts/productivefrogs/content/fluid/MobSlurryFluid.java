package com.flatts.productivefrogs.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * Mob Slurry (#281, predation Phase 3) - a mob condensed into a bucketable
 * fluid by the Slurry Press, spent in the Mob Slurry Basin to respawn that mob
 * for a Predator Frog to farm. The mob-side twin of Slime Milk, on the R-1
 * single-fluid model: ONE {@code mob_slurry} fluid, the mob it condenses
 * riding as the {@code SLURRIED_ENTITY} data component on the bucket /
 * {@code FluidResource} (preserved through tanks/pipes by the 26.1 transfer
 * API).
 *
 * <p><b>No world form, by design</b> ({@code docs/predator_frogs.md} Phase 3):
 * unlike milk there is no source block and no BE - the slurry lives ONLY in
 * buckets, tanks/pipes, and inside the Basin. That is what lets a waterlogged
 * Basin sit in a pool with no mixing or washing-away: the fluid never exists
 * as a block, so there is nothing for the water to fight. Same shape as
 * Liquid Experience (no {@code .block()}; a force-placed fluid state resolves
 * to air), with a bucket like milk. Both forms refuse all spread.
 */
public final class MobSlurryFluid {

    private MobSlurryFluid() {
        // holder for the two fluid subclasses
    }

    /** The source fluid. Never spreads, never placeable. */
    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
            // never spread
        }
    }

    /** The flowing form - registered for completeness but never manifests (no spread, no block). */
    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
            // never spread
        }
    }
}
