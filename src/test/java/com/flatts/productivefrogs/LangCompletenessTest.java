package com.flatts.productivefrogs;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flatts.productivefrogs.data.Category;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Lang-completeness gate. Every {@code SlimeVariant} JSON we ship must carry an
 * explicit {@code en_us.json} entry in each of the five per-variant key families,
 * and every {@link Category} must carry its species-level keys, so shipped
 * content reads with a curated name instead of falling back to the runtime
 * title-case fallback. That fallback exists only for downstream datapack-only
 * variants we don't ship (a pack-added variant cannot bundle client lang) - see
 * {@code com.flatts.productivefrogs.util.VariantNames}.
 *
 * <p>This enforces what {@code scripts/audit_lang_keys.py} reports. It reads the
 * processed resources off the test classpath, so it checks the assets that
 * actually ship in the jar (not a stale source copy). Regression-pins the
 * cross-mod "raw lang key in the Froglight tooltip" bug: a new variant JSON
 * without its five lang keys now fails the build.
 */
class LangCompletenessTest {

    /** Key prefixes the item/entity getName paths build per variant (each + {@code <variant_path>}). */
    private static final List<String> PER_VARIANT_FAMILIES = List.of(
        "entity.productivefrogs.resource_slime.",          // ResourceSlime entity name + JEI info
        "item.productivefrogs.slime_bucket.",              // SlimeBucketItem
        "item.productivefrogs.resource_slime_spawn_egg.",  // ResourceSlimeSpawnEggItem
        "block.productivefrogs.configurable_froglight.",   // ConfigurableFroglightItem
        "item.productivefrogs.slime_milk_bucket."          // SlimeMilkBucketItem
    );

    private static final Path RESOURCES_ROOT = resourcesRoot();
    private static final Path VARIANT_DIR =
        RESOURCES_ROOT.resolve("data/productivefrogs/productivefrogs/slime_variant");
    private static final JsonObject LANG = loadLang();
    private static final Set<String> LANG_KEYS = LANG.keySet();

    /**
     * Locate the resources root (e.g. {@code build/resources/main}) by navigating
     * up from the lang file's classpath URL: {@code <root>/assets/productivefrogs/lang/en_us.json}
     * is four parents below {@code <root>}. Working-directory-independent.
     */
    private static Path resourcesRoot() {
        try {
            Path lang = Paths.get(LangCompletenessTest.class
                .getResource("/assets/productivefrogs/lang/en_us.json").toURI());
            return lang.getParent().getParent().getParent().getParent();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate en_us.json on the test classpath", e);
        }
    }

    private static JsonObject loadLang() {
        Path lang = RESOURCES_ROOT.resolve("assets/productivefrogs/lang/en_us.json");
        try {
            return JsonParser.parseString(Files.readString(lang)).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + lang, e);
        }
    }

    /** Every shipped variant id (the {@code slime_variant/<id>.json} file stems). */
    static Stream<String> shippedVariantIds() {
        try (Stream<Path> files = Files.list(VARIANT_DIR)) {
            return files
                .map(p -> p.getFileName().toString())
                .filter(n -> n.endsWith(".json"))
                .map(n -> n.substring(0, n.length() - ".json".length()))
                .sorted()
                .toList()  // materialize before the directory stream closes
                .stream();
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + VARIANT_DIR, e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shippedVariantIds")
    void everyShippedVariantHasAllPerVariantLangKeys(String variantId) {
        List<String> missing = PER_VARIANT_FAMILIES.stream()
            .map(family -> family + variantId)
            .filter(key -> !LANG_KEYS.contains(key))
            .toList();
        assertTrue(missing.isEmpty(),
            () -> "Variant '" + variantId + "' is missing lang keys:\n  "
                + String.join("\n  ", missing)
                + "\nFill them (run: python scripts/audit_lang_keys.py --write) so the shipped "
                + "variant reads with a curated name rather than the title-case fallback.");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Category.class)
    void everyCategoryHasSpeciesLevelLangKeys(Category category) {
        String id = category.id();
        List<String> required = List.of(
            "entity.productivefrogs.resource_slime." + id,
            "entity.productivefrogs.resource_frog." + id,
            "entity.productivefrogs.resource_tadpole." + id,
            "entity.productivefrogs." + id + "_slime",                // parent species entity
            "item.productivefrogs.frog_egg." + id,                    // primed Frog Egg bottle name
            "item.productivefrogs.slime_bucket." + id,
            "item.productivefrogs.resource_tadpole_bucket." + id,
            "item.productivefrogs." + id + "_frog_spawn_egg",
            "item.productivefrogs." + id + "_tadpole_spawn_egg",
            "item.productivefrogs." + id + "_slime_spawn_egg",        // parent species spawn egg
            "block.productivefrogs." + category.primedEggItemName(),  // Primed Frog Egg block
            "productivefrogs.jei.parent_species." + id + ".info"
        );
        List<String> missing = required.stream().filter(k -> !LANG_KEYS.contains(k)).toList();
        assertTrue(missing.isEmpty(),
            () -> "Category '" + id + "' is missing lang keys:\n  " + String.join("\n  ", missing));
    }

    @Test
    void allReferencedJeiInfoKeysExist() {
        List<String> keys = new ArrayList<>(List.of(
            "productivefrogs.jei.variant_slime.info",
            "productivefrogs.jei.variant_froglight.info",
            "productivefrogs.jei.slime_milk.info",
            "productivefrogs.jei.frog.info",
            "productivefrogs.jei.tadpole.info",
            "productivefrogs.jei.frog_egg.info",
            "productivefrogs.jei.primed_egg.info",
            "productivefrogs.jei.empty_frog_egg.info",
            "productivefrogs.jei.slime_milker.info",
            "productivefrogs.jei.empty_slime_bucket.info",
            "productivefrogs.jei.empty_tadpole_bucket.info"
        ));
        for (Category category : Category.values()) {
            keys.add("productivefrogs.jei.parent_species." + category.id() + ".info");
        }
        List<String> missing = keys.stream().filter(k -> !LANG_KEYS.contains(k)).toList();
        assertTrue(missing.isEmpty(),
            () -> "JEI plugin references info keys with no en_us.json entry:\n  "
                + String.join("\n  ", missing));
    }

    /**
     * Copy-lint: the Froglight block's display name is "Froglight" everywhere
     * ({@code block.productivefrogs.configurable_froglight} and every per-variant
     * name). "Configurable Froglight" is the registry-flavored id and must not
     * leak into player-facing copy (it did in two JEI info strings).
     */
    @Test
    void noPlayerFacingValueSaysConfigurableFroglight() {
        List<String> offenders = LANG.entrySet().stream()
            .filter(e -> e.getValue().getAsString().contains("Configurable Froglight"))
            .map(Map.Entry::getKey)
            .toList();
        assertTrue(offenders.isEmpty(),
            () -> "Player-facing lang values must say \"Froglight\", not \"Configurable Froglight\":\n  "
                + String.join("\n  ", offenders));
    }
}
