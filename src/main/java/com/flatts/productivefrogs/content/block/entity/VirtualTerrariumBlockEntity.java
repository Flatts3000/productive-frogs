package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.fluid.LiquidExperienceFluid;
import com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.content.transfer.FluidTankResourceHandler;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.data.PredatorPrey;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.event.FrogTongueDropHandler;
import com.flatts.productivefrogs.event.MidasTongueDropHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BlockEntity backing the Virtual Terrarium Processor - it virtualizes one frog's
 * eat loop with no spawned entities (see {@code docs/virtual_terrarium.md}).
 *
 * <p>Three production paths, chosen by the loaded frog + its feedstock fluid:
 * <ul>
 *   <li><b>Resource</b> frog + Slime Milk (matching category) -&gt; Froglights.</li>
 *   <li><b>Midas</b> frog + Mimic Milk -&gt; Prismatic Froglights (gated by equivalence).</li>
 *   <li><b>Predator</b> frog + Mob Slurry (valid prey) -&gt; the mob's player-credited loot
 *       (Looting scaled from Bounty) plus the mob's real XP as Liquid Experience.</li>
 * </ul>
 * Upgrades, the RF buffer, and the Display Dome requirement land in later slices.
 */
public class VirtualTerrariumBlockEntity extends BlockEntity implements MenuProvider {

    public static final int FEEDSTOCK_CAPACITY = 8_000;
    public static final int XP_CAPACITY = 16_000;
    public static final int FEEDSTOCK_PER_CYCLE = 100;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_INTERVAL = 1;
    public static final int DATA_COUNT = 2;

    private static final GameProfile VT_PROFILE =
        new GameProfile(UUID.fromString("b0551a15-4a15-4a15-8a15-711711711711"), "[PF Virtual Terrarium]");

    private final VirtualTerrariumInventory inventory = new VirtualTerrariumInventory(this::setChanged);

    /** Fill-only feedstock: Slime Milk, Mimic Milk, or Mob Slurry. */
    private final FluidTank feedstock = new FluidTank(FEEDSTOCK_CAPACITY,
        fluid -> fluid.is(PFFluids.SLIME_MILK.get())
            || fluid.is(PFFluids.MIMIC_MILK.get())
            || fluid.is(PFFluids.MOB_SLURRY.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };

    /** Product tank: Liquid Experience banked from predator kills (extract-only). */
    private final FluidTank xpTank = new FluidTank(XP_CAPACITY,
        fluid -> fluid.is(PFFluids.LIQUID_EXPERIENCE.get())) {
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

    public FluidTank getXpTank() {
        return xpTank;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    // -- capability adapters (wired in PFModBusEvents) --

    private ResourceHandler<FluidResource> feedstockResourceCached;
    private ResourceHandler<FluidResource> productFluidResourceCached;

    /** Non-DOWN faces: fill-only feedstock intake. */
    public ResourceHandler<FluidResource> feedstockResource() {
        if (feedstockResourceCached == null) {
            feedstockResourceCached = new FluidTankResourceHandler(feedstock, null, true, false, this::setChanged);
        }
        return feedstockResourceCached;
    }

    /** DOWN face: extract-only product fluids (Liquid Experience for now; molten later). */
    public ResourceHandler<FluidResource> productFluidResource() {
        if (productFluidResourceCached == null) {
            productFluidResourceCached = new FluidTankResourceHandler(xpTank, null, false, true, this::setChanged);
        }
        return productFluidResourceCached;
    }

    // -- the eat loop --

    public static void serverTick(Level level, BlockPos pos, BlockState state, VirtualTerrariumBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
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

    /** Whether the block can run this tick, per the loaded frog's path. */
    private boolean productive(ServerLevel level) {
        FrogKind kind = loadedFrogKind();
        if (kind == null) {
            return false;
        }
        FluidStack fluid = feedstock.getFluid();
        if (fluid.isEmpty() || fluid.getAmount() < FEEDSTOCK_PER_CYCLE) {
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
        FluidStack fluid = feedstock.getFluid();
        FrogKind kind = loadedFrogKind();
        int bounty = loadedStat("Bounty");
        if (kind instanceof FrogKind.Resource) {
            Identifier variantId = fluid.get(PFDataComponents.SLIME_VARIANT.get());
            finishSingle(level, fluid, FrogTongueDropHandler.buildFroglight(variantId, null), bounty);
        } else if (kind instanceof FrogKind.Midas) {
            Identifier itemId = fluid.get(PFDataComponents.SYNTHESIZED_ITEM.get());
            finishSingle(level, fluid, MidasTongueDropHandler.buildPrismaticFroglight(itemId), bounty);
        } else if (kind instanceof FrogKind.Predator) {
            producePredator(level, fluid, bounty);
        }
    }

    /** Resource / Midas: one Froglight stack, Bounty x Teeming scaled. */
    private void finishSingle(ServerLevel level, FluidStack fluid, ItemStack froglight, int bounty) {
        int perSlime = FrogStats.bountyDropCount(bounty, PFConfig.bountyMaxDrops(), PFConfig.statCap());
        int slimes = MilkSpawnEconomy.batchQuantity(MilkCharge.fromFluid(fluid).quantity());
        froglight.setCount(Math.min(Math.max(1, perSlime * slimes), froglight.getMaxStackSize()));
        if (inventory.outputFull(froglight)) {
            return;
        }
        inventory.pushOutput(froglight);
        consumeAndReschedule(level, fluid);
    }

    /** Predator: an entity-free loot roll + the mob's real XP as Liquid Experience. */
    private void producePredator(ServerLevel level, FluidStack fluid, int bounty) {
        EntityType<?> type = slurriedType(fluid);
        if (type == null) {
            return;
        }
        List<ItemStack> drops = new ArrayList<>();
        int xp = rollMobLoot(level, type, bounty, drops);
        // Backpressure: only produce if every drop has a home (nothing voided).
        if (drops.size() > inventory.emptyOutputSlots()) {
            return;
        }
        for (ItemStack drop : drops) {
            inventory.pushOutput(drop);
        }
        if (xp > 0) {
            xpTank.fill(new FluidStack(PFFluids.LIQUID_EXPERIENCE.get(), LiquidExperienceFluid.pointsToMb(xp)),
                IFluidHandler.FluidAction.EXECUTE);
        }
        consumeAndReschedule(level, fluid);
    }

    /**
     * The boss-altar entity-free kill: a throwaway phantom as loot context, the mob's
     * default loot table rolled with a Looting-N fake-player attack (N from Bounty).
     * Returns the mob's XP reward. Never adds a live entity to the world.
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

            type.getDefaultLootTable().ifPresent(tableKey -> {
                ResourceKey<LootTable> key = tableKey;
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
        feedstock.drain(FEEDSTOCK_PER_CYCLE, IFluidHandler.FluidAction.EXECUTE);
        progress = 0;
        interval = MilkSpawnEconomy.intervalTicks(MilkCharge.fromFluid(fluid).speed(), level.getRandom());
        setChanged();
        setWorking(level, worldPosition, level.getBlockState(worldPosition), true);
    }

    // -- lookups --

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

    // The mob's base XP reward. Mob#xpReward is set at construction (before death
    // modifiers); read it off the phantom so a virtual kill pays the mob's real XP.
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
        inventory.serialize(output.child("Inventory"));
        if (!feedstock.getFluid().isEmpty()) {
            output.store("Feedstock", FluidStack.CODEC, feedstock.getFluid());
        }
        if (!xpTank.getFluid().isEmpty()) {
            output.store("Xp", FluidStack.CODEC, xpTank.getFluid());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        progress = Math.max(0, input.getIntOr("Progress", 0));
        interval = Math.max(1, input.getIntOr("Interval", 200));
        input.child("Inventory").ifPresent(inventory::deserialize);
        feedstock.setFluid(input.read("Feedstock", FluidStack.CODEC).orElse(FluidStack.EMPTY));
        xpTank.setFluid(input.read("Xp", FluidStack.CODEC).orElse(FluidStack.EMPTY));
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
