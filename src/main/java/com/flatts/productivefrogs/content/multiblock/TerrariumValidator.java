package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Validates the loose-adjacency Terrarium multiblock from a Controller anchor.
 * The geometry is the settled #185 ruling: a <b>5x5x5 interior cavity</b>
 * (unrestricted - any blocks/air) wrapped in a one-thick shell, so the full
 * footprint is 7x7x7 and the shell is 7^3 - 5^3 = 218 cells.
 *
 * <p><b>The anchor ambiguity.</b> The Controller's inward direction is known
 * ({@code FACING.getOpposite()}; FACING points outward, front-to-player like the
 * other appliances), but it may sit on any of the 25 cells of its shell face,
 * so the cavity origin is ambiguous on the two axes perpendicular to inward.
 * The interior is unrestricted (may be fully solid), so flood-fill-on-air can't
 * locate it. Instead we <b>enumerate the 25 candidate cavity placements</b>
 * (the centered cell first) and accept the first that fully validates. Two
 * distinct candidates cannot both pass under a one-thick shell - a wrong
 * candidate's shell cuts through the real cavity's air and fails {@code not_solid}
 * - so first-pass-wins is unambiguous and there are no false positives.
 *
 * <p>Cost ceiling per validate: 25 candidates x 218 cells = ~5,450
 * {@code getBlockState} calls; the Controller throttles validation to a config
 * cadence (~30 ticks) and caches the result between runs, so this is cheap.
 */
public final class TerrariumValidator {

    /** Interior edge length (cavity is INTERIOR x INTERIOR x INTERIOR). */
    private static final int INTERIOR = 5;

    /** Candidate (u,v) face offsets of the cavity near-corner, centered first. */
    private static final int[][] CANDIDATE_ORDER = buildCandidateOrder();

    private TerrariumValidator() {
        // utility class
    }

    public static TerrariumValidationResult validate(ServerLevel level, BlockPos controllerPos,
            BlockState controllerState) {
        if (!controllerState.is(PFBlocks.TERRARIUM_CONTROLLER.get())) {
            return TerrariumValidationResult.failed("not_a_controller", controllerPos);
        }
        Direction outward = controllerState.getValue(BlockStateProperties.FACING);
        Direction inward = outward.getOpposite();
        Direction[] perp = perpendicular(inward.getAxis());
        Direction uDir = perp[0];
        Direction vDir = perp[1];
        BlockPos faceCell = controllerPos.relative(inward); // candidate cavity near-face cell

        TerrariumValidationResult centeredFailure = null;
        for (int[] ab : CANDIDATE_ORDER) {
            BlockPos nearCorner = faceCell.relative(uDir, -ab[0]).relative(vDir, -ab[1]);
            BlockPos farCorner = nearCorner
                .relative(uDir, INTERIOR - 1)
                .relative(vDir, INTERIOR - 1)
                .relative(inward, INTERIOR - 1);
            BlockPos cavityMin = componentMin(nearCorner, farCorner);
            BlockPos cavityMax = componentMax(nearCorner, farCorner);
            TerrariumValidationResult r = validateCandidate(level, controllerPos, cavityMin, cavityMax);
            if (r.formed()) {
                return r;
            }
            if (centeredFailure == null) {
                centeredFailure = r; // the centered candidate's problem is the player-facing one
            }
        }
        return centeredFailure != null
            ? centeredFailure
            : TerrariumValidationResult.failed("not_solid", controllerPos);
    }

    private static TerrariumValidationResult validateCandidate(ServerLevel level, BlockPos controllerPos,
            BlockPos cavityMin, BlockPos cavityMax) {
        BlockPos shellMin = cavityMin.offset(-1, -1, -1);
        BlockPos shellMax = cavityMax.offset(1, 1, 1);

        Block controllerBlock = PFBlocks.TERRARIUM_CONTROLLER.get();
        Block sprinklerBlock = PFBlocks.SPRINKLER.get();
        Block incubatorBlock = PFBlocks.INCUBATOR.get();
        Block hatchBlock = PFBlocks.HATCH.get();

        int controllers = 0;
        int hatches = 0;
        int incubators = 0;
        BlockPos hatchPos = null;
        List<BlockPos> sprinklerList = new ArrayList<>();
        List<BlockPos> incubatorList = new ArrayList<>();

        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = shellMin.getX(); x <= shellMax.getX(); x++) {
            for (int y = shellMin.getY(); y <= shellMax.getY(); y++) {
                for (int z = shellMin.getZ(); z <= shellMax.getZ(); z++) {
                    if (within(x, y, z, cavityMin, cavityMax)) {
                        continue; // interior cell - unrestricted, never inspected
                    }
                    p.set(x, y, z);
                    int extremes = extremes(x, y, z, shellMin, shellMax);
                    BlockState s = level.getBlockState(p);
                    boolean isMachine = s.is(controllerBlock) || s.is(sprinklerBlock)
                        || s.is(incubatorBlock) || s.is(hatchBlock);
                    if (isMachine) {
                        BlockPos ip = p.immutable();
                        if (s.is(sprinklerBlock)) {
                            // Ceiling cells only: a top-layer face cell.
                            if (extremes != 1 || y != shellMax.getY()) {
                                return TerrariumValidationResult.failed("sprinkler_off_ceiling", ip);
                            }
                            sprinklerList.add(ip);
                        } else {
                            if (extremes != 1) {
                                return TerrariumValidationResult.failed("machine_on_edge", ip);
                            }
                            // Inward face must point into the cavity.
                            Direction face = s.getValue(BlockStateProperties.FACING);
                            BlockPos inner = ip.relative(face.getOpposite());
                            if (!within(inner.getX(), inner.getY(), inner.getZ(), cavityMin, cavityMax)) {
                                return TerrariumValidationResult.failed("machine_facing_wrong", ip);
                            }
                            if (s.is(controllerBlock)) {
                                controllers++;
                            } else if (s.is(hatchBlock)) {
                                hatches++;
                                hatchPos = ip;
                            } else {
                                incubators++;
                                incubatorList.add(ip);
                            }
                        }
                    } else if (!s.isCollisionShapeFullBlock(level, p)) {
                        return TerrariumValidationResult.failed("not_solid", p.immutable());
                    }
                    // plain full-cube solid: legal at any role, nothing to tally
                }
            }
        }

        if (controllers > 1) {
            return TerrariumValidationResult.failed("multiple_controllers", controllerPos);
        }
        if (controllers == 0) {
            // The anchor is always a shell cell, so this only fires if the anchor
            // landed off this candidate's shell - treat as a non-forming candidate.
            return TerrariumValidationResult.failed("not_a_controller", controllerPos);
        }
        if (hatches == 0) {
            return TerrariumValidationResult.failed("no_hatch", null);
        }
        if (hatches > 1) {
            return TerrariumValidationResult.failed("multiple_hatches", null);
        }
        if (incubators == 0) {
            return TerrariumValidationResult.failed("no_incubator", null);
        }
        return TerrariumValidationResult.formed(controllerPos, cavityMin, cavityMax, hatchPos,
            sprinklerList, incubatorList);
    }

    private static boolean within(int x, int y, int z, BlockPos min, BlockPos max) {
        return x >= min.getX() && x <= max.getX()
            && y >= min.getY() && y <= max.getY()
            && z >= min.getZ() && z <= max.getZ();
    }

    private static int extremes(int x, int y, int z, BlockPos shellMin, BlockPos shellMax) {
        int e = 0;
        if (x == shellMin.getX() || x == shellMax.getX()) {
            e++;
        }
        if (y == shellMin.getY() || y == shellMax.getY()) {
            e++;
        }
        if (z == shellMin.getZ() || z == shellMax.getZ()) {
            e++;
        }
        return e;
    }

    private static Direction[] perpendicular(Direction.Axis inAxis) {
        List<Direction> dirs = new ArrayList<>(2);
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != inAxis) {
                dirs.add(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE));
            }
        }
        return new Direction[] { dirs.get(0), dirs.get(1) };
    }

    private static BlockPos componentMin(BlockPos a, BlockPos b) {
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    private static BlockPos componentMax(BlockPos a, BlockPos b) {
        return new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    private static int[][] buildCandidateOrder() {
        List<int[]> order = new ArrayList<>(INTERIOR * INTERIOR);
        int center = INTERIOR / 2;
        order.add(new int[] { center, center });
        for (int u = 0; u < INTERIOR; u++) {
            for (int v = 0; v < INTERIOR; v++) {
                if (u != center || v != center) {
                    order.add(new int[] { u, v });
                }
            }
        }
        return order.toArray(new int[0][]);
    }
}
