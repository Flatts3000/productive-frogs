public class PFConfig {
    // ... existing code ...

    public static final ConfigBoolean frogStatsEnabled = new ConfigBoolean(
        "frogStats.enabled",
        true,
        "Enable frog stat breeding layer (Appetite / Bounty / Reach bred via Sweetslime)"
    );

    // ... existing code ...
}