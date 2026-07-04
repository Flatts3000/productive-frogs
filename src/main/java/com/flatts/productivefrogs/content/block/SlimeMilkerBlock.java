package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Slime Milker — furnace-style production block per {@code docs/farming.md}
 * and the redesign tracked in {@code docs/known_issues.md}.
 *
 * <p>Two-slot inventory (input Slime Bucket, output Slime Milk bucket) plus
 * a 100-tick cook timer; right-click opens a {@link SlimeMilkerBlockEntity}-
 * backed GUI rather than the legacy hand-swap. Hopper I/O is wired via a
 * side-aware {@code Capabilities.ItemHandler.BLOCK} provider in
 * {@code PFModBusEvents} (top + horizontal faces = input view, bottom = output).
 *
 * <p>Variant resolution: the cook loop in {@link SlimeMilkerBlockEntity#serverTick}
 * reads the input Slime Bucket's full variant id via {@link #readBucketVariantId}
 * and outputs that variant's own Slime Milk bucket (per-variant items, v1.8 - the
 * item identity carries the variant). It fail-closes when the input bucket carries
 * no variant, or when the variant has no per-variant milk fluid.
 *
 * <p>Future polish tracked in {@code docs/backlog.md}: press animation,
 * facing-based block model (top input port + side output spout), tighter
 * audio cue.
 */
public class SlimeMilkerBlock extends Block implements EntityBlock {

    /**
     * Horizontal facing direction of the block. The {@code front} texture
     * (slime-window door) renders on this face; the other three horizontal
     * faces use the {@code side} texture.
     */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    /**
     * True while the BlockEntity's cook timer is making progress (slime being
     * pressed). Drives the swap from idle to working textures via the
     * blockstate JSON — see assets/productivefrogs/blockstates/slime_milker.json.
     * The BlockEntity flips this on/off from {@code serverTick}.
     */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    public SlimeMilkerBlock(Properties properties) {
        super(properties);
        // Default state: facing north, idle. Placement overrides FACING from
        // the player's horizontal direction (so the front faces the player).
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(WORKING, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Front faces the player. ctx.getHorizontalDirection() returns the
        // direction the player is looking; the block's "front" side should
        // face the player, so we invert with getOpposite().
        return this.defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(WORKING, Boolean.FALSE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SlimeMilkerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.SLIME_MILKER.get(), SlimeMilkerBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        // Empty-hand right-click → open GUI. Item-in-hand follows the same
        // path via useItemOn → super; deferring to one entry point keeps
        // the open-the-menu logic centralized.
        return openMilkerMenu(state, level, pos, player);
    }

    @Override
    protected net.minecraft.world.InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        // Open the GUI regardless of held item. 1.21.1 useItemOn returns
        // InteractionResult (not InteractionResult); SUCCESS / PASS_TO_DEFAULT_BLOCK_INTERACTION
        // are the relevant return values.
        InteractionResult openResult = openMilkerMenu(state, level, pos, player);
        return openResult == InteractionResult.SUCCESS
            ? net.minecraft.world.InteractionResult.SUCCESS
            : net.minecraft.world.InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    private InteractionResult openMilkerMenu(BlockState state, Level level, BlockPos pos, Player player) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MenuProvider provider) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        // The lambda passed to openMenu writes the BE's BlockPos into the
        // extra-data buffer. The client-side menu ctor (wired via
        // IMenuTypeExtension.create in PFMenuTypes) then reads that
        // position back out to resolve the same BlockEntity and pick up
        // its inventory + progress counter.
        serverPlayer.openMenu(provider, buf -> buf.writeBlockPos(pos));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // Notify comparators / redstone of removal. Item-dropping is handled
        // by playerWillDestroy above; we don't reach into the BE here.
        level.updateNeighbourForOutputSignal(pos, this);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SlimeMilkerBlockEntity milker) {
                com.flatts.productivefrogs.content.block.entity.SlimeMilkerInventory inv = milker.getInventory();
                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack stack = inv.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        net.minecraft.world.Containers.dropItemStack(
                            level, pos.getX(), pos.getY(), pos.getZ(), stack);
                    }
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Pull the variant path (e.g. {@code "iron"}) out of a Slime Bucket's
     * {@code BUCKET_ENTITY_DATA} NBT. Returns null when the bucket is empty
     * (no entity data), the entity didn't store a Variant tag, or the stored
     * Variant id is malformed.
     *
     * <p>Widened from package-private to public when the BlockEntity moved
     * out of this package — {@link SlimeMilkerBlockEntity#serverTick} needs
     * to walk the same parsing chain the legacy right-click flow did.
     */
    @Nullable
    public static String readBucketVariant(ItemStack stack) {
        Identifier id = readBucketVariantId(stack);
        return id == null ? null : id.getPath();
    }

    /**
     * Full variant id (e.g. {@code productivefrogs:iron}) from a Slime Bucket's
     * {@code BUCKET_ENTITY_DATA}, or null when absent/malformed. Delegates to
     * {@link ResourceTadpoleBucketItem#readVariant} - the single canonical reader
     * of a bucket's {@code Variant} NBT (both bucket types share the layout), so
     * there is one parser rather than three. The milker stamps this onto the
     * output Slime Milk bucket's {@code SLIME_VARIANT} component, preserving the
     * namespace so cross-namespace datapack variants survive the conversion.
     */
    @Nullable
    public static Identifier readBucketVariantId(ItemStack stack) {
        return ResourceTadpoleBucketItem.readVariant(stack);
    }

    /**
     * Vanilla's BaseEntityBlock.createTickerHelper guards against mismatched
     * BlockEntityType subclasses at runtime, but the protected accessor on
     * BaseEntityBlock isn't visible from a Block subclass. Reimplement the
     * type-safe cast here so the ticker hookup type-checks cleanly.
     */
    @SuppressWarnings("unchecked")
    @Nullable
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> createTickerHelper(
            BlockEntityType<A> serverType,
            BlockEntityType<E> clientType,
            BlockEntityTicker<? super E> ticker) {
        return serverType == clientType ? (BlockEntityTicker<A>) ticker : null;
    }
}
