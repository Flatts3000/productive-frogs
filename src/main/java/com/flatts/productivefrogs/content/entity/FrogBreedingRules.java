package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.data.FrogKind;

/**
 * The single home for kind-pairing POLICY - the pure rules live on
 * {@link FrogKind} ({@code canMateWith}/{@code offspringWith}); this class
 * applies the config gating ({@code predators.enabled},
 * {@code breeding.sameSpeciesOnly}) exactly once, consumed by BOTH
 * {@code ResourceFrog#canMate} and the conception capture - so the two can
 * never drift (review finding #8).
 *
 * <p>Written as nested exhaustive switches over the sealed {@link FrogKind}
 * hierarchy: adding a kind (Apex, Phase 4) fails compilation here, restoring
 * the design promise the previous instanceof-chain broke.
 */
public final class FrogBreedingRules {

    private FrogBreedingRules() {
        // policy holder, not instantiable
    }

    /**
     * Whether two kinds may mate under the current config.
     * <ul>
     *   <li>Midas: its own line, config-independent (Midas x Midas only).</li>
     *   <li>Predator: any pairing involving one requires {@code predators.enabled};
     *       when on, breed-true (own kind) only.</li>
     *   <li>Resource x resource: same species always; the designated predator
     *       crosses when {@code predators.enabled}; anything when
     *       {@code sameSpeciesOnly} is disabled (the pre-2.0 vanilla fallback).</li>
     * </ul>
     */
    public static boolean canMate(FrogKind mine, FrogKind theirs) {
        return switch (mine) {
            case FrogKind.Midas m -> m.canMateWith(theirs);
            case FrogKind.Predator p -> PFConfig.predatorsEnabled() && p.canMateWith(theirs);
            // Apex (Phase 4): breed-true only, and only while predation is on -
            // the whole tier rides the predators.enabled master like predators.
            case FrogKind.Apex a -> PFConfig.predatorsEnabled() && a.canMateWith(theirs);
            case FrogKind.Resource r -> switch (theirs) {
                case FrogKind.Midas m2 -> false;
                case FrogKind.Predator p2 -> false; // never back down the ladder
                case FrogKind.Apex a2 -> false; // never back down the ladder
                case FrogKind.Resource r2 -> {
                    if (!PFConfig.sameSpeciesOnly()) {
                        yield true; // vanilla fallback: any resource pair
                    }
                    if (r.equals(r2)) {
                        yield true;
                    }
                    // The designated resource crosses that conceive a predator (#281).
                    yield PFConfig.predatorsEnabled() && r.canMateWith(r2);
                }
            };
        };
    }

    /**
     * The kind a conception produces, never null: the pure
     * {@link FrogKind#offspringWith} result, except that a predator offspring is
     * suppressed while {@code predators.enabled} is off (a config flip mid-love),
     * and any pairing with no defined offspring (e.g. a {@code sameSpeciesOnly=false}
     * odd pair) breeds true to the pregnant parent - the pre-2.0 behavior.
     *
     * <p>Note the designated crosses conceive their predator even under
     * {@code sameSpeciesOnly=false} - intended (#281): the crosses ARE the
     * predator acquisition path, and {@code predators.enabled} is the opt-out.
     */
    public static FrogKind offspring(FrogKind pregnant, FrogKind mate) {
        FrogKind offspring = pregnant.offspringWith(mate);
        // A predator OR apex offspring is suppressed while predators.enabled is
        // off (a config flip mid-love) - the conception breeds true instead.
        if (offspring == null
                || ((offspring instanceof FrogKind.Predator || offspring instanceof FrogKind.Apex)
                    && !PFConfig.predatorsEnabled())) {
            return pregnant;
        }
        return offspring;
    }
}
