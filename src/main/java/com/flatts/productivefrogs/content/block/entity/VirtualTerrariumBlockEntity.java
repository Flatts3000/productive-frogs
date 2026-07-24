package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.item.MimicMilkBucketItem;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.content.recipe.CrucibleMeltRecipe;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.event.MidasTongueDropHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the Virtual Terrarium Processor - it virtualizes one frog's
 * eat loop with no spawned entities (see {@code docs/virtual_terrarium.md}).
 *
 * <p>On this line the two eat paths are <b>Resource</b> (Slime Milk) and
 * <b>Midas</b> (Mimic Milk, gated on {@code equivalenceEnabled}). The predator
 * path and its Liquid Experience tank exist only on the 2.x line, which has
 * predation to build them against; they are out of scope here.
 *
 * <p>The core loop needs no power; the Overclock upgrade draws RF from a
 * receive-only buffer and HARD STALLS when it can't pay. Smelter and Melter run
 * for free.
 *
 * <p><b>Milk-model note:</b> on 1.21.1 each variant's Slime Milk is its own fluid
 * (v1.8), so the loaded variant comes from the <b>fluid identity</b>
 * ({@link com.flatts.productivefrogs.registry.PFVariantMilk#variantOf}), not a
 * {@code SLIME_VARIANT} component. Mimic Milk is still one fluid + the
 * {@code SYNTHESIZED_ITEM} component. Catalyst/budget components ride the
 * {@link FluidStack} on both.
 */
public class VirtualTerrariumBlockEntity extends BlockEntity implements MenuProvider {

    /** Exactly one bucket-charge of feedstock: a bucket or a pipe fills it to 1000 mB and no further. */
    public static final int FEEDSTOCK_CAPACITY = 1_000;
    public static final int MOLTEN_CAPACITY = 8_000;

    public static final int ENERGY_CAPACITY = 100_000;
    public static final int ENERGY_MAX_RECEIVE = 2_000;
    // Only the Overclock upgrade draws RF (the Smelter and Melter run for free).
    private static final int OVERCLOCK_RF_PER_CYCLE = 400;
    private static final int MAX_OVERCLOCK = 3;
    // Each Bounty upgrade adds +1 output; capped at 8 (matches VirtualTerrariumInventory#upgradeCap).
    private static final int MAX_BOUNTY_UPGRADE = 8;
    // Each Appetite upgrade shortens the cycle by a flat 15% (multiplicative), capped at 8.
    private static final double APPETITE_SPEED_FACTOR = 0.85;
    private static final int MAX_APPETITE_UPGRADE = 8;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_INTERVAL = 1;
    public static final int DATA_PRODUCT = 2;   // molten tank amount (mB)
    public static final int DATA_ENERGY = 3;    // full int syncs on this line (Distiller precedent)
    public static final int DATA_STATUS = 4;    // Status.ordinal() - drives the GUI idle/error line
    public static final int DATA_COUNT = 5;

    private final VirtualTerrariumInventory inventory = new VirtualTerrariumInventory(this::onSlotChanged);

    private final FluidTank feedstock = new FluidTank(FEEDSTOCK_CAPACITY, VirtualTerrariumBlockEntity::isFeedstockFluid) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Molten output from the Melter upgrade (one fluid at a time). */
    private final FluidTank moltenTank = new FluidTank(MOLTEN_CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Receive-only RF buffer - cables fill it, the Overclock spends it internally (the Distiller's shape). */
    private final class ReceiveOnlyEnergy extends EnergyStorage {
        ReceiveOnlyEnergy() {
            super(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
        }

        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            int received = super.receiveEnergy(toReceive, simulate);
            if (received > 0 && !simulate) {
                setChanged();
            }
            return received;
        }

        void consume(int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }

        void load(int stored) {
            this.energy = Math.max(0, Math.min(this.capacity, stored));
        }
    }

    private final ReceiveOnlyEnergy energy = new ReceiveOnlyEnergy();

    private int progress = 0;
    private int interval = 200;
    // Last status computed by serverTick, reused by the synced DATA_STATUS slot so the
    // (registry-touching) productive() check isn't recomputed per tick while a GUI is open.
    private Status lastStatus = Status.NO_DOME;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_INTERVAL -> interval;
                case DATA_PRODUCT -> moltenTank.getFluidAmount();
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_STATUS -> lastStatus.ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_PROGRESS) {
                progress = value;
            } else if (index == DATA_INTERVAL) {
                interval = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public VirtualTerrariumBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.VIRTUAL_TERRARIUM.get(), pos, state);
    }

    public VirtualTerrariumInventory getInventory() {
        return inventory;
    }

    public FluidTank getFeedstock() {
        return feedstock;
    }

    public FluidTank getMoltenTank() {
        return moltenTank;
    }

    /** Capacity of the product tank (for the GUI gauge). */
    public int productCapacity() {
        return MOLTEN_CAPACITY;
    }

    /** True when the product is molten metal (a Melter is installed). */
    public boolean productIsMolten() {
        return inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get());
    }

    /** Whether the tank accepts this fluid: any per-variant Slime Milk, or Mimic Milk. */
    private static boolean isFeedstockFluid(FluidStack fluid) {
        return isFeedstockFluid(fluid.getFluid());
    }

    private static boolean isFeedstockFluid(Fluid fluid) {
        return com.flatts.productivefrogs.registry.PFVariantMilk.variantOf(fluid) != null
            || fluid == PFFluids.MIMIC_MILK.get();
    }

    /** True for a filled feedstock bucket (Slime Milk / Mimic Milk). */
    public static boolean isFeedstockBucket(ItemStack stack) {
        return stack.getItem() instanceof SlimeMilkBucketItem
            || stack.getItem() instanceof MimicMilkBucketItem;
    }

    /** True for an empty vanilla bucket (the drain-to-bucket key). */
    public static boolean isEmptyBucket(ItemStack stack) {
        return stack.getItem() == Items.BUCKET;
    }

    /**
     * Fill the EMPTY feedstock tank from a filled feedstock bucket - exactly one
     * 1000 mB charge, preserving the milk's identity + catalyst components. Returns
     * the empty bucket to hand back, or EMPTY if not accepted (not a feedstock
     * bucket, or the tank already holds a charge).
     */
    public ItemStack fillFromBucket(ItemStack bucket) {
        if (!feedstock.getFluid().isEmpty()) {
            return ItemStack.EMPTY;
        }
        FluidStack fluid = fluidFromFeedstockBucket(bucket);
        if (fluid.isEmpty() || feedstock.fill(fluid, IFluidHandler.FluidAction.SIMULATE) < FEEDSTOCK_CAPACITY) {
            return ItemStack.EMPTY;
        }
        // Stamp the spawn budget so the readout counts N/cap from the first tick.
        MilkCharge charge = MilkCharge.fromFluid(fluid);
        fluid.set(PFDataComponents.SPAWNS_REMAINING.get(), charge.spawnsRemaining());
        fluid.set(PFDataComponents.MILK_CAPACITY.get(), charge.capacity());
        feedstock.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        syncToClient();
        return new ItemStack(Items.BUCKET);
    }

    /**
     * Drain the loaded feedstock back into a filled milk bucket carrying the same
     * identity + remaining spawn budget. Returns EMPTY if the tank holds nothing
     * bucketable. Empties the tank.
     */
    public ItemStack drainToBucket() {
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = bucketFor(fluid);
        if (bucket.isEmpty()) {
            return ItemStack.EMPTY;
        }
        copyComponentToItem(fluid, bucket, PFDataComponents.SYNTHESIZED_ITEM.get());
        copyComponentToItem(fluid, bucket, PFDataComponents.SPAWNS_REMAINING.get());
        copyComponentToItem(fluid, bucket, PFDataComponents.MILK_CAPACITY.get());
        copyComponentToItem(fluid, bucket, PFDataComponents.MILK_SPEED.get());
        copyComponentToItem(fluid, bucket, PFDataComponents.MILK_QUANTITY.get());
        copyComponentToItem(fluid, bucket, PFDataComponents.MILK_INFINITE.get());
        feedstock.setFluid(FluidStack.EMPTY);
        setChanged();
        syncToClient();
        return bucket;
    }

    private static <T> void copyComponentToItem(FluidStack from, ItemStack to,
            net.minecraft.core.component.DataComponentType<T> type) {
        T value = from.get(type);
        if (value != null) {
            to.set(type, value);
        }
    }

    /**
     * Build the 1000 mB feedstock FluidStack a bucket represents. On this line a
     * Slime Milk bucket carries its variant on its ITEM identity (v1.8), so the
     * fluid is the variant's own source fluid; a Mimic Milk bucket is the single
     * Mimic fluid + its {@code SYNTHESIZED_ITEM}. Catalyst components ride across
     * from the bucket either way.
     */
    private static FluidStack fluidFromFeedstockBucket(ItemStack bucket) {
        Fluid fluid;
        if (bucket.getItem() instanceof SlimeMilkBucketItem milk) {
            fluid = com.flatts.productivefrogs.registry.PFVariantMilk.sourceFluid(milk.variantId());
        } else if (bucket.getItem() instanceof MimicMilkBucketItem) {
            fluid = PFFluids.MIMIC_MILK.get();
        } else {
            return FluidStack.EMPTY;
        }
        if (fluid == null) {
            return FluidStack.EMPTY;
        }
        FluidStack fs = new FluidStack(fluid, FEEDSTOCK_CAPACITY);
        copyComponent(bucket, fs, PFDataComponents.SYNTHESIZED_ITEM.get());
        copyComponent(bucket, fs, PFDataComponents.SPAWNS_REMAINING.get());
        copyComponent(bucket, fs, PFDataComponents.MILK_CAPACITY.get());
        copyComponent(bucket, fs, PFDataComponents.MILK_SPEED.get());
        copyComponent(bucket, fs, PFDataComponents.MILK_QUANTITY.get());
        copyComponent(bucket, fs, PFDataComponents.MILK_INFINITE.get());
        return fs;
    }

    private static <T> void copyComponent(ItemStack from, FluidStack to,
            net.minecraft.core.component.DataComponentType<T> type) {
        T value = from.get(type);
        if (value != null) {
            to.set(type, value);
        }
    }

    private static ItemStack bucketFor(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ResourceLocation variant = com.flatts.productivefrogs.registry.PFVariantMilk.variantOf(fluid.getFluid());
        if (variant != null) {
            Item bucket = com.flatts.productivefrogs.registry.PFVariantMilk.bucket(variant);
            return bucket == null ? ItemStack.EMPTY : new ItemStack(bucket);
        }
        if (fluid.is(PFFluids.MIMIC_MILK.get())) {
            ResourceLocation item = fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            return item == null ? ItemStack.EMPTY : MimicMilkBucketItem.forItem(item);
        }
        return ItemStack.EMPTY;
    }

    /** Push the current BE state to watching clients (feedstock / molten fluid identity for the GUI + renderer). */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The receive-only RF buffer (GameTest / Jade read access; cables fill it via the capability). */
    public EnergyStorage energyStorage() {
        return energy;
    }

    /** DOWN-face fluid output: the molten tank (only filled when a Melter is installed). */
    public IFluidHandler moltenHandler() {
        return extractOnlyMolten;
    }

    private final IFluidHandler extractOnlyMolten = new IFluidHandler() {
        @Override
        public int getTanks() {
            return moltenTank.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return moltenTank.getFluidInTank(index);
        }

        @Override
        public int getTankCapacity(int index) {
            return moltenTank.getTankCapacity(index);
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return false; // product only leaves; pipes can't push molten in
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return moltenTank.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return moltenTank.drain(maxDrain, action);
        }
    };

    /** Fill-only feedstock intake for pipes: one bucket while empty, drain is a no-op. */
    public IFluidHandler feedstockHandler() {
        return fillOnlyFeedstock;
    }

    private final IFluidHandler fillOnlyFeedstock = new IFluidHandler() {
        @Override
        public int getTanks() {
            return feedstock.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int index) {
            return feedstock.getFluidInTank(index);
        }

        @Override
        public int getTankCapacity(int index) {
            return feedstock.getTankCapacity(index);
        }

        @Override
        public boolean isFluidValid(int index, FluidStack stack) {
            return feedstock.getFluid().isEmpty() && isFeedstockFluid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!feedstock.getFluid().isEmpty() || resource.getAmount() < FEEDSTOCK_CAPACITY
                    || !isFeedstockFluid(resource)) {
                return 0;
            }
            // Take exactly one bucket, stamping the budget like the hand-fill path.
            FluidStack one = resource.copyWithAmount(FEEDSTOCK_CAPACITY);
            if (action.execute()) {
                MilkCharge charge = MilkCharge.fromFluid(one);
                one.set(PFDataComponents.SPAWNS_REMAINING.get(), charge.spawnsRemaining());
                one.set(PFDataComponents.MILK_CAPACITY.get(), charge.capacity());
                // setFluid (unlike fill) does NOT fire onContentsChanged, so mark the
                // BE dirty here or a pipe-filled-but-idle block loses the milk on reload.
                feedstock.setFluid(one);
                setChanged();
                syncToClient();
            }
            return FEEDSTOCK_CAPACITY;
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

    /** Jade status: why the block is or isn't producing (the idle-reason readout). */
    public enum Status { NO_DOME, NO_FROG, NO_FEEDSTOCK, MISMATCH, NEEDS_POWER, PRODUCING }

    /**
     * Current status for Jade, computed server-side. Priority mirrors {@link #productive}:
     * no Dome, then no frog, then no feedstock, then a kind/feedstock mismatch, then an RF stall.
     */
    public Status status() {
        if (!(level instanceof ServerLevel sl)) {
            return Status.PRODUCING; // client fallback; the real value arrives via server data
        }
        if (!hasDomeAbove(sl)) {
            return Status.NO_DOME;
        }
        if (inventory.getFrog().isEmpty()) {
            return Status.NO_FROG;
        }
        if (feedstock.getFluid().isEmpty()) {
            return Status.NO_FEEDSTOCK;
        }
        if (!productive(sl)) {
            return Status.MISMATCH;
        }
        if (powerStalled()) {
            return Status.NEEDS_POWER;
        }
        return Status.PRODUCING;
    }

    /** True when the Overclock upgrade is installed - the only upgrade that draws RF. */
    public boolean hasPoweredUpgrade() {
        return inventory.hasUpgrade(PFItems.VT_UPGRADE_OVERCLOCK.get());
    }

    private boolean powerStalled() {
        int cost = rfCostPerCycle();
        return cost > 0 && energy.getEnergyStored() < cost;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    // -- capability views (wired in PFModBusEvents) --

    public IItemHandler outputView() {
        return inventory.outputView();
    }

    public IItemHandler frogView() {
        return inventory.frogView();
    }

    // -- the eat loop --

    public static void serverTick(Level level, BlockPos pos, BlockState state, VirtualTerrariumBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Status status = be.status();
        be.lastStatus = status;
        if (status != Status.PRODUCING) {
            be.resetProgress();
            setWorking(level, pos, state, false);
            return;
        }
        be.progress = Math.min(be.progress + 1, be.interval);
        be.setChanged();
        if (be.progress >= be.interval) {
            be.produce(serverLevel);
        } else {
            setWorking(level, pos, state, true);
        }
    }

    /** Whether the block can run this tick: a Dome above, a loaded frog, and matching feedstock. */
    private boolean productive(ServerLevel level) {
        if (!hasDomeAbove(level)) {
            return false;
        }
        ItemStack frog = inventory.getFrog();
        if (frog.isEmpty()) {
            return false;
        }
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty()) {
            return false;
        }
        if (loadedIsMidas()) {
            return PFConfig.equivalenceEnabled()
                && fluid.is(PFFluids.MIMIC_MILK.get())
                && fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get()) != null;
        }
        Category frogCategory = loadedCategory();
        if (frogCategory == null) {
            return false;
        }
        ResourceLocation variant = com.flatts.productivefrogs.registry.PFVariantMilk.variantOf(fluid.getFluid());
        return variant != null && categoryOf(level, variant) == frogCategory;
    }

    private void produce(ServerLevel level) {
        int rfCost = rfCostPerCycle();
        if (rfCost > 0 && energy.getEnergyStored() < rfCost) {
            return; // hard stall: the Overclock upgrade can't be paid
        }
        FluidStack fluid = feedstock.getFluid();
        boolean produced;
        if (loadedIsMidas()) {
            ResourceLocation itemId = fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            produced = emitFroglight(level, MidasTongueDropHandler.buildPrismaticFroglight(itemId), fluid);
        } else {
            ResourceLocation variant = com.flatts.productivefrogs.registry.PFVariantMilk.variantOf(fluid.getFluid());
            produced = emitFroglight(level, FrogTongueDropHandler.buildFroglight(variant, null), fluid);
        }
        if (produced) {
            if (rfCost > 0) {
                energy.consume(rfCost);
            }
            consumeAndReschedule(level, fluid);
        }
    }

    /** One Froglight batch, optionally smelted or melted. Returns true if produced. */
    private boolean emitFroglight(ServerLevel level, ItemStack froglight, FluidStack fluid) {
        if (froglight.isEmpty()) {
            return false;
        }
        boolean melter = inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get());
        // Cheap backpressure gate BEFORE any recipe lookup so a full output/tank does not
        // re-run smelt/melt recipes every tick.
        if (melter ? moltenTank.getFluidAmount() >= MOLTEN_CAPACITY : inventory.outputFull()) {
            return false;
        }
        // The frog's own Bounty gives its drop count; each Bounty UPGRADE adds a flat +1 output.
        int perSlime = FrogStats.bountyDropCount(effectiveBounty(), PFConfig.bountyMaxDrops(), PFConfig.statCap())
            + bountyUpgradeCount();
        int total = Math.max(1, perSlime * MilkSpawnEconomy.batchQuantity(MilkCharge.fromFluid(fluid).quantity()));
        ItemStack single = froglight.copyWithCount(1);

        if (melter) {
            FluidStack molten = meltOne(level, single);
            if (!molten.isEmpty()) {
                FluidStack batch = molten.copyWithAmount(molten.getAmount() * total);
                int accepted = moltenTank.fill(batch, IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    return false; // molten tank full or holds a different fluid: pause
                }
                // Fill what fits - a nearly-full tank produces a partial batch rather than stalling forever.
                moltenTank.fill(batch.copyWithAmount(accepted), IFluidHandler.FluidAction.EXECUTE);
                return true;
            }
            // no melt recipe for this variant: fall through and output the raw Froglight
        }

        ItemStack unit = single;
        if (inventory.hasUpgrade(PFItems.VT_UPGRADE_SMELTER.get())) {
            ItemStack smelted = smeltOne(level, single);
            if (!smelted.isEmpty()) {
                unit = smelted;
            }
        }
        int count = Math.min(unit.getCount() * total, inventory.outputCapacity(unit));
        if (count <= 0) {
            return false;
        }
        inventory.pushOutput(unit.copyWithCount(count));
        return true;
    }

    /** The smelted result for a single input, or EMPTY when there is no smelting recipe. */
    private static ItemStack smeltOne(ServerLevel level, ItemStack single) {
        RecipeManager manager = level.getRecipeManager();
        SingleRecipeInput input = new SingleRecipeInput(single);
        Optional<RecipeHolder<SmeltingRecipe>> match = manager.getRecipeFor(RecipeType.SMELTING, input, level);
        return match.map(holder -> holder.value().assemble(input, level.registryAccess())).orElse(ItemStack.EMPTY);
    }

    /** The molten fluid for a single Froglight input, or EMPTY when there is no melt recipe. */
    private static FluidStack meltOne(ServerLevel level, ItemStack single) {
        RecipeManager manager = level.getRecipeManager();
        Optional<RecipeHolder<CrucibleMeltRecipe>> match =
            manager.getRecipeFor(PFRecipeTypes.CRUCIBLE_MELTING.get(), new SingleRecipeInput(single), level);
        return match.map(holder -> holder.value().result()).orElse(FluidStack.EMPTY);
    }

    private void consumeAndReschedule(ServerLevel level, FluidStack fluid) {
        // Spend one of the milk's spawns - exactly like a placed Slime Milk source. The
        // liquid is NOT drained per eat; the tank only empties once the budget hits zero.
        spendOneSpawn();
        progress = 0;
        interval = computeInterval(fluid);
        setChanged();
        syncToClient();
        setWorking(level, worldPosition, level.getBlockState(worldPosition), true);
    }

    /** Decrement the loaded feedstock's spawn budget; empty the tank when it runs out. */
    private void spendOneSpawn() {
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty()) {
            return;
        }
        MilkCharge charge = MilkCharge.fromFluid(fluid);
        if (charge.infinite()) {
            return; // Endless catalyst: never depletes
        }
        int remaining = charge.spawnsRemaining() - 1;
        if (remaining <= 0) {
            feedstock.setFluid(FluidStack.EMPTY); // milk used up - now it is consumed
            return;
        }
        FluidStack updated = fluid.copy();
        updated.set(PFDataComponents.SPAWNS_REMAINING.get(), remaining);
        updated.set(PFDataComponents.MILK_CAPACITY.get(), charge.capacity()); // fixed denominator for N/cap
        feedstock.setFluid(updated);
    }

    /** Spawns remaining on the loaded feedstock (0 when empty). */
    public int feedstockSpawnsRemaining() {
        FluidStack fluid = feedstock.getFluid();
        return fluid.isEmpty() ? 0 : MilkCharge.fromFluid(fluid).spawnsRemaining();
    }

    /** Spawn-budget capacity of the loaded feedstock (for the N/cap readout). */
    public int feedstockSpawnsCapacity() {
        FluidStack fluid = feedstock.getFluid();
        return fluid.isEmpty() ? 0 : MilkCharge.fromFluid(fluid).capacity();
    }

    /** True when the loaded feedstock never depletes (Endless catalyst). */
    public boolean feedstockInfinite() {
        FluidStack fluid = feedstock.getFluid();
        return !fluid.isEmpty() && MilkCharge.fromFluid(fluid).infinite();
    }

    // -- upgrade-tuned figures --

    private int effectiveBounty() {
        return Math.min(PFConfig.statCap(), loadedStat("Bounty"));
    }

    private int bountyUpgradeCount() {
        return Math.min(MAX_BOUNTY_UPGRADE, inventory.countUpgrade(PFItems.VT_UPGRADE_BOUNTY.get()));
    }

    private int effectiveAppetite() {
        return Math.min(PFConfig.statCap(), loadedStat("Appetite"));
    }

    private double appetiteUpgradeFactor() {
        int n = Math.min(MAX_APPETITE_UPGRADE, inventory.countUpgrade(PFItems.VT_UPGRADE_APPETITE.get()));
        return Math.pow(APPETITE_SPEED_FACTOR, n);
    }

    /** RF drawn per cycle - only the Overclock upgrade costs power (Smelter / Melter are free). */
    private int rfCostPerCycle() {
        return Math.min(MAX_OVERCLOCK, inventory.countUpgrade(PFItems.VT_UPGRADE_OVERCLOCK.get())) * OVERCLOCK_RF_PER_CYCLE;
    }

    private int computeInterval(FluidStack fluid) {
        // Deterministic base (NOT the random spawn interval) so the eat duration is predictable
        // and the Appetite / Overclock effects read as visible speed changes, not random noise.
        int base = MilkSpawnEconomy.intervalTicksDeterministic(MilkCharge.fromFluid(fluid).speed());
        int span = Math.max(1, PFConfig.statCap() - FrogStats.STAT_MIN);
        double appetiteFactor = 1.0 - 0.5 * Math.max(0.0, Math.min(1.0,
            (effectiveAppetite() - FrogStats.STAT_MIN) / (double) span));
        double scaled = base * appetiteFactor
            * appetiteUpgradeFactor()   // flat -15%/upgrade, decoupled from the frog stat
            * Math.pow(0.5, Math.min(MAX_OVERCLOCK, inventory.countUpgrade(PFItems.VT_UPGRADE_OVERCLOCK.get())));
        return Math.max(1, (int) Math.round(scaled));
    }

    // -- lookups --

    private boolean hasDomeAbove(Level level) {
        return level.getBlockState(worldPosition.above()).is(PFBlocks.VIRTUAL_TERRARIUM_DOME.get());
    }

    /** Whether a Display Dome sits directly above (works client-side too - for the dome renderer). */
    public boolean hasDome() {
        return level != null && hasDomeAbove(level);
    }

    @Nullable
    private CompoundTag loadedFrogTag() {
        ItemStack frog = inventory.getFrog();
        CustomData data = frog.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /**
     * The loaded frog's Category, or null when it has none (a Midas frog, or an
     * empty slot). On 1.21.1 a netted frog stores its species as a {@code "Category"}
     * string via {@code saveWithoutId} - the pre-FrogKind model.
     */
    @Nullable
    private Category loadedCategory() {
        CompoundTag tag = loadedFrogTag();
        if (tag == null || !tag.contains("Category", Tag.TAG_STRING)) {
            return null;
        }
        try {
            return Category.valueOf(tag.getString("Category"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Whether the loaded frog is a Midas frog (the {@code "Midas"} flag on its saved NBT). */
    private boolean loadedIsMidas() {
        CompoundTag tag = loadedFrogTag();
        return tag != null && tag.getBoolean("Midas");
    }

    private int loadedStat(String key) {
        CompoundTag tag = loadedFrogTag();
        if (tag == null || !tag.contains(key, Tag.TAG_INT)) {
            return FrogStats.STAT_MIN;
        }
        return Math.max(FrogStats.STAT_MIN, tag.getInt(key));
    }

    @Nullable
    private static Category categoryOf(ServerLevel level, ResourceLocation variantId) {
        var registry = level.registryAccess().registry(PFRegistries.SLIME_VARIANT).orElse(null);
        SlimeVariant variant = registry == null ? null : registry.get(variantId);
        return variant == null ? null : variant.category();
    }

    /**
     * Inventory change hook: mark dirty, and when an upgrade slot changes, invalidate the
     * block's capabilities so a Melter insert/remove flips the DOWN fluid handler for pipes.
     */
    private void onSlotChanged(int slot) {
        setChanged();
        Level lvl = getLevel();
        if (slot >= VirtualTerrariumInventory.UPGRADE_START && lvl != null && !lvl.isClientSide()) {
            lvl.invalidateCapabilities(worldPosition);
        }
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    /**
     * Test seam: arm the cycle so the NEXT {@link #serverTick} produces immediately
     * (the real interval is ~200 ticks and would blow a GameTest's tick budget). The
     * tick still gates on {@link #status()} being PRODUCING, so the setup - Dome,
     * frog, matching feedstock, power - must be in place for it to fire.
     */
    @org.jetbrains.annotations.VisibleForTesting
    public void forceReadyToFire() {
        interval = 1;
        progress = 0;
        setChanged();
    }

    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getBlock() instanceof VirtualTerrariumProcessorBlock
            && state.getValue(VirtualTerrariumProcessorBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(VirtualTerrariumProcessorBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    // -- serialization --

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress);
        tag.putInt("Interval", interval);
        tag.putInt("Energy", energy.getEnergyStored());
        CompoundTag invTag = new CompoundTag();
        inventory.serialize(invTag);
        tag.put("Inventory", invTag);
        tag.put("Feedstock", feedstock.writeToNBT(registries, new CompoundTag()));
        tag.put("Molten", moltenTank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = Math.max(0, tag.getInt("Progress"));
        interval = Math.max(1, tag.contains("Interval", Tag.TAG_INT) ? tag.getInt("Interval") : 200);
        energy.load(tag.getInt("Energy"));
        if (tag.contains("Inventory", Tag.TAG_COMPOUND)) {
            inventory.deserialize(tag.getCompound("Inventory"));
        }
        feedstock.readFromNBT(registries, tag.getCompound("Feedstock"));
        moltenTank.readFromNBT(registries, tag.getCompound("Molten"));
    }

    // Client sync: the screen + dome renderer + Jade read everything from the update tag.
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

    // -- MenuProvider --

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.virtual_terrarium");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu(containerId, playerInv, this, dataAccess);
    }
}
