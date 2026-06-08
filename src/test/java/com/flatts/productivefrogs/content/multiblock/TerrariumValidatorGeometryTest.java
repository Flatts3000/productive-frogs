package com.flatts.productivefrogs.content.multiblock;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/**
 * Pure-geometry coverage for {@link TerrariumValidator} - the anchor math the
 * in-world GameTests only exercise indirectly. No level needed.
 */
class TerrariumValidatorGeometryTest {

    @Test
    void candidateOrderCoversThe5x5FaceCenteredFirst() {
        assertEquals(25, TerrariumValidator.CANDIDATE_ORDER.length, "one candidate per 5x5 face cell");
        assertArrayEquals(new int[] {2, 2}, TerrariumValidator.CANDIDATE_ORDER[0], "centered cell tried first");
        Set<String> seen = new HashSet<>();
        for (int[] ab : TerrariumValidator.CANDIDATE_ORDER) {
            assertTrue(ab[0] >= 0 && ab[0] < 5 && ab[1] >= 0 && ab[1] < 5, "offsets within 0..4");
            seen.add(ab[0] + "," + ab[1]);
        }
        assertEquals(25, seen.size(), "all 25 offsets are distinct");
    }

    @Test
    void perpendicularAxesExcludeTheInwardAxis() {
        assertEquals(Set.of(Direction.Axis.X, Direction.Axis.Z),
            axisSet(TerrariumValidator.perpendicular(Direction.Axis.Y)), "Y inward -> X,Z");
        assertEquals(Set.of(Direction.Axis.Y, Direction.Axis.Z),
            axisSet(TerrariumValidator.perpendicular(Direction.Axis.X)), "X inward -> Y,Z");
        assertEquals(Set.of(Direction.Axis.X, Direction.Axis.Y),
            axisSet(TerrariumValidator.perpendicular(Direction.Axis.Z)), "Z inward -> X,Y");
    }

    private static Set<Direction.Axis> axisSet(Direction[] dirs) {
        return Set.of(dirs[0].getAxis(), dirs[1].getAxis());
    }

    @Test
    void extremesClassifiesCornerEdgeFaceInterior() {
        BlockPos min = new BlockPos(0, 0, 0);
        BlockPos max = new BlockPos(6, 6, 6);
        assertEquals(3, TerrariumValidator.extremes(0, 0, 0, min, max), "corner = 3 extremes");
        assertEquals(2, TerrariumValidator.extremes(0, 0, 3, min, max), "edge = 2 extremes");
        assertEquals(1, TerrariumValidator.extremes(0, 3, 3, min, max), "face = 1 extreme");
        assertEquals(0, TerrariumValidator.extremes(3, 3, 3, min, max), "interior = 0 extremes");
    }

    @Test
    void cavityBoundsAreExactlyFiveCubedAtTheCenteredAnchor() {
        // Controller at origin facing WEST (outward) -> inward EAST(+X); its inward
        // neighbour (1,0,0) is the cavity near-face. Centered offsets (2,2) put the
        // cavity symmetric on the two perpendicular axes.
        BlockPos[] bounds = TerrariumValidator.cavityBounds(new BlockPos(0, 0, 0), Direction.WEST, 2, 2);
        BlockPos cavityMin = bounds[0];
        BlockPos cavityMax = bounds[1];
        assertEquals(5, cavityMax.getX() - cavityMin.getX() + 1, "5 along X");
        assertEquals(5, cavityMax.getY() - cavityMin.getY() + 1, "5 along Y");
        assertEquals(5, cavityMax.getZ() - cavityMin.getZ() + 1, "5 along Z");
        // Near face one step inward from the Controller, cavity spans x 1..5.
        assertEquals(1, cavityMin.getX(), "near face abuts the Controller's inward neighbour");
        assertEquals(5, cavityMax.getX());
        // The Controller (0,0,0) lands on the shell (cavity inflated by 1) as a face cell.
        assertEquals(0, cavityMin.getX() - 1, "shellMin.x is the Controller's x");
        assertTrue(0 >= cavityMin.getY() - 1 && 0 <= cavityMax.getY() + 1, "Controller within shell on Y");
        assertTrue(0 >= cavityMin.getZ() - 1 && 0 <= cavityMax.getZ() + 1, "Controller within shell on Z");
    }
}
