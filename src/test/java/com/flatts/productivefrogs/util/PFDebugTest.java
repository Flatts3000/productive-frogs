package com.flatts.productivefrogs.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.util.PFDebug.Area;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for the {@link PFDebug} gate. No Minecraft runtime needed: the
 * area flags, flag parsing, and the {@code logOnce} dedup are plain Java. The
 * zero-cost promise is pinned by asserting the message supplier is never invoked
 * while an area is off.
 *
 * <p>{@code Area.enabled} is static (shared JVM state), so each test resets all
 * areas off in {@link #reset()} for isolation.
 */
class PFDebugTest {

    @BeforeEach
    @AfterEach
    void reset() {
        PFDebug.setAll(false);
    }

    @Test
    void allAreasOffByDefault() {
        for (Area area : Area.values()) {
            assertFalse(PFDebug.on(area), area.id() + " should default off");
        }
        assertTrue(PFDebug.enabledIds().isEmpty());
    }

    @Test
    void applyFlagSpecEnablesExactlyTheListedAreas() {
        List<String> unknown = PFDebug.applyFlagSpec("render,tint");

        assertTrue(unknown.isEmpty(), "no unknown tokens expected");
        assertTrue(PFDebug.on(Area.RENDER));
        assertTrue(PFDebug.on(Area.TINT));
        for (Area area : Area.values()) {
            if (area != Area.RENDER && area != Area.TINT) {
                assertFalse(PFDebug.on(area), area.id() + " should stay off");
            }
        }
    }

    @Test
    void applyFlagSpecAllEnablesEveryArea() {
        PFDebug.applyFlagSpec("all");
        for (Area area : Area.values()) {
            assertTrue(PFDebug.on(area), area.id() + " should be on after 'all'");
        }
    }

    @Test
    void applyFlagSpecIgnoresUnknownTokensWithoutThrowing() {
        List<String> unknown = PFDebug.applyFlagSpec(" render , bogus , , nope ");

        assertEquals(List.of("bogus", "nope"), unknown);
        assertTrue(PFDebug.on(Area.RENDER), "the valid token still applies");
    }

    @Test
    void applyFlagSpecHandlesNullAndBlank() {
        assertTrue(PFDebug.applyFlagSpec(null).isEmpty());
        assertTrue(PFDebug.applyFlagSpec("   ").isEmpty());
        for (Area area : Area.values()) {
            assertFalse(PFDebug.on(area));
        }
    }

    @Test
    void byIdResolvesCaseInsensitivelyAndRejectsAllKeyword() {
        assertEquals(Area.RENDER, PFDebug.byId("render").orElseThrow());
        assertEquals(Area.RENDER, PFDebug.byId("RENDER").orElseThrow());
        assertEquals(Area.MILK_SOURCE, PFDebug.byId("milk_source").orElseThrow());
        assertTrue(PFDebug.byId("all").isEmpty(), "'all' is a keyword, not an area");
        assertTrue(PFDebug.byId("nope").isEmpty());
        assertTrue(PFDebug.byId(null).isEmpty());
    }

    @Test
    void supplierNotInvokedWhenAreaOff() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> msg = () -> {
            calls.incrementAndGet();
            return "should not build";
        };

        // RENDER is off (reset). Neither the lazy log nor logOnce may touch the supplier.
        PFDebug.log(Area.RENDER, msg);
        PFDebug.logOnce(Area.RENDER, "key", msg);

        assertEquals(0, calls.get(), "supplier must not run while the area is off");
        assertFalse(PFDebug.on(Area.RENDER));
    }

    @Test
    void logOnceDedupsByKeyAndReArmsOnToggle() {
        AtomicInteger calls = new AtomicInteger();
        Supplier<String> msg = () -> {
            calls.incrementAndGet();
            return "msg";
        };

        PFDebug.setEnabled(Area.RENDER, true);

        PFDebug.logOnce(Area.RENDER, "k1", msg);
        PFDebug.logOnce(Area.RENDER, "k1", msg); // same key: deduped
        assertEquals(1, calls.get(), "same key logs once");

        PFDebug.logOnce(Area.RENDER, "k2", msg); // new key: fires
        assertEquals(2, calls.get(), "a new key fires again");

        // Re-enabling clears the area's dedup keys, so k1 fires once more.
        PFDebug.setEnabled(Area.RENDER, true);
        PFDebug.logOnce(Area.RENDER, "k1", msg);
        assertEquals(3, calls.get(), "toggle re-arms one-shots");
    }

    @Test
    void setAllTogglesEveryAreaAndEnabledIdsReflectsState() {
        PFDebug.setAll(true);
        assertEquals(Area.values().length, PFDebug.enabledIds().size());

        PFDebug.setEnabled(Area.RENDER, false);
        assertFalse(PFDebug.enabledIds().contains("render"));
        assertTrue(PFDebug.enabledIds().contains("tint"));
    }
}
