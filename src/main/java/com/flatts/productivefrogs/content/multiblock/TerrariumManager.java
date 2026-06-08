package com.flatts.productivefrogs.content.multiblock;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Per-{@link ServerLevel} registry of formed Terraria, keyed by Controller
 * {@link BlockPos}. The phase-3 froglight override and the phase-2 milk
 * distribution both consult this: {@link #containing} answers "is this position
 * inside a formed Terrarium's cavity?" in O(active terraria) (a tiny set).
 *
 * <p><b>Server-thread only.</b> All access is from block-entity ticks, block
 * break, and level-lifecycle events, which run on the server thread, so a plain
 * {@link HashMap} is sufficient - no synchronization. The registry is transient:
 * it is NOT persisted (no SavedData). On level load it rebuilds lazily as each
 * Controller re-validates on its next throttled tick (a one-tick window where a
 * frog could eat before re-registration is acceptable - that drop falls to the
 * normal world path).
 *
 * @see TerrariumValidator
 * @see TerrariumValidationResult
 */
public final class TerrariumManager {

    /** A validated Terrarium. Carries every field phases 2-4 consume. */
    public record FormedTerrarium(BlockPos controllerPos, AABB cavity, BlockPos hatchPos,
            List<BlockPos> sprinklers, List<BlockPos> incubators) {
    }

    private static final Map<ServerLevel, Map<BlockPos, FormedTerrarium>> ACTIVE = new HashMap<>();

    private TerrariumManager() {
        // utility class
    }

    /** Register (or replace) the formed Terrarium anchored at {@code result.controllerPos()}. */
    public static void register(ServerLevel level, TerrariumValidationResult result) {
        if (!result.formed()) {
            throw new IllegalArgumentException("register() with an unformed result");
        }
        BlockPos controller = result.controllerPos().immutable();
        ACTIVE.computeIfAbsent(level, l -> new HashMap<>()).put(controller,
            new FormedTerrarium(controller, result.cavityAabb(), result.hatchPos().immutable(),
                result.sprinklers().stream().map(BlockPos::immutable).toList(),
                result.incubators().stream().map(BlockPos::immutable).toList()));
    }

    /** Drop the Terrarium anchored at {@code controllerPos}. Idempotent. */
    public static void deregister(ServerLevel level, BlockPos controllerPos) {
        Map<BlockPos, FormedTerrarium> byController = ACTIVE.get(level);
        if (byController != null) {
            byController.remove(controllerPos.immutable());
            if (byController.isEmpty()) {
                ACTIVE.remove(level);
            }
        }
    }

    /**
     * The formed Terrarium whose cavity contains {@code pos}, or null. Used by
     * the froglight-output override (phase 3) on a frog's position.
     */
    @Nullable
    public static FormedTerrarium containing(Level level, Vec3 pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        Map<BlockPos, FormedTerrarium> byController = ACTIVE.get(serverLevel);
        if (byController == null) {
            return null;
        }
        for (FormedTerrarium t : byController.values()) {
            if (t.cavity().contains(pos)) {
                return t;
            }
        }
        return null;
    }

    /** The formed Terrarium anchored at {@code controllerPos}, or null. */
    @Nullable
    public static FormedTerrarium byController(ServerLevel level, BlockPos controllerPos) {
        Map<BlockPos, FormedTerrarium> byController = ACTIVE.get(level);
        return byController == null ? null : byController.get(controllerPos.immutable());
    }

    /** The formed Terrarium that owns {@code sprinklerPos} as one of its Sprinklers, or null. */
    @Nullable
    public static FormedTerrarium owningSprinkler(ServerLevel level, BlockPos sprinklerPos) {
        Map<BlockPos, FormedTerrarium> byController = ACTIVE.get(level);
        if (byController == null) {
            return null;
        }
        BlockPos key = sprinklerPos.immutable();
        for (FormedTerrarium t : byController.values()) {
            if (t.sprinklers().contains(key)) {
                return t;
            }
        }
        return null;
    }

    /** The formed Terrarium that owns {@code incubatorPos} as one of its Incubators, or null. */
    @Nullable
    public static FormedTerrarium owningIncubator(ServerLevel level, BlockPos incubatorPos) {
        Map<BlockPos, FormedTerrarium> byController = ACTIVE.get(level);
        if (byController == null) {
            return null;
        }
        BlockPos key = incubatorPos.immutable();
        for (FormedTerrarium t : byController.values()) {
            if (t.incubators().contains(key)) {
                return t;
            }
        }
        return null;
    }

    /** Drop all Terraria in a level (on {@code LevelEvent.Unload}). */
    public static void onLevelUnload(ServerLevel level) {
        ACTIVE.remove(level);
    }

    /** Drop everything (on server stop). */
    public static void clearAll() {
        ACTIVE.clear();
    }
}
