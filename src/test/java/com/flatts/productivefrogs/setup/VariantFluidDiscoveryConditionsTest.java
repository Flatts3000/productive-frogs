package com.flatts.productivefrogs.setup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VariantFluidDiscovery#conditionsMet} - the mod-init gate
 * that decides whether a variant gets a per-variant Slime Milk fluid. It must
 * match NeoForge's datapack-registry condition evaluation (array AND-ed; supports
 * {@code mod_loaded} / {@code or} / {@code and} / {@code not}) so the fluid is
 * minted iff the variant actually loads. The {@code or} handling is the reason
 * this exists: the shared silicon variant gates on {@code or(ae2, refinedstorage)},
 * and a flat parser would fail open and mint a dead fluid on a vanilla-only pack.
 *
 * <p>{@code productivefrogs} is loaded in the JUnit mod context; the ABSENT ids are
 * synthetic ids no mod will ever register, so the test stays correct regardless of
 * which dev/test mods are on the classpath (e.g. Refined Storage, now a dev-runtime
 * dependency, is loaded - so a real mod id like {@code refinedstorage} can NOT be
 * used as an "absent" sentinel).
 */
class VariantFluidDiscoveryConditionsTest {

    private static final String PRESENT = "productivefrogs";
    private static final String ABSENT_A = "pf_absent_mod_alpha";
    private static final String ABSENT_B = "pf_absent_mod_beta";

    /** Wrap one or more condition objects in a variant JSON's conditions array. */
    private static boolean met(String... conditionJson) {
        JsonObject root = new JsonObject();
        root.add("neoforge:conditions",
            JsonParser.parseString("[" + String.join(",", conditionJson) + "]").getAsJsonArray());
        return VariantFluidDiscovery.conditionsMet(root);
    }

    private static String modLoaded(String modid) {
        return "{\"type\":\"neoforge:mod_loaded\",\"modid\":\"" + modid + "\"}";
    }

    private static String or(String... inner) {
        return "{\"type\":\"neoforge:or\",\"values\":[" + String.join(",", inner) + "]}";
    }

    private static String and(String... inner) {
        return "{\"type\":\"neoforge:and\",\"values\":[" + String.join(",", inner) + "]}";
    }

    private static String not(String inner) {
        return "{\"type\":\"neoforge:not\",\"value\":" + inner + "}";
    }

    @Test
    void noConditionsLoads() {
        assertTrue(VariantFluidDiscovery.conditionsMet(new JsonObject()));
        assertTrue(met()); // empty conditions array
    }

    @Test
    void modLoadedGate() {
        assertTrue(met(modLoaded(PRESENT)));
        assertFalse(met(modLoaded(ABSENT_A)));
    }

    @Test
    void arrayIsAnded() {
        // Two entries in the array must both hold.
        assertTrue(met(modLoaded(PRESENT), modLoaded(PRESENT)));
        assertFalse(met(modLoaded(PRESENT), modLoaded(ABSENT_A)));
    }

    @Test
    void orGate() {
        // The silicon case: load when EITHER provider is present.
        assertTrue(met(or(modLoaded(ABSENT_A), modLoaded(PRESENT))));
        assertFalse(met(or(modLoaded(ABSENT_A), modLoaded(ABSENT_B))));
    }

    @Test
    void andGate() {
        assertTrue(met(and(modLoaded(PRESENT), modLoaded(PRESENT))));
        assertFalse(met(and(modLoaded(PRESENT), modLoaded(ABSENT_A))));
    }

    @Test
    void notGate() {
        assertTrue(met(not(modLoaded(ABSENT_A))));
        assertFalse(met(not(modLoaded(PRESENT))));
    }

    @Test
    void nestedComposition() {
        // or( absent, and(present, present) ) -> true via the AND branch.
        assertTrue(met(or(modLoaded(ABSENT_A), and(modLoaded(PRESENT), modLoaded(PRESENT)))));
        // and( present, not(absent) ) -> true.
        assertTrue(met(and(modLoaded(PRESENT), not(modLoaded(ABSENT_B)))));
        // and( present, not(present) ) -> false.
        assertFalse(met(and(modLoaded(PRESENT), not(modLoaded(PRESENT)))));
    }

    @Test
    void emptyOrIsFalseEmptyAndIsTrue() {
        // Matches NeoForge OrCondition/AndCondition test semantics: an empty OR has
        // no satisfied alternative (false); an empty AND is vacuously true.
        assertFalse(met(or()));
        assertTrue(met(and()));
    }

    @Test
    void unknownConditionFailsOpen() {
        assertTrue(met("{\"type\":\"neoforge:some_future_condition\"}"));
    }
}
