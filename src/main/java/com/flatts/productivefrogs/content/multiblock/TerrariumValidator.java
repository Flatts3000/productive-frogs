package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Validates the loose-adjacency Terrarium multiblock from a Controller anchor.
 * The geometry is the settled #185 ruling, refined for slime survivability: a
 * <b>5x4x5 interior cavity</b> ({@code FOOTPRINT}x{@code HEIGHT}x{@code FOOTPRINT},
 * unrestricted - any blocks/air) wrapped in a one-thick shell, so the full
 * footprint is 7x6x7 and the shell is 7*6*7 - 5*4*5 = 194 cells. The height is
 * capped at 4 because slimes spawned from ceiling Sprinklers take fatal fall
 * damage from a taller drop; sizing is strict (only exactly 5x4x5 forms).
 *
 * <p><b>Strictness is structural.</b> Each candidate fixes the cavity at exactly
 * 5x4x5 and the shell is checked at exactly cavity-inflated-by-1; a too-large or
 * too-small air pocket puts a shell cell in the wrong place ({@code not_solid})
 * and fails. Exactly-5x4x5 falls out of the shell-exact scan - there is no range
 * check to loosen.
 *
 * <p><b>The anchor ambiguity.</b> The Controller's inward direction is known
 * ({@code FACING.getOpposite()}; FACING points outward, front-to-player like the
 * other appliances), but it may sit on any face cell of its shell face, so the
 * cavity origin is ambiguous on the two axes perpendicular to inward. The
 * interior is unrestricted (may be fully solid), so flood-fill-on-air can't
 * locate it. Instead we <b>enumerate the candidate cavity placements</b> (the
 * centered cell first) and accept the first that fully validates. The candidate
 * count is facing-dependent: a Controller on the floor/ceiling (inward = Y) has
 * two footprint perpendiculars -> 5x5 = 25 candidates; one on a wall (inward
 * horizontal) has one footprint and one height perpendicular -> 5x4 = 20. Two
 * distinct candidates cannot both pass under a one-thick shell, so
 * first-pass-wins is unambiguous and there are no false positives.
 *
 * <p>Cost ceiling per validate: 25 candidates x 194 cells = ~4,850
 * {@code getBlockState} calls; the Controller throttles validation to a config
 * cadence (~30 ticks) and caches the result between runs, so this is cheap.
 */
public final class TerrariumValidator {

    /** Cavity edge length on the two horizontal (footprint) axes. */
    static final int FOOTPRINT = 5;

    /** Cavity edge length on the vertical (Y) axis - capped so ceiling-spawned slimes survive the drop. */
    static final int HEIGHT = 4;

    /**
     * Themeable shell blocks that are NOT full-cube solids but still seal the
     * shell - e.g. mud (a frog-habitat floor). Pack-extensible: add block ids to
     * {@code data/productivefrogs/tags/block/terrarium_shell.json}.
     */
    static final TagKey<Block> TERRARIUM_SHELL =
        TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "terrarium_shell"));

    private TerrariumValidator() {
        // utility class
    }

    /** Cavity edge length along {@code axis}: {@link #HEIGHT} for Y, else {@link #FOOTPRINT}. */
    static int sizeOf(Direction.Axis axis) {
        return axis == Direction.Axis.Y ? HEIGHT : FOOTPRINT;
    }

    public static TerrariumValidationResult validate(ServerLevel level, BlockPos controllerPos,
            BlockState controllerState) {
        if (!controllerState.is(PFBlocks.TERRARIUM_CONTROLLER.get())) {
            return TerrariumValidationResult.failed("not_a_controller", controllerPos);
        }
        Direction outward = controllerState.getValue(BlockStateProperties.FACING);
        Direction[] perp = perpendicular(outward.getAxis());
        int[][] candidateOrder = buildCandidateOrder(sizeOf(perp[0].getAxis()), sizeOf(perp[1].getAxis()));
        TerrariumValidationResult centeredFailure = null;
        for (int[] ab : candidateOrder) {
            BlockPos[] bounds = cavityBounds(controllerPos, outward, ab[0], ab[1]);
            TerrariumValidationResult r = validateCandidate(level, controllerPos, bounds[0], bounds[1]);
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

    /**
     * Pure geometry: the 5x4x5 cavity bounds (inclusive {@code [min, max]}) for
     * candidate face-offset {@code (a, b)} from the Controller anchor, where
     * {@code a} slides along {@code perp[0]} and {@code b} along {@code perp[1]}.
     * Each axis uses its own size ({@link #sizeOf}) so the cavity is 5 on the two
     * footprint axes and 4 on Y regardless of which face the Controller mounts on.
     * Extracted so the anchor math is unit-testable without a level.
     */
    static BlockPos[] cavityBounds(BlockPos controllerPos, Direction outward, int a, int b) {
        Direction inward = outward.getOpposite();
        Direction[] perp = perpendicular(inward.getAxis());
        BlockPos faceCell = controllerPos.relative(inward); // cavity near-face cell
        BlockPos nearCorner = faceCell.relative(perp[0], -a).relative(perp[1], -b);
        BlockPos farCorner = nearCorner
            .relative(perp[0], sizeOf(perp[0].getAxis()) - 1)
            .relative(perp[1], sizeOf(perp[1].getAxis()) - 1)
            .relative(inward, sizeOf(inward.getAxis()) - 1);
        return new BlockPos[] { componentMin(nearCorner, farCorner), componentMax(nearCorner, farCorner) };
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
                    } else if (!isShellSolid(s, level, p)) {
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

    /** A shell cell seals if it's a full-cube solid OR a themeable {@link #TERRARIUM_SHELL} block (e.g. mud). */
    private static boolean isShellSolid(BlockState state, ServerLevel level, BlockPos pos) {
        return state.isCollisionShapeFullBlock(level, pos) || state.is(TERRARIUM_SHELL);
    }

    static int extremes(int x, int y, int z, BlockPos shellMin, BlockPos shellMax) {
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

    static Direction[] perpendicular(Direction.Axis inAxis) {
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

    /**
     * Candidate {@code (a, b)} face offsets of the cavity near-corner, the centered
     * cell first. {@code sizeA}/{@code sizeB} are the cavity sizes along the two
     * axes perpendicular to inward, so the grid is {@code sizeA x sizeB}
     * (5x5 = 25 for a floor/ceiling anchor, 5x4 = 20 for a wall anchor).
     */
    static int[][] buildCandidateOrder(int sizeA, int sizeB) {
        List<int[]> order = new ArrayList<>(sizeA * sizeB);
        int centerA = sizeA / 2;
        int centerB = sizeB / 2;
        order.add(new int[] { centerA, centerB });
        for (int u = 0; u < sizeA; u++) {
            for (int v = 0; v < sizeB; v++) {
                if (u != centerA || v != centerB) {
                    order.add(new int[] { u, v });
                }
            }
        }
        return order.toArray(new int[0][]);
    }
}
