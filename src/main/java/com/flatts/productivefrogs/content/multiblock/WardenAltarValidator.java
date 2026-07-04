package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Validates the Warden Altar - the "Shrieker Pit" (#279) - by anchoring on its
 * Hatch, which sits <b>in the shaft wall</b> at pit-floor level so pipes reach
 * its outer face (maintainer ruling 2026-07-04; the original floor-center
 * anchor was pipe-unreachable). An open-topped 5x5 shaft, four deep: a
 * Reinforced Sculk Froglight floor and shaft lining, a Reinforced Echo Shard
 * Froglight rim at grade carrying the four Shrieker Receptacles (fuel: 4 sculk
 * shriekers, one per warning level), and the Echoing Catalyst capstone set into
 * the floor beneath the pit center (a Sculk Catalyst proves a first Warden
 * kill). Wardenbane perches in the cavity in front of the Hatch; the Warden
 * replica rises from the pit center.
 *
 * <p><b>Facing-aware:</b> the offset tables are authored in a canonical frame
 * where the pit interior lies <b>+Z (SOUTH)</b> of the Hatch. The Hatch may sit
 * in any of the four walls; {@link #validate} tries each rotation and returns
 * the resolved <b>interior direction</b> in its {@link Result} (the
 * wither-altar pattern). Reads only block identity, so it runs client-side
 * (Jade) and server-side (the summon) alike.
 */
public final class WardenAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout) and the resolved interior direction. */
    public record Result(boolean valid, String detail, @Nullable Direction interior) {
    }

    /** The canonical authoring frame: the pit interior lies this way from the Hatch. SOUTH is the identity. */
    public static final Direction CANONICAL_INTERIOR = Direction.SOUTH;

    /** The Echoing Catalyst capstone, set into the floor beneath the pit center. */
    private static final int[][] CAPSTONE = {{0, -1, 2}};

    /** Reinforced Sculk Froglight: the 5x5 pit floor (minus the capstone) + the shaft lining (minus the Hatch). */
    private static final int[][] SCULK = buildSculk();

    /** Reinforced Echo Shard Froglight: the rim ring at grade, minus the four receptacle sockets. */
    private static final int[][] ECHO_RIM = buildEchoRim();

    /** The four Shrieker Receptacles, at the rim cardinals around the pit center. */
    private static final int[][] RECEPTACLES = {{0, 3, 0}, {0, 3, 4}, {2, 3, 2}, {-2, 3, 2}};

    /** The open shaft: the 3x3 interior column, floor to rim. */
    private static final int[][] AIR_REQUIRED = buildAir();

    private WardenAltarValidator() {
    }

    private static boolean isRing(int dx, int dz) {
        return Math.max(Math.abs(dx), Math.abs(dz - 2)) == 2;
    }

    private static int[][] buildSculk() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                if (!(dx == 0 && dz == 2)) {
                    out.add(new int[] {dx, -1, dz}); // floor (capstone at the center)
                }
                if (isRing(dx, dz)) {
                    for (int dy = 0; dy <= 2; dy++) {
                        if (!(dx == 0 && dy == 0 && dz == 0)) { // the Hatch cell
                            out.add(new int[] {dx, dy, dz});    // shaft lining
                        }
                    }
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildEchoRim() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                if (isRing(dx, dz)
                        && !(dx == 0 && (dz == 0 || dz == 4)) && !(Math.abs(dx) == 2 && dz == 2)) {
                    out.add(new int[] {dx, 3, dz});
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildAir() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = 1; dz <= 3; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
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
        if (!level.getBlockState(hatch).is(PFBlocks.WARDEN_ALTAR_HATCH.get())) {
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

    /** A single-orientation pass: {@code stage} = how many checks passed (5 = valid). */
    private record Oriented(int stage, String detail) {
        boolean valid() {
            return stage == 5;
        }
    }

    private static Oriented validateOriented(LevelReader level, BlockPos hatch, Direction interior) {
        if (!allMatch(level, hatch, CAPSTONE, interior, PFBlocks.ECHOING_CATALYST.get())) {
            return new Oriented(0, "missing the Echoing Catalyst in the pit floor (defeat a Warden first)");
        }
        if (!allMatch(level, hatch, SCULK, interior, PFBlocks.REINFORCED_SCULK_FROGLIGHT.get())) {
            return new Oriented(1, "incomplete Reinforced Sculk Froglight floor/lining");
        }
        if (!allMatch(level, hatch, ECHO_RIM, interior, PFBlocks.REINFORCED_ECHO_SHARD_FROGLIGHT.get())) {
            return new Oriented(2, "incomplete Reinforced Echo Shard Froglight rim");
        }
        if (!allMatch(level, hatch, RECEPTACLES, interior, PFBlocks.SHRIEKER_RECEPTACLE.get())) {
            return new Oriented(3, "missing a Shrieker Receptacle on the rim");
        }
        for (int[] o : AIR_REQUIRED) {
            if (!level.getBlockState(hatch.offset(rotateOffset(o[0], o[1], o[2], interior))).isAir()) {
                return new Oriented(4, "the shaft must be clear (keep the interior air)");
            }
        }
        return new Oriented(5, "ready");
    }

    /**
     * Rotate an authored offset (canonical frame: interior = +Z / SOUTH) about the
     * vertical axis so the interior instead lies along {@code interior}. SOUTH is
     * the identity (mirrors {@code WitherAltarValidator.rotateOffset}).
     */
    static BlockPos rotateOffset(int dx, int dy, int dz, Direction interior) {
        return switch (interior) {
            case SOUTH -> new BlockPos(dx, dy, dz);
            case WEST -> new BlockPos(-dz, dy, dx);
            case NORTH -> new BlockPos(-dx, dy, -dz);
            case EAST -> new BlockPos(dz, dy, -dx);
            default -> new BlockPos(dx, dy, dz); // UP/DOWN never passed (HORIZONTAL plane only)
        };
    }

    /** The four Shrieker Receptacle positions for a Hatch at {@code hatch} with the given interior direction. */
    public static BlockPos[] receptacles(BlockPos hatch, Direction interior) {
        BlockPos[] out = new BlockPos[RECEPTACLES.length];
        for (int i = 0; i < RECEPTACLES.length; i++) {
            out[i] = hatch.offset(rotateOffset(RECEPTACLES[i][0], RECEPTACLES[i][1], RECEPTACLES[i][2], interior));
        }
        return out;
    }

    /** Where Wardenbane pins: the cavity cell directly in front of the wall-mounted Hatch. */
    public static BlockPos wardenbanePos(BlockPos hatch, Direction interior) {
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
