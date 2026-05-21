package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Single Froglight item with per-variant identity carried in a data component.
 * The direct analog of Productive Bees' {@code configurable_honeycomb}: one
 * registered Item, N distinct visual + naming surfaces driven by JSON data
 * (see {@code docs/productive_bees_analysis.md} §2).
 *
 * <p>Distinct from our six hand-registered {@code <category>_froglight} blocks
 * — those are the broad-category placeable blocks (Metallic Froglight, etc).
 * The configurable item drops from Resource Frogs when they eat a slime that
 * carries a {@link com.flatts.productivefrogs.data.SlimeVariant}; downstream
 * smelting recipes (future PR) consume the variant to produce the specific
 * base resource. Players who want decoration place the broad category block;
 * the configurable item is the production-loop currency.
 */
public final class ConfigurableFroglightItem extends Item {

    public ConfigurableFroglightItem(Properties properties) {
        super(properties);
    }

    /**
     * Display name: when the variant resolves in the registry, builds
     * {@code item.productivefrogs.configurable_froglight.<variant_path>}
     * — e.g. "Iron Froglight". Falls back to the base item name otherwise.
     */
    @Override
    public Component getName(ItemStack stack) {
        Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null && variantLoaded(variantId)) {
            return Component.translatable(
                getDescriptionId() + "." + variantId.getPath()
            );
        }
        return Component.translatable(getDescriptionId());
    }

    /**
     * Cheap client-side check: does the variant id resolve in the loaded
     * registry? If not, we don't want to surface a raw translation key as
     * the display name. Same defensive pattern as
     * {@code ResourceSlime#getName} — datapack/mod may have been removed.
     */
    private static boolean variantLoaded(Identifier id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        Registry<?> registry = mc.level.registryAccess().lookup(PFRegistries.SLIME_VARIANT).orElse(null);
        return registry != null && registry.containsKey(id);
    }
}
