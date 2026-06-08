package com.flatts.productivefrogs.util;

import com.flatts.productivefrogs.ProductiveFrogs;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cross-cutting debug observability for Productive Frogs. One helper, called the
 * same way at every layer, off by default (zero cost in production), flipped on
 * per-area when diagnosing. See {@code docs/observability.md} for the full design.
 *
 * <p>Each {@link Area} maps onto one layer of the mod. An area is enabled either
 * at startup via the {@value #SYSTEM_PROPERTY} system property (a comma-separated
 * list of area ids, or {@code all}) or live in-game via {@code /pf debug <area>
 * on|off} (registered by {@code PFCommands}).
 *
 * <p>Output goes through the {@code productivefrogs} logger at INFO with a
 * {@code [PF/<area>]} prefix, so {@code grep "\[PF/render\]" latest.log} isolates
 * one layer. The area gate is the filter; INFO (not DEBUG) keeps gated lines in
 * {@code latest.log} without raising the log4j level.
 *
 * <p><b>Cost when off:</b> the helper short-circuits on a single {@code volatile
 * boolean} read, so no log I/O happens and a {@link Supplier} message is never
 * built. The caller still evaluates eager args and the {@link #logOnce} dedup key
 * before the gate, so hot paths (per-frame render, per-tick AI, item color
 * handlers) wrap the call in {@link #on(Area)} to avoid even that allocation;
 * rarely-firing event-driven call sites can call directly.
 */
public final class PFDebug {

    /** System property read once at mod construction: a CSV of area ids or {@code all}. */
    public static final String SYSTEM_PROPERTY = "productivefrogs.debug";

    /** Reserved control keyword (system property + command): matches every area. */
    public static final String ALL = "all";

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductiveFrogs.MOD_ID);

    /**
     * Dedup keys for {@link #logOnce}. A {@code ConcurrentHashMap}-backed set
     * because render runs on the client thread and gameplay on the server thread
     * (the same JVM in singleplayer). Keys are {@code area.id + ":" + dedupKey};
     * an area toggle clears its own keys so one-shots re-arm.
     */
    private static final Set<String> SEEN = ConcurrentHashMap.newKeySet();

    /**
     * One area per mod layer. The {@code id} is the token used in the system
     * property and the command, and the {@code [PF/<id>]} log prefix.
     */
    public enum Area {
        LIFECYCLE("lifecycle"),
        REGISTRY("registry"),
        CONFIG("config"),
        INFUSION("infusion"),
        SPLIT("split"),
        TONGUE("tongue"),
        EGG("egg"),
        SENSOR("sensor"),
        MILKER("milker"),
        MILK_SOURCE("milk_source"),
        CHURN("churn"),
        RENDER("render"),
        TINT("tint"),
        SPAWNERY("spawnery");

        private final String id;
        // Written by the command (server thread) + startup flags; read by render
        // (client thread). volatile is sufficient for cross-thread visibility.
        private volatile boolean enabled;

        Area(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }
    }

    private PFDebug() {
        // static utility
    }

    /** The gate. A volatile read; cheap enough for per-frame hot paths. */
    public static boolean on(Area area) {
        return area.enabled;
    }

    /** Eager message. Use only when the args are already in hand (no formatting cost). */
    public static void log(Area area, String msg) {
        if (area.enabled) {
            LOGGER.info("[PF/{}] {}", area.id, msg);
        }
    }

    /** SLF4J-formatted message ({@code {}} placeholders). Args are only formatted when the area is open. */
    public static void log(Area area, String fmt, Object... args) {
        if (area.enabled) {
            LOGGER.info("[PF/" + area.id + "] " + fmt, args);
        }
    }

    /** Lazy message: the supplier runs only when the area is open. Prefer this on hot paths. */
    public static void log(Area area, Supplier<String> msg) {
        if (area.enabled) {
            LOGGER.info("[PF/{}] {}", area.id, msg.get());
        }
    }

    /**
     * Logs once per {@code (area, key)}. A steady render scene logs once per
     * entity instead of every frame. Fold the changing value into the key
     * (e.g. {@code entityId + "/" + variantId}) so a change re-emits. The
     * supplier runs only when the area is open and the key is new.
     */
    public static void logOnce(Area area, Object key, Supplier<String> msg) {
        if (area.enabled && SEEN.add(area.id + ":" + key)) {
            LOGGER.info("[PF/{}] {}", area.id, msg.get());
        }
    }

    /** Enable/disable one area. Enabling clears the area's dedup keys so one-shots re-arm. */
    public static void setEnabled(Area area, boolean enabled) {
        area.enabled = enabled;
        // Only re-arm one-shots on enable; disabling needs no dedup scan.
        if (enabled) {
            resetDedup(area);
        }
    }

    /** Enable/disable every area at once (the {@code all} keyword). */
    public static void setAll(boolean enabled) {
        for (Area area : Area.values()) {
            area.enabled = enabled;
        }
        SEEN.clear();
    }

    /** Drop the dedup keys for one area so its {@link #logOnce} one-shots fire again. */
    public static void resetDedup(Area area) {
        SEEN.removeIf(key -> key.startsWith(area.id + ":"));
    }

    /** Resolve an area by its id (case-insensitive). Empty for unknown ids and for {@code all}. */
    public static Optional<Area> byId(String id) {
        if (id == null) {
            return Optional.empty();
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (Area area : Area.values()) {
            if (area.id.equals(normalized)) {
                return Optional.of(area);
            }
        }
        return Optional.empty();
    }

    /**
     * Parse and apply a CSV flag spec (the system-property form, e.g.
     * {@code "render,tint"} or {@code "all"}). Enables matched areas, ignores
     * blanks, and returns the list of unknown tokens (never throws). The caller
     * decides how to surface unknowns.
     */
    public static List<String> applyFlagSpec(String spec) {
        List<String> unknown = new ArrayList<>();
        if (spec == null || spec.isBlank()) {
            return unknown;
        }
        for (String raw : spec.split(",")) {
            String token = raw.trim().toLowerCase(Locale.ROOT);
            if (token.isEmpty()) {
                continue;
            }
            if (token.equals(ALL)) {
                setAll(true);
                continue;
            }
            Optional<Area> area = byId(token);
            if (area.isPresent()) {
                setEnabled(area.get(), true);
            } else {
                unknown.add(token);
            }
        }
        return unknown;
    }

    /**
     * Read {@value #SYSTEM_PROPERTY} and enable the listed areas. Call once from
     * the mod constructor, before any handler or renderer runs. Logs one warning
     * if the property names unknown areas; never throws.
     */
    public static void bootstrapFromSystemProperty() {
        String spec = System.getProperty(SYSTEM_PROPERTY);
        List<String> unknown = applyFlagSpec(spec);
        if (!unknown.isEmpty()) {
            LOGGER.warn("[PF/debug] ignoring unknown debug area(s) in -D{}: {}", SYSTEM_PROPERTY, unknown);
        }
        if (spec != null && !spec.isBlank()) {
            LOGGER.info("[PF/debug] enabled areas: {}", enabledIds());
        }
    }

    /** The ids of the currently-enabled areas (for the command listing + startup log). */
    public static List<String> enabledIds() {
        List<String> ids = new ArrayList<>();
        for (Area area : Area.values()) {
            if (area.enabled) {
                ids.add(area.id);
            }
        }
        return ids;
    }
}
