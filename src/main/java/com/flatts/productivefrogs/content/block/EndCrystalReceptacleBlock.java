package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.EndCrystalReceptacleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * End Crystal Receptacle (#249) - one of the four sockets ringing the dragon
 * altar at the canonical exit-portal crystal positions. Holds exactly one End
 * Crystal (right-clicked in, or pumped in by automation); a full set of four
 * fires the altar's summon, which spends them. The {@link #FILLED} blockstate
 * flips the block texture and drives the on-top crystal render.
 *
 * <p>No ticker - the receptacle is passive storage; the summon state machine
 * (a later chunk) reads the four receptacles. Contents drop on break.
 */
public class EndCrystalReceptacleBlock extends Block implements EntityBlock {

    /** True while a crystal sits in the receptacle. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public EndCrystalReceptacleBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EndCrystalReceptacleBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.END_CRYSTAL)
                || !(level.getBlockEntity(pos) instanceof EndCrystalReceptacleBlockEntity be)
                || be.isFilled()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide()) {
            be.tryInsert(stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof EndCrystalReceptacleBlockEntity be) || !be.isFilled()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = be.extract();
            if (!out.isEmpty() && !player.getInventory().add(out)) {
                player.drop(out, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof EndCrystalReceptacleBlockEntity be) {
            ItemStack held = be.contents();
            if (!held.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
