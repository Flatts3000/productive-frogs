package com.flatts.productivefrogs.content.block;

import com.flatts.productivefrogs.content.block.entity.SprinklerBlockEntity;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Sprinkler - a ceiling-cell spawn source for the Terrarium. Phase 1 is an
 * inert shell: it places, breaks, and counts as a sealed ceiling cell for
 * {@link com.flatts.productivefrogs.content.multiblock.TerrariumValidator}
 * (validation requires it in a top-layer face cell; it implicitly faces down).
 *
 * <p>{@link #FILLED} ships now (unused) so phase 2 - which runs the real placed
 * Slime Milk spawn loop and adds milk-drip particles while filled - doesn't have
 * to reshape the blockstate JSON. {@link #FACING} is carried for consistency with
 * the other machines (the validator does not facing-check Sprinklers).
 */
public class SprinklerBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public SprinklerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
            .setValue(FACING, Direction.DOWN)
            .setValue(FILLED, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILLED);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
            .setValue(FACING, context.getNearestLookingDirection().getOpposite())
            .setValue(FILLED, Boolean.FALSE);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /**
     * Client ambient particles: a filled Sprinkler drips milk down into the
     * cavity, tinted to the held variant's colour. Gated strictly on {@link #FILLED}
     * AND a non-empty BE, so an empty/drained Sprinkler is silent. The variant id
     * is synced to the client BE (see {@code SprinklerBlockEntity.sync()}), so the
     * drip recolours the moment milk loads or drains.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(FILLED)) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof SprinklerBlockEntity be) || be.isEmpty()) {
            return;
        }
        ResourceLocation variant = be.getVariantId();
        int rgb = variant == null ? DEFAULT_MILK_RGB : variantColor(level, variant);
        double x = pos.getX() + 0.25 + random.nextDouble() * 0.5;
        double y = pos.getY() - 0.05; // just under the down face
        double z = pos.getZ() + 0.25 + random.nextDouble() * 0.5;
        level.addParticle(Category.dustParticle(rgb), x, y, z, 0.0, -0.06, 0.0);
    }

    /** Milky off-white fallback before the variant registry is available. */
    private static final int DEFAULT_MILK_RGB = 0xF0F0E0;

    private static int variantColor(Level level, ResourceLocation variant) {
        Registry<SlimeVariant> registry = level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) {
            return DEFAULT_MILK_RGB;
        }
        SlimeVariant v = registry.get(variant);
        return v == null ? DEFAULT_MILK_RGB : v.primaryColor();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SprinklerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, PFBlockEntities.SPRINKLER.get(), SprinklerBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        // Empty bucket -> drain the held milk back to its per-variant bucket.
        if (stack.is(Items.BUCKET) && level.getBlockEntity(pos) instanceof SprinklerBlockEntity be && !be.isEmpty()) {
            if (!level.isClientSide()) {
                ItemStack milk = be.drainToBucket(level, pos, state);
                if (!milk.isEmpty()) {
                    stack.shrink(1);
                    if (!player.getInventory().add(milk)) {
                        player.drop(milk, false);
                    }
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.BUCKET_FILL,
                        net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
