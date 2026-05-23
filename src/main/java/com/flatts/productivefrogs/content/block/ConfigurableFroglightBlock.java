package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Placeable form of the variant Froglight item. Mirrors vanilla Froglight
 * mechanics (light level 15, RotatedPillarBlock axis state) and stores its
 * variant identifier in a {@link ConfigurableFroglightBlockEntity} so the
 * in-world tint can resolve per-variant via
 * {@code PFClientEvents#onRegisterBlockColors}.
 *
 * <p>Why a BlockEntity instead of a blockstate property: the variant is a
 * datapack registry ({@link com.flatts.productivefrogs.registry.PFRegistries#SLIME_VARIANT}),
 * so the value space isn't compile-time fixed. BlockEntity NBT supports any
 * registry-loaded identifier without requiring a code change per new variant.
 *
 * <p>Why not one block per variant: same reason — modpack/datapack-added
 * variants would need their own block registrations otherwise. The one-block /
 * one-BE architecture lets the system extend purely by adding JSONs to the
 * SlimeVariant datapack registry.
 *
 * <p>Variant transfer happens in two places: placement via
 * {@link com.flatts.productivefrogs.content.item.ConfigurableFroglightItem#updateCustomBlockEntityTag}
 * (writes the item's {@code SLIME_VARIANT} component into the BE), and drop via
 * the loot table at {@code data/productivefrogs/loot_tables/blocks/configurable_froglight.json}
 * (copies BE NBT into the dropped item's component).
 */
public class ConfigurableFroglightBlock extends RotatedPillarBlock implements EntityBlock {

    public ConfigurableFroglightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConfigurableFroglightBlockEntity(pos, state);
    }
}
