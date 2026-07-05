package com.flatts.productivefrogs.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * The Slime Milk fluid. As of the 26.1 re-implementation (R-1,
 * {@code docs/port_mc_26_1_reimplementation.md}) there is ONE {@code slime_milk}
 * fluid; the variant it represents rides as the {@code SLIME_VARIANT} data
 * component on the bucket {@code ItemStack} / {@code FluidResource} and on the
 * placed source block's {@link com.flatts.productivefrogs.content.block.entity.SlimeMilkSourceBlockEntity}.
 * The 26.1 transfer API ({@code ResourceHandler<FluidResource>}) preserves that
 * component through tanks/pipes, so per-variant fluids (v1.8) are no longer
 * needed. This mirrors the Mimic Milk lane exactly.
 *
 * <p><b>Source-only / non-flowing by design</b> (maintainer decision 2026-06-29).
 * Both forms override {@link net.minecraft.world.level.material.FlowingFluid#spreadTo}
 * to refuse <i>all</i> spread, so a placed Slime Milk block stays exactly one
 * source cell. Milk's real roles (transport as a {@code FluidResource}, automation,
 * and source-spawning) never needed vanilla-style spreading pools, and dropping
 * spreading removes the one wrinkle a single shared fluid has: a flowing cell has
 * no BE and so cannot resolve its per-variant tint. Every Slime Milk block is now
 * a BE-backed source whose tint + spawn variant read straight off that BE.
 */
public final class SlimeMilkFluid {

    private SlimeMilkFluid() {
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
