package com.flatts.productivefrogs.content.entity;

/**
 * A Resource Frog's <b>Training Focus</b> - the stat its earned training points
 * pour into as it levels up from eating slimes (docs/frog_stats_redesign.md, R4).
 *
 * <p>{@link #AUTO} (the default for any frog the player never touches) fills the
 * lowest live stat with headroom first - balanced growth, zero input. The three
 * stat focuses pour points into that one stat until it reaches its talent ceiling,
 * then overflow to the next stat with headroom so a point is never wasted.
 *
 * <p>The player cycles a frog's Focus by sneak-right-clicking it with an empty
 * hand ({@code ResourceFrog#mobInteract}); the order below is the cycle order.
 * Persisted by {@link #ordinal()} on the entity (synced for the Jade readout);
 * {@link #byOrdinal(int)} clamps a tampered/out-of-range value back to {@code AUTO}.
 */
public enum FrogFocus {
    APPETITE,
    BOUNTY,
    REACH,
    AUTO;

    private static final FrogFocus[] VALUES = values();

    /** The next Focus in the cycle order ({@code APPETITE -> BOUNTY -> REACH -> AUTO -> APPETITE}). */
    public FrogFocus next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    /** The Focus for an ordinal, falling back to {@link #AUTO} for any out-of-range value. */
    public static FrogFocus byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : AUTO;
    }

    /** Lowercase id used as the lang-key suffix ({@code productivefrogs.frog.focus.<id>}). */
    public String id() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
