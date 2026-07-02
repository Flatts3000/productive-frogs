package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.TerrariumControllerBlock;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.content.multiblock.TerrariumValidationResult;
import com.flatts.productivefrogs.content.multiblock.TerrariumValidator;
import com.flatts.productivefrogs.content.menu.TerrariumControllerMenu;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Terrarium Controller's block entity. Two jobs:
 *
 * <ol>
 *   <li><b>Validation</b> (phase 1): a throttled tick re-runs {@link TerrariumValidator},
 *       registers/deregisters in {@link TerrariumManager}, and flips
 *       {@link TerrariumControllerBlock#FORMED}.</li>
 *   <li><b>Milk funnel</b> (phase 2): a FIFO buffer of {@link MilkCharge} - one
 *       variant at a time, rejected until the buffer drains - fed by hand
 *       (right-click a milk bucket) or by pipe ({@link #fluidHandler()}, fill-only;
 *       catalysts ride the {@code FluidStack} via the component-preserving wrapper).
 *       Each tick it round-robins a charge into an empty Sprinkler, or tops up a
 *       draining matching one, so a Sprinkler spawns identically to a hand-placed
 *       catalyzed source. The hopper-fed bucket slot arrives with the GUI (phase 5);
 *       hand + pipe are the phase-2 intake paths.</li>
 * </ol>
 */
public class TerrariumControllerBlockEntity extends BlockEntity implements MenuProvider {

    /** ContainerData indices for the status screen. */
    public static final int DATA_FORMED = 0;
    public static final int DATA_CHARGES = 1;
    public static final int DATA_BUFFER_DEPTH = 2;
    public static final int DATA_PROBLEM = 3; // index into PROBLEM_KEYS, or -1
    public static final int DATA_SPRINKLERS = 4; // count in the formed multiblock, 0 when unformed
    public static final int DATA_INCUBATORS = 5; // count in the formed multiblock, 0 when unformed
    public static final int DATA_FROGS = 6; // live frogs in the cavity, 0 when unformed
    public static final int DATA_FROG_CAP = 7; // configured frog cap
    public static final int DATA_COUNT = 8;

    /** Stable order of validation failure keys for {@link #DATA_PROBLEM} sync. */
    public static final String[] PROBLEM_KEYS = {
        "not_a_controller", "not_solid", "machine_on_edge", "machine_facing_wrong",
        "sprinkler_off_ceiling", "no_hatch", "multiple_hatches", "no_incubator", "multiple_controllers"
    };

    private int tickCounter;
    /** Validate on the first tick after placement/load so the formed state + GUI are correct within a tick. */
    private boolean needsInitialValidation = true;
    private boolean formed;
    @Nullable
    private TerrariumValidationResult lastResult;

    /** FIFO buffer of milk charges; all share {@link #tankVariant}. */
    private final Deque<MilkCharge> charges = new ArrayDeque<>();
    @Nullable
    private Identifier tankVariant;
    /** Equivalence lane (#253): true when {@link #tankVariant} is a synthesized item id (Mimic Milk). */
    private boolean tankMimic;
    private int distributeCursor;

    /**
     * Client-only: absolute position of the first structural problem, synced via
     * {@link #getUpdateTag} (never persisted to disk). Drives the status GUI's
     * coordinate readout and the in-world problem outline; null when the terrarium
     * is formed or the current problem carries no position.
     */
    @Nullable
    private BlockPos clientProblemPos;

    private final ControllerFluidIntake fluidIntake = new ControllerFluidIntake();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FORMED -> formed ? 1 : 0;
                case DATA_CHARGES -> charges.size();
                case DATA_BUFFER_DEPTH -> PFConfig.terrariumControllerBufferDepth();
                case DATA_PROBLEM -> problemOrdinal();
                case DATA_SPRINKLERS -> formedCount(true);
                case DATA_INCUBATORS -> formedCount(false);
                case DATA_FROGS -> cavityFrogCount();
                case DATA_FROG_CAP -> PFConfig.terrariumFrogCap();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // client-side mirror only; server is authoritative
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public TerrariumControllerBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.TERRARIUM_CONTROLLER.get(), pos, state);
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    /** Live frogs whose center is inside the cavity (the value the frog cap compares against); 0 when unformed. */
    private int cavityFrogCount() {
        if (!formed || !(level instanceof ServerLevel server)) {
            return 0;
        }
        TerrariumManager.FormedTerrarium t = TerrariumManager.byController(server, worldPosition);
        if (t == null) {
            return 0;
        }
        var cavity = t.cavity();
        return server.getEntitiesOfClass(com.flatts.productivefrogs.content.entity.ResourceFrog.class, cavity,
            f -> cavity.contains(f.getX(), f.getY(), f.getZ())).size();
    }

    /** Sprinkler ({@code true}) or Incubator ({@code false}) count in the formed multiblock; 0 when unformed. */
    private int formedCount(boolean sprinklers) {
        if (!formed || lastResult == null || !lastResult.formed()) {
            return 0;
        }
        return (sprinklers ? lastResult.sprinklers() : lastResult.incubators()).size();
    }

    private int problemOrdinal() {
        if (formed || lastResult == null || lastResult.firstProblem() == null) {
            return -1;
        }
        String key = lastResult.firstProblem().messageKey();
        for (int i = 0; i < PROBLEM_KEYS.length; i++) {
            if (PROBLEM_KEYS[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.terrarium_controller");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new TerrariumControllerMenu(containerId, playerInv, this, dataAccess);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerrariumControllerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // First tick after placement/load: validate immediately so the formed state,
        // TerrariumManager registration, and GUI are correct within a tick rather than
        // after a full validation interval.
        if (be.needsInitialValidation) {
            be.needsInitialValidation = false;
            be.runValidation(server, pos, state);
        }
        int interval = Math.max(1, PFConfig.terrariumValidationIntervalTicks());
        if (++be.tickCounter >= interval) {
            be.tickCounter = 0;
            be.runValidation(server, pos, state);
        }
        if (be.formed) {
            be.distribute(server, pos);
        }
    }

    // ---- validation (phase 1) ------------------------------------------

    public TerrariumValidationResult runValidation(ServerLevel level, BlockPos pos, BlockState state) {
        BlockPos prevProblem = problemPosOf(this.lastResult);
        TerrariumValidationResult result = TerrariumValidator.validate(level, pos, state);
        this.lastResult = result;
        if (result.formed()) {
            TerrariumManager.register(level, result);
        } else {
            TerrariumManager.deregister(level, pos);
        }
        if (result.formed() != this.formed) {
            this.formed = result.formed();
            // setBlock(UPDATE_ALL) re-sends the BE update tag (incl. the problem pos) to clients.
            level.setBlock(pos, state.setValue(TerrariumControllerBlock.FORMED, this.formed), Block.UPDATE_ALL);
        } else if (!java.util.Objects.equals(prevProblem, problemPosOf(result))) {
            // Still unformed, but the offending block moved: refresh clients so the GUI
            // coordinates and the in-world outline track the current problem.
            syncToClients();
        }
        return result;
    }

    /** The first structural problem's position for {@code result}, or null (formed / positionless problem). */
    @Nullable
    private static BlockPos problemPosOf(@Nullable TerrariumValidationResult result) {
        return result != null && result.firstProblem() != null ? result.firstProblem().at() : null;
    }

    public TerrariumValidationResult forceValidate(ServerLevel level, BlockPos pos) {
        return runValidation(level, pos, level.getBlockState(pos));
    }

    public boolean isFormed() {
        return formed;
    }

    @Nullable
    public TerrariumValidationResult lastResult() {
        return lastResult;
    }

    public void onBroken(ServerLevel level, BlockPos pos) {
        TerrariumManager.deregister(level, pos);
    }

    // 26.1 port: multiblock teardown runs here (the BE still exists on the removal path), NOT in
    // the block's affectNeighborsAfterRemoval, which runs after the BE is gone and could no longer
    // read it. Deregisters from TerrariumManager so the formed structure is torn down on break.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            onBroken(serverLevel, pos);
        }
    }

    // ---- milk funnel (phase 2) -----------------------------------------

    /**
     * Whether the buffer can take another charge of {@code variant}: buffer not
     * full, reject-until-empty on variant, AND not a boss-tier ({@code spawn_catalyst})
     * variant. Boss milk is refused outright - a Sprinkler can't reproduce the
     * source's 6-face catalyst altar gate, so the Terrarium must not become an
     * altar bypass (issue #184). All intake paths (bucket, pipe fill, isFluidValid)
     * funnel through here.
     */
    public boolean canAccept(Identifier variant) {
        return charges.size() < PFConfig.terrariumControllerBufferDepth()
            // A variant (Slime Milk) charge can't mix with a buffered mimic charge,
            // even on an unlikely id collision (defense-in-depth; canAcceptBucket
            // already enforces this single-kind rule for the bucket path).
            && !tankMimic
            && (tankVariant == null || tankVariant.equals(variant))
            && !requiresCatalystAltar(variant);
    }

    /** Boss-tier variants ({@code spawn_catalyst}) are altar-gated; the Controller refuses them. */
    private boolean requiresCatalystAltar(Identifier variant) {
        return level != null && SlimeMilkSourceBlock.variantRequiresCatalyst(level, variant);
    }

    /**
     * Hand-feed entry: push a charge built from a milk bucket. Returns false (and
     * consumes nothing) when the bucket isn't milk, has no variant, or the buffer
     * is full / holds another variant.
     */
    public boolean pushChargeFromBucket(ItemStack milkBucket) {
        if (!canAcceptBucket(milkBucket)) {
            return false;
        }
        // Equivalence lane (#253): a Mimic Milk bucket buffers under its synthesized
        // item id with the mimic flag; a variant bucket under its variant id. The
        // buffer is single-kind (one variant OR one mimic item at a time).
        if (milkBucket.getItem() instanceof com.flatts.productivefrogs.content.item.MimicMilkBucketItem) {
            tankVariant = milkBucket.get(
                com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get());
            tankMimic = true;
        } else {
            tankVariant = SlimeMilkBucketItem.variantOf(milkBucket);
            tankMimic = false;
        }
        charges.addLast(MilkCharge.fromBucket(milkBucket));
        syncToClients();
        return true;
    }

    /**
     * Whether a right-clicked milk bucket would be buffered (no mutation) - the
     * block uses this to decide whether to intercept the click or fall through to
     * the GUI. Handles both a per-variant Slime Milk bucket and a Mimic Milk bucket
     * (#253); the buffer is single-kind, so the two never mix.
     */
    public boolean canAcceptBucket(ItemStack bucket) {
        if (bucket.getItem() instanceof com.flatts.productivefrogs.content.item.MimicMilkBucketItem) {
            Identifier item = bucket.get(
                com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get());
            return item != null
                && charges.size() < PFConfig.terrariumControllerBufferDepth()
                && (tankVariant == null || (tankMimic && tankVariant.equals(item)));
        }
        if (bucket.getItem() instanceof SlimeMilkBucketItem) {
            Identifier variant = SlimeMilkBucketItem.variantOf(bucket);
            return variant != null && !tankMimic && canAccept(variant);
        }
        return false;
    }

    /** Mark dirty AND push a BE update so the GUI/Jade see the buffered variant (not just the int charge count). */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void distribute(ServerLevel level, BlockPos pos) {
        if (charges.isEmpty() || tankVariant == null) {
            return;
        }
        TerrariumManager.FormedTerrarium terrarium = TerrariumManager.byController(level, pos);
        if (terrarium == null) {
            return;
        }
        List<BlockPos> sprinklers = terrarium.sprinklers();
        int n = sprinklers.size();
        if (n == 0) {
            return;
        }
        // 1. Round-robin a fresh charge into the next empty Sprinkler.
        for (int i = 0; i < n; i++) {
            int idx = (distributeCursor + i) % n;
            if (level.getBlockEntity(sprinklers.get(idx)) instanceof SprinklerBlockEntity sprinkler
                    && sprinkler.acceptsFreshCharge()) {
                sprinkler.loadCharge(tankVariant, tankMimic, charges.removeFirst());
                distributeCursor = (idx + 1) % n;
                onChargesChanged();
                return;
            }
        }
        // 2. Else top up the first draining matching Sprinkler.
        int threshold = PFConfig.terrariumSprinklerTopUpThreshold();
        for (BlockPos sprinklerPos : sprinklers) {
            if (level.getBlockEntity(sprinklerPos) instanceof SprinklerBlockEntity sprinkler
                    && sprinkler.wantsTopUp(tankVariant, tankMimic, threshold)) {
                sprinkler.mergeCharge(charges.removeFirst());
                onChargesChanged();
                return;
            }
        }
    }

    /** A drained buffer can switch variant; clear the lock so a new variant is accepted. */
    private void onChargesChanged() {
        if (charges.isEmpty()) {
            tankVariant = null;
            tankMimic = false;
        }
        syncToClients();
    }

    /** Fill-only fluid handler for pipe intake (catalysts ride the FluidStack). */
    public IFluidHandler fluidHandler() {
        return fluidIntake;
    }

    /**
     * The 26.1 {@code Capabilities.Fluid.BLOCK} view: a fill-only milk funnel (drain
     * is a no-op; contents live as {@link MilkCharge}s, not a reservoir). It has no
     * tank, so the journal snapshots the charge buffer + tank variant directly to
     * stay transaction-correct; the sync is deferred to commit.
     */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidResource() {
        return new ControllerFluidResource();
    }

    /**
     * The variant a milk {@link net.neoforged.neoforge.transfer.fluid.FluidResource}
     * carries (26.1 R-1), or null when it isn't Slime Milk. With one component-carrying
     * fluid the variant rides the {@code SLIME_VARIANT} component (copied on by the milk
     * bucket resource handler), so the funnel reads it directly instead of a per-variant
     * fluid reverse lookup.
     */
    @Nullable
    private static Identifier milkVariantOf(net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
        if (resource.getFluid() != PFFluids.SLIME_MILK.get()) {
            return null;
        }
        return resource.get(PFDataComponents.SLIME_VARIANT.get());
    }

    /** The variant a milk {@link FluidStack} carries (legacy IFluidHandler path), or null. */
    @Nullable
    private static Identifier milkVariantOf(FluidStack stack) {
        if (stack.getFluid() != PFFluids.SLIME_MILK.get()) {
            return null;
        }
        return stack.get(PFDataComponents.SLIME_VARIANT.get());
    }

    /** Snapshot of the funnel state the fluid intake mutates, for transaction rollback. */
    private record FunnelSnapshot(java.util.ArrayDeque<MilkCharge> charges, Identifier tankVariant) {}

    private final class ControllerFluidResource
            implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> {

        private final FunnelJournal journal = new FunnelJournal();

        @Override
        public int size() {
            return 1;
        }

        @Override
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            return net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return (long) PFConfig.terrariumControllerBufferDepth() * 1000L;
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            Identifier variant = milkVariantOf(resource);
            return variant != null && canAccept(variant);
        }

        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount <= 0) {
                return 0;
            }
            Identifier variant = milkVariantOf(resource);
            if (variant == null || !canAccept(variant)) {
                return 0;
            }
            int room = PFConfig.terrariumControllerBufferDepth() - charges.size();
            int chargesToFill = Math.min(room, amount / 1000);
            if (chargesToFill <= 0) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            tankVariant = variant;
            net.neoforged.neoforge.fluids.FluidStack stack = resource.toStack(amount);
            for (int i = 0; i < chargesToFill; i++) {
                charges.addLast(MilkCharge.fromFluid(stack));
            }
            return chargesToFill * 1000;
        }

        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0;
        }

        private final class FunnelJournal
                extends net.neoforged.neoforge.transfer.transaction.SnapshotJournal<FunnelSnapshot> {

            @Override
            protected FunnelSnapshot createSnapshot() {
                return new FunnelSnapshot(new java.util.ArrayDeque<>(charges), tankVariant);
            }

            @Override
            protected void revertToSnapshot(FunnelSnapshot snapshot) {
                charges.clear();
                charges.addAll(snapshot.charges());
                tankVariant = snapshot.tankVariant();
            }

            @Override
            protected void onRootCommit(FunnelSnapshot originalState) {
                syncToClients();
            }
        }
    }

    /** Test seam: current buffered charge count. */
    public int bufferedCharges() {
        return charges.size();
    }

    /** Test seam: the variant the buffer currently holds, or null. */
    @Nullable
    public Identifier tankVariant() {
        return tankVariant;
    }

    /** Client-only: the first structural problem's position for the GUI/outline, or null. */
    @Nullable
    public BlockPos clientProblemPos() {
        return clientProblemPos;
    }

    private final class ControllerFluidIntake implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY; // a funnel, not a reservoir - contents live as charges
        }

        @Override
        public int getTankCapacity(int tank) {
            return PFConfig.terrariumControllerBufferDepth() * 1000;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            Identifier variant = milkVariantOf(stack);
            return variant != null && canAccept(variant);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            Identifier variant = milkVariantOf(resource);
            if (variant == null || !canAccept(variant)) {
                return 0;
            }
            int room = PFConfig.terrariumControllerBufferDepth() - charges.size();
            int chargesToFill = Math.min(room, resource.getAmount() / 1000);
            if (chargesToFill <= 0) {
                return 0;
            }
            if (action.execute()) {
                tankVariant = variant;
                for (int i = 0; i < chargesToFill; i++) {
                    charges.addLast(MilkCharge.fromFluid(resource));
                }
                syncToClients();
            }
            return chargesToFill * 1000;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // ---- serialization -------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (tankVariant != null) {
            output.putString("TankVariant", tankVariant.toString());
            if (tankMimic) {
                output.putBoolean("TankMimic", true);
            }
        }
        if (!charges.isEmpty()) {
            ListTag list = new ListTag();
            for (MilkCharge charge : charges) {
                list.add(charge.toTag());
            }
            output.store("Charges", ExtraCodecs.NBT, list);
        }
        // distributeCursor is a transient round-robin position; it self-establishes
        // within one distribute cycle, so it is intentionally not persisted.
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String tank = input.getStringOr("TankVariant", "");
        tankVariant = tank.isEmpty() ? null : Identifier.tryParse(tank);
        tankMimic = input.getBooleanOr("TankMimic", false);
        charges.clear();
        input.read("Charges", ExtraCodecs.NBT).ifPresent(tag -> {
            if (tag instanceof ListTag list) {
                list.compoundStream().forEach(charge -> charges.addLast(MilkCharge.fromTag(charge)));
            }
        });
        distributeCursor = 0;
        // Client-only render/GUI hint (absent on disk loads -> null); see getUpdateTag.
        long problem = input.getLongOr("ProblemPos", Long.MIN_VALUE);
        clientProblemPos = problem == Long.MIN_VALUE ? null : BlockPos.of(problem);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries); // sync tankVariant + charge count for Jade
        // Client-only hint (deliberately NOT written by saveAdditional, so it never
        // persists to disk): the offending block for the status GUI's coordinates and
        // the in-world problem outline. Long.MIN_VALUE encodes "no positioned problem".
        BlockPos problem = formed ? null : problemPosOf(lastResult);
        tag.putLong("ProblemPos", problem == null ? Long.MIN_VALUE : problem.asLong());
        return tag;
    }

    @Override
    @Nullable
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
