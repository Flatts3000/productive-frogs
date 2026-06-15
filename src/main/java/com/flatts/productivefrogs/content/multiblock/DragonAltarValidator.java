package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Validates the End Dragon Altar (#249) by anchoring on its central Hatch. The
 * altar augments a vanilla End exit portal, so the bedrock / end_portal / dragon
 * egg are the vanilla structure already there (the egg only after a real first
 * kill); the player adds the crystal receptacles and the reinforced froglights.
 *
 * <p><b>Strict:</b> every block of the canonical altar must be present at its
 * exact offset from the Hatch - all Wither Skeleton Skull froglights, all 4
 * Nether Star froglights, all 4 receptacles, the dragon-egg capstone, and the
 * complete exit portal (20 end_portal cells + the 41-cell bedrock fountain, which
 * also carries the central plinth). Offsets are generated from the captured
 * {@code dragon_altar} structure, not hand-typed. Reads only block identity, so
 * it runs client-side (Jade) and server-side (the summon) alike.
 */
public final class DragonAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout). */
    public record Result(boolean valid, String detail) {
    }

    // Offsets from the Hatch (dx, dy, dz), generated from the canonical structure.
    private static final int[][] BEDROCK = {
        {-3, -7, -1}, {-3, -7, 0}, {-3, -7, 1}, {-2, -8, -1}, {-2, -8, 0}, {-2, -8, 1}, {-2, -7, -2}, {-2, -7, 2},
        {-1, -8, -2}, {-1, -8, -1}, {-1, -8, 0}, {-1, -8, 1}, {-1, -8, 2}, {-1, -7, -3}, {-1, -7, 3},
        {0, -8, -2}, {0, -8, -1}, {0, -8, 0}, {0, -8, 1}, {0, -8, 2}, {0, -7, -3}, {0, -7, 0}, {0, -7, 3},
        {0, -6, 0}, {0, -5, 0}, {0, -4, 0},
        {1, -8, -2}, {1, -8, -1}, {1, -8, 0}, {1, -8, 1}, {1, -8, 2}, {1, -7, -3}, {1, -7, 3},
        {2, -8, -1}, {2, -8, 0}, {2, -8, 1}, {2, -7, -2}, {2, -7, 2}, {3, -7, -1}, {3, -7, 0}, {3, -7, 1}
    };
    private static final int[][] EXIT_PORTAL = {
        {-2, -7, -1}, {-2, -7, 0}, {-2, -7, 1}, {-1, -7, -2}, {-1, -7, -1}, {-1, -7, 0}, {-1, -7, 1}, {-1, -7, 2},
        {0, -7, -2}, {0, -7, -1}, {0, -7, 1}, {0, -7, 2}, {1, -7, -2}, {1, -7, -1}, {1, -7, 0}, {1, -7, 1}, {1, -7, 2},
        {2, -7, -1}, {2, -7, 0}, {2, -7, 1}
    };
    private static final int[][] RECEPTACLES = {{-3, -6, 0}, {0, -6, -3}, {0, -6, 3}, {3, -6, 0}};
    private static final int[][] NETHER_STAR = {{-2, -3, -2}, {-2, -3, 2}, {2, -3, -2}, {2, -3, 2}};
    private static final int[][] WSS = {
        {-3, -6, -1}, {-3, -6, 1}, {-2, -6, -2}, {-2, -6, 2}, {-2, -5, -2}, {-2, -5, 2}, {-2, -4, -2}, {-2, -4, 2},
        {-1, -6, -3}, {-1, -6, 3}, {1, -6, -3}, {1, -6, 3}, {2, -6, -2}, {2, -6, 2}, {2, -5, -2}, {2, -5, 2},
        {2, -4, -2}, {2, -4, 2}, {3, -6, -1}, {3, -6, 1}
    };

    private DragonAltarValidator() {
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        if (!level.getBlockState(hatch).is(PFBlocks.END_DRAGON_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch");
        }
        if (!level.getBlockState(hatch.above()).is(Blocks.DRAGON_EGG)) {
            return new Result(false, "missing the Dragon Egg capstone (defeat the dragon first)");
        }
        if (!allMatch(level, hatch, BEDROCK, Blocks.BEDROCK) || !allMatch(level, hatch, EXIT_PORTAL, Blocks.END_PORTAL)) {
            return new Result(false, "not built on a complete End exit portal");
        }
        if (!allMatch(level, hatch, RECEPTACLES, PFBlocks.END_CRYSTAL_RECEPTACLE.get())) {
            return new Result(false, "missing an End Crystal Receptacle");
        }
        if (!allMatch(level, hatch, NETHER_STAR, PFBlocks.REINFORCED_NETHER_STAR_FROGLIGHT.get())) {
            return new Result(false, "missing a Reinforced Nether Star Froglight");
        }
        if (!allMatch(level, hatch, WSS, PFBlocks.REINFORCED_WITHER_SKELETON_SKULL_FROGLIGHT.get())) {
            return new Result(false, "missing a Reinforced Wither Skeleton Skull Froglight");
        }
        return new Result(true, "ready");
    }

    private static boolean allMatch(LevelReader level, BlockPos hatch, int[][] offsets, Block block) {
        for (int[] o : offsets) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).is(block)) {
                return false;
            }
        }
        return true;
    }

    /** The four receptacle positions for a Hatch at {@code hatch} (the summon reads these). */
    public static BlockPos[] receptacles(BlockPos hatch) {
        BlockPos[] out = new BlockPos[RECEPTACLES.length];
        for (int i = 0; i < RECEPTACLES.length; i++) {
            out[i] = hatch.offset(RECEPTACLES[i][0], RECEPTACLES[i][1], RECEPTACLES[i][2]);
        }
        return out;
    }
}
