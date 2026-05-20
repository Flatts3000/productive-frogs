package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The Frog Egg item — a glass bottle filled with frogspawn. Carries an
 * optional {@link Category} via the {@link PFDataComponents#CONTAINED_CATEGORY}
 * data component:
 *
 * <ul>
 *   <li>Component absent → bottle contains vanilla frogspawn. Placing puts
 *       down {@code minecraft:frogspawn}.</li>
 *   <li>Component present → bottle contains a primed egg of that category.
 *       Placing puts down the matching Primed Frog Egg block.</li>
 * </ul>
 *
 * <p>Placement uses the same {@code ClipContext.Fluid.SOURCE_ONLY} raytrace
 * pattern as vanilla {@code PlaceOnWaterBlockItem} (used by lily pads and
 * {@code Items.FROGSPAWN}): the default item raytrace passes through water
 * and hits whatever solid block is below, so without the fluid-aware
 * raytrace you can never actually click "on" the water surface.
 * {@code useOn} returns PASS unconditionally so we fall through to
 * {@code use}, which runs the fluid raytrace and places the block one above
 * the water source.
 *
 * <p>Either way, placement transforms the held stack back into an empty
 * {@code minecraft:glass_bottle} via {@link ItemUtils#createFilledResult}, so
 * the bottle/egg <-> empty-bottle round-trip matches vanilla water-bottle /
 * fish-bucket semantics exactly.
 */
public final class FrogEggItem extends Item {

    public FrogEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY);

        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }

        BlockPos waterPos = hit.getBlockPos();
        if (!level.mayInteract(player, waterPos)) {
            return InteractionResult.PASS;
        }

        FluidState fluid = level.getFluidState(waterPos);
        if (!fluid.is(Fluids.WATER) || !fluid.isSource()) {
            return InteractionResult.PASS;
        }

        BlockPos placePos = waterPos.above();
        if (!player.mayUseItemAt(placePos, hit.getDirection(), held)) {
            return InteractionResult.PASS;
        }

        BlockState target = level.getBlockState(placePos);
        if (!target.isAir() && !target.canBeReplaced()) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            Category contained = held.get(PFDataComponents.CONTAINED_CATEGORY.get());

            Block placeBlock = contained == null
                ? Blocks.FROGSPAWN
                : PFBlocks.primedEgg(contained);

            if (!level.setBlockAndUpdate(placePos, placeBlock.defaultBlockState())) {
                return InteractionResult.PASS;
            }

            level.playSound(null, placePos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 0.6F, 1.0F);

            ItemStack result = ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE));
            player.setItemInHand(hand, result);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        Category contained = stack.get(PFDataComponents.CONTAINED_CATEGORY.get());
        if (contained == null) {
            return Component.translatable(getDescriptionId());
        }
        return Component.translatable("item.productivefrogs.frog_egg." + contained.id());
    }
}
