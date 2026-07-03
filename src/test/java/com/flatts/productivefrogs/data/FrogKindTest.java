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
                if (other != p) {
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
}
