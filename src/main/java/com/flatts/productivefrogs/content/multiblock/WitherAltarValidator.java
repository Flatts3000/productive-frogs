package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * Validates the Wither Altar (#247) by anchoring on its central Hatch. Unlike the
 * dragon altar (which augments the vanilla End exit portal), the whole structure is
 * player-built from PF blocks, so every block is checked against this canonical
 * layout. Built to mirror the dragon altar's validator shape.
 *
 * <p><b>Strict:</b> every block of the canonical altar must be present at its exact
 * offset from the Hatch - the Withered Star capstone beneath it, the Reinforced Soul
 * Sand Froglight floor, the Reinforced Blaze Rod Froglight pillars flanking the
 * ritual, and the vanilla Wither summon T rendered as receptacles (4 Soul Sand + 3
 * Wither Skull). Reads only block identity, so it runs client-side (Jade) and
 * server-side (the summon) alike.
 *
 * <p>Frame: the Hatch faces +Z toward the ritual; Witherbane pins on top of the Hatch
 * (at {@code hatch.above()}) and the replica forms over the skull receptacles.
 */
public final class WitherAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout). */
    public record Result(boolean valid, String detail) {
    }

    // Offsets from the Hatch (dx, dy, dz), generated from the captured wither_altar
    // structure (the Hatch is the anchor at (0,0,0); the altar faces +Z toward the ritual).
    // Reinforced Soul Sand Froglight floor strip.
    private static final int[][] SOUL_SAND_FLOOR = {
        {-1, -1, 0}, {-1, -1, 1}, {-1, -1, 2}, {-1, -1, 3}, {-1, 0, 3},
        {0, -1, 0}, {0, -1, 1}, {0, -1, 3},
        {1, -1, 0}, {1, -1, 1}, {1, -1, 2}, {1, -1, 3}, {1, 0, 3}
    };
    // Reinforced Blaze Rod Froglight shell (the arena frame).
    private static final int[][] BLAZE_ROD_SHELL = {
        {-2, -1, -1}, {-2, -1, 0}, {-2, -1, 1}, {-2, -1, 2}, {-2, -1, 3}, {-2, 0, -1}, {-2, 0, 3},
        {-2, 1, -1}, {-2, 1, 3}, {-2, 2, -1}, {-2, 2, 3}, {-2, 3, -1}, {-2, 3, 0}, {-2, 3, 1}, {-2, 3, 2}, {-2, 3, 3},
        {-1, -1, -1}, {-1, 3, -1}, {-1, 3, 3}, {0, -1, -1}, {0, 3, -1}, {0, 3, 3}, {1, -1, -1}, {1, 3, -1}, {1, 3, 3},
        {2, -1, -1}, {2, -1, 0}, {2, -1, 1}, {2, -1, 2}, {2, -1, 3}, {2, 0, -1}, {2, 0, 3},
        {2, 1, -1}, {2, 1, 3}, {2, 2, -1}, {2, 2, 3}, {2, 3, -1}, {2, 3, 0}, {2, 3, 1}, {2, 3, 2}, {2, 3, 3}
    };
    // The vanilla Wither summon T at the +Z wall: 3 soul sand across + 1 stem, skulls on top of the 3.
    private static final int[][] SOUL_SAND_RECEPTACLES = {{-1, 1, 3}, {0, 0, 3}, {0, 1, 3}, {1, 1, 3}};
    private static final int[][] SKULL_RECEPTACLES = {{-1, 2, 3}, {0, 2, 3}, {1, 2, 3}};
    private static final int[][] CAPSTONE = {{0, -1, 2}};
    // The interior cavity that must stay clear (where the replica forms + Witherbane perches).
    // Marked with copper grates in the authoring structure; enforced as air here.
    private static final int[][] AIR_REQUIRED = {
        {-1, 0, 0}, {-1, 0, 1}, {-1, 0, 2}, {-1, 1, 0}, {-1, 1, 1}, {-1, 1, 2}, {-1, 2, 0}, {-1, 2, 1}, {-1, 2, 2},
        {0, 0, 1}, {0, 0, 2}, {0, 1, 0}, {0, 1, 1}, {0, 1, 2}, {0, 2, 0}, {0, 2, 1}, {0, 2, 2},
        {1, 0, 0}, {1, 0, 1}, {1, 0, 2}, {1, 1, 0}, {1, 1, 1}, {1, 1, 2}, {1, 2, 0}, {1, 2, 1}, {1, 2, 2}
    };

    private WitherAltarValidator() {
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        if (!level.getBlockState(hatch).is(PFBlocks.WITHER_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch");
        }
        if (!allMatch(level, hatch, CAPSTONE, PFBlocks.WITHERED_STAR.get())) {
            return new Result(false, "missing the Withered Star beneath the Hatch (defeat the Wither first)");
        }
        if (!allMatch(level, hatch, SOUL_SAND_FLOOR, PFBlocks.REINFORCED_SOUL_SAND_FROGLIGHT.get())) {
            return new Result(false, "incomplete Reinforced Soul Sand Froglight floor");
        }
        if (!allMatch(level, hatch, BLAZE_ROD_SHELL, PFBlocks.REINFORCED_BLAZE_ROD_FROGLIGHT.get())) {
            return new Result(false, "incomplete Reinforced Blaze Rod Froglight shell");
        }
        if (!allMatch(level, hatch, SOUL_SAND_RECEPTACLES, PFBlocks.SOUL_SAND_RECEPTACLE.get())) {
            return new Result(false, "missing a Soul Sand Receptacle");
        }
        if (!allMatch(level, hatch, SKULL_RECEPTACLES, PFBlocks.WITHER_SKULL_RECEPTACLE.get())) {
            return new Result(false, "missing a Wither Skull Receptacle");
        }
        if (!allAir(level, hatch, AIR_REQUIRED)) {
            return new Result(false, "the summon cavity must be clear (keep the interior air)");
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

    private static boolean allAir(LevelReader level, BlockPos hatch, int[][] offsets) {
        for (int[] o : offsets) {
            if (!level.getBlockState(hatch.offset(o[0], o[1], o[2])).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** All seven summon receptacle positions (4 soul sand + 3 skull) for a Hatch at {@code hatch}. */
    public static BlockPos[] receptacles(BlockPos hatch) {
        BlockPos[] out = new BlockPos[SOUL_SAND_RECEPTACLES.length + SKULL_RECEPTACLES.length];
        int i = 0;
        for (int[] o : SOUL_SAND_RECEPTACLES) {
            out[i++] = hatch.offset(o[0], o[1], o[2]);
        }
        for (int[] o : SKULL_RECEPTACLES) {
            out[i++] = hatch.offset(o[0], o[1], o[2]);
        }
        return out;
    }

    /** Where Witherbane pins: on top of the Hatch, facing the ritual (+Z). */
    public static BlockPos witherbanePos(BlockPos hatch) {
        return hatch.above();
    }
}
