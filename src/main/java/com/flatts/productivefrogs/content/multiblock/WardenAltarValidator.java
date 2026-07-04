package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * Validates the Warden Altar - the "Shrieker Pit" (#279) - by anchoring on its
 * central Hatch at the pit floor. An open-topped 5x5 shaft, four deep: a
 * Reinforced Sculk Froglight floor and shaft lining, a Reinforced Echo Shard
 * Froglight rim at grade carrying the four Shrieker Receptacles (the per-summon
 * fuel: 4 sculk shriekers, one per warning level), and the Echoing Catalyst
 * capstone set into the floor directly beneath the Hatch (crafted from a Sculk
 * Catalyst - building the altar proves a first Warden kill). Wardenbane perches
 * on the Hatch; the Warden replica rises from the pit floor.
 *
 * <p><b>Strict and symmetric:</b> every block is checked at its exact offset from
 * the Hatch. The layout is 4-fold rotationally symmetric (receptacles at the four
 * rim cardinals, capstone centered), so unlike the wither altar no facing-aware
 * rotation pass is needed. Reads only block identity, so it runs client-side
 * (Jade) and server-side (the summon) alike.
 */
public final class WardenAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout). */
    public record Result(boolean valid, String detail) {
    }

    /** The Echoing Catalyst capstone, set into the pit floor directly beneath the Hatch. */
    private static final int[][] CAPSTONE = {{0, -1, 0}};

    // Reinforced Sculk Froglight: the 5x5 pit floor (minus the capstone cell) and
    // the three shaft-lining rings below grade.
    private static final int[][] SCULK = buildSculk();

    // Reinforced Echo Shard Froglight: the rim ring at grade, minus the four
    // cardinal receptacle sockets.
    private static final int[][] ECHO_RIM = buildEchoRim();

    /** The four Shrieker Receptacles, at the rim cardinals (fuel: one sculk shrieker each). */
    private static final int[][] RECEPTACLES = {{0, 3, 2}, {0, 3, -2}, {2, 3, 0}, {-2, 3, 0}};

    // The open shaft: the 3x3 interior from the Hatch level to the rim, air except
    // the Hatch cell itself. The Warden replica needs the full column clear.
    private static final int[][] AIR_REQUIRED = buildAir();

    private WardenAltarValidator() {
    }

    private static int[][] buildSculk() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!(dx == 0 && dz == 0)) {
                    out.add(new int[] {dx, -1, dz}); // floor (capstone under the hatch at 0,-1,0)
                }
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    for (int dy = 0; dy <= 2; dy++) {
                        out.add(new int[] {dx, dy, dz}); // shaft lining
                    }
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildEchoRim() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2
                        && !(dx == 0 && Math.abs(dz) == 2) && !(Math.abs(dx) == 2 && dz == 0)) {
                    out.add(new int[] {dx, 3, dz});
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildAir() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    if (!(dx == 0 && dz == 0 && dy == 0)) { // the Hatch cell
                        out.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        if (!level.getBlockState(hatch).is(PFBlocks.WARDEN_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch");
        }
        if (!allMatch(level, hatch, CAPSTONE, PFBlocks.ECHOING_CATALYST.get())) {
            return new Result(false, "missing the Echoing Catalyst beneath the Hatch (defeat a Warden first)");
        }
        if (!allMatch(level, hatch, SCULK, PFBlocks.REINFORCED_SCULK_FROGLIGHT.get())) {
            return new Result(false, "incomplete Reinforced Sculk Froglight floor/lining");
        }
        if (!allMatch(level, hatch, ECHO_RIM, PFBlocks.REINFORCED_ECHO_SHARD_FROGLIGHT.get())) {
            return new Result(false, "incomplete Reinforced Echo Shard Froglight rim");
        }
        if (!allMatch(level, hatch, RECEPTACLES, PFBlocks.SHRIEKER_RECEPTACLE.get())) {
            return new Result(false, "missing a Shrieker Receptacle on the rim");
        }
        for (int[] o : AIR_REQUIRED) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).isAir()) {
                return new Result(false, "the shaft must be clear (keep the interior air)");
            }
        }
        return new Result(true, "ready");
    }

    /** The four Shrieker Receptacle positions for a Hatch at {@code hatch}. */
    public static BlockPos[] receptacles(BlockPos hatch) {
        BlockPos[] out = new BlockPos[RECEPTACLES.length];
        for (int i = 0; i < RECEPTACLES.length; i++) {
            out[i] = hatch.offset(RECEPTACLES[i][0], RECEPTACLES[i][1], RECEPTACLES[i][2]);
        }
        return out;
    }

    /** Where Wardenbane pins: on top of the Hatch, at the pit floor. */
    public static BlockPos wardenbanePos(BlockPos hatch) {
        return hatch.above();
    }

    private static boolean allMatch(LevelReader level, BlockPos hatch, int[][] offsets, Block block) {
        for (int[] o : offsets) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).is(block)) {
                return false;
            }
        }
        return true;
    }
}
