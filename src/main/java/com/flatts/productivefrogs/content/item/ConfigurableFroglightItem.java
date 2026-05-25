package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Variant-keyed Froglight item. Carries its variant identity in a
 * {@link com.flatts.productivefrogs.registry.PFDataComponents#SLIME_VARIANT}
 * data component; backed by {@link com.flatts.productivefrogs.content.block.ConfigurableFroglightBlock}
 * + its block-entity so the variant survives placement and drops.
 *
 * <p>Direct analog of Productive Bees' {@code configurable_honeycomb} pattern:
 * one registered Item, N visual surfaces driven by JSON data (see
 * {@code docs/productive_bees_analysis.md} §2). V1.5 removed the six
 * fixed-palette decorative species Froglight blocks; the data-driven
 * Configurable Froglight is now the sole Froglight type — every drop from a
 * frog kill is a variant-stamped instance of this item.
 *
 * <p>Variant round-trip:
 * <ol>
 *   <li>Frog tongue kill emits the item with {@code SLIME_VARIANT} set
 *       (see {@code FrogTongueDropHandler}).</li>
 *   <li>Player places it; {@link #updateCustomBlockEntityTag} copies the
 *       component into the BE's {@code Variant} NBT (write-through path
 *       called by vanilla on placement).</li>
 *   <li>Client renders the placed block tinted via
 *       {@link com.flatts.productivefrogs.client.PFClientEvents}'s registered
 *       {@code BlockColor} which reads the BE's variant.</li>
 *   <li>Player breaks the block; the loot table at
 *       {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}
 *       copies the BE's variant back into the dropped item's component.</li>
 * </ol>
 */
public final class ConfigurableFroglightItem extends BlockItem {

    public ConfigurableFroglightItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Hook vanilla calls during block placement to seed the BE with NBT from
     * the item stack. We use it to copy the {@code SLIME_VARIANT} component
     * into the BE's {@code Variant} field. Returns {@code true} when we
     * actually mutated the BE (variant was present on the stack AND the BE at
     * the placement position was our BE type) so vanilla can mark the BE
     * changed; returns {@code false} on the no-op paths (unstamped stack, or
     * BE missing/wrong type — neither should happen in practice but we fail
     * closed).
     */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, @Nullable Player player, ItemStack stack, BlockState state) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId == null) {
            return false;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ConfigurableFroglightBlockEntity froglightBe) {
            froglightBe.setVariantId(variantId);
            return true;
        }
        return false;
    }

    /**
     * Display name resolution. Builds
     * {@code block.productivefrogs.configurable_froglight.<variant_path>}
     * (the {@code block.} prefix comes from {@code useBlockDescriptionPrefix()}
     * on the item properties) from the {@code SLIME_VARIANT} component when
     * present, else falls back to the base block name. Server + client both
     * use this (the server path surfaces it in advancement displays, F3+B
     * debug, etc.) so it can't touch client-only classes.
     */
    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId != null) {
            // Built-in variants have explicit lang keys; a datapack-added variant
            // (no lang) falls back to a title-cased name so it still reads cleanly.
            return Component.translatableWithFallback(
                getDescriptionId() + "." + variantId.getPath(),
                com.flatts.productivefrogs.util.VariantNames.titleCase(variantId) + " Froglight"
            );
        }
        return Component.translatable(getDescriptionId());
    }
}
