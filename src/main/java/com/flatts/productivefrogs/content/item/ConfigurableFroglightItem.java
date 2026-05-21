package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.registry.PFDataComponents;
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
     * Display name resolution: builds
     * {@code item.productivefrogs.configurable_froglight.<variant_path>} from
     * the {@code SLIME_VARIANT} data component when present, else falls back
     * to the base item name.
     *
     * <p>This runs on both client and server (server uses it for things like
     * advancement display, F3+B), so it can't touch client-only classes like
     * {@code Minecraft}. We deliberately don't validate the variant against
     * the registry here — when a lang entry is missing (third-party datapack
     * adds variants without lang strings), vanilla's translation fallback
     * surfaces the raw key, which is the right "fix your datapack" signal.
     */
    @Override
    public Component getName(ItemStack stack) {
        Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            return Component.translatable(
                getDescriptionId() + "." + variantId.getPath()
            );
        }
        return Component.translatable(getDescriptionId());
    }
}
