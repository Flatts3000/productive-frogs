package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

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
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();

        BlockPos placePos = context.getClickedFace() == Direction.UP
            ? clicked.above()
            : clicked.relative(context.getClickedFace());

        BlockPos belowPlace = placePos.below();
        BlockState below = level.getBlockState(belowPlace);
        BlockState target = level.getBlockState(placePos);

        boolean waterBelow = below.getFluidState().is(Fluids.WATER) && below.getFluidState().isSource();
        boolean targetEmpty = target.isAir() || target.canBeReplaced();

        if (!waterBelow || !targetEmpty) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            ItemStack held = context.getItemInHand();
            Category contained = held.get(PFDataComponents.CONTAINED_CATEGORY.get());

            Block placeBlock = contained == null
                ? Blocks.FROGSPAWN
                : PFBlocks.primedEgg(contained);

            level.setBlockAndUpdate(placePos, placeBlock.defaultBlockState());
            level.playSound(null, placePos, SoundEvents.FROGSPAWN_HATCH, SoundSource.BLOCKS, 0.6F, 1.0F);

            Player player = context.getPlayer();
            if (player != null) {
                ItemStack result = ItemUtils.createFilledResult(
                    held,
                    player,
                    new ItemStack(Items.GLASS_BOTTLE)
                );
                player.setItemInHand(context.getHand(), result);
            }
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
