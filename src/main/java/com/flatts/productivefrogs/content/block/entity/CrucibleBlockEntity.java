package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.CrucibleBlock;
import com.flatts.productivefrogs.content.recipe.CrucibleMeltRecipe;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFDataMaps;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the {@link CrucibleBlock}. Functional mirror of Ex
 * Deorum's crucible model (behavioral reimplementation - no GPL code), adapted
 * to Froglight inputs:
 *
 * <p><b>Solids buffer.</b> Inserting a Froglight evaluates its
 * {@code crucible_melting} recipe ONCE and converts it immediately into queued
 * "solids" (the recipe's mB amount) plus a pending fluid type. Up to
 * {@link #MAX_SOLIDS} (4,000 = four Froglights) queue; more Froglights can be
 * added while earlier ones are still melting, as long as the fluid matches.
 *
 * <p><b>Continuous melt.</b> Every 10 ticks, {@code heat * MELT_PER_HEAT} mB
 * move from solids into the tank - so a torch (heat 1) melts 1,000 mB in 400
 * ticks, matching the original spec timing, and a hotter source mid-melt just
 * speeds it up. No heat = solids wait. Tank full = solids wait (nothing is
 * ever voided or ejected).
 *
 * <p><b>I/O.</b> Hand: right-click Froglight inserts; any fluid-handler item
 * (bucket) drains; a glass bottle pulls 250 mB when the tank holds water
 * (Ex Deorum parity). Automation: an insert-only, recipe-validated
 * {@link IItemHandler} plus the extract-only fluid handler, both wired in
 * {@code PFModBusEvents}.
 *
 * <p><b>Visuals.</b> The contained fluid lights the block via the NeoForge
 * aux light manager (lava glows full-strength), and the client renderer
 * ({@code client/renderer/CrucibleRenderer}) draws the rising fluid surface
 * plus a variant-tinted Froglight "solids" surface inside the basin. The
 * {@link CrucibleBlock#LIT} state still tracks actively-melting-over-heat for
 * the glowing interior texture.
 */
public class CrucibleBlockEntity extends BlockEntity {

    /** Queued-solids cap: four Froglights' worth. */
    public static final int MAX_SOLIDS = 4_000;

    /** Tank capacity per the spec - four buckets. */
    public static final int TANK_CAPACITY = 4_000;

    /**
     * mB melted per heat point per melt pulse (every 10 ticks). 25 keeps the
     * spec's timing: 1,000 mB over a torch (heat 1) = 400 ticks.
     */
    public static final int MELT_PER_HEAT = 25;

    /** Ticks between melt pulses, mirroring Ex Deorum's half-second cadence. */
    public static final int MELT_PULSE_TICKS = 10;

    /** Glass-bottle extraction amount when the tank holds water. */
    public static final int BOTTLE_MB = 250;

    private final FluidTank tank = new FluidTank(TANK_CAPACITY);

    /** Queued not-yet-melted mB. */
    private int solids = 0;

    /**
     * The fluid the queued solids will melt into; null when solids == 0 and
     * the tank is empty. Evaluated once at insert time from the recipe.
     */
    @Nullable
    private Fluid pendingFluid = null;

    /**
     * Variant id of the most recently inserted Froglight - drives the solids
     * surface tint in the renderer. Cosmetic only; survives saves so the
     * surface doesn't flicker grey on reload.
     */
    @Nullable
    private ResourceLocation lastVariant = null;

    /** Extract-only view handed to pipes and FluidUtil bucket interactions. */
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
                onContentsChanged();
            }
            return drained;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = tank.drain(maxDrain, action);
            if (action.execute() && !drained.isEmpty()) {
                onContentsChanged();
            }
            return drained;
        }
    };

    /**
     * Insert-only item view for hoppers/pipes: accepts exactly the Froglights
     * the recipe set + fluid-match rules allow, converting them to solids
     * immediately (no internal item slot - the Ex Deorum shape).
     */
    private final IItemHandler insertOnlyItems = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || insertCheck(stack) != InsertCheck.OK) {
                return stack;
            }
            if (simulate) {
                ItemStack rest = stack.copy();
                rest.shrink(1);
                return rest;
            }
            ItemStack rest = stack.copy();
            ItemStack one = rest.split(1);
            if (!acceptFroglight(one)) {
                return stack;
            }
            return rest;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return insertCheck(stack) == InsertCheck.OK;
        }
    };

    public CrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.CRUCIBLE.get(), pos, state);
    }

    /** The pipe-facing extract-only fluid handler (also serves bucket clicks). */
    public IFluidHandler fluidHandler() {
        return extractOnlyTank;
    }

    /** The hopper-facing insert-only item handler. */
    public IItemHandler itemHandler() {
        return insertOnlyItems;
    }

    /** Tank contents, for the renderer/Jade/GameTests. Do not mutate. */
    public FluidStack fluid() {
        return tank.getFluid();
    }

    /** Queued not-yet-melted mB, for the renderer/Jade/GameTests. */
    public int solids() {
        return solids;
    }

    /** The fluid queued solids will become, or null when idle. */
    @Nullable
    public Fluid pendingFluid() {
        return pendingFluid;
    }

    /** Variant of the most recent Froglight, for the solids-surface tint. */
    @Nullable
    public ResourceLocation lastVariant() {
        return lastVariant;
    }

    public boolean isMelting() {
        return solids > 0;
    }

    /** Insert outcome - mirrors Ex Deorum's YES / FULL / NO click semantics. */
    public enum InsertCheck {
        /** Accepted: melt recipe exists, fluid matches, solids have room. */
        OK,
        /** Valid input but the solids queue is full - consume the click, do nothing. */
        FULL,
        /** Not meltable here (no recipe / fluid mismatch) - pass the click through. */
        REJECT
    }

    /** Classify a prospective insert without mutating anything. */
    public InsertCheck insertCheck(ItemStack stack) {
        if (stack.isEmpty() || level == null) {
            return InsertCheck.REJECT;
        }
        CrucibleMeltRecipe recipe = recipeFor(stack);
        if (recipe == null) {
            return InsertCheck.REJECT;
        }
        FluidStack result = recipe.result();
        // Single-fluid rule across BOTH buffers: the result must match the
        // tank's contents (when any) and the pending solids' fluid (when any).
        if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), result)) {
            return InsertCheck.REJECT;
        }
        if (pendingFluid != null && solids > 0 && result.getFluid() != pendingFluid) {
            return InsertCheck.REJECT;
        }
        if (solids + result.getAmount() > MAX_SOLIDS) {
            return InsertCheck.FULL;
        }
        return InsertCheck.OK;
    }

    /**
     * Consume one Froglight from the given single-item stack into the solids
     * queue. Caller has already classified via {@link #insertCheck} and split
     * off a single item; returns false only on a lost race.
     */
    public boolean acceptFroglight(ItemStack one) {
        if (level == null || level.isClientSide() || insertCheck(one) != InsertCheck.OK) {
            return false;
        }
        CrucibleMeltRecipe recipe = recipeFor(one);
        if (recipe == null) {
            return false;
        }
        solids += recipe.result().getAmount();
        pendingFluid = recipe.result().getFluid();
        ResourceLocation variant = one.get(PFDataComponents.SLIME_VARIANT.get());
        if (variant != null) {
            lastVariant = variant;
        }
        level.playSound(null, worldPosition, SoundEvents.LAVA_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.0F);
        PFDebug.log(PFDebug.Area.REGISTRY, () -> String.format(
            "crucible @%s queued froglight -> %d mB solids of %s", worldPosition, solids, pendingFluid));
        onContentsChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrucibleBlockEntity be) {
        boolean activelyMelting = false;
        if (be.solids > 0 && be.pendingFluid != null && level.getGameTime() % MELT_PULSE_TICKS == 0L) {
            int heat = be.heatBelow();
            if (heat > 0) {
                int delta = Math.min(be.solids, heat * MELT_PER_HEAT);
                int space = be.tank.getCapacity() - be.tank.getFluidAmount();
                delta = Math.min(delta, space);
                if (delta > 0) {
                    be.solids -= delta;
                    if (be.tank.isEmpty()) {
                        be.tank.setFluid(new FluidStack(be.pendingFluid, delta));
                    } else {
                        be.tank.getFluid().grow(delta);
                    }
                    if (be.solids == 0 && be.tank.getFluidAmount() >= 1000) {
                        level.playSound(null, pos, SoundEvents.BUCKET_FILL_LAVA, SoundSource.BLOCKS, 0.5F, 1.1F);
                    }
                    be.onContentsChanged();
                }
            }
        }
        // LIT = solids actively melting over live heat (drives the glowing
        // interior texture). Checked every tick so a removed heat source
        // dims promptly.
        if (be.solids > 0 && be.heatBelow() > 0) {
            activelyMelting = true;
        }
        setLit(level, pos, state, activelyMelting);
    }

    /** The variant whose placed Froglight doubles as a heat source. */
    private static final ResourceLocation LAVA_VARIANT =
        ResourceLocation.fromNamespaceAndPath("productivefrogs", "lava");

    /**
     * Heat value of the block below: data-map lookup plus two rules the map
     * can't express - an unlit campfire contributes nothing (blockstate), and
     * a placed LAVA Froglight heats like lava itself (the variant lives on the
     * BlockEntity, invisible to a block-keyed map; the value is read from
     * lava's own map entry so pack overrides track automatically). The latter
     * closes a tidy loop: the Crucible's lava output begets the heat plate for
     * the next Crucible.
     */
    public int heatBelow() {
        if (level == null) {
            return 0;
        }
        BlockState below = level.getBlockState(worldPosition.below());
        if (below.getBlock() instanceof CampfireBlock && !below.getValue(CampfireBlock.LIT)) {
            return 0;
        }
        if (level.getBlockEntity(worldPosition.below()) instanceof ConfigurableFroglightBlockEntity froglight
                && LAVA_VARIANT.equals(froglight.getVariantId())) {
            return PFDataMaps.heatOf(net.minecraft.world.level.block.Blocks.LAVA);
        }
        return PFDataMaps.heatOf(below.getBlock());
    }

    /** Glass-bottle extraction: 250 mB of water when the tank holds water. */
    public boolean canFillBottle() {
        return tank.getFluid().getFluid() == Fluids.WATER && tank.getFluidAmount() >= BOTTLE_MB;
    }

    /** Drain the bottle's worth after {@link #canFillBottle} approved. */
    public void drainBottle() {
        tank.drain(BOTTLE_MB, IFluidHandler.FluidAction.EXECUTE);
        onContentsChanged();
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

    /**
     * One funnel for every mutation: clears the pending fluid when both
     * buffers empty, re-emits the aux fluid light, persists, and syncs the
     * client (the renderer reads tank/solids from the update tag).
     */
    private void onContentsChanged() {
        if (solids == 0 && tank.isEmpty()) {
            pendingFluid = null;
        }
        updateFluidLight();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Emit the contained fluid's own light through NeoForge's aux light
     * manager - a crucible of lava glows like lava (Ex Deorum parity). The
     * LIT blockstate's level-11 glow stacks independently while melting.
     */
    private void updateFluidLight() {
        if (level == null) {
            return;
        }
        var lightManager = level.getAuxLightManager(worldPosition);
        if (lightManager != null) {
            int light = tank.isEmpty() ? 0 : tank.getFluid().getFluid().getFluidType().getLightLevel();
            lightManager.setLightAt(worldPosition, light);
        }
    }

    private static void setLit(Level level, BlockPos pos, BlockState state, boolean lit) {
        if (!(state.getBlock() instanceof CrucibleBlock)) {
            return;
        }
        if (state.getValue(CrucibleBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(CrucibleBlock.LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    // -------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Solids", solids);
        if (pendingFluid != null) {
            tag.putString("PendingFluid", BuiltInRegistries.FLUID.getKey(pendingFluid).toString());
        }
        if (lastVariant != null) {
            tag.putString("LastVariant", lastVariant.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Tank", Tag.TAG_COMPOUND)) {
            tank.readFromNBT(registries, tag.getCompound("Tank"));
        }
        solids = Math.max(0, Math.min(
            tag.contains("Solids", Tag.TAG_INT) ? tag.getInt("Solids") : 0, MAX_SOLIDS));
        pendingFluid = tag.contains("PendingFluid", Tag.TAG_STRING)
            ? BuiltInRegistries.FLUID.get(ResourceLocation.parse(tag.getString("PendingFluid")))
            : null;
        if (pendingFluid == Fluids.EMPTY) {
            pendingFluid = null;
        }
        lastVariant = tag.contains("LastVariant", Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString("LastVariant"))
            : null;
        updateFluidLight();
    }

    // Client sync: the renderer + Jade read everything from the update tag.
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
