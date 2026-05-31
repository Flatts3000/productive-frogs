package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
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

    /**
     * Froglight variants that double as furnace fuel, mapped to their burn time
     * in ticks. Each value matches the vanilla item that species is themed on:
     * {@code coal} -> a coal item (1600t, eight smelts); {@code blaze} -> a blaze
     * rod (2400t, twelve smelts). A Froglight is the resource block of its slime
     * species, so the fuel-resource species burning is the natural read. Every
     * variant absent from this map stays inert decoration. Add an entry to make a
     * new variant fuel.
     */
    private static final Map<ResourceLocation, Integer> FUEL_BURN_TICKS = Map.of(
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "coal"), 1600,
        ResourceLocation.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "blaze"), 2400
    );

    public ConfigurableFroglightItem(Block block, Properties properties) {
        super(block, properties);
    }

    /**
     * Make the fuel-resource Froglights (coal, blaze) burn in a furnace like the
     * vanilla item they are made of. The variant rides a per-stack
     * {@code SLIME_VARIANT} component, and every Froglight shares one registered
     * item, so fuel value has to be decided per-stack here - the
     * {@code furnace_fuels} data map keys on the item and would (wrongly) make
     * every variant fuel. Variants absent from {@link #FUEL_BURN_TICKS} defer to
     * {@code super} (0 = not fuel). Never returns a negative value: NeoForge's
     * {@code ItemStack#getBurnTime} throws on a negative burn time.
     */
    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        ResourceLocation variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
        Integer ticks = variantId == null ? null : FUEL_BURN_TICKS.get(variantId);
        return ticks != null ? ticks : super.getBurnTime(stack, recipeType);
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
