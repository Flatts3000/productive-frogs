package com.flatts.productivefrogs.content.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * Liquid Experience (#281 Phase 2) - XP as a fluid, so builds can bank it in
 * tanks and pipe it like any other liquid. Member of the NeoForge-defined
 * {@code c:experience} fluid tag at the documented ecosystem standard of
 * {@link #MB_PER_POINT 20 mB per XP point}, so any third-party XP tank, drain,
 * or pump that keys on the tag interoperates with PF's fluid directly.
 *
 * <p><b>Deliberately the simplest fluid shape in the mod</b> - simpler than both
 * existing families, because XP is fungible:
 * <ul>
 *   <li>Unlike Slime Milk / Mimic Milk (26.1 R-1), it carries <b>no data
 *       components</b> - there is no variant or synthesized item to preserve, so
 *       its bucket uses NeoForge's stock
 *       {@code net.neoforged.neoforge.transfer.fluid.BucketResourceHandler}
 *       unmodified (a first for PF buckets).</li>
 *   <li>Unlike milk, it has <b>no source block and no BlockEntity</b> - it lives
 *       in tanks, pipes, and buckets only ("no world pools",
 *       {@code docs/predator_frogs.md} Phase 2). Like the molten metals, the
 *       fluid properties declare no {@code .block()}, so nothing can place it.</li>
 *   <li>Unlike molten metal, it <b>does</b> have a bucket
 *       ({@code LiquidExperienceBucketItem}) - the bucket's right-click is the
 *       player-facing spend path (drink the bucket, receive the XP).</li>
 * </ul>
 *
 * <p>Both forms refuse all spread (the milk pattern), so even a mod that
 * force-places the fluid state gets a single inert cell, never a pool.
 *
 * <p>The conversion constants live here so Phase 4 (boss altars paying XP as
 * fluid into their Hatch tanks) and any future drain share one exact ratio -
 * the risk register pins it with a unit test so it can never drift.
 */
public final class LiquidExperienceFluid {

    /**
     * The community-standard conversion: 20 mB of {@code c:experience} fluid per
     * 1 XP point (documented on NeoForge's {@code Tags.Fluids.EXPERIENCE}).
     */
    public static final int MB_PER_POINT = 20;

    /** One bucket (1000 mB) is exactly 50 XP points - no remainder at the bucket boundary. */
    public static final int POINTS_PER_BUCKET = FluidType.BUCKET_VOLUME / MB_PER_POINT;

    private LiquidExperienceFluid() {
        // holder for the two fluid subclasses + the conversion constants
    }

    /** Exact XP points -&gt; millibuckets (the altar payout direction, Phase 4). */
    public static int pointsToMb(int points) {
        return points * MB_PER_POINT;
    }

    /**
     * Millibuckets -&gt; whole XP points, flooring. A drain converting fluid back
     * to XP must leave the sub-point remainder ({@code mb % MB_PER_POINT}) in the
     * tank rather than round it away - conservation at the 20 mB boundary.
     */
    public static int mbToWholePoints(int mb) {
        return mb / MB_PER_POINT;
    }

    /** The source fluid. Never spreads. */
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
