package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

/**
 * Single component-driven spawn egg for every Resource Slime variant. Replaces
 * the pre-v1.1 per-variant spawn-egg item IDs ({@code iron_slime_spawn_egg},
 * {@code copper_slime_spawn_egg}, ...) with one registered item whose identity
 * is carried in the {@code SLIME_VARIANT} data component - the same
 * one-item-N-surfaces pattern as {@link ConfigurableFroglightItem}.
 *
 * <p>A per-variant stack (built by {@code PFItems.resourceSlimeSpawnEgg}) carries
 * the variant id in two places:
 * <ul>
 *   <li>{@code SLIME_VARIANT} - drives the display name (this class), the
 *       inventory tint ({@code PFClientEvents}), and the JEI subtype.</li>
 *   <li>the vanilla {@code ENTITY_DATA} {@code "Variant"} field - so the
 *       {@link net.minecraft.world.item.SpawnEggItem} spawn path stamps the
 *       variant onto the {@code ResourceSlime} it creates (the slime resolves
 *       its category from the variant registry on spawn).</li>
 * </ul>
 *
 * <p>The creative tab and JEI enumerate variants from the {@code SLIME_VARIANT}
 * datapack registry, so a new variant needs no spawn-egg Java edit - the only
 * remaining per-variant Java touch is the Slime Milk {@code VARIANTS} entry
 * (fluids must register at mod-init). See {@code docs/code_review_2026_05_24.md}
 * CR-9.
 */
public final class ResourceSlimeSpawnEggItem extends SpawnEggItem {

    public ResourceSlimeSpawnEggItem(EntityType<? extends Mob> type, int backgroundColor,
                                     int highlightColor, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            return Component.translatable(getDescriptionId() + "." + variantId.getPath());
        }
        return Component.translatable(getDescriptionId());
    }
}
