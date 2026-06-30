package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.menu.CastingMoldMenu;
import com.flatts.productivefrogs.content.recipe.MoldCastingRecipe;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the Casting Mold - the molten lane's sink and the mod's
 * third GUI block ({@code docs/froglight_crucible.md}, wave 2 part B).
 *
 * <p><b>Input</b>: molten fluid only, three routes - the TOWER pull (sitting
 * directly on a Crucible, drained through its extract-only handler), pipes via
 * the fill-enabled {@code FluidHandler.BLOCK}, or a bucket poured by hand.
 * Fluid is accepted only when a {@code mold_casting} recipe consumes it - and
 * those recipes take {@code c:molten_<metal>} TAGS, so AllTheOres molten casts
 * here exactly like PF's own (the second half of the ATM interop).
 *
 * <p><b>Solidify</b>: while the single-fluid buffer holds a full cast's worth
 * (90 mB for ingots), progress ticks to {@link #CAST_TIME} and the result
 * lands in the output slot (one slot, stacks - furnace-style). Output full or
 * mismatched = the cast waits; nothing is voided.
 *
 * <p><b>GUI</b>: fluid gauge + progress arrow + output slot
 * ({@link CastingMoldMenu} / {@code CastingMoldScreen}); hoppers extract the
 * output from any face (the down face is the Crucible when towered, so
 * side-extraction is the automation path there).
 */
public class CastingMoldBlockEntity extends BlockEntity implements MenuProvider {

    /** Buffer capacity - one bucket. */
    public static final int TANK_CAPACITY = 1_000;

    /** Ticks per cast once a full cast's worth is buffered. */
    public static final int CAST_TIME = 60;

    /** mB pulled per tick from a Crucible directly below (the tower). */
    public static final int TOWER_PULL_PER_TICK = 45;

    public static final int OUTPUT_SLOT = 0;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_FLUID_AMOUNT = 2;
    public static final int DATA_COUNT = 3;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY);
    private int progress = 0;

    // One-entry recipe cache (see recipeForType). Transient - rebuilt on the
    // first lookup after load; invalidated by a RecipeManager swap (reload).
    @Nullable
    private MoldCastingRecipe cachedRecipe;
    @Nullable
    private RecipeManager cachedRecipeManager;

    private final ItemStackHandler output = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** Extract-only output view for hoppers/pipes. */
    private final IItemHandler outputView = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return output.getStackInSlot(OUTPUT_SLOT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return output.extractItem(OUTPUT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return output.getSlotLimit(OUTPUT_SLOT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    /**
     * Fill-only fluid handler: accepts castable molten; drain is a no-op.
     * Molten committed to the Mold leaves as a cast item, never back out as
     * fluid - the inverse of the Crucible's extract-only handler, and it
     * keeps pipe networks from fighting the cast loop over the buffer.
     */
    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return tank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return tank.getFluidInTank(index);
        }

        @Override
        public int getTankCapacity(int index) {
            return tank.getTankCapacity(index);
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return acceptsFluid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!acceptsFluid(resource)) {
                return 0;
            }
            int filled = tank.fill(resource, action);
            if (action.execute() && filled > 0) {
                setChanged();
                syncToClients();
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> CAST_TIME;
                case DATA_FLUID_AMOUNT -> tank.getFluidAmount();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_PROGRESS) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CastingMoldBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.CASTING_MOLD.get(), pos, state);
    }

    public IFluidHandler fluidHandler() {
        return fluidHandler;
    }

    /**
     * The 26.1 {@code Capabilities.Fluid.BLOCK} view: fill-only (molten in, never
     * back out), recipe-gated by {@link #acceptsFluid}. Wraps {@link #tank} with the
     * snapshot transaction discipline; commit fires setChanged + sync.
     */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidResource() {
        return new com.flatts.productivefrogs.content.transfer.FluidTankResourceHandler(
            tank, this::acceptsFluid, true, false, () -> {
                setChanged();
                syncToClients();
            });
    }

    public IItemHandler outputView() {
        return outputView;
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} view: extract-only over the cast-item output slot. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> outputResource() {
        return new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(output, new int[] {OUTPUT_SLOT}, false, true);
    }

    public ItemStackHandler output() {
        return output;
    }

    /** Buffer contents, for the screen gauge / Jade / GameTests. Do not mutate. */
    public FluidStack fluid() {
        return tank.getFluid();
    }

    public int progress() {
        return progress;
    }

    /**
     * A fluid is accepted iff some {@code mold_casting} recipe consumes its
     * TYPE (single-fluid buffer: must also match current contents). Amount
     * sufficiency is the solidify loop's concern, not the fill gate's, so a
     * partial 45 mB pull is still accepted toward a 90 mB cast.
     */
    public boolean acceptsFluid(FluidStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }
        if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), stack)) {
            return false;
        }
        return recipeForType(stack) != null;
    }

    @Nullable
    private MoldCastingRecipe recipeForBuffer() {
        if (level == null || tank.isEmpty()) {
            return null;
        }
        FluidStack buffered = tank.getFluid();
        MoldCastingRecipe recipe = recipeForType(buffered);
        // Type matched via the cache; amount sufficiency checked separately
        // (the buffer fills 45 mB at a time toward a 90 mB cast).
        return recipe != null && recipe.fluid().amount() <= buffered.getAmount() ? recipe : null;
    }

    /**
     * Type-only recipe lookup with a one-entry cache. serverTick calls this
     * every tick (and pipes hammer it through {@code isFluidValid}), so the
     * full-registry scan only reruns when the queried fluid stops matching
     * the cached recipe or a datapack reload swaps the RecipeManager.
     */
    @Nullable
    private MoldCastingRecipe recipeForType(FluidStack stack) {
        RecipeManager manager = level.recipeAccess() instanceof RecipeManager rm ? rm : null;
        if (manager == null) {
            return null;
        }
        if (manager == cachedRecipeManager && cachedRecipe != null
                && cachedRecipe.fluid().ingredient().test(stack)) {
            return cachedRecipe;
        }
        cachedRecipeManager = manager;
        cachedRecipe = manager.recipeMap().byType(PFRecipeTypes.MOLD_CASTING.get()).stream()
            .map(RecipeHolder::value)
            .filter(recipe -> recipe.fluid().ingredient().test(stack))
            .findFirst()
            .orElse(null);
        return cachedRecipe;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingMoldBlockEntity be) {
        // Tower pull: sitting directly on a Crucible drains its tank straight
        // into the buffer - no pipes (the spec's three-block tower).
        if (level.getBlockEntity(pos.below()) instanceof CrucibleBlockEntity crucible) {
            int space = be.tank.getCapacity() - be.tank.getFluidAmount();
            if (space > 0) {
                FluidStack available = crucible.fluid();
                if (!available.isEmpty() && be.acceptsFluid(available)) {
                    int want = Math.min(space, TOWER_PULL_PER_TICK);
                    boolean wasEmpty = be.tank.isEmpty();
                    // copyWithAmount keeps the source's data components on the
                    // drain request - a bare new FluidStack(fluid, mB) would
                    // bounce off a component-carrying molten.
                    FluidStack pulled = crucible.fluidHandler().drain(
                        available.copyWithAmount(want), IFluidHandler.FluidAction.EXECUTE);
                    if (!pulled.isEmpty()) {
                        be.tank.fill(pulled, IFluidHandler.FluidAction.EXECUTE);
                        be.setChanged();
                        // The pull runs every tick; a client packet per tick is
                        // waste (the GUI amount rides ContainerData anyway).
                        // Sync the world/Jade view on the type appearing, on the
                        // source running dry, and on a 10-tick heartbeat.
                        if (wasEmpty || pulled.getAmount() < want || level.getGameTime() % 10 == 0) {
                            be.syncToClients();
                        }
                    }
                }
            }
        }
        // Solidify: full cast buffered -> progress -> result to the output slot.
        MoldCastingRecipe recipe = be.recipeForBuffer();
        if (recipe == null) {
            be.resetProgress();
            return;
        }
        ItemStack result = recipe.result();
        ItemStack simulated = be.output.insertItem(OUTPUT_SLOT, result.copy(), true);
        if (!simulated.isEmpty()) {
            // Output full or holds a different item - wait, void nothing.
            return;
        }
        be.progress++;
        be.setChanged();
        if (be.progress >= CAST_TIME) {
            be.tank.drain(recipe.fluid().amount(), IFluidHandler.FluidAction.EXECUTE);
            be.output.insertItem(OUTPUT_SLOT, result.copy(), false);
            be.progress = 0;
            level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.3F, 1.4F);
            PFDebug.log(PFDebug.Area.REGISTRY, () -> String.format(
                "casting mold @%s cast %s", pos, result.getItem()));
            be.syncToClients();
        }
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // -------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        tank.serialize(output.child("Tank"));
        this.output.serialize(output.child("Output"));
        output.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Tank").ifPresent(tank::deserialize);
        input.child("Output").ifPresent(output::deserialize);
        progress = Math.max(0, Math.min(input.getIntOr("Progress", 0), CAST_TIME));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // -------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.casting_mold");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new CastingMoldMenu(containerId, playerInv, this, dataAccess);
    }
}
