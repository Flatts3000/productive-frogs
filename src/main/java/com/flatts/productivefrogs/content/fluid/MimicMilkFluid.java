package com.flatts.productivefrogs.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * The Mimic Milk fluid (Equivalence lane, #253). Unlike the per-variant Slime
 * Milk fluids, there is ONE Mimic Milk fluid; the synthesized item it represents
 * rides on the source block's {@code MimicMilkSourceBlockEntity}, not on the
 * fluid registry object.
 *
 * <p><b>Source-only / non-flowing by design.</b> Both forms override
 * {@link net.minecraft.world.level.material.FlowingFluid#spreadTo} to refuse
 * <i>all</i> spread, so a placed Mimic Milk block stays exactly one source cell.
 * This sidesteps the v1.8 per-variant-fluid wall entirely: a single shared fluid
 * would have no per-instance colour on flowing blocks (which carry no BE), so by
 * never flowing, every Mimic Milk block is a BE-backed source whose tint + spawn
 * item read straight off that BE. (The documented trade-off: a third-party tank
 * mod piping this fluid may strip the item id - PF's own machines read the BE.)
 */
public final class MimicMilkFluid {

    private MimicMilkFluid() {
        // holder for the two fluid subclasses
    }

    /** The placeable source fluid. Never spreads. */
    public static final class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        protected void spreadTo(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState target) {
            // never spread
        }
    }

    /** The flowing form - registered for completeness but never manifests (no spread). */
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
