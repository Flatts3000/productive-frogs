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
import com.flatts.productivefrogs.registry.PFVariantMilk;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
    public static final int DATA_COUNT = 6;

    /** Stable order of validation failure keys for {@link #DATA_PROBLEM} sync. */
    public static final String[] PROBLEM_KEYS = {
        "not_a_controller", "not_solid", "machine_on_edge", "machine_facing_wrong",
        "sprinkler_off_ceiling", "no_hatch", "multiple_hatches", "no_incubator", "multiple_controllers"
    };

    private int tickCounter;
    private boolean formed;
    @Nullable
    private TerrariumValidationResult lastResult;

    /** FIFO buffer of milk charges; all share {@link #tankVariant}. */
    private final Deque<MilkCharge> charges = new ArrayDeque<>();
    @Nullable
    private ResourceLocation tankVariant;
    private int distributeCursor;

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
        TerrariumValidationResult result = TerrariumValidator.validate(level, pos, state);
        this.lastResult = result;
        if (result.formed()) {
            TerrariumManager.register(level, result);
        } else {
            TerrariumManager.deregister(level, pos);
        }
        if (result.formed() != this.formed) {
            this.formed = result.formed();
            level.setBlock(pos, state.setValue(TerrariumControllerBlock.FORMED, this.formed), Block.UPDATE_ALL);
        }
        return result;
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

    // ---- milk funnel (phase 2) -----------------------------------------

    /**
     * Whether the buffer can take another charge of {@code variant}: buffer not
     * full, reject-until-empty on variant, AND not a boss-tier ({@code spawn_catalyst})
     * variant. Boss milk is refused outright - a Sprinkler can't reproduce the
     * source's 6-face catalyst altar gate, so the Terrarium must not become an
     * altar bypass (issue #184). All intake paths (bucket, pipe fill, isFluidValid)
     * funnel through here.
     */
    public boolean canAccept(ResourceLocation variant) {
        return charges.size() < PFConfig.terrariumControllerBufferDepth()
            && (tankVariant == null || tankVariant.equals(variant))
            && !requiresCatalystAltar(variant);
    }

    /** Boss-tier variants ({@code spawn_catalyst}) are altar-gated; the Controller refuses them. */
    private boolean requiresCatalystAltar(ResourceLocation variant) {
        return level != null && SlimeMilkSourceBlock.variantRequiresCatalyst(level, variant);
    }

    /**
     * Hand-feed entry: push a charge built from a milk bucket. Returns false (and
     * consumes nothing) when the bucket isn't milk, has no variant, or the buffer
     * is full / holds another variant.
     */
    public boolean pushChargeFromBucket(ItemStack milkBucket) {
        if (!(milkBucket.getItem() instanceof SlimeMilkBucketItem milk)) {
            return false;
        }
        ResourceLocation variant = milk.variantId();
        if (variant == null || !canAccept(variant)) {
            return false;
        }
        tankVariant = variant;
        charges.addLast(MilkCharge.fromBucket(milkBucket));
        setChanged();
        return true;
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
                sprinkler.loadCharge(tankVariant, charges.removeFirst());
                distributeCursor = (idx + 1) % n;
                onChargesChanged();
                return;
            }
        }
        // 2. Else top up the first draining matching Sprinkler.
        int threshold = PFConfig.terrariumSprinklerTopUpThreshold();
        for (BlockPos sprinklerPos : sprinklers) {
            if (level.getBlockEntity(sprinklerPos) instanceof SprinklerBlockEntity sprinkler
                    && sprinkler.wantsTopUp(tankVariant, threshold)) {
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
        }
        setChanged();
    }

    /** Fill-only fluid handler for pipe intake (catalysts ride the FluidStack). */
    public IFluidHandler fluidHandler() {
        return fluidIntake;
    }

    /** Test seam: current buffered charge count. */
    public int bufferedCharges() {
        return charges.size();
    }

    /** Test seam: the variant the buffer currently holds, or null. */
    @Nullable
    public ResourceLocation tankVariant() {
        return tankVariant;
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
            ResourceLocation variant = PFVariantMilk.variantOf(stack.getFluid());
            return variant != null && canAccept(variant);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            ResourceLocation variant = PFVariantMilk.variantOf(resource.getFluid());
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
                setChanged();
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (tankVariant != null) {
            tag.putString("TankVariant", tankVariant.toString());
        }
        if (!charges.isEmpty()) {
            ListTag list = new ListTag();
            for (MilkCharge charge : charges) {
                list.add(charge.toTag());
            }
            tag.put("Charges", list);
        }
        tag.putInt("DistributeCursor", distributeCursor);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tankVariant = tag.contains("TankVariant", Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString("TankVariant")) : null;
        charges.clear();
        if (tag.contains("Charges", Tag.TAG_LIST)) {
            ListTag list = tag.getList("Charges", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                charges.addLast(MilkCharge.fromTag(list.getCompound(i)));
            }
        }
        distributeCursor = Math.max(0, tag.getInt("DistributeCursor"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // sync tankVariant + charge count for Jade
        return tag;
    }

    @Override
    @Nullable
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
