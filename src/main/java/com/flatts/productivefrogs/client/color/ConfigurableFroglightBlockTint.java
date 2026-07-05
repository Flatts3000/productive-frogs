package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.client.SynthesizedTint;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Variant-keyed Configurable Froglight block tint. Reads the variant identifier
 * from the block entity, looks up the matching {@link SlimeVariant} in the
 * datapack registry, and returns its {@code primary_color}. A placed Prismatic
 * Froglight (#253) instead tints from its carried item's sprite-average colour
 * (runtime resolver). Bound at tint index 0 in the block model.
 */
public final class ConfigurableFroglightBlockTint implements BlockTintSource {

    @Override
    public int color(BlockState state) {
        // No world context (e.g. terrain-particle colour query) - untinted.
        return -1;
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (level == null || pos == null) {
            return -1;
        }
        var be = level.getBlockEntity(pos);
        if (!(be instanceof ConfigurableFroglightBlockEntity froglightBe)) {
            return -1;
        }
        // Equivalence lane (#253): a placed Prismatic Froglight tints from its
        // carried item's sprite-average colour (runtime resolver).
        Identifier synthBlockItem = froglightBe.getSynthesizedItem();
        if (synthBlockItem != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(synthBlockItem).orElse(null);
            return item == null ? -1 : SynthesizedTint.colorFor(item);
        }
        Identifier variantId = froglightBe.getVariantId();
        if (variantId == null) {
            return -1;
        }
        var beLevel = froglightBe.getLevel();
        if (beLevel == null) {
            return -1;
        }
        var registry = PFRegistries.variants(beLevel.registryAccess());
        if (registry == null) {
            return -1;
        }
        SlimeVariant variant = PFRegistries.variant(registry, variantId);
        final int argb = variant == null ? -1 : Tints.opaque(variant.primaryColor());
        if (PFDebug.on(PFDebug.Area.TINT)) {
            PFDebug.logOnce(PFDebug.Area.TINT, "froglight_block/" + variantId,
                () -> String.format("configurable_froglight(block) variant=%s -> #%08X", variantId, argb));
        }
        return argb;
    }
}
