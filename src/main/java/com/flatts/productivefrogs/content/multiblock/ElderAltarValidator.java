package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

/**
 * Validates the Elder Guardian Altar - the "Monument Well" (#280) - by anchoring
 * on its Hatch, which sits <b>in the tank wall</b> at water-floor level so pipes
 * reach its outer face (maintainer ruling 2026-07-04; the original floor-center
 * anchor was pipe-unreachable). A sealed 5x5x5 aquarium: a Reinforced Sponge
 * Froglight floor, Reinforced Light Blue Stained Glass walls (the aquarium
 * view), a Reinforced Prismarine Froglight roof with the four Tide Offering
 * Receptacles at the corners (fuel: 4 prismarine crystals) and the Monument
 * Core capstone at the roof center (a Wet Sponge - the Elder's signature drop).
 * The 3x3x3 interior must be water source blocks; Elderbane swims in front of
 * the Hatch and the Elder Guardian replica forms at the tank center.
 *
 * <p><b>Facing-aware:</b> the offset tables are authored in a canonical frame
 * where the tank interior lies <b>+Z (SOUTH)</b> of the Hatch; {@link #validate}
 * tries each rotation and returns the resolved <b>interior direction</b> in its
 * {@link Result} (the wither-altar pattern). Reads only block/fluid identity,
 * so it runs client-side (Jade) and server-side alike.
 */
public final class ElderAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout) and the resolved interior direction. */
    public record Result(boolean valid, String detail, @Nullable Direction interior) {
    }

    /** The canonical authoring frame: the tank interior lies this way from the Hatch. SOUTH is the identity. */
    public static final Direction CANONICAL_INTERIOR = Direction.SOUTH;

    /** The Monument Core capstone, at the roof center. */
    private static final int[][] CAPSTONE = {{0, 3, 2}};

    /** Reinforced Sponge Froglight: the 5x5 tank floor. */
    private static final int[][] SPONGE_FLOOR = buildFloor();

    /** Reinforced Light Blue Stained Glass: the three wall rings (minus the Hatch cell). */
    private static final int[][] GLASS_WALLS = buildWalls();

    /** Reinforced Prismarine Froglight: the roof plate (corners = receptacles, center = the Core). */
    private static final int[][] PRISMARINE_ROOF = buildRoof();

    /** The four Tide Offering Receptacles, at the roof corners. */
    private static final int[][] RECEPTACLES = {{2, 3, 0}, {-2, 3, 0}, {2, 3, 4}, {-2, 3, 4}};

    /** The flooded interior: every 3x3x3 cell must be a water source. */
    private static final int[][] WATER_REQUIRED = buildWater();

    private ElderAltarValidator() {
    }

    private static boolean isRing(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz - 2)) == 2;
    }

    private static int[][] buildFloor() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                out.add(new int[] {dx, -1, dz});
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildWalls() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                if (isRing(dx, dz)) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (!(dx == 0 && dy == 0 && dz == 0)) { // the Hatch cell
                            out.add(new int[] {dx, dy, dz});
                        }
                    }
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildRoof() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                boolean corner = Math.abs(dx) == 2 && (dz == 0 || dz == 4);
                boolean center = dx == 0 && dz == 2;
                if (!corner && !center) {
                    out.add(new int[] {dx, 3, dz});
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildWater() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    out.add(new int[] {dx, dy, dz});
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        return validate(level, hatch, null);
    }

    /** Validate with an orientation hint (the hatch's cached direction) - the common case is one pass. */
    public static Result validate(LevelReader level, BlockPos hatch, @Nullable Direction hint) {
        if (!level.getBlockState(hatch).is(PFBlocks.ELDER_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch", null);
        }
        if (hint != null && hint.getAxis().isHorizontal() && validateOriented(level, hatch, hint).valid()) {
            return new Result(true, "ready", hint);
        }
        Oriented best = null;
        for (Direction interior : Direction.Plane.HORIZONTAL) {
            Oriented o = validateOriented(level, hatch, interior);
            if (o.valid()) {
                return new Result(true, "ready", interior);
            }
            if (best == null || o.stage() > best.stage()) {
                best = o;
            }
        }
        return new Result(false, best.detail(), null);
    }

    /** A single-orientation pass: {@code stage} = how many checks passed (6 = valid). */
    private record Oriented(int stage, String detail) {
        boolean valid() {
            return stage == 6;
        }
    }

    private static Oriented validateOriented(LevelReader level, BlockPos hatch, Direction interior) {
        if (!allMatch(level, hatch, CAPSTONE, interior, PFBlocks.MONUMENT_CORE.get())) {
            return new Oriented(0, "missing the Monument Core at the roof center");
        }
        if (!allMatch(level, hatch, SPONGE_FLOOR, interior, PFBlocks.REINFORCED_SPONGE_FROGLIGHT.get())) {
            return new Oriented(1, "incomplete Reinforced Sponge Froglight floor");
        }
        if (!allMatch(level, hatch, GLASS_WALLS, interior, PFBlocks.REINFORCED_LIGHT_BLUE_STAINED_GLASS.get())) {
            return new Oriented(2, "incomplete Reinforced Light Blue Stained Glass walls");
        }
        if (!allMatch(level, hatch, PRISMARINE_ROOF, interior, PFBlocks.REINFORCED_PRISMARINE_FROGLIGHT.get())) {
            return new Oriented(3, "incomplete Reinforced Prismarine Froglight roof");
        }
        if (!allMatch(level, hatch, RECEPTACLES, interior, PFBlocks.TIDE_OFFERING_RECEPTACLE.get())) {
            return new Oriented(4, "missing a Tide Offering Receptacle at a roof corner");
        }
        for (int[] o : WATER_REQUIRED) {
            BlockPos p = hatch.offset(rotateOffset(o[0], o[1], o[2], interior));
            if (!level.getBlockState(p).is(Blocks.WATER) || !level.getFluidState(p).isSource()) {
                return new Oriented(5, "the tank must be flooded (fill the interior with water sources)");
            }
        }
        return new Oriented(6, "ready");
    }

    /** Rotate a canonical offset (interior = +Z / SOUTH) so the interior lies along {@code interior}. */
    static BlockPos rotateOffset(int dx, int dy, int dz, Direction interior) {
        return switch (interior) {
            case SOUTH -> new BlockPos(dx, dy, dz);
            case WEST -> new BlockPos(-dz, dy, dx);
            case NORTH -> new BlockPos(-dx, dy, -dz);
            case EAST -> new BlockPos(dz, dy, -dx);
            default -> new BlockPos(dx, dy, dz); // UP/DOWN never passed (HORIZONTAL plane only)
        };
    }

    /** The four Tide Offering Receptacle positions for a Hatch at {@code hatch} with the given interior direction. */
    public static BlockPos[] receptacles(BlockPos hatch, Direction interior) {
        BlockPos[] out = new BlockPos[RECEPTACLES.length];
        for (int i = 0; i < RECEPTACLES.length; i++) {
            out[i] = hatch.offset(rotateOffset(RECEPTACLES[i][0], RECEPTACLES[i][1], RECEPTACLES[i][2], interior));
        }
        return out;
    }

    /** Where Elderbane swims: the water cell directly in front of the wall-mounted Hatch. */
    public static BlockPos elderbanePos(BlockPos hatch, Direction interior) {
        return hatch.offset(rotateOffset(0, 0, 1, interior));
    }

    private static boolean allMatch(LevelReader level, BlockPos hatch, int[][] offsets, Direction interior, Block block) {
        for (int[] o : offsets) {
            if (!level.getBlockState(hatch.offset(rotateOffset(o[0], o[1], o[2], interior))).is(block)) {
                return false;
            }
        }
        return true;
    }
}
