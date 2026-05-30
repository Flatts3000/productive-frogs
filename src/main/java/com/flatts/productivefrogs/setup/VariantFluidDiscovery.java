package com.flatts.productivefrogs.setup;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.util.PFDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Mod-init discovery of the variant ids that should get a per-variant Slime Milk
 * fluid. Runs in the {@code ProductiveFrogs} constructor, before the Fluid /
 * Block / Item {@code DeferredRegister}s fire, because {@code BuiltInRegistries.FLUID}
 * freezes right after mod construction (a fluid can only exist for a variant
 * known at this point - see {@code docs/refactor_data_driven_variants.md}).
 *
 * <p>Two sources, both readable at mod-init:
 * <ul>
 *   <li><b>Built-in</b>: PF's shipped variants, listed in the bundled
 *       {@code /productivefrogs/variants_index.json} (a classpath resource, so we
 *       avoid fragile classpath directory listing). Each name's bundled
 *       {@code slime_variant} JSON is read for {@code neoforge:mod_loaded}
 *       conditions so an absent cross-mod source skips minting a dead fluid.</li>
 *   <li><b>Pack</b>: {@code config/productivefrogs/variants/*.json} (plain NIO),
 *       the one place a pack adds an automatable variant. The same files are
 *       registered as a datapack by {@code PFModBusEvents#onAddPackFinders} so
 *       they also feed the {@code slime_variant} content registry.</li>
 * </ul>
 *
 * <p>Variants the mod cannot see here (e.g. dropped into a live world datapack)
 * get no per-variant fluid and therefore no milk - the deliberate
 * "no milk for unknown variants" rule.
 */
public final class VariantFluidDiscovery {

    private static final String INDEX_RESOURCE = "/productivefrogs/variants_index.json";
    private static final String BUILTIN_VARIANT_DIR =
        "/data/" + ProductiveFrogs.MOD_ID + "/" + ProductiveFrogs.MOD_ID + "/slime_variant/";

    /** Config-relative folder where a pack drops automatable variant JSONs. */
    public static final String PACK_VARIANT_SUBPATH = ProductiveFrogs.MOD_ID + "/variants";

    private VariantFluidDiscovery() {
        // utility class
    }

    /**
     * Resolve every variant id whose Slime Milk should get a per-variant fluid.
     * Order is stable (built-ins first in index order, then pack additions) so the
     * registration order is deterministic across launches. Never throws - a read
     * failure logs and yields an empty/partial set rather than aborting mod load.
     */
    public static Set<ResourceLocation> discover() {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        collectBuiltins(ids);
        // NOTE: the config/productivefrogs/variants folder (collectPackAdditions) is
        // NOT scanned yet. Minting a fluid for a pack-added variant only helps if the
        // same file ALSO feeds the slime_variant content registry, which needs a
        // custom PackResources to expose the flat config files under the datapack
        // path. Until that lands, scanning the folder would create fluids with no
        // content (a broken half-variant), so it stays disabled. Shipped (bundled)
        // variants are unaffected - they get content from PF's bundled datapack.
        // See docs/automated_milk_variants.md ("Adding your own automatable variant").
        PFDebug.log(PFDebug.Area.REGISTRY, () -> "milk variant discovery: " + ids.size() + " ids " + ids);
        return ids;
    }

    private static void collectBuiltins(Set<ResourceLocation> ids) {
        JsonObject index = readJsonResource(INDEX_RESOURCE);
        if (index == null || !index.has("variants") || !index.get("variants").isJsonArray()) {
            ProductiveFrogs.LOGGER.warn("variants_index.json missing or malformed; no built-in milk fluids will register");
            return;
        }
        for (JsonElement el : index.getAsJsonArray("variants")) {
            String name = el.getAsString();
            JsonObject variantJson = readJsonResource(BUILTIN_VARIANT_DIR + name + ".json");
            if (variantJson != null && !conditionsMet(variantJson)) {
                continue;
            }
            ids.add(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name));
        }
    }

    private static void collectPackAdditions(Set<ResourceLocation> ids) {
        Path dir;
        try {
            // FMLPaths may be uninitialized in a bare unit-test JVM; guard the access.
            dir = FMLPaths.CONFIGDIR.get().resolve(PACK_VARIANT_SUBPATH);
        } catch (Exception e) {
            return;
        }
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String fileName = p.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - ".json".length());
                JsonObject json = readJsonFile(p);
                if (json != null && !conditionsMet(json)) {
                    return;
                }
                ids.add(ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name));
            });
        } catch (Exception e) {
            ProductiveFrogs.LOGGER.warn("failed listing pack variant folder {}: {}", dir, e.toString());
        }
    }

    /**
     * Evaluate a variant JSON's {@code neoforge:conditions} for {@code mod_loaded}
     * gates. Returns false only when a required mod is provably absent; any parse
     * trouble or unknown condition type fails open (registers), since a stray dead
     * fluid is cheaper than wrongly dropping a present variant.
     */
    private static boolean conditionsMet(JsonObject json) {
        if (!json.has("neoforge:conditions") || !json.get("neoforge:conditions").isJsonArray()) {
            return true;
        }
        JsonArray conditions = json.getAsJsonArray("neoforge:conditions");
        for (JsonElement el : conditions) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject cond = el.getAsJsonObject();
            String type = cond.has("type") ? cond.get("type").getAsString() : "";
            if ("neoforge:mod_loaded".equals(type) && cond.has("modid")) {
                String modid = cond.get("modid").getAsString();
                if (!isModLoaded(modid)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isModLoaded(String modid) {
        try {
            return ModList.get().isLoaded(modid);
        } catch (Exception e) {
            // ModList not ready - fail open rather than wrongly skip.
            return true;
        }
    }

    private static JsonObject readJsonResource(String resourcePath) {
        try (InputStream in = VariantFluidDiscovery.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (Exception e) {
            ProductiveFrogs.LOGGER.warn("failed reading bundled variant json {}: {}", resourcePath, e.toString());
            return null;
        }
    }

    private static JsonObject readJsonFile(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (Exception e) {
            ProductiveFrogs.LOGGER.warn("failed reading pack variant json {}: {}", path, e.toString());
            return null;
        }
    }
}
