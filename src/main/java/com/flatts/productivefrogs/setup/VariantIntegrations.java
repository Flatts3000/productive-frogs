package com.flatts.productivefrogs.setup;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.util.PFDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

/**
 * Maps each built-in cross-mod Slime Variant to the host mod(s) that provide it,
 * derived from the variant JSON's {@code neoforge:mod_loaded} conditions (#204).
 * This is the data behind {@code variants.disabledIntegrations}: a pack lists a
 * provider modid (e.g. {@code mekanism}) and every variant gated solely behind
 * that mod is force-disabled, exactly like {@code disabledVariants} (unprimable,
 * undiscoverable, hidden from JEI + the creative tab).
 *
 * <p><b>Why derive from the condition, not a new field:</b> the provider mod is
 * already encoded in every cross-mod variant's {@code mod_loaded} gate, and
 * {@link VariantFluidDiscovery} already reads these JSONs at mod-init. Reusing
 * that single source of truth avoids a parallel {@code integration} codec field
 * that could drift from the gate, and means the generator scripts stay in sync
 * for free. (The variant's {@code primer_tag} like {@code c:ingots/tin} can't
 * supply this - it names the resource, never the provider mod.)
 *
 * <p><b>Multi-provider variants</b> (e.g. {@code silicon}, gated {@code ae2 OR
 * refinedstorage}) map to the full provider set and are disabled <i>only when
 * every</i> provider is listed - disabling one provider leaves the variant
 * reachable via the other. Modids under a {@code neoforge:not} are ignored (a
 * {@code not} gate means "when this mod is absent", so it is not a provider).
 *
 * <p>The map is built lazily from the bundled JSONs on first use and cached. Only
 * built-in variants are covered; a variant added purely through a live world
 * datapack has no entry and is never integration-disabled (consistent with the
 * milk contract, which likewise only knows mod-init-time variants).
 */
public final class VariantIntegrations {

    private static volatile Map<Identifier, Set<String>> providers;

    private VariantIntegrations() {
        // utility class
    }

    /**
     * True if {@code id} is a known cross-mod variant whose every provider mod is
     * in {@code disabledKeys}. A first-party variant (no provider gate) and an
     * unknown id both return false. {@code disabledKeys} is the config list.
     */
    public static boolean allProvidersDisabled(Identifier id, Collection<? extends String> disabledKeys) {
        if (disabledKeys.isEmpty()) {
            return false;
        }
        Set<String> mods = providers().get(id);
        return mods != null && !mods.isEmpty() && disabledKeys.containsAll(mods);
    }

    /** Provider modids for a variant id (empty if first-party or unknown). Exposed for tests. */
    public static Set<String> providersOf(Identifier id) {
        return providers().getOrDefault(id, Set.of());
    }

    private static Map<Identifier, Set<String>> providers() {
        Map<Identifier, Set<String>> p = providers;
        if (p == null) {
            synchronized (VariantIntegrations.class) {
                p = providers;
                if (p == null) {
                    p = build();
                    providers = p;
                }
            }
        }
        return p;
    }

    private static Map<Identifier, Set<String>> build() {
        Map<Identifier, Set<String>> map = new HashMap<>();
        for (String name : VariantFluidDiscovery.bundledVariantNames()) {
            JsonObject json = VariantFluidDiscovery.readBundledVariant(name);
            if (json == null) {
                continue;
            }
            Set<String> mods = new HashSet<>();
            collectModLoaded(json.get("neoforge:conditions"), mods);
            if (!mods.isEmpty()) {
                // Store an immutable copy so the public providersOf() can't leak a
                // mutable view of the cached map's values.
                map.put(Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, name), Set.copyOf(mods));
            }
        }
        PFDebug.log(PFDebug.Area.REGISTRY, () -> "variant integrations: " + map.size() + " cross-mod variants mapped");
        return Map.copyOf(map);
    }

    /**
     * Walk a conditions element gathering every {@code neoforge:mod_loaded} modid,
     * descending into {@code or}/{@code and} value lists but skipping {@code not}
     * subtrees (a negated mod is not a provider). Mirrors the condition shapes
     * {@code VariantFluidDiscovery.evalCondition} handles.
     */
    private static void collectModLoaded(JsonElement el, Set<String> out) {
        if (el == null) {
            return;
        }
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                collectModLoaded(child, out);
            }
            return;
        }
        if (!el.isJsonObject()) {
            return;
        }
        JsonObject cond = el.getAsJsonObject();
        String type = cond.has("type") ? cond.get("type").getAsString() : "";
        switch (type) {
            case "neoforge:mod_loaded" -> {
                if (cond.has("modid")) {
                    out.add(cond.get("modid").getAsString());
                }
            }
            case "neoforge:or", "neoforge:and" -> {
                if (cond.has("values") && cond.get("values").isJsonArray()) {
                    JsonArray values = cond.getAsJsonArray("values");
                    for (JsonElement child : values) {
                        collectModLoaded(child, out);
                    }
                }
            }
            default -> {
                // neoforge:not (skip - a negated mod is not a provider) and any
                // unhandled type contribute no providers.
            }
        }
    }
}
