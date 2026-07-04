package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * Validates the Elder Guardian Altar - the "Monument Well" (#280) - by anchoring
 * on its central Hatch in the tank floor. A sealed 5x5x5 water tank: a
 * Reinforced Sponge Froglight floor, Reinforced Light Blue Stained Glass walls
 * (the aquarium view - maintainer ruling 2026-07-04), a Reinforced Prismarine
 * Froglight roof, the four Tide Offering Receptacles standing at the roof corners like
 * monument spires (the per-summon fuel: 4 prismarine crystals), and the Monument
 * Core capstone at the roof center (crafted from a Wet Sponge - the Elder
 * Guardian's signature drop). The 3x3x3 interior must be water source blocks;
 * Elderbane swims above the Hatch and the Elder Guardian replica forms in the
 * water.
 *
 * <p><b>Strict and symmetric:</b> every block is checked at its exact offset from
 * the Hatch; the layout is 4-fold rotationally symmetric so no facing-aware
 * rotation pass is needed (mirrors {@link WardenAltarValidator}). Reads only
 * block/fluid identity, so it runs client-side (Jade) and server-side alike.
 */
public final class ElderAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout). */
    public record Result(boolean valid, String detail) {
    }

    /** The Monument Core capstone, at the roof center. */
    private static final int[][] CAPSTONE = {{0, 3, 0}};

    /** Reinforced Sponge Froglight: the 5x5 tank floor (the Hatch sits at its center, one above). */
    private static final int[][] SPONGE_FLOOR = buildFloor();

    /** Reinforced Light Blue Stained Glass: the three wall rings (the aquarium view). */
    private static final int[][] GLASS_WALLS = buildWalls();

    /** Reinforced Prismarine Froglight: the roof plate. */
    private static final int[][] PRISMARINE_ROOF = buildRoof();

    /** The four Tide Offering Receptacles, at the roof corners (fuel: prismarine crystals). */
    private static final int[][] RECEPTACLES = {{2, 3, 2}, {2, 3, -2}, {-2, 3, 2}, {-2, 3, -2}};

    /** The flooded interior: every 3x3x3 cell except the Hatch must be a water source. */
    private static final int[][] WATER_REQUIRED = buildWater();

    private ElderAltarValidator() {
    }

    private static int[][] buildFloor() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                out.add(new int[] {dx, -1, dz});
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildWalls() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == 2) {
                    for (int dy = 0; dy <= 2; dy++) {
                        out.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildRoof() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                boolean center = dx == 0 && dz == 0;
                if (!corner && !center) {
                    out.add(new int[] {dx, 3, dz}); // roof (corners = receptacles, center = the Core)
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    private static int[][] buildWater() {
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    if (!(dx == 0 && dz == 0 && dy == 0)) { // the Hatch cell
                        out.add(new int[] {dx, dy, dz});
                    }
                }
            }
        }
        return out.toArray(int[][]::new);
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        if (!level.getBlockState(hatch).is(PFBlocks.ELDER_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch");
        }
        if (!allMatch(level, hatch, CAPSTONE, PFBlocks.MONUMENT_CORE.get())) {
            return new Result(false, "missing the Monument Core at the roof center");
        }
        if (!allMatch(level, hatch, SPONGE_FLOOR, PFBlocks.REINFORCED_SPONGE_FROGLIGHT.get())) {
            return new Result(false, "incomplete Reinforced Sponge Froglight floor");
        }
        if (!allMatch(level, hatch, GLASS_WALLS, PFBlocks.REINFORCED_LIGHT_BLUE_STAINED_GLASS.get())) {
            return new Result(false, "incomplete Reinforced Light Blue Stained Glass walls");
        }
        if (!allMatch(level, hatch, PRISMARINE_ROOF, PFBlocks.REINFORCED_PRISMARINE_FROGLIGHT.get())) {
            return new Result(false, "incomplete Reinforced Prismarine Froglight roof");
        }
        if (!allMatch(level, hatch, RECEPTACLES, PFBlocks.TIDE_OFFERING_RECEPTACLE.get())) {
            return new Result(false, "missing a Tide Offering Receptacle at a roof corner");
        }
        for (int[] o : WATER_REQUIRED) {
            BlockPos p = hatch.offset(o[0], o[1], o[2]);
            if (!level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.WATER)
                    || !level.getFluidState(p).isSource()) {
                return new Result(false, "the tank must be flooded (fill the interior with water sources)");
            }
        }
        return new Result(true, "ready");
    }

    /** The four Tide Offering Receptacle positions for a Hatch at {@code hatch}. */
    public static BlockPos[] receptacles(BlockPos hatch) {
        BlockPos[] out = new BlockPos[RECEPTACLES.length];
        for (int i = 0; i < RECEPTACLES.length; i++) {
            out[i] = hatch.offset(RECEPTACLES[i][0], RECEPTACLES[i][1], RECEPTACLES[i][2]);
        }
        return out;
    }

    /** Where Elderbane swims: the water cell on top of the Hatch. */
    public static BlockPos elderbanePos(BlockPos hatch) {
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
