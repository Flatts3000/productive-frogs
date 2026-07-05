package com.flatts.productivefrogs.content.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;

/**
 * The shared geometry of the facing-aware altar validators (#247/#279/#280).
 * Every validator authors its offset tables in a canonical frame where the
 * altar's distinguishing direction (the wither's ritual wall, the pit/tank
 * interior) points <b>+Z / SOUTH</b>, then tries the four horizontal rotations.
 * This class owns the rotation math and the offset scans - the review found
 * three byte-identical private copies (~80 lines each), meaning a rotation fix
 * applied to one altar could silently miss the others.
 */
public final class AltarGeometry {

    private AltarGeometry() {
    }

    /**
     * Rotate an authored offset (canonical frame: the distinguishing direction =
     * +Z / SOUTH) about the vertical axis so it instead points along
     * {@code toward}. SOUTH is the identity; {@code dy} never changes.
     */
    public static BlockPos rotateOffset(int dx, int dy, int dz, Direction toward) {
        return switch (toward) {
            case SOUTH -> new BlockPos(dx, dy, dz);
            case WEST -> new BlockPos(-dz, dy, dx);
            case NORTH -> new BlockPos(-dx, dy, -dz);
            case EAST -> new BlockPos(dz, dy, -dx);
            default -> new BlockPos(dx, dy, dz); // UP/DOWN never passed (HORIZONTAL plane only)
        };
    }

    /** Whether every rotated offset from {@code anchor} is {@code block}. */
    public static boolean allMatch(LevelReader level, BlockPos anchor, int[][] offsets, Direction toward, Block block) {
        for (int[] o : offsets) {
            if (!level.getBlockState(anchor.offset(rotateOffset(o[0], o[1], o[2], toward))).is(block)) {
                return false;
            }
        }
        return true;
    }

    /** Whether every rotated offset from {@code anchor} is air. */
    public static boolean allAir(LevelReader level, BlockPos anchor, int[][] offsets, Direction toward) {
        for (int[] o : offsets) {
            if (!level.getBlockState(anchor.offset(rotateOffset(o[0], o[1], o[2], toward))).isAir()) {
                return false;
            }
        }
        return true;
    }

    /** The rotated world positions of {@code offsets} for an anchor at {@code anchor}. */
    public static BlockPos[] positions(BlockPos anchor, int[][] offsets, Direction toward) {
        BlockPos[] out = new BlockPos[offsets.length];
        for (int i = 0; i < offsets.length; i++) {
            out[i] = anchor.offset(rotateOffset(offsets[i][0], offsets[i][1], offsets[i][2], toward));
        }
        return out;
    }
}
