package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu;
import com.flatts.productivefrogs.content.transfer.FluidTankResourceHandler;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * BlockEntity backing the Virtual Terrarium Processor - it virtualizes one frog's
 * eat loop with no spawned entities (see {@code docs/virtual_terrarium.md}).
 *
 * <p>Slice 1: the Resource path. A filled Frog Net in the frog slot plus Slime Milk
 * in the feedstock tank, when the milk's category matches the frog's, produces that
 * variant's Froglights into the output on the milk-economy cadence - Bounty scales
 * the count. Midas/Predator paths, upgrades, the RF buffer, and the Display Dome
 * requirement land in later slices.
 */
public class VirtualTerrariumBlockEntity extends BlockEntity implements MenuProvider {

    /** Feedstock tank capacity (8 buckets). */
    public static final int FEEDSTOCK_CAPACITY = 8_000;
    /** Milk consumed per completed cycle. */
    public static final int FEEDSTOCK_PER_CYCLE = 100;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_INTERVAL = 1;
    public static final int DATA_COUNT = 2;

    private final VirtualTerrariumInventory inventory = new VirtualTerrariumInventory(this::setChanged);

    /** Fill-only Slime Milk intake (slice 1); Mimic Milk / Mob Slurry added later. */
    private final FluidTank feedstock = new FluidTank(FEEDSTOCK_CAPACITY,
        fluid -> fluid.is(PFFluids.SLIME_MILK.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private int progress = 0;
    private int interval = 200;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_INTERVAL -> interval;
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

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    // -- capability adapters (wired in PFModBusEvents) --

    private ResourceHandler<FluidResource> feedstockResourceCached;

    public ResourceHandler<FluidResource> feedstockResource() {
        if (feedstockResourceCached == null) {
            feedstockResourceCached = new FluidTankResourceHandler(
                feedstock, null, true, false, this::setChanged);
        }
        return feedstockResourceCached;
    }

    // -- the eat loop --

    public static void serverTick(Level level, BlockPos pos, BlockState state, VirtualTerrariumBlockEntity be) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        if (!be.productive(serverLevel)) {
            be.resetProgress();
            setWorking(level, pos, state, false);
            return;
        }
        be.progress++;
        be.setChanged();
        if (be.progress >= be.interval) {
            be.produce(serverLevel);
        } else {
            setWorking(level, pos, state, true);
        }
    }

    /** Whether the block can run this tick: loaded Resource frog + matching Slime Milk. */
    private boolean productive(net.minecraft.server.level.ServerLevel level) {
        FrogKind kind = loadedFrogKind();
        if (!(kind instanceof FrogKind.Resource resource)) {
            return false;
        }
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty() || fluid.getAmount() < FEEDSTOCK_PER_CYCLE) {
            return false;
        }
        Identifier variantId = fluid.get(PFDataComponents.SLIME_VARIANT.get());
        if (variantId == null) {
            return false;
        }
        Category milkCategory = categoryOf(level, variantId);
        return milkCategory == resource.category();
    }

    private void produce(net.minecraft.server.level.ServerLevel level) {
        FluidStack fluid = feedstock.getFluid();
        Identifier variantId = fluid.get(PFDataComponents.SLIME_VARIANT.get());
        int bounty = loadedStat("Bounty");
        int perSlime = FrogStats.bountyDropCount(bounty, PFConfig.bountyMaxDrops(), PFConfig.statCap());
        int slimes = MilkSpawnEconomy.batchQuantity(com.flatts.productivefrogs.content.multiblock.MilkCharge.fromFluid(fluid).quantity());
        int total = Math.max(1, perSlime * slimes);

        ItemStack froglight = FrogTongueDropHandler.buildFroglight(variantId, null);
        froglight.setCount(Math.min(total, froglight.getMaxStackSize()));

        // Backpressure: if the output can't take the batch, pause without resetting.
        if (inventory.outputFull(froglight)) {
            return;
        }
        inventory.pushOutput(froglight);
        feedstock.drain(FEEDSTOCK_PER_CYCLE, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        progress = 0;
        interval = MilkSpawnEconomy.intervalTicks(
            com.flatts.productivefrogs.content.multiblock.MilkCharge.fromFluid(fluid).speed(), level.getRandom());
        setChanged();
        BlockState state = level.getBlockState(worldPosition);
        setWorking(level, worldPosition, state, true);
    }

    @org.jetbrains.annotations.Nullable
    private FrogKind loadedFrogKind() {
        ItemStack frog = inventory.getFrog();
        CustomData data = frog.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        return FrogKind.readFromTag(data.copyTag()).orElse(null);
    }

    private int loadedStat(String key) {
        ItemStack frog = inventory.getFrog();
        CustomData data = frog.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        return data == null ? FrogStats.STAT_MIN : Math.max(FrogStats.STAT_MIN, data.copyTag().getIntOr(key, FrogStats.STAT_MIN));
    }

    @org.jetbrains.annotations.Nullable
    private static Category categoryOf(net.minecraft.server.level.ServerLevel level, Identifier variantId) {
        SlimeVariant variant = PFRegistries.variant(level.registryAccess(), variantId);
        return variant == null ? null : variant.category();
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (state.getBlock() instanceof VirtualTerrariumProcessorBlock
            && state.getValue(VirtualTerrariumProcessorBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(VirtualTerrariumProcessorBlock.WORKING, working),
                Block.UPDATE_CLIENTS);
        }
    }

    // -- serialization --

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", progress);
        output.putInt("Interval", interval);
        inventory.serialize(output.child("Inventory"));
        FluidStack fluid = feedstock.getFluid();
        if (!fluid.isEmpty()) {
            output.store("Feedstock", FluidStack.CODEC, fluid);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = Math.max(0, input.getIntOr("Progress", 0));
        interval = Math.max(1, input.getIntOr("Interval", 200));
        input.child("Inventory").ifPresent(inventory::deserialize);
        feedstock.setFluid(input.read("Feedstock", FluidStack.CODEC).orElse(FluidStack.EMPTY));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
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
        return new VirtualTerrariumMenu(containerId, playerInv, this, dataAccess);
    }
}
