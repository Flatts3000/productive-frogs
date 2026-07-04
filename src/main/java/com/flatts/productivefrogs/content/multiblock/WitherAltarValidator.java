package com.flatts.productivefrogs.content.multiblock;

import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Validates the Wither Altar (#247) by anchoring on its central Hatch. Unlike the
 * dragon altar (which augments the vanilla End exit portal), the whole structure is
 * player-built from PF blocks, so every block is checked against this canonical
 * layout. Built to mirror the dragon altar's validator shape.
 *
 * <p><b>Strict:</b> every block of the canonical altar must be present at its exact
 * offset from the Hatch - the Withered Star capstone set into the floor, the Reinforced Soul
 * Sand Froglight floor, the Reinforced Glowstone Froglight pillars flanking the
 * ritual, and the vanilla Wither summon T rendered as receptacles (4 Soul Sand + 3
 * Wither Skull). Reads only block identity, so it runs client-side (Jade) and
 * server-side (the summon) alike.
 *
 * <p><b>Facing-aware (#263-sibling fix).</b> The offset tables are authored in a
 * canonical frame where the ritual wall points <b>+Z (SOUTH)</b>. The Hatch is a
 * non-directional block, so the altar may be built facing any of the four horizontal
 * directions; {@link #validate} tries each rotation of the frame and returns the
 * <b>resolved ritual direction</b> in its {@link Result}. The Witherbane perch
 * orientation, the receptacle item render, and the summon replica all read that
 * direction so the altar works in any orientation (previously it only validated when
 * built in the one canonical world rotation - the build-orientation bug Gargish hit).
 * SOUTH is the identity rotation, so altars built before this change validate
 * unchanged.
 */
public final class WitherAltarValidator {

    /** Outcome: valid, plus a short reason when not (for the Jade readout) and the resolved ritual direction. */
    public record Result(boolean valid, String detail, @Nullable Direction ritual) {
    }

    // Offsets from the Hatch (dx, dy, dz), generated from the captured wither_altar
    // structure (the Hatch is the anchor at (0,0,0); the canonical frame faces +Z / SOUTH
    // toward the ritual). validate() rotates these about the vertical axis per candidate.
    // Reinforced Soul Sand Froglight floor strip.
    private static final int[][] SOUL_SAND_FLOOR = {
        {-1, -1, 0}, {-1, -1, 1}, {-1, -1, 2}, {-1, -1, 3}, {-1, 0, 3},
        {0, -1, 0}, {0, -1, 1}, {0, -1, 3},
        {1, -1, 0}, {1, -1, 1}, {1, -1, 2}, {1, -1, 3}, {1, 0, 3}
    };
    // Reinforced Glowstone Froglight shell (the arena frame).
    private static final int[][] GLOWSTONE_SHELL = {
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

    /** The canonical authoring frame: the ritual wall points this way. SOUTH is the identity rotation. */
    public static final Direction CANONICAL_RITUAL = Direction.SOUTH;

    private WitherAltarValidator() {
    }

    public static Result validate(LevelReader level, BlockPos hatch) {
        return validate(level, hatch, null);
    }

    /**
     * Validate with an orientation hint: a formed altar's direction virtually
     * never changes, so the hatch passes its cached orientation and the common
     * case is ONE full pass instead of scanning up to four rotations every
     * reconcile (review finding).
     */
    public static Result validate(LevelReader level, BlockPos hatch, @Nullable Direction hint) {
        if (!level.getBlockState(hatch).is(PFBlocks.WITHER_ALTAR_HATCH.get())) {
            return new Result(false, "no hatch", null);
        }
        if (hint != null && hint.getAxis().isHorizontal() && validateOriented(level, hatch, hint).valid()) {
            return new Result(true, "ready", hint);
        }
        // Try each horizontal orientation; accept the first that fully matches. On
        // failure, report the orientation that got furthest (the player-facing problem).
        Oriented best = null;
        for (Direction ritual : Direction.Plane.HORIZONTAL) {
            Oriented o = validateOriented(level, hatch, ritual);
            if (o.valid()) {
                return new Result(true, "ready", ritual);
            }
            if (best == null || o.stage() > best.stage()) {
                best = o;
            }
        }
        return new Result(false, best.detail(), null);
    }

    /** A single-orientation pass: {@code stage} = how many checks passed (6 = valid). */
    private record Oriented(int stage, String detail, Direction ritual) {
        boolean valid() {
            return stage == 6;
        }
    }

    private static Oriented validateOriented(LevelReader level, BlockPos hatch, Direction ritual) {
        if (!AltarGeometry.allMatch(level, hatch, CAPSTONE, ritual, PFBlocks.WITHERED_STAR.get())) {
            return new Oriented(0, "missing the Withered Star (defeat the Wither first)", ritual);
        }
        if (!AltarGeometry.allMatch(level, hatch, SOUL_SAND_FLOOR, ritual, PFBlocks.REINFORCED_SOUL_SAND_FROGLIGHT.get())) {
            return new Oriented(1, "incomplete Reinforced Soul Sand Froglight floor", ritual);
        }
        if (!AltarGeometry.allMatch(level, hatch, GLOWSTONE_SHELL, ritual, PFBlocks.REINFORCED_GLOWSTONE_FROGLIGHT.get())) {
            return new Oriented(2, "incomplete Reinforced Glowstone Froglight shell", ritual);
        }
        if (!AltarGeometry.allMatch(level, hatch, SOUL_SAND_RECEPTACLES, ritual, PFBlocks.SOUL_SAND_RECEPTACLE.get())) {
            return new Oriented(3, "missing a Soul Sand Receptacle", ritual);
        }
        if (!AltarGeometry.allMatch(level, hatch, SKULL_RECEPTACLES, ritual, PFBlocks.WITHER_SKULL_RECEPTACLE.get())) {
            return new Oriented(4, "missing a Wither Skull Receptacle", ritual);
        }
        if (!AltarGeometry.allAir(level, hatch, AIR_REQUIRED, ritual)) {
            return new Oriented(5, "the summon cavity must be clear (keep the interior air)", ritual);
        }
        return new Oriented(6, "ready", ritual);
    }



    /**
     * Rotate an authored offset (canonical frame: ritual points +Z / SOUTH) about the
     * vertical axis so the ritual instead points along {@code ritual}. Horizontal only;
     * {@code dy} is unchanged. SOUTH is the identity. Package-visible for geometry tests.
     */
    static BlockPos rotateOffset(int dx, int dy, int dz, Direction ritual) {
        return AltarGeometry.rotateOffset(dx, dy, dz, ritual);
    }

    /**
     * All seven summon receptacle positions (4 soul sand + 3 skull) for a Hatch at
     * {@code hatch}, with the ritual wall pointing {@code ritual}.
     */
    public static BlockPos[] receptacles(BlockPos hatch, Direction ritual) {
        BlockPos[] out = new BlockPos[SOUL_SAND_RECEPTACLES.length + SKULL_RECEPTACLES.length];
        int i = 0;
        for (int[] o : SOUL_SAND_RECEPTACLES) {
            out[i++] = hatch.offset(rotateOffset(o[0], o[1], o[2], ritual));
        }
        for (int[] o : SKULL_RECEPTACLES) {
            out[i++] = hatch.offset(rotateOffset(o[0], o[1], o[2], ritual));
        }
        return out;
    }

    /** Back-compat: receptacles in the canonical (SOUTH) orientation. */
    public static BlockPos[] receptacles(BlockPos hatch) {
        return receptacles(hatch, CANONICAL_RITUAL);
    }

    /** Where Witherbane pins: on top of the Hatch (rotation-independent). It faces the ritual. */
    public static BlockPos witherbanePos(BlockPos hatch) {
        return hatch.above();
    }
}
