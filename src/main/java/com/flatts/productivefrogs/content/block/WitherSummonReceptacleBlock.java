package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.WitherSummonReceptacleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Wither Altar (#247) summon receptacle - one socket of the vanilla Wither summon
 * ritual, rendered as a receptacle. Holds exactly one of its {@linkplain #accepted()
 * accepted item} (Soul Sand or a Wither Skeleton Skull, set per registered instance);
 * a full T of 4 soul sand + 3 skulls fires the altar's summon, which spends them.
 *
 * <p>Parameterized rather than one class per item: both receptacle types share this
 * block (and {@link WitherSummonReceptacleBlockEntity}), differing only in the item
 * they accept. The {@link #FILLED} blockstate flips the texture / on-top render.
 * No ticker - passive storage the summon state machine reads. Contents drop on break.
 */
public class WitherSummonReceptacleBlock extends Block implements EntityBlock {

    /** True while the receptacle holds its item. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    private final Item accepted;

    public WitherSummonReceptacleBlock(Properties properties, Item accepted) {
        super(properties);
        this.accepted = accepted;
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, Boolean.FALSE));
    }

    /** The single item this receptacle accepts (Soul Sand or a Wither Skeleton Skull). */
    public Item accepted() {
        return accepted;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FILLED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WitherSummonReceptacleBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(accepted)
                || !(level.getBlockEntity(pos) instanceof WitherSummonReceptacleBlockEntity be)
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
        if (!(level.getBlockEntity(pos) instanceof WitherSummonReceptacleBlockEntity be) || !be.isFilled()) {
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
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof WitherSummonReceptacleBlockEntity be) {
            ItemStack held = be.contents();
            if (!held.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
