package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;

/**
 * Validates the End Dragon Altar (#249) by anchoring on its central Hatch. The
 * altar augments a vanilla End exit portal, so the bedrock / end_portal / dragon
 * egg are the vanilla structure already there; the player adds the crystal
 * receptacles and the reinforced froglight pillars on top.
 *
 * <p>Offsets are relative to the Hatch, derived from the captured structure
 * (Hatch at the centre column, y-top; see {@code docs} and the {@code dragon_altar}
 * structure capture). This checks the load-bearing skeleton - the dragon-egg gate
 * (which only exists after a real first kill), the four receptacles, the four
 * Nether Star froglights, the four corner Wither Skeleton Skull froglights, and a
 * live exit portal beneath - not every decorative cell. Reads only block identity,
 * so it runs client-side (Jade) and server-side (the summon) alike.
 */
public final class DragonAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout). */
    public record Result(boolean valid, String detail) {
    }

    // Offsets from the Hatch (dx, dy, dz).
    private static final int[][] EXIT_PORTAL = {{-1, -6, 0}, {1, -6, 0}, {0, -6, -1}, {0, -6, 1}};
    private static final int[][] RECEPTACLES = {{0, -5, -3}, {0, -5, 3}, {-3, -5, 0}, {3, -5, 0}};
    private static final int[][] NETHER_STAR = {{-2, -4, -2}, {-2, -4, 2}, {2, -4, -2}, {2, -4, 2}};
    private static final int[][] WSS_CORNERS = {{-2, -5, -2}, {-2, -5, 2}, {2, -5, -2}, {2, -5, 2}};

    private DragonAltarValidator() {
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        if (!level.getBlockState(hatch).is(PFBlocks.END_DRAGON_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch");
        }
        if (!level.getBlockState(hatch.above()).is(Blocks.DRAGON_EGG)) {
            return new Result(false, "missing the Dragon Egg capstone (defeat the dragon first)");
        }
        for (int[] o : EXIT_PORTAL) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).is(Blocks.END_PORTAL)) {
                return new Result(false, "not built over an End exit portal");
            }
        }
        for (int[] o : RECEPTACLES) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).is(PFBlocks.END_CRYSTAL_RECEPTACLE.get())) {
                return new Result(false, "missing an End Crystal Receptacle");
            }
        }
        for (int[] o : NETHER_STAR) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).is(PFBlocks.REINFORCED_NETHER_STAR_FROGLIGHT.get())) {
                return new Result(false, "missing a Reinforced Nether Star Froglight");
            }
        }
        for (int[] o : WSS_CORNERS) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).is(PFBlocks.REINFORCED_WITHER_SKELETON_SKULL_FROGLIGHT.get())) {
                return new Result(false, "missing a Reinforced Wither Skeleton Skull Froglight");
            }
        }
        return new Result(true, "ready");
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
