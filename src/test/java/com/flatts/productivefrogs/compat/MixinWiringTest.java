package com.flatts.productivefrogs.compat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Guards the plumbing that makes the mixin load at all.
 *
 * <p>Every other test in this package exercises the algorithm. None of them
 * notice if the mixin never runs, and the ways it can silently never run are
 * all edits that look harmless: renaming the mixin class, moving it out of the
 * declared package, renaming the config plugin, or dropping the
 * {@code [[mixins]]} line from {@code neoforge.mods.toml}. Each of those
 * leaves a green build and an unpatched furnace.
 *
 * <p>The in-world half of this - whether the mixin actually reaches Iron
 * Furnaces' class in a running game - is
 * {@code PFGameTests.ironFurnacesAutoSplitPreservesFroglightVariants}, which
 * needs the mod present and so cannot live here.
 */
class MixinWiringTest {

    private static final String CONFIG = "productivefrogs.mixins.json";
    private static final String MIXIN_PACKAGE = "com.flatts.productivefrogs.mixin";

    private static JsonObject config() throws IOException {
        try (InputStream in = MixinWiringTest.class.getClassLoader().getResourceAsStream(CONFIG)) {
            assertNotNull(in, CONFIG + " is missing from the mod's resources");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static String modsToml() throws IOException {
        try (InputStream in = MixinWiringTest.class.getClassLoader()
                .getResourceAsStream("META-INF/neoforge.mods.toml")) {
            assertNotNull(in, "neoforge.mods.toml is missing");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void modsTomlRegistersTheMixinConfig() throws IOException {
        String toml = modsToml();
        assertTrue(toml.contains("[[mixins]]"), "neoforge.mods.toml declares no [[mixins]] block");
        assertTrue(toml.contains("config = \"" + CONFIG + "\""),
            "neoforge.mods.toml does not point at " + CONFIG + ", so no mixin is ever loaded");
    }

    @Test
    void everyDeclaredMixinClassExists() throws IOException {
        JsonObject config = config();
        assertEquals(MIXIN_PACKAGE, config.get("package").getAsString(),
            "the declared mixin package moved; every entry below resolves against it");

        JsonArray mixins = config.getAsJsonArray("mixins");
        assertFalse(mixins.isEmpty(), "the mixin config lists nothing, so it patches nothing");

        // Checked as a compiled resource, not via Class.forName: Mixin marks
        // mixin classes invalid for ordinary classloading, so loading one
        // throws NoClassDefFoundError even when it is present and correct.
        for (var entry : mixins) {
            String path = MIXIN_PACKAGE.replace('.', '/') + "/" + entry.getAsString() + ".class";
            assertNotNull(MixinWiringTest.class.getClassLoader().getResource(path),
                "mixin config lists " + entry.getAsString()
                    + ", which is not on the classpath - it was renamed or moved out of "
                    + MIXIN_PACKAGE);
        }
    }

    @Test
    void theIronFurnacesMixinIsStillListed() throws IOException {
        JsonArray mixins = config().getAsJsonArray("mixins");
        boolean listed = false;
        for (var entry : mixins) {
            listed |= "BlockIronFurnaceTileBaseMixin".equals(entry.getAsString());
        }
        assertTrue(listed, "the Iron Furnaces patch is no longer wired up. If that is deliberate, "
            + "delete docs/ironfurnaces_autosplit_fix.md, the config key and these tests too");
    }

    @Test
    void theConfigPluginIsPresentAndUsable() throws IOException {
        String plugin = config().get("plugin").getAsString();
        assertEquals(PFMixinPlugin.class.getName(), plugin,
            "the mixin config names a different plugin class than the one in the source tree");

        Class<?> loaded = assertDoesNotThrow(() -> Class.forName(plugin));
        assertTrue(org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin.class.isAssignableFrom(loaded),
            "the plugin must implement IMixinConfigPlugin or Mixin refuses the whole config");
    }

    /**
     * The plugin is what keeps the game alive for players without Iron
     * Furnaces. If it ever returned true unconditionally, Mixin would fail to
     * resolve the target class and crash every one of them.
     */
    @Test
    void theConfigPluginGatesTheIronFurnacesMixin() {
        PFMixinPlugin plugin = new PFMixinPlugin();
        assertFalse(
            plugin.shouldApplyMixin("ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase",
                MIXIN_PACKAGE + ".BlockIronFurnaceTileBaseMixin"),
            "with Iron Furnaces absent - as it is in unit tests - the mixin must not be applied");
    }

    /** Compatibility level has to match the toolchain, or Mixin rejects the config outright. */
    @Test
    void compatibilityLevelMatchesTheToolchain() throws IOException {
        assertEquals("JAVA_21", config().get("compatibilityLevel").getAsString());
    }
}
