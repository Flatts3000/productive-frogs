package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
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
 * the loot table at {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}
 * (copies BE NBT into the dropped item's component).
 *
 * <p><b>Brewed aura (#162):</b> a Froglight may also carry a {@code STORED_EFFECT}
 * component. When placed, the effect rides into the BE (same two transfer paths
 * as the variant), and the BE's {@link ConfigurableFroglightBlockEntity#serverTick}
 * applies it in a radius. Empty-hand <b>right-click toggles</b> the aura on/off
 * with distinct sounds; while on, the block emits effect-colored swirl particles.
 */
public class ConfigurableFroglightBlock extends RotatedPillarBlock implements EntityBlock {

    public ConfigurableFroglightBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConfigurableFroglightBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Server: the aura application loop. Client: the aura particle stream.
        // Both early-out on a single null/flag check for a plain or toggled-off
        // Froglight, so the per-tick cost on decorative Froglights is one branch.
        return createTickerHelper(type, PFBlockEntities.CONFIGURABLE_FROGLIGHT.get(),
            level.isClientSide()
                ? ConfigurableFroglightBlockEntity::clientTick
                : ConfigurableFroglightBlockEntity::serverTick);
    }

    /**
     * Empty-hand right-click toggles the aura (#162). Left-click was rejected -
     * it collides with block-breaking. No-op on a plain Froglight (no effect to
     * toggle). Distinct activate/deactivate sounds, quiet + pitched up so it
     * reads as a charm rather than a beacon.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ConfigurableFroglightBlockEntity froglight)
                || froglight.getEffect() == null || !PFConfig.brewedFroglightsEnabled()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            boolean nowOn = froglight.toggleAura();
            level.playSound(null, pos,
                nowOn ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE,
                SoundSource.BLOCKS, 0.4F, 1.5F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Pick-block (middle-click) - the third variant-transfer surface, easy to
     * miss next to placement and loot: stamp the BE's variant (and any brewed
     * effect) onto the cloned stack, or creative pick-block hands back a generic
     * (untinted, effectless) Froglight.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (level.getBlockEntity(pos) instanceof ConfigurableFroglightBlockEntity froglight) {
            if (froglight.getVariantId() != null) {
                stack.set(PFDataComponents.SLIME_VARIANT.get(), froglight.getVariantId());
            }
            if (froglight.getEffect() != null) {
                stack.set(PFDataComponents.STORED_EFFECT.get(), froglight.getEffect());
            }
        }
        return stack;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType,
            BlockEntityType<E> clientType,
            BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }
}
