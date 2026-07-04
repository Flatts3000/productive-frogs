package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.SummonReceptacleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
 * block (and {@link SummonReceptacleBlockEntity}), differing only in the item
 * they accept. The {@link #FILLED} blockstate flips the texture / on-top render.
 * No ticker - passive storage the summon state machine reads. Contents drop on break.
 */
public class SummonReceptacleBlock extends Block implements EntityBlock {

    /** True while the receptacle holds its item. */
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    /** Where the held item renders: flat against the ritual-facing side, or sitting on top. */
    public enum DisplayMode {
        /** Against the arena-facing face (the wither T: soul sand / skulls read at a glance). */
        FACE,
        /** Standing on the receptacle's top surface (block-shaped fuel: the sculk shrieker). */
        TOP
    }

    private final Item accepted;
    private final DisplayMode displayMode;

    public SummonReceptacleBlock(Properties properties, Item accepted) {
        this(properties, accepted, DisplayMode.FACE);
    }

    public SummonReceptacleBlock(Properties properties, Item accepted, DisplayMode displayMode) {
        super(properties);
        this.accepted = accepted;
        this.displayMode = displayMode;
        this.registerDefaultState(this.stateDefinition.any().setValue(FILLED, Boolean.FALSE));
    }

    /** How the renderer presents the held item. */
    public DisplayMode displayMode() {
        return displayMode;
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
        return new SummonReceptacleBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(accepted)
                || !(level.getBlockEntity(pos) instanceof SummonReceptacleBlockEntity be)
                || be.isFilled()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            be.tryInsert(stack);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof SummonReceptacleBlockEntity be) || !be.isFilled()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = be.extract();
            if (!out.isEmpty() && !player.getInventory().add(out)) {
                player.drop(out, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    // 26.1: the held-item drop lives in SummonReceptacleBlockEntity#preRemoveSideEffects
    // (which fires while the BE still exists, unlike affectNeighborsAfterRemoval where it is
    // already gone). This block has no BE-independent removal side effect, so no override here.
}
