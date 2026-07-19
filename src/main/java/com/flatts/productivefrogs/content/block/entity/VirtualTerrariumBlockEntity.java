package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.fluid.LiquidExperienceFluid;
import com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.content.recipe.CrucibleMeltRecipe;
import com.flatts.productivefrogs.content.transfer.FluidTankResourceHandler;
import com.flatts.productivefrogs.content.transfer.ReceiveOnlyEnergyHandler;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.data.PredatorPrey;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.event.MidasTongueDropHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * BlockEntity backing the Virtual Terrarium Processor - it virtualizes one frog's
 * eat loop with no spawned entities (see {@code docs/virtual_terrarium.md}).
 *
 * <p>Three eat paths (Resource / Midas / Predator), tuned by the frog's stats, the
 * feedstock's catalysts, and the installed upgrades. The core loop needs no power;
 * the Smelter / Melter / Overclock upgrades draw RF from a receive-only buffer and
 * HARD STALL when it can't pay.
 */
public class VirtualTerrariumBlockEntity extends BlockEntity implements MenuProvider {

    // Exactly one bucket-charge of feedstock (R-1 component-carrying milk): a hand
    // bucket or a pump fills it to 1000 mB and no further; it drains as the frog eats.
    public static final int FEEDSTOCK_CAPACITY = 1_000;
    public static final int XP_CAPACITY = 16_000;
    public static final int MOLTEN_CAPACITY = 8_000;
    public static final int FEEDSTOCK_PER_CYCLE = 100;

    public static final int ENERGY_CAPACITY = 100_000;
    public static final int ENERGY_MAX_RECEIVE = 2_000;
    // Only the Overclock upgrade draws RF (the Smelter and Melter run for free).
    private static final int OVERCLOCK_RF_PER_CYCLE = 400;
    private static final int MAX_OVERCLOCK = 3;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_INTERVAL = 1;
    public static final int DATA_FEEDSTOCK = 2;
    public static final int DATA_PRODUCT = 3;
    // Energy (0..100k) overflows a 16-bit ContainerData slot, so it rides two shorts.
    public static final int DATA_ENERGY_LO = 4;
    public static final int DATA_ENERGY_HI = 5;
    public static final int DATA_STATUS = 6;   // Status.ordinal() - drives the GUI idle/error line
    public static final int DATA_COUNT = 7;

    private static final GameProfile VT_PROFILE =
        new GameProfile(UUID.fromString("b0551a15-4a15-4a15-8a15-711711711711"), "[PF Virtual Terrarium]");

    private final VirtualTerrariumInventory inventory = new VirtualTerrariumInventory(this::onSlotChanged);

    private final FluidTank feedstock = new FluidTank(FEEDSTOCK_CAPACITY,
        fluid -> fluid.is(PFFluids.SLIME_MILK.get())
            || fluid.is(PFFluids.MIMIC_MILK.get())
            || fluid.is(PFFluids.MOB_SLURRY.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    private final FluidTank xpTank = new FluidTank(XP_CAPACITY,
        fluid -> fluid.is(PFFluids.LIQUID_EXPERIENCE.get())) {
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

    /** Receive-only RF buffer (cables fill; the powered upgrades spend internally). */
    private final class ReceiveOnlyEnergy extends EnergyStorage implements ReceiveOnlyEnergyHandler.Source {
        ReceiveOnlyEnergy() {
            super(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
        }

        @Override
        public int currentEnergy() {
            return this.energy;
        }

        @Override
        public void setEnergy(int amount) {
            this.energy = Math.max(0, Math.min(this.capacity, amount));
        }

        @Override
        public int energyCapacity() {
            return this.capacity;
        }

        @Override
        public int maxInsertPerOp() {
            return this.maxReceive;
        }

        @Override
        public void onEnergyCommitted() {
            setChanged();
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

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_INTERVAL -> interval;
                case DATA_FEEDSTOCK -> feedstock.getFluidAmount();
                case DATA_PRODUCT -> activeProductTank().getFluidAmount();
                case DATA_ENERGY_LO -> energy.getEnergyStored() & 0xFFFF;
                case DATA_ENERGY_HI -> (energy.getEnergyStored() >> 16) & 0xFFFF;
                case DATA_STATUS -> status().ordinal();
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

    public FluidTank getXpTank() {
        return xpTank;
    }

    public FluidTank getMoltenTank() {
        return moltenTank;
    }

    /** The product tank currently in play: molten while a Melter is installed, else Liquid Experience. */
    public FluidTank activeProductTank() {
        return inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get()) ? moltenTank : xpTank;
    }

    /** Capacity of the active product tank (for the GUI gauge). */
    public int productCapacity() {
        return inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get()) ? MOLTEN_CAPACITY : XP_CAPACITY;
    }

    /** True when the active product is molten metal (Melter installed) rather than Liquid Experience. */
    public boolean productIsMolten() {
        return inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get());
    }

    /** True for a filled feedstock bucket (Slime Milk / Mimic Milk / Mob Slurry). */
    public static boolean isFeedstockBucket(ItemStack stack) {
        return stack.getItem() instanceof com.flatts.productivefrogs.content.item.SlimeMilkBucketItem
            || stack.getItem() instanceof com.flatts.productivefrogs.content.item.MimicMilkBucketItem
            || stack.getItem() instanceof com.flatts.productivefrogs.content.item.MobSlurryBucketItem;
    }

    /**
     * Fill the EMPTY feedstock tank from a filled feedstock bucket - exactly one
     * 1000 mB charge, preserving the milk's variant + catalyst components. Returns
     * the empty bucket to hand back, or {@link ItemStack#EMPTY} if not accepted (not
     * a feedstock bucket, or the tank already holds a charge). Never consumes more
     * or less than one whole bucket.
     */
    public ItemStack fillFromBucket(ItemStack bucket) {
        if (!feedstock.getFluid().isEmpty()) {
            return ItemStack.EMPTY; // capacity is exactly one bucket; already charged
        }
        FluidStack fluid = fluidFromFeedstockBucket(bucket);
        if (fluid.isEmpty() || feedstock.fill(fluid, IFluidHandler.FluidAction.SIMULATE) < FEEDSTOCK_CAPACITY) {
            return ItemStack.EMPTY;
        }
        feedstock.fill(fluid, IFluidHandler.FluidAction.EXECUTE);
        setChanged();
        syncToClient();
        return new ItemStack(net.minecraft.world.item.Items.BUCKET);
    }

    /** Build the 1000 mB feedstock FluidStack a bucket represents, copying its identity + catalyst components. */
    private static FluidStack fluidFromFeedstockBucket(ItemStack bucket) {
        net.minecraft.world.level.material.Fluid fluid;
        if (bucket.getItem() instanceof com.flatts.productivefrogs.content.item.SlimeMilkBucketItem) {
            fluid = PFFluids.SLIME_MILK.get();
        } else if (bucket.getItem() instanceof com.flatts.productivefrogs.content.item.MimicMilkBucketItem) {
            fluid = PFFluids.MIMIC_MILK.get();
        } else if (bucket.getItem() instanceof com.flatts.productivefrogs.content.item.MobSlurryBucketItem) {
            fluid = PFFluids.MOB_SLURRY.get();
        } else {
            return FluidStack.EMPTY;
        }
        FluidStack fs = new FluidStack(fluid, FEEDSTOCK_CAPACITY);
        copyComponent(bucket, fs, PFDataComponents.SLIME_VARIANT.get());
        copyComponent(bucket, fs, PFDataComponents.SYNTHESIZED_ITEM.get());
        copyComponent(bucket, fs, PFDataComponents.SLURRIED_ENTITY.get());
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

    /** Push the current BE state to watching clients (feedstock / product fluid identity for the GUI). */
    private void syncToClient() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The receive-only RF buffer (GameTest / Jade read access; cables fill it via the capability). */
    public EnergyStorage energyStorage() {
        return energy;
    }

    /** Jade status: why the block is or isn't producing (the idle-reason readout). */
    public enum Status { NO_DOME, NO_FROG, NO_FEEDSTOCK, MISMATCH, NEEDS_POWER, PRODUCING }

    /**
     * Current status for Jade, computed server-side from the BE's level. Priority
     * mirrors {@link #productive}: no Dome, then no frog, then no/insufficient
     * feedstock, then a kind/feedstock mismatch, then an RF stall.
     */
    public Status status() {
        if (!(level instanceof ServerLevel sl)) {
            return Status.PRODUCING; // client fallback; the real value arrives via server data
        }
        if (!hasDomeAbove(sl)) {
            return Status.NO_DOME;
        }
        if (loadedFrogKind() == null) {
            return Status.NO_FROG;
        }
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty() || fluid.getAmount() < feedstockPerCycle(fluid)) {
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

    /** True when a powered upgrade is installed but the RF buffer can't pay this cycle. */
    private boolean powerStalled() {
        int cost = rfCostPerCycle();
        return cost > 0 && energy.getEnergyStored() < cost;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    // -- capability adapters (wired in PFModBusEvents) --

    private ResourceHandler<FluidResource> feedstockResourceCached;
    private ResourceHandler<FluidResource> xpResourceCached;
    private ResourceHandler<FluidResource> moltenResourceCached;
    private EnergyHandler energyHandlerCached;

    public ResourceHandler<FluidResource> feedstockResource() {
        if (feedstockResourceCached == null) {
            feedstockResourceCached = new FluidTankResourceHandler(feedstock, null, true, false, this::setChanged);
        }
        return feedstockResourceCached;
    }

    /** DOWN-face fluid output: molten while a Melter is installed, else Liquid Experience. */
    public ResourceHandler<FluidResource> productFluidResource() {
        if (inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get())) {
            if (moltenResourceCached == null) {
                moltenResourceCached = new FluidTankResourceHandler(moltenTank, null, false, true, this::setChanged);
            }
            return moltenResourceCached;
        }
        if (xpResourceCached == null) {
            xpResourceCached = new FluidTankResourceHandler(xpTank, null, false, true, this::setChanged);
        }
        return xpResourceCached;
    }

    public EnergyHandler energyHandler() {
        if (energyHandlerCached == null) {
            energyHandlerCached = new ReceiveOnlyEnergyHandler(energy);
        }
        return energyHandlerCached;
    }

    // -- the eat loop --

    public static void serverTick(Level level, BlockPos pos, BlockState state, VirtualTerrariumBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Not runnable (no dome / frog / feedstock / mismatch) OR the Overclock can't be
        // paid: hold at zero progress rather than filling the bar and freezing at full.
        if (!be.productive(serverLevel) || be.powerStalled()) {
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

    /** Whether the block can run this tick: a Dome above, and a loaded frog with matching feedstock. */
    private boolean productive(ServerLevel level) {
        if (!hasDomeAbove(level)) {
            return false;
        }
        FrogKind kind = loadedFrogKind();
        if (kind == null) {
            return false;
        }
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty() || fluid.getAmount() < feedstockPerCycle(fluid)) {
            return false;
        }
        if (kind instanceof FrogKind.Resource resource) {
            if (!fluid.is(PFFluids.SLIME_MILK.get())) {
                return false;
            }
            Identifier variantId = fluid.get(PFDataComponents.SLIME_VARIANT.get());
            return variantId != null && categoryOf(level, variantId) == resource.category();
        }
        if (kind instanceof FrogKind.Midas) {
            return PFConfig.equivalenceEnabled()
                && fluid.is(PFFluids.MIMIC_MILK.get())
                && fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get()) != null;
        }
        if (kind instanceof FrogKind.Predator predator) {
            if (!PFConfig.predatorsEnabled() || !fluid.is(PFFluids.MOB_SLURRY.get())) {
                return false;
            }
            EntityType<?> type = slurriedType(fluid);
            if (type == null) {
                return false;
            }
            HolderLookup.RegistryLookup<PredatorPrey> registry = PFRegistries.predatorPrey(level.registryAccess());
            return PredatorPrey.predatorFor(registry, type) == predator;
        }
        return false;
    }

    private void produce(ServerLevel level) {
        FrogKind kind = loadedFrogKind();
        int rfCost = rfCostPerCycle();
        if (rfCost > 0 && energy.getEnergyStored() < rfCost) {
            return; // hard stall: the Overclock upgrade can't be paid
        }
        FluidStack fluid = feedstock.getFluid();
        boolean produced;
        if (kind instanceof FrogKind.Resource) {
            Identifier variantId = fluid.get(PFDataComponents.SLIME_VARIANT.get());
            produced = emitFroglight(level, FrogTongueDropHandler.buildFroglight(variantId, null), fluid);
        } else if (kind instanceof FrogKind.Midas) {
            Identifier itemId = fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            produced = emitFroglight(level, MidasTongueDropHandler.buildPrismaticFroglight(itemId), fluid);
        } else if (kind instanceof FrogKind.Predator) {
            produced = emitPredator(level, fluid);
        } else {
            produced = false;
        }
        if (produced) {
            if (rfCost > 0) {
                energy.consume(rfCost);
            }
            consumeAndReschedule(level, fluid);
        }
    }

    /** Resource / Midas: one Froglight batch, optionally smelted or melted. Returns true if produced. */
    private boolean emitFroglight(ServerLevel level, ItemStack froglight, FluidStack fluid) {
        int perSlime = FrogStats.bountyDropCount(effectiveBounty(), PFConfig.bountyMaxDrops(), PFConfig.statCap());
        int total = Math.max(1, perSlime * MilkSpawnEconomy.batchQuantity(MilkCharge.fromFluid(fluid).quantity()));
        ItemStack single = froglight.copyWithCount(1);

        if (inventory.hasUpgrade(PFItems.VT_UPGRADE_MELTER.get())) {
            FluidStack molten = meltOne(level, single);
            if (!molten.isEmpty()) {
                FluidStack batch = molten.copyWithAmount(molten.getAmount() * total);
                int accepted = moltenTank.fill(batch, IFluidHandler.FluidAction.SIMULATE);
                if (accepted <= 0) {
                    return false; // molten tank full or holds a different fluid: pause
                }
                // Fill what fits - a nearly-full tank produces a partial batch rather than
                // stalling forever when one cycle's molten exceeds the tank capacity.
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
        // Distribute the batch across the output slots (up to their combined capacity).
        int count = Math.min(unit.getCount() * total, inventory.outputCapacity(unit));
        if (count <= 0) {
            return false;
        }
        inventory.pushOutput(unit.copyWithCount(count));
        return true;
    }

    /** Predator: the entity-free loot roll + the mob's real XP as Liquid Experience. */
    private boolean emitPredator(ServerLevel level, FluidStack fluid) {
        EntityType<?> type = slurriedType(fluid);
        if (type == null) {
            return false;
        }
        List<ItemStack> drops = new ArrayList<>();
        int xp = rollMobLoot(level, type, effectiveBounty(), drops);
        if (inventory.hasUpgrade(PFItems.VT_UPGRADE_SMELTER.get())) {
            drops = smeltDrops(level, drops);
        }
        if (drops.size() > inventory.emptyOutputSlots()) {
            return false; // nothing voided
        }
        for (ItemStack drop : drops) {
            inventory.pushOutput(drop);
        }
        if (xp > 0) {
            xpTank.fill(new FluidStack(PFFluids.LIQUID_EXPERIENCE.get(), LiquidExperienceFluid.pointsToMb(xp)),
                IFluidHandler.FluidAction.EXECUTE);
        }
        return true;
    }

    private List<ItemStack> smeltDrops(ServerLevel level, List<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>(drops.size());
        for (ItemStack drop : drops) {
            ItemStack smelted = smeltOne(level, drop.copyWithCount(1));
            if (!smelted.isEmpty()) {
                out.add(smelted.copyWithCount(Math.min(smelted.getCount() * drop.getCount(), smelted.getMaxStackSize())));
            } else {
                out.add(drop);
            }
        }
        return out;
    }

    /** The smelted result for a single input, or EMPTY when there is no smelting recipe. */
    private static ItemStack smeltOne(ServerLevel level, ItemStack single) {
        if (!(level.recipeAccess() instanceof RecipeManager manager)) {
            return ItemStack.EMPTY;
        }
        SingleRecipeInput input = new SingleRecipeInput(single);
        Optional<RecipeHolder<SmeltingRecipe>> match = manager.getRecipeFor(RecipeType.SMELTING, input, level);
        return match.map(holder -> holder.value().assemble(input)).orElse(ItemStack.EMPTY);
    }

    /** The molten fluid for a single Froglight input, or EMPTY when there is no melt recipe. */
    private static FluidStack meltOne(ServerLevel level, ItemStack single) {
        if (!(level.recipeAccess() instanceof RecipeManager manager)) {
            return FluidStack.EMPTY;
        }
        Optional<RecipeHolder<CrucibleMeltRecipe>> match =
            manager.getRecipeFor(PFRecipeTypes.CRUCIBLE_MELTING.get(), new SingleRecipeInput(single), level);
        return match.map(holder -> holder.value().result()).orElse(FluidStack.EMPTY);
    }

    /**
     * The boss-altar entity-free kill: a throwaway phantom as loot context, the mob's
     * default loot table rolled with a Looting-N fake-player attack. Returns the mob's XP.
     */
    private int rollMobLoot(ServerLevel level, EntityType<?> type, int bounty, List<ItemStack> out) {
        Entity phantom = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (phantom == null) {
            return 0;
        }
        try {
            phantom.snapTo(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, 0.0F, 0.0F);
            FakePlayer killer = FakePlayerFactory.get(level, VT_PROFILE);
            killer.setPos(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5);
            int looting = FrogStats.bountyLootingLevel(bounty, PFConfig.statCap());
            ItemStack sword = new ItemStack(Items.NETHERITE_SWORD);
            if (looting > 0) {
                Holder<Enchantment> loot = level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LOOTING);
                sword.enchant(loot, looting);
            }
            killer.setItemInHand(InteractionHand.MAIN_HAND, sword);

            type.getDefaultLootTable().ifPresent(key -> {
                LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, phantom)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(worldPosition))
                    .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                    .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                    .create(LootContextParamSets.ENTITY);
                LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
                table.getRandomItems(params, level.getRandom().nextLong(), out::add);
            });
            killer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            return mobXp(phantom);
        } finally {
            phantom.discard();
        }
    }

    private void consumeAndReschedule(ServerLevel level, FluidStack fluid) {
        int perCycle = feedstockPerCycle(fluid);
        if (perCycle > 0) {
            feedstock.drain(perCycle, IFluidHandler.FluidAction.EXECUTE);
        }
        progress = 0;
        interval = computeInterval(level, fluid);
        setChanged();
        setWorking(level, worldPosition, level.getBlockState(worldPosition), true);
    }

    // -- upgrade-tuned figures --

    private int effectiveBounty() {
        return Math.min(PFConfig.statCap(),
            loadedStat("Bounty") + 2 * inventory.countUpgrade(PFItems.VT_UPGRADE_BOUNTY.get()));
    }

    private int effectiveAppetite() {
        return Math.min(PFConfig.statCap(),
            loadedStat("Appetite") + 2 * inventory.countUpgrade(PFItems.VT_UPGRADE_APPETITE.get()));
    }

    /**
     * Millibuckets of feedstock drained per eat cycle. Longevity is governed by the
     * feedstock's OWN catalysts, exactly like a placed Slime Milk source - not by any
     * dedicated upgrade: the INFINITE catalyst ({@link MilkCharge#infinite}) never
     * depletes, and the COUNT catalyst (a {@code capacity} above the default budget)
     * stretches the tank proportionally. Mob Slurry / Mimic Milk carry no catalysts,
     * so they read the default drain.
     */
    private int feedstockPerCycle(FluidStack fluid) {
        MilkCharge charge = MilkCharge.fromFluid(fluid);
        if (charge.infinite()) {
            return 0;
        }
        int base = MilkSpawnEconomy.defaultSpawnCount();
        double factor = base / (double) Math.max(base, charge.capacity());
        return Math.max(25, (int) Math.round(FEEDSTOCK_PER_CYCLE * factor));
    }

    /** RF drawn per cycle - only the Overclock upgrade costs power (Smelter / Melter are free). */
    private int rfCostPerCycle() {
        return Math.min(MAX_OVERCLOCK, inventory.countUpgrade(PFItems.VT_UPGRADE_OVERCLOCK.get())) * OVERCLOCK_RF_PER_CYCLE;
    }

    private int computeInterval(ServerLevel level, FluidStack fluid) {
        int base = MilkSpawnEconomy.intervalTicks(MilkCharge.fromFluid(fluid).speed(), level.getRandom());
        int span = Math.max(1, PFConfig.statCap() - FrogStats.STAT_MIN);
        double appetiteFactor = 1.0 - 0.5 * Math.max(0.0, Math.min(1.0,
            (effectiveAppetite() - FrogStats.STAT_MIN) / (double) span));
        double scaled = base * appetiteFactor
            * Math.pow(0.5, Math.min(MAX_OVERCLOCK, inventory.countUpgrade(PFItems.VT_UPGRADE_OVERCLOCK.get())));
        return Math.max(1, (int) Math.round(scaled));
    }

    // -- lookups --

    private boolean hasDomeAbove(Level level) {
        return level.getBlockState(worldPosition.above())
            .is(com.flatts.productivefrogs.registry.PFBlocks.VIRTUAL_TERRARIUM_DOME.get());
    }

    @org.jetbrains.annotations.Nullable
    private FrogKind loadedFrogKind() {
        ItemStack frog = inventory.getFrog();
        CustomData data = frog.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : FrogKind.readFromTag(data.copyTag()).orElse(null);
    }

    private int loadedStat(String key) {
        ItemStack frog = inventory.getFrog();
        CustomData data = frog.get(DataComponents.CUSTOM_DATA);
        return data == null ? FrogStats.STAT_MIN
            : Math.max(FrogStats.STAT_MIN, data.copyTag().getIntOr(key, FrogStats.STAT_MIN));
    }

    @org.jetbrains.annotations.Nullable
    private static EntityType<?> slurriedType(FluidStack fluid) {
        Identifier mobId = fluid.get(PFDataComponents.SLURRIED_ENTITY.get());
        return mobId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(mobId).orElse(null);
    }

    @org.jetbrains.annotations.Nullable
    private static Category categoryOf(ServerLevel level, Identifier variantId) {
        SlimeVariant variant = PFRegistries.variant(level.registryAccess(), variantId);
        return variant == null ? null : variant.category();
    }

    private static final java.lang.reflect.Field XP_REWARD_FIELD = resolveXpField();

    private static java.lang.reflect.Field resolveXpField() {
        try {
            java.lang.reflect.Field field = net.minecraft.world.entity.Mob.class.getDeclaredField("xpReward");
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private static int mobXp(Entity phantom) {
        if (phantom instanceof net.minecraft.world.entity.Mob && XP_REWARD_FIELD != null) {
            try {
                return Math.max(0, XP_REWARD_FIELD.getInt(phantom));
            } catch (IllegalAccessException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** Inventory change hook: mark dirty, and when an upgrade slot changes, invalidate the
     *  block's capabilities so a Melter insert/remove flips the DOWN fluid handler for pipes. */
    private void onSlotChanged(int slot) {
        setChanged();
        Level lvl = getLevel();
        if (slot >= VirtualTerrariumInventory.UPGRADE_START && lvl != null && !lvl.isClientSide()) {
            lvl.invalidateCapabilities(worldPosition);
        }
    }

    /** Refund the feedstock and banked Liquid Experience as buckets on break (nothing voided).
     *  Molten metals are bucket-less by design, so the transient molten tank is not refunded. */
    public void dropFluids(Level lvl, BlockPos pos) {
        dropFluidAsBuckets(lvl, pos, feedstock.getFluid());
        dropFluidAsBuckets(lvl, pos, xpTank.getFluid());
    }

    private static void dropFluidAsBuckets(Level lvl, BlockPos pos, FluidStack fluid) {
        ItemStack bucket = bucketFor(fluid);
        if (bucket.isEmpty()) {
            return;
        }
        int buckets = fluid.getAmount() / net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME;
        for (int i = 0; i < buckets; i++) {
            net.minecraft.world.Containers.dropItemStack(lvl, pos.getX(), pos.getY(), pos.getZ(), bucket.copy());
        }
    }

    private static ItemStack bucketFor(FluidStack fluid) {
        if (fluid.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (fluid.is(PFFluids.SLIME_MILK.get())) {
            Identifier v = fluid.get(PFDataComponents.SLIME_VARIANT.get());
            return v == null ? ItemStack.EMPTY
                : com.flatts.productivefrogs.content.item.SlimeMilkBucketItem.forVariant(v);
        }
        if (fluid.is(PFFluids.MIMIC_MILK.get())) {
            Identifier itm = fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            return itm == null ? ItemStack.EMPTY
                : com.flatts.productivefrogs.content.item.MimicMilkBucketItem.forItem(itm);
        }
        if (fluid.is(PFFluids.MOB_SLURRY.get())) {
            Identifier mob = fluid.get(PFDataComponents.SLURRIED_ENTITY.get());
            return mob == null ? ItemStack.EMPTY
                : com.flatts.productivefrogs.content.item.MobSlurryBucketItem.forEntity(mob);
        }
        if (fluid.is(PFFluids.LIQUID_EXPERIENCE.get())) {
            return new ItemStack(PFItems.LIQUID_EXPERIENCE_BUCKET.get());
        }
        return ItemStack.EMPTY;
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
            level.setBlock(pos, state.setValue(VirtualTerrariumProcessorBlock.WORKING, working), Block.UPDATE_CLIENTS);
        }
    }

    // -- serialization --

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", progress);
        output.putInt("Interval", interval);
        output.putInt("Energy", energy.getEnergyStored());
        inventory.serialize(output.child("Inventory"));
        if (!feedstock.getFluid().isEmpty()) {
            output.store("Feedstock", FluidStack.CODEC, feedstock.getFluid());
        }
        if (!xpTank.getFluid().isEmpty()) {
            output.store("Xp", FluidStack.CODEC, xpTank.getFluid());
        }
        if (!moltenTank.getFluid().isEmpty()) {
            output.store("Molten", FluidStack.CODEC, moltenTank.getFluid());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = Math.max(0, input.getIntOr("Progress", 0));
        interval = Math.max(1, input.getIntOr("Interval", 200));
        energy.load(input.getIntOr("Energy", 0));
        input.child("Inventory").ifPresent(inventory::deserialize);
        feedstock.setFluid(input.read("Feedstock", FluidStack.CODEC).orElse(FluidStack.EMPTY));
        xpTank.setFluid(input.read("Xp", FluidStack.CODEC).orElse(FluidStack.EMPTY));
        moltenTank.setFluid(input.read("Molten", FluidStack.CODEC).orElse(FluidStack.EMPTY));
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
