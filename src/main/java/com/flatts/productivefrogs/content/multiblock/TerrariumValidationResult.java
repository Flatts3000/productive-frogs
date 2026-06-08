package com.flatts.productivefrogs.content.multiblock;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Outcome of one {@link TerrariumValidator} run against a candidate Controller.
 *
 * <p>On success it carries everything later phases need: the cavity bounds
 * (as inclusive corners, plus an {@link #cavityAabb()} helper), the Hatch and
 * Controller positions, and the Sprinkler / Incubator position lists - these
 * become the {@link TerrariumManager.FormedTerrarium} record. On failure it
 * carries the {@link Problem} the player sees when they right-click the
 * Controller ("why won't it form"): a lang message key plus the offending
 * {@link BlockPos}.
 *
 * <p>The 5x4x5 / 7x6x7 geometry and the placement rules are the settled
 * rulings from issue #185 / {@code docs/terrarium.md}.
 */
public record TerrariumValidationResult(
    boolean formed,
    @Nullable BlockPos cavityMin,
    @Nullable BlockPos cavityMax,
    @Nullable BlockPos controllerPos,
    @Nullable BlockPos hatchPos,
    List<BlockPos> sprinklers,
    List<BlockPos> incubators,
    @Nullable Problem firstProblem) {

    /** A single structural failure: a {@code message.productivefrogs.terrarium.*} key + where. */
    public record Problem(String messageKey, @Nullable BlockPos at) {
    }

    public static TerrariumValidationResult formed(BlockPos controllerPos, BlockPos cavityMin, BlockPos cavityMax,
            BlockPos hatchPos, List<BlockPos> sprinklers, List<BlockPos> incubators) {
        return new TerrariumValidationResult(true, cavityMin, cavityMax, controllerPos, hatchPos,
            List.copyOf(sprinklers), List.copyOf(incubators), null);
    }

    public static TerrariumValidationResult failed(String messageKey, @Nullable BlockPos at) {
        return new TerrariumValidationResult(false, null, null, null, null, List.of(), List.of(),
            new Problem(messageKey, at));
    }

    /** Inclusive cavity corners as a block-aligned AABB (used for entity-count scans). */
    public AABB cavityAabb() {
        if (cavityMin == null || cavityMax == null) {
            throw new IllegalStateException("cavityAabb() on an unformed result");
        }
        return new AABB(
            cavityMin.getX(), cavityMin.getY(), cavityMin.getZ(),
            cavityMax.getX() + 1.0, cavityMax.getY() + 1.0, cavityMax.getZ() + 1.0);
    }
}
