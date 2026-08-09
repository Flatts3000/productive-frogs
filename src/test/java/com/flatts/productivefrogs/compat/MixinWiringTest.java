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

    /**
     * Our {@code neoforge.mods.toml}, specifically.
     *
     * <p>Every mod on the classpath ships a file at that exact path, including
     * NeoForge itself, so {@code getResourceAsStream} returns whichever one
     * happens to come first and the answer is not stable between runs. Scan all
     * of them and take the one that declares this mod.
     */
    private static String modsToml() throws IOException {
        var urls = MixinWiringTest.class.getClassLoader().getResources("META-INF/neoforge.mods.toml");
        while (urls.hasMoreElements()) {
            try (InputStream in = urls.nextElement().openStream()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                if (content.contains("productivefrogs") || content.contains("${mod_id}")) {
                    return content;
                }
            }
        }
        throw new AssertionError("no neoforge.mods.toml on the classpath declares productivefrogs");
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
            + "delete docs/ironfurnaces_component_fixes.md, the config key and these tests too");
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
     * Furnaces, so both directions of the gate are asserted with an injected
     * mod-presence lookup.
     *
     * <p>Asserting through the real {@link net.neoforged.fml.loading.FMLLoader}
     * would be worthless here: it reports nothing in a unit-test JVM, so every
     * assertion passes for the correct implementation, for a hardcoded
     * {@code false}, and for a typo'd mod id alike - and the last two silently
     * disable the patch for every player.
     */
    @Test
    void theConfigPluginGatesOnTheTargetModBeingPresent() {
        String ironFurnaces = MIXIN_PACKAGE + ".BlockIronFurnaceTileBaseMixin";

        assertFalse(PFMixinPlugin.shouldApplyMixin(ironFurnaces, modId -> false),
            "with Iron Furnaces absent the mixin must not be applied, or players without it crash");
        assertTrue(PFMixinPlugin.shouldApplyMixin(ironFurnaces, "ironfurnaces"::equals),
            "with Iron Furnaces present the mixin must be applied, or the patch is dead for everyone");
        assertFalse(PFMixinPlugin.shouldApplyMixin(ironFurnaces, "iron_furnaces"::equals),
            "the gate must key off the real mod id; a typo here disables the patch silently");
    }

    /**
     * Unknown mixins are refused rather than waved through. The plugin exists
     * to fail closed; a future compat mixin added to the json without a gate
     * entry should cost a missing patch, not a crash for everyone lacking its
     * target mod.
     */
    @Test
    void theConfigPluginRefusesUnregisteredMixins() {
        assertFalse(
            PFMixinPlugin.shouldApplyMixin(MIXIN_PACKAGE + ".SomeFutureCompatMixin", modId -> true),
            "an unregistered mixin must not be applied even when every mod is present");
    }

    /** Every mixin in the config must be registered in the gate, or it will never apply. */
    @Test
    void everyDeclaredMixinIsRegisteredInTheGate() throws IOException {
        for (var entry : config().getAsJsonArray("mixins")) {
            String fqcn = MIXIN_PACKAGE + "." + entry.getAsString();
            assertTrue(PFMixinPlugin.shouldApplyMixin(fqcn, modId -> true),
                entry.getAsString() + " is listed in " + CONFIG + " but not in PFMixinPlugin.REQUIRED_MOD, "
                    + "so it is refused at load and silently never applies");
        }
    }

    /** Compatibility level has to match the toolchain, or Mixin rejects the config outright. */
    @Test
    void compatibilityLevelMatchesTheToolchain() throws IOException {
        assertEquals("JAVA_21", config().get("compatibilityLevel").getAsString());
    }

    /**
     * The config must stay non-required, which is what actually delivers the
     * fail-open promise the docs make.
     *
     * <p>{@code injectors.defaultRequire: 0} only governs the {@code @Inject}.
     * The {@code @Shadow} of Iron Furnaces' {@code FACTORY_INPUT} is resolved
     * when the mixin is applied, and on a required config that failure is
     * fatal - so a future Iron Furnaces renaming that field would crash every
     * player who has both mods, rather than quietly dropping the patch.
     */
    @Test
    void theConfigStaysNonRequiredSoFailuresAreNotFatal() throws IOException {
        assertFalse(config().get("required").getAsBoolean(),
            "a required mixin config turns any resolution failure into a crash for every player; "
                + "see docs/ironfurnaces_component_fixes.md, which promises the opposite");
        assertEquals(0, config().getAsJsonObject("injectors").get("defaultRequire").getAsInt());
    }
}
