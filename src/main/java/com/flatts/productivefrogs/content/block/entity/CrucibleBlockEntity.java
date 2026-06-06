package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.CrucibleBlock;
import com.flatts.productivefrogs.content.recipe.CrucibleMeltRecipe;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataMaps;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the {@link CrucibleBlock}. Owns the single-fluid
 * 4,000 mB tank, the one-at-a-time melt slot, and the heat-scaled melt loop.
 *
 * <p><b>Melt loop</b> ({@link #serverTick}): while a Froglight is loaded and
 * the block below carries heat ({@code productivefrogs:crucible_heat} data
 * map, Ex Deorum-parity values), {@code meltProgress += heat} per tick;
 * completion at {@link #MELT_TOTAL} (400). So a torch (1) melts in 400 ticks,
 * lava (3) in ~133, fire (5) in 80 - and a heat source swapped mid-melt
 * changes the rate naturally. No heat = no progress (and the LIT state drops).
 *
 * <p><b>Tank semantics</b>: single fluid; a Froglight is only accepted when
 * its melt result matches the current contents (or the tank is empty) AND the
 * full result amount fits. Drain to switch fluids. The pipe-facing capability
 * (wired in {@code PFModBusEvents}) is extract-only - input is items, not
 * fluid.
 *
 * <p>Campfire lit-ness is the one state-sensitive heat check, handled here in
 * code (the data map keys on the block only).
 */
public class CrucibleBlockEntity extends BlockEntity {

    /** Melt completion threshold; per-tick advance equals the heat value. */
    public static final int MELT_TOTAL = 400;

    /** Tank capacity per the spec - four buckets. */
    public static final int TANK_CAPACITY = 4_000;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY);

    /** Extract-only view handed to pipes via FluidHandler.BLOCK. */
    private final IFluidHandler extractOnlyTank = new IFluidHandler() {
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
            // Insertion is item-driven (Froglights); pipes can't push fluid in.
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack drained = tank.drain(resource, action);
            if (action.execute() && !drained.isEmpty()) {
                setChanged();
                syncToClients();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = tank.drain(maxDrain, action);
            if (action.execute() && !drained.isEmpty()) {
                setChanged();
                syncToClients();
            }
            return drained;
        }
    };

    /** The Froglight currently melting; empty when idle. */
    private ItemStack melting = ItemStack.EMPTY;
    private int meltProgress = 0;

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.CRUCIBLE.get(), pos, state);
    }

    /** The pipe-facing extract-only handler (also drains via bucket clicks). */
    public IFluidHandler fluidHandler() {
        return extractOnlyTank;
    }

    /** Tank contents, for Jade/GameTests. Do not mutate. */
    public FluidStack fluid() {
        return tank.getFluid();
    }

    public boolean isMelting() {
        return !melting.isEmpty();
    }

    public int meltProgress() {
        return meltProgress;
    }

    /**
     * Try to load one Froglight from the player's hand as the melt input.
     * Rejected (with a fail-closed log, no item consumed) when a melt is
     * already running, the Froglight has no melt recipe, or the recipe's
     * result doesn't match/fit the tank. Heat is deliberately NOT required to
     * load - a player can stage a Froglight and light the fire after; the
     * melt loop just waits.
     */
    public boolean tryInsertFroglight(ItemStack held, @Nullable Player player) {
        if (level == null || level.isClientSide() || held.isEmpty()) {
            return false;
        }
        if (isMelting()) {
            return false;
        }
        CrucibleMeltRecipe recipe = recipeFor(held);
        if (recipe == null) {
            PFDebug.logOnce(PFDebug.Area.REGISTRY, "crucible-norecipe#" + worldPosition,
                () -> String.format("crucible @%s rejected froglight: no melt recipe", worldPosition));
            return false;
        }
        if (!resultFits(recipe.result())) {
            return false;
        }
        melting = held.copyWithCount(1);
        held.shrink(1);
        meltProgress = 0;
        setChanged();
        syncToClients();
        level.playSound(null, worldPosition, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        if (!be.isMelting()) {
            setLit(level, pos, state, false);
            return;
        }
        int heat = be.heatBelow();
        if (heat <= 0) {
            setLit(level, pos, state, false);
            return;
        }
        CrucibleMeltRecipe recipe = be.recipeFor(be.melting);
        if (recipe == null || !be.resultFits(recipe.result())) {
            // Datapack changed under us (recipe removed) or the tank was filled
            // by an earlier melt of a different fluid that then got topped off -
            // eject the Froglight rather than voiding it.
            Block.popResource(level, pos.above(), be.melting);
            be.melting = ItemStack.EMPTY;
            be.meltProgress = 0;
            be.setChanged();
            be.syncToClients();
            setLit(level, pos, state, false);
            return;
        }
        be.meltProgress += heat;
        if (be.meltProgress >= MELT_TOTAL) {
            be.tank.fill(recipe.result().copy(), IFluidHandler.FluidAction.EXECUTE);
            be.melting = ItemStack.EMPTY;
            be.meltProgress = 0;
            level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.6F, 1.1F);
            PFDebug.log(PFDebug.Area.REGISTRY, () -> String.format(
                "crucible @%s melted froglight -> %s", pos, recipe.result().getFluid()));
            be.syncToClients();
        }
        be.setChanged();
        setLit(level, pos, state, true);
    }

    /**
     * Heat value of the block below: data-map lookup plus the one
     * state-sensitive rule - an unlit campfire contributes nothing.
     */
    public int heatBelow() {
        if (level == null) {
            return 0;
        }
        BlockState below = level.getBlockState(worldPosition.below());
        if (below.getBlock() instanceof CampfireBlock && !below.getValue(CampfireBlock.LIT)) {
            return 0;
        }
        return PFDataMaps.heatOf(below.getBlock());
    }

    @Nullable
    private CrucibleMeltRecipe recipeFor(ItemStack stack) {
        if (level == null) {
            return null;
        }
        Optional<RecipeHolder<CrucibleMeltRecipe>> match = level.getRecipeManager()
            .getRecipeFor(PFRecipeTypes.CRUCIBLE_MELTING.get(), new SingleRecipeInput(stack), level);
        return match.map(RecipeHolder::value).orElse(null);
    }

    /** Single-fluid tank rule: result must match contents (or tank empty) and fully fit. */
    private boolean resultFits(FluidStack result) {
        if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), result)) {
            return false;
        }
        return tank.getFluidAmount() + result.getAmount() <= tank.getCapacity();
    }

    private static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (!(state.getBlock() instanceof CrucibleBlock)) {
            return;
        }
        if (state.getValue(CrucibleBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), Block.UPDATE_CLIENTS);
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        if (!melting.isEmpty()) {
            tag.put("Melting", melting.save(registries));
        }
        tag.putInt("MeltProgress", meltProgress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Tank", Tag.TAG_COMPOUND)) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        }
        melting = tag.contains("Melting", Tag.TAG_COMPOUND)
            ? ItemStack.parseOptional(registries, tag.getCompound("Melting"))
            : ItemStack.EMPTY;
        // Clamp like the Milker: a tampered save must not stall the melt loop.
        int loaded = tag.contains("MeltProgress", Tag.TAG_INT) ? tag.getInt("MeltProgress") : 0;
        meltProgress = Math.max(0, Math.min(loaded, MELT_TOTAL));
    }

    // Client sync for look-at HUDs (Jade): without these, tank contents and
    // melt progress are stale until a block update. Same shape as the Milker.
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
