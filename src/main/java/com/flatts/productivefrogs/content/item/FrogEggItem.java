package com.flatts.productivefrogs.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * The Frog Egg item — a glass bottle filled with frogspawn.
 *
 * <p>Right-clicking this item on a water source places a vanilla
 * {@code minecraft:frogspawn} block adjacent to that water (mirroring vanilla
 * frog behavior: frogspawn lives on water) and transforms the held stack back
 * into an empty {@code minecraft:glass_bottle}.
 *
 * <p>The acquisition side (bottling vanilla frogspawn into a Frog Egg) is
 * handled by {@link com.flatts.productivefrogs.event.FrogspawnBottlingHandler}.
 */
public final class FrogEggItem extends Item {

    public FrogEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();

        // Vanilla frogspawn sits on top of water source blocks. Two valid
        // targets when the player clicks on a water block:
        //   1. They clicked the top of a water source — place at clicked+up.
        //   2. They clicked a face other than top — place at clicked+face.
        BlockPos placePos = context.getClickedFace() == Direction.UP
            ? clicked.above()
            : clicked.relative(context.getClickedFace());

        // The block BELOW the placement position must be a full water source
        // (vanilla FrogspawnBlock.mayPlaceOn behavior). And the placement
        // position itself must be empty.
        BlockPos belowPlace = placePos.below();
        BlockState below = level.getBlockState(belowPlace);
        BlockState target = level.getBlockState(placePos);

        boolean waterBelow = below.getFluidState().is(Fluids.WATER)
            && below.getFluidState().isSource();
        boolean targetEmpty = target.isAir() || target.canBeReplaced();

        if (!waterBelow || !targetEmpty) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            level.setBlockAndUpdate(placePos, Blocks.FROGSPAWN.defaultBlockState());
            level.playSound(
                null,
                placePos,
                SoundEvents.FROGSPAWN_HATCH,
                SoundSource.BLOCKS,
                0.6F,
                1.0F
            );

            // Transform the held stack back into an empty glass bottle.
            Player player = context.getPlayer();
            if (player != null) {
                ItemStack held = context.getItemInHand();
                if (!player.getAbilities().instabuild) {
                    held.shrink(1);
                    ItemStack emptyBottle = new ItemStack(Items.GLASS_BOTTLE);
                    if (!player.addItem(emptyBottle)) {
                        player.drop(emptyBottle, false);
                    }
                }
            }
        }

        return InteractionResult.SUCCESS;
    }
}
