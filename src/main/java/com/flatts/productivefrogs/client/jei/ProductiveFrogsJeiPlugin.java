package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;

/**
 * JEI plugin for Productive Frogs.
 *
 * <p>Teaches JEI how to differentiate items that share a single registry id but
 * vary by data component, so each variant/category shows up as a distinct line
 * in the JEI sidebar and recipe lookups instead of collapsing into one generic
 * entry. Without this, the Slime Bucket / Configurable Froglight / Frog Egg /
 * Resource Tadpole Bucket all show as single rows and a search for {@code iron}
 * or {@code aquatic} can't find anything past the spawn eggs.
 *
 * <p>Subtype keys, by item:
 *
 * <ul>
 *   <li><b>Slime Bucket</b> + <b>Resource Tadpole Bucket</b> →
 *       {@link DataComponents#BUCKET_ENTITY_DATA}. The captured entity's NBT
 *       (which carries {@code Category} for both buckets and {@code Variant}
 *       for slime buckets only) lives in this component, so hashing on it gives
 *       JEI a stable per-subtype identity.</li>
 *   <li><b>Configurable Froglight</b> →
 *       {@link PFDataComponents#SLIME_VARIANT}. Stamped by
 *       {@code FrogTongueDropHandler} when a frog kills a variant-locked slime;
 *       used by the smelting recipes to pick the right output ingot/gem.</li>
 *   <li><b>Frog Egg bottle</b> →
 *       {@link PFDataComponents#CONTAINED_CATEGORY}. Set by
 *       {@code FrogspawnBottlingHandler} when a glass bottle scoops primed
 *       frogspawn.</li>
 * </ul>
 *
 * <p>Each subtype only shows up in JEI if a stack with that subtype is emitted
 * somewhere JEI can find (creative tab, recipe input/output, recipe catalyst).
 * The creative tab in {@code PFCreativeTabs.PRODUCTIVE_FROGS_TAB.displayItems}
 * is the canonical place — it emits one stamped stack per variant/category for
 * each of these four items. Smelting recipes for variant Configurable Froglight
 * also surface via {@code neoforge:components} ingredient matching.
 *
 * <p>{@link #getPluginUid} returns {@code productivefrogs:jei_plugin}; the
 * {@link JeiPlugin} annotation is JEI's auto-discovery hook (it scans loaded
 * mods for annotated {@link IModPlugin} implementations at startup), so the
 * class doesn't need to be wired up anywhere else.
 *
 * <p>Lives under {@code .client.jei.} because JEI is client-only at runtime —
 * the plugin class never loads on a dedicated server. JEI itself is
 * {@code compileOnly} on our build classpath; if the player runs without JEI
 * installed, this class is dead code and the {@link JeiPlugin} auto-discovery
 * scan simply doesn't fire. (The project convention deliberately avoids a
 * {@code compat/} Java package for cross-mod work — JSON datapacks gated by
 * {@code mod_loaded} cover everything else, but JEI's plugin API has no JSON
 * equivalent, hence this one exception.)
 */
@JeiPlugin
public final class ProductiveFrogsJeiPlugin implements IModPlugin {

    private static final Identifier PLUGIN_UID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Buckets — subtype by BUCKET_ENTITY_DATA. The vanilla component
        // carries the captured entity's NBT, which is what differentiates
        // an iron Slime Bucket from a copper one (Variant tag) and a
        // METALLIC Tadpole Bucket from a GEM one (Category tag).
        registration.registerFromDataComponentTypes(
            PFItems.SLIME_BUCKET.get(),
            DataComponents.BUCKET_ENTITY_DATA
        );
        registration.registerFromDataComponentTypes(
            PFItems.RESOURCE_TADPOLE_BUCKET.get(),
            DataComponents.BUCKET_ENTITY_DATA
        );

        // Configurable Froglight — subtype by SLIME_VARIANT. The smelting
        // recipes already match on this via NeoForge's neoforge:components
        // ingredient, so JEI recipe lookups need the same per-variant
        // identity to surface the right recipes.
        registration.registerFromDataComponentTypes(
            PFItems.CONFIGURABLE_FROGLIGHT.get(),
            PFDataComponents.SLIME_VARIANT.get()
        );

        // Frog Egg bottle — subtype by CONTAINED_CATEGORY. Six primed
        // bottles, one per category; the unprimed (no-component) bottle
        // is the seventh JEI entry.
        registration.registerFromDataComponentTypes(
            PFItems.FROG_EGG.get(),
            PFDataComponents.CONTAINED_CATEGORY.get()
        );
    }
}
