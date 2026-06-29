package com.flatts.productivefrogs.content.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * Pure-geometry coverage for {@link WitherAltarValidator}'s facing-aware rotation - the
 * math the in-world GameTests exercise only at the canonical and one rotated orientation.
 * Locks {@link WitherAltarValidator#rotateOffset} so a layout/offset edit can't silently
 * break a non-canonical build. No level needed.
 */
class WitherAltarValidatorGeometryTest {

    @Test
    void southIsTheIdentityRotation() {
        // Every authored offset must pass through unchanged in the canonical frame, so
        // altars built before the facing-aware fix validate identically.
        assertEquals(new BlockPos(0, 2, 3),
            WitherAltarValidator.rotateOffset(0, 2, 3, Direction.SOUTH));
        assertEquals(new BlockPos(-1, -1, 0),
            WitherAltarValidator.rotateOffset(-1, -1, 0, Direction.SOUTH));
    }

    @Test
    void rotatesTheRitualWallOntoEachHorizontalFace() {
        // The front-centre skull offset {0,2,3}: dz=3 is "toward the ritual" and must
        // swing to +Z/-X/-Z/+X as the ritual points S/W/N/E. dy (the height) never moves.
        assertEquals(new BlockPos(0, 2, 3), WitherAltarValidator.rotateOffset(0, 2, 3, Direction.SOUTH));
        assertEquals(new BlockPos(-3, 2, 0), WitherAltarValidator.rotateOffset(0, 2, 3, Direction.WEST));
        assertEquals(new BlockPos(0, 2, -3), WitherAltarValidator.rotateOffset(0, 2, 3, Direction.NORTH));
        assertEquals(new BlockPos(3, 2, 0), WitherAltarValidator.rotateOffset(0, 2, 3, Direction.EAST));
    }

    @Test
    void rotationPreservesHeightAndChirality() {
        // An off-axis offset {1,3,2}: the rotation is a rigid turn about Y (preserves dy
        // and is a 90-degree step), distinct in all four orientations.
        Set<BlockPos> seen = new HashSet<>();
        for (Direction ritual : Direction.Plane.HORIZONTAL) {
            BlockPos r = WitherAltarValidator.rotateOffset(1, 3, 2, ritual);
            assertEquals(3, r.getY(), "height (dy) is never rotated");
            seen.add(r);
        }
        assertEquals(4, seen.size(), "an off-axis offset is distinct in all four orientations");
        assertEquals(new BlockPos(-2, 3, 1), WitherAltarValidator.rotateOffset(1, 3, 2, Direction.WEST));
        assertEquals(new BlockPos(-1, 3, -2), WitherAltarValidator.rotateOffset(1, 3, 2, Direction.NORTH));
        assertEquals(new BlockPos(2, 3, -1), WitherAltarValidator.rotateOffset(1, 3, 2, Direction.EAST));
    }

    @Test
    void receptaclesRotateWithTheRitualAndDefaultToCanonical() {
        BlockPos hatch = new BlockPos(10, 64, -20);
        BlockPos[] canonical = WitherAltarValidator.receptacles(hatch);
        BlockPos[] south = WitherAltarValidator.receptacles(hatch, Direction.SOUTH);
        assertEquals(7, canonical.length, "4 soul sand + 3 skull receptacles");
        for (int i = 0; i < canonical.length; i++) {
            assertEquals(canonical[i], south[i], "no-arg receptacles() is the SOUTH (canonical) set");
        }
        // A west-facing altar puts every receptacle on the -X wall, not the +Z wall.
        for (BlockPos rp : WitherAltarValidator.receptacles(hatch, Direction.WEST)) {
            assertNotEquals(hatch.getZ() + 3, rp.getZ(), "west ritual: receptacles leave the +Z wall");
            assertEquals(hatch.getX() - 3, rp.getX(), "west ritual: receptacles sit on the -X wall");
        }
    }
}
