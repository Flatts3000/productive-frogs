package com.flatts.productivefrogs.content.fluid;

import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * The Slime Milk fluid, subclassed only to make its <b>spread gentler than
 * water's</b>. Flowing milk normally washes away any non-solid block in its
 * path the way flowing water sweeps off a plant - that destroyed frogspawn,
 * water source blocks, and neighboring Slime Milk source blocks in the
 * production pools milk is meant to fill (docs/known_issues.md).
 *
 * <p>Both the {@link Source} and {@link Flowing} forms override
 * {@link net.minecraft.world.level.material.FlowingFluid#canSpreadTo} - the
 * single choke point vanilla checks before {@code spreadTo} replaces (and so
 * destroys) the block already at a target position - and refuse to spread into:
 * <ul>
 *   <li><b>frogspawn / Primed Frog Eggs</b> - so a running milk pool can't wash
 *       away spawn before it hatches;</li>
 *   <li><b>any fluid SOURCE block</b> ({@link FluidState#isSource()}) - so milk
 *       never overwrites a water source or a neighboring milk source.</li>
 * </ul>
 * Everything else (flowing-into-flowing, empty air, the normal slope/level
 * math) defers to {@code super}, so the only behavioral change is the refusal
 * to destroy those protected blocks.
 */
public final class SlimeMilkFluid {

    private SlimeMilkFluid() {
        // holder for the two fluid subclasses
    }

    /**
     * Shared guard: true when flowing milk must NOT spread into (and thus
     * destroy) the block currently at the target position.
     */
    private static boolean isProtected(BlockState spreadState, FluidState fluidState) {
        if (spreadState.is(Blocks.FROGSPAWN) || spreadState.getBlock() instanceof PrimedFrogEggBlock) {
            return true;
        }
        // Never displace a fluid source - covers water sources and other Slime
        // Milk source blocks. Flowing fluid (isSource() == false) is unaffected.
        return fluidState.isSource();
    }

    /** The placeable source fluid. */
    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canSpreadTo(BlockGetter level, BlockPos pos, BlockState state, Direction direction,
                                      BlockPos spreadPos, BlockState spreadState, FluidState fluidState, Fluid fluid) {
            if (isProtected(spreadState, fluidState)) {
                return false;
            }
            return super.canSpreadTo(level, pos, state, direction, spreadPos, spreadState, fluidState, fluid);
        }
    }

    /** The flowing form spread from a source. */
    public static final class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        protected boolean canSpreadTo(BlockGetter level, BlockPos pos, BlockState state, Direction direction,
                                      BlockPos spreadPos, BlockState spreadState, FluidState fluidState, Fluid fluid) {
            if (isProtected(spreadState, fluidState)) {
                return false;
            }
            return super.canSpreadTo(level, pos, state, direction, spreadPos, spreadState, fluidState, fluid);
        }
    }
}
