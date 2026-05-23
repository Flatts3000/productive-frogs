package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
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
 * backed GUI rather than the legacy hand-swap. Hopper I/O is not exposed in
 * this PR — the BlockEntity ships without a {@code Capabilities.Item.BLOCK}
 * provider, so vanilla hoppers can't push into INPUT_SLOT or pull from
 * OUTPUT_SLOT yet. The follow-up that wires up the new
 * {@code ResourceHandler<ItemResource>} API is tracked in
 * {@code docs/known_issues.md}.
 *
 * <p>Variant resolution still goes through {@link #readBucketVariant} →
 * {@code PFFluidTypes.VARIANTS} → {@code PFItems.MILK_BUCKETS}: the cook
 * loop in {@link SlimeMilkerBlockEntity#serverTick} performs the lookup
 * each tick and fail-closes when the input bucket has no Variant tag or
 * an unknown variant.
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
    protected InteractionResult useItemOn(
        ItemStack stack,
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        BlockHitResult hit
    ) {
        // Open the GUI regardless of held item — players can drop a Slime
        // Bucket directly into the input slot through the inventory. The
        // legacy "hand-swap on right-click" behavior was superseded by
        // the furnace-style redesign tracked in known_issues.md.
        return openMilkerMenu(state, level, pos, player);
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
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // Drop inventory contents when the block is broken. 1.21.11 replaced
        // the onRemove hook with affectNeighborsAfterRemoval; the BE is
        // already gone at this point so we can't read its inventory here.
        // Drop-on-break is handled via Block#playerDestroy → loot table
        // instead — see the slime_milker loot table for the simple "drop
        // self" rule. The Milker's inventory is dropped via
        // {@link #playerWillDestroy} before the BE is removed.
        net.minecraft.world.Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SlimeMilkerBlockEntity milker) {
                com.flatts.productivefrogs.content.block.entity.SlimeMilkerInventory inv = milker.getInventory();
                for (int i = 0; i < inv.size(); i++) {
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
        CustomData data = stack.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) {
            return null;
        }
        String raw = data.copyTag().getString("Variant").orElse(null);
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? null : id.getPath();
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
