package com.flatts.productivefrogs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the unified frog-identity model (#281): the id round-trip every
 * serialization surface leans on, the settled breeding pair map
 * (docs/predator_frogs.md - Bog x Cave -> Prowler, Infernal x Geode -> Cinder,
 * Tide x Bog -> Gulper, Void x Geode -> Rift), and the pure pairing rules
 * (breed-true, designated crosses, everything else refuses).
 */
class FrogKindTest {

    @Test
    void idRoundTripsForEveryKind() {
        for (Category c : Category.values()) {
            FrogKind kind = FrogKind.resource(c);
            assertSame(kind, FrogKind.byId(kind.id()), kind.id());
        }
        assertSame(FrogKind.MIDAS, FrogKind.byId("midas"));
        for (FrogKind.Predator p : FrogKind.Predator.values()) {
            assertSame(p, FrogKind.byId(p.id()), p.id());
        }
        assertNull(FrogKind.byId("resource/not_a_species"));
        assertNull(FrogKind.byId("predator/not_a_predator"));
        assertNull(FrogKind.byId(""));
    }

    @Test
    void settledCrossPairMapIsExact() {
        assertSame(FrogKind.Predator.PROWLER, FrogKind.Predator.fromCross(Category.BOG, Category.CAVE));
        assertSame(FrogKind.Predator.CINDER, FrogKind.Predator.fromCross(Category.INFERNAL, Category.GEODE));
        assertSame(FrogKind.Predator.GULPER, FrogKind.Predator.fromCross(Category.TIDE, Category.BOG));
        assertSame(FrogKind.Predator.RIFT, FrogKind.Predator.fromCross(Category.VOID, Category.GEODE));
    }

    @Test
    void crossIsUnordered() {
        for (FrogKind.Predator p : FrogKind.Predator.values()) {
            assertSame(p, FrogKind.Predator.fromCross(p.anchor(), p.partner()));
            assertSame(p, FrogKind.Predator.fromCross(p.partner(), p.anchor()));
        }
    }

    @Test
    void undesignatedPairsHaveNoCross() {
        // Same-species is never a cross.
        for (Category c : Category.values()) {
            assertNull(FrogKind.Predator.fromCross(c, c));
        }
        // Every undesignated cross pair refuses (the 4 designated pairs are the
        // ONLY ones with a mapping - enumerated in settledCrossPairMapIsExact).
        assertNull(FrogKind.Predator.fromCross(Category.BOG, Category.GEODE));
        assertNull(FrogKind.Predator.fromCross(Category.BOG, Category.INFERNAL));
        assertNull(FrogKind.Predator.fromCross(Category.BOG, Category.VOID));
        assertNull(FrogKind.Predator.fromCross(Category.CAVE, Category.GEODE));
        assertNull(FrogKind.Predator.fromCross(Category.CAVE, Category.TIDE));
        assertNull(FrogKind.Predator.fromCross(Category.CAVE, Category.INFERNAL));
        assertNull(FrogKind.Predator.fromCross(Category.CAVE, Category.VOID));
        assertNull(FrogKind.Predator.fromCross(Category.GEODE, Category.TIDE));
        assertNull(FrogKind.Predator.fromCross(Category.TIDE, Category.INFERNAL));
        assertNull(FrogKind.Predator.fromCross(Category.TIDE, Category.VOID));
        assertNull(FrogKind.Predator.fromCross(Category.INFERNAL, Category.VOID));
    }

    @Test
    void resourceKindsBreedTrueAndCross() {
        FrogKind bog = FrogKind.resource(Category.BOG);
        FrogKind cave = FrogKind.resource(Category.CAVE);
        FrogKind tide = FrogKind.resource(Category.TIDE);

        // Breed true.
        assertTrue(bog.canMateWith(bog));
        assertSame(bog, bog.offspringWith(bog));

        // The designated cross conceives the predator, both directions.
        assertTrue(bog.canMateWith(cave));
        assertTrue(cave.canMateWith(bog));
        assertSame(FrogKind.Predator.PROWLER, bog.offspringWith(cave));
        assertSame(FrogKind.Predator.PROWLER, cave.offspringWith(bog));

        // An undesignated pair refuses and has no offspring.
        assertFalse(cave.canMateWith(tide));
        assertNull(cave.offspringWith(tide));
    }

    @Test
    void predatorsBreedTrueWithOwnKindOnly() {
        for (FrogKind.Predator p : FrogKind.Predator.values()) {
            assertTrue(p.canMateWith(p));
            assertSame(p, p.offspringWith(p));
            for (FrogKind.Predator other : FrogKind.Predator.values()) {
                if (other == p) {
                    continue;
                }
                // Phase 4: the four designated cross-environment pairs conceive
                // their Apex; every other cross-kind predator pair still refuses.
                FrogKind.Apex apex = FrogKind.Apex.fromCross(p, other);
                if (apex != null) {
                    assertTrue(p.canMateWith(other));
                    assertSame(apex, p.offspringWith(other));
                } else {
                    assertFalse(p.canMateWith(other));
                    assertNull(p.offspringWith(other));
                }
            }
            // Never back down the ladder or across mechanisms.
            assertFalse(p.canMateWith(FrogKind.resource(p.fallbackCategory())));
            assertFalse(p.canMateWith(FrogKind.MIDAS));
            assertFalse(FrogKind.resource(p.fallbackCategory()).canMateWith(p));
        }
    }

    @Test
    void apexCrossesAreTheSettledPairsAndBreedTrue() {
        // The four settled pairs (issue #281 Phase 4), unordered.
        assertSame(FrogKind.Apex.WITHER, FrogKind.Apex.fromCross(FrogKind.Predator.CINDER, FrogKind.Predator.PROWLER));
        assertSame(FrogKind.Apex.DRAGON, FrogKind.Apex.fromCross(FrogKind.Predator.RIFT, FrogKind.Predator.CINDER));
        assertSame(FrogKind.Apex.ELDER, FrogKind.Apex.fromCross(FrogKind.Predator.GULPER, FrogKind.Predator.PROWLER));
        assertSame(FrogKind.Apex.WARDEN, FrogKind.Apex.fromCross(FrogKind.Predator.PROWLER, FrogKind.Predator.RIFT));
        assertSame(FrogKind.Apex.WITHER, FrogKind.Apex.fromCross(FrogKind.Predator.PROWLER, FrogKind.Predator.CINDER));
        for (FrogKind.Apex a : FrogKind.Apex.values()) {
            assertTrue(a.canMateWith(a));
            assertSame(a, a.offspringWith(a));
            for (FrogKind.Apex other : FrogKind.Apex.values()) {
                if (other != a) {
                    assertFalse(a.canMateWith(other));
                }
            }
            // The top of the ladder: never back down.
            assertFalse(a.canMateWith(a.anchor()));
            assertFalse(a.canMateWith(FrogKind.resource(a.fallbackCategory())));
            assertFalse(a.canMateWith(FrogKind.MIDAS));
        }
    }

    @Test
    void midasIsItsOwnLine() {
        assertTrue(FrogKind.MIDAS.canMateWith(FrogKind.MIDAS));
        assertSame(FrogKind.MIDAS, FrogKind.MIDAS.offspringWith(FrogKind.MIDAS));
        for (Category c : Category.values()) {
            assertFalse(FrogKind.MIDAS.canMateWith(FrogKind.resource(c)));
            assertFalse(FrogKind.resource(c).canMateWith(FrogKind.MIDAS));
        }
    }

    @Test
    void fallbackCategoriesFollowTheAnchors() {
        assertEquals(Category.BOG, FrogKind.Predator.PROWLER.fallbackCategory());
        assertEquals(Category.INFERNAL, FrogKind.Predator.CINDER.fallbackCategory());
        assertEquals(Category.TIDE, FrogKind.Predator.GULPER.fallbackCategory());
        assertEquals(Category.VOID, FrogKind.Predator.RIFT.fallbackCategory());
        assertEquals(Category.VOID, FrogKind.MIDAS.fallbackCategory());
        assertEquals(Category.GEODE, FrogKind.resource(Category.GEODE).fallbackCategory());
    }

    @Test
    void nameSuffixesMatchTheSettledNames() {
        assertEquals("prowler", FrogKind.Predator.PROWLER.nameSuffix());
        assertEquals("cinder", FrogKind.Predator.CINDER.nameSuffix());
        assertEquals("gulper", FrogKind.Predator.GULPER.nameSuffix());
        assertEquals("rift", FrogKind.Predator.RIFT.nameSuffix());
        assertEquals("midas", FrogKind.MIDAS.nameSuffix());
        assertEquals("bog", FrogKind.resource(Category.BOG).nameSuffix());
    }

    /**
     * The NBT resolution contract (review finding #1): legacy keys WIN over the
     * modern Kind id. 26.1's TypedEntityData.loadInto merges a spawn egg's baked
     * legacy NBT over a full entity save that already carries the default Kind -
     * reading Kind first made every legacy egg hatch the BOG default.
     */
    @org.junit.jupiter.api.Test
    void legacyKeysWinOverModernKindId() {
        java.util.Optional<String> noKind = java.util.Optional.empty();
        java.util.Optional<String> defaultKind = java.util.Optional.of("resource/bog");
        java.util.Optional<String> noCategory = java.util.Optional.empty();

        // The spawn-egg merge shape: stale default Kind + the egg's legacy Category.
        assertSame(FrogKind.resource(Category.CAVE),
            FrogKind.resolve(defaultKind, false, java.util.Optional.of("CAVE")).orElseThrow());
        // The Midas egg merge shape: stale default Kind + Category VOID + Midas.
        assertSame(FrogKind.MIDAS,
            FrogKind.resolve(defaultKind, true, java.util.Optional.of("VOID")).orElseThrow());
        // Modern-only data (a normal world save / a predator egg) resolves by Kind.
        assertSame(FrogKind.Predator.PROWLER,
            FrogKind.resolve(java.util.Optional.of("predator/prowler"), false, noCategory).orElseThrow());
        // An unparseable legacy category falls through to the Kind id.
        assertSame(FrogKind.resource(Category.BOG),
            FrogKind.resolve(defaultKind, false, java.util.Optional.of("NOT_A_SPECIES")).orElseThrow());
        // Nothing readable -> empty.
        assertTrue(FrogKind.resolve(noKind, false, noCategory).isEmpty());
    }

    /**
     * Every permitted subclass of the sealed hierarchy has at least one
     * registered kind (review finding #9): the BY_ID map is the one identity
     * surface the compiler's exhaustiveness cannot cover, so this test is the
     * enforcement - adding Apex to the permits list without registering it in
     * FrogKind.Registry fails here instead of silently deserializing to BOG.
     */
    @org.junit.jupiter.api.Test
    void everyPermittedSubclassIsRegistered() {
        Class<?>[] permitted = FrogKind.class.getPermittedSubclasses();
        assertTrue(permitted.length >= 3, "sealed hierarchy lost its permits list?");
        for (Class<?> sub : permitted) {
            assertTrue(FrogKind.all().stream().anyMatch(sub::isInstance),
                sub.getSimpleName() + " has no registered kinds in FrogKind.Registry - "
                    + "byId would return null and entities would deserialize to the BOG fallback");
        }
        // And the sync index round-trips for every registered kind.
        for (FrogKind kind : FrogKind.all()) {
            assertSame(kind, FrogKind.bySyncIndex(FrogKind.syncIndex(kind)), kind.id());
        }
    }
}
