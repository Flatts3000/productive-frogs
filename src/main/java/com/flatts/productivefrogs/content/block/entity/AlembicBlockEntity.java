package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.MimicMilkBucketItem;
import com.flatts.productivefrogs.content.item.MimicSlimeBucketItem;
import com.flatts.productivefrogs.content.item.SlimeBucketItem;
import com.flatts.productivefrogs.content.menu.AlembicMenu;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFItemTags;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for the Alembic - the front of the Equivalence lane (#253). Takes
 * an empty bucket + an OFF-ROSTER item + RF and outputs a Mimic Slime Bucket
 * carrying that item (the player supplies the bucket, so buckets are conserved
 * through the lane).
 *
 * <p>RF-powered like the {@link DistillerBlockEntity} (same receive-only buffer
 * pattern). The synthesizability filter ({@link #canSynthesize}) is the dupe-safety
 * gate: allow-by-default, with a pack-overridable deny tag plus hard exclusions
 * (container-bearing items, our own pipeline items, anything already on a slime
 * variant lane). Consumes input + RF transactionally on the completing tick.
 */
public class AlembicBlockEntity extends BlockEntity implements MenuProvider {

    public static final int BUCKET_SLOT = 0;
    public static final int ITEM_SLOT = 1;
    public static final int OUTPUT_SLOT = 2;

    public static final int SYNTH_TIME = 100;
    public static final int ENERGY_CAPACITY = 200_000;
    public static final int ENERGY_MAX_RECEIVE = 5_000;
    public static final int RF_PER_TICK = 400;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_ENERGY_CAP = 3;
    public static final int DATA_COUNT = 4;

    private int progress = 0;

    private final ItemStackHandler items = new ItemStackHandler(3) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case BUCKET_SLOT -> stack.is(Items.BUCKET);
                // Lenient at the slot (no level here for the registry gate); the
                // authoritative synthesizability check runs in serverTick.
                case ITEM_SLOT -> !stack.is(Items.BUCKET);
                // OUTPUT_SLOT accepts ONLY the synthesized result type, so the
                // serverTick's internal insertItem succeeds while a mod holding the
                // raw handler can't shove an arbitrary item into the output (the
                // menu's mayPlace + the output-only capability view already block
                // GUI / hopper access; this hardens the raw-handler path too).
                default -> stack.getItem() instanceof MimicSlimeBucketItem;
            };
        }
    };

    private final IItemHandler inputView = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            // inputView exposes only slots 0..1, so OUTPUT_SLOT (2) is unreachable here.
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.isItemValid(slot, stack);
        }
    };

    private final IItemHandler outputView = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(OUTPUT_SLOT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return items.extractItem(OUTPUT_SLOT, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(OUTPUT_SLOT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    private final class ReceiveOnlyEnergy extends EnergyStorage
            implements com.flatts.productivefrogs.content.transfer.ReceiveOnlyEnergyHandler.Source {
        ReceiveOnlyEnergy() {
            super(ENERGY_CAPACITY, ENERGY_MAX_RECEIVE, 0);
        }

        // 26.1 EnergyHandler journal window (see ReceiveOnlyEnergyHandler): the
        // adapter reads + writes the stored energy directly to support
        // read-your-writes plus abort-rollback, which the receive-only public
        // API cannot do. setEnergy fires no callback; the commit one does.
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

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> SYNTH_TIME;
                case DATA_ENERGY -> energy.getEnergyStored();
                case DATA_ENERGY_CAP -> energy.getMaxEnergyStored();
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

    public AlembicBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.ALEMBIC.get(), pos, state);
    }

    /**
     * The dupe-safety filter. Allow-by-default (#253 decision): an off-roster item
     * is synthesizable unless it is denied. Fail-closed on the registry gate.
     */
    public static boolean canSynthesize(@Nullable Level level, ItemStack stack) {
        if (stack.isEmpty() || stack.is(Items.BUCKET)) {
            return false;
        }
        // Pack-overridable deny tag (boss-tier items, structure/command blocks, etc.).
        if (stack.is(PFItemTags.ALEMBIC_DENIED)) {
            return false;
        }
        // Container-bearing items would launder their contents (the ProjectE
        // shulker/bundle dupe); refuse them outright.
        if (stack.has(DataComponents.CONTAINER)
                || stack.has(DataComponents.BUNDLE_CONTENTS)
                || stack.has(DataComponents.CHARGED_PROJECTILES)) {
            return false;
        }
        // Type-only lane: refuse any item carrying non-default component state - a
        // Patchouli guide's book id, potion contents, enchantments, written-book
        // pages, a custom name, damage, etc. The lane strips components, so such an
        // item would come out meaningless or invalid (a guide book with no book id
        // reads "Invalid book: no ID defined"). Plain resources have an empty patch.
        // This also subsumes the container checks above and blocks component
        // laundering generally. (#253)
        if (!stack.getComponentsPatch().isEmpty()) {
            return false;
        }
        // Our own pipeline items - no recursion / self-dupe. The instanceof checks
        // catch every per-variant milk + slime bucket in one shot.
        Item item = stack.getItem();
        if (item == PFItems.CONFIGURABLE_FROGLIGHT.get()
                || item instanceof MimicSlimeBucketItem
                || item instanceof MimicMilkBucketItem
                || item instanceof SlimeBucketItem
                || item instanceof com.flatts.productivefrogs.content.item.SlimeMilkBucketItem
                || item instanceof com.flatts.productivefrogs.content.item.ResourceTadpoleBucketItem) {
            return false;
        }
        // Roster gate: anything that already primes a slime variant (including the
        // weight-0 boss primers) has its own authored lane - refuse it here.
        if (level != null) {
            if (SlimeVariant.findByPrimer(PFRegistries.variants(level.registryAccess()), stack) != null) {
                return false;
            }
        }
        return true;
    }

    public ItemStackHandler items() {
        return items;
    }

    public IItemHandler inputView() {
        return inputView;
    }

    public IItemHandler outputView() {
        return outputView;
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} input view: insert-only over the bucket + item slots (per-slot validity routes each). */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> inputResource() {
        return new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(
            items, new int[] {BUCKET_SLOT, ITEM_SLOT}, true, false);
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} output view: extract-only over the Mimic Slime Bucket slot. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> outputResource() {
        return new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(items, new int[] {OUTPUT_SLOT}, false, true);
    }

    public EnergyStorage energyStorage() {
        return energy;
    }

    /** The 26.1 {@code Capabilities.Energy.BLOCK} view (receive-only); wraps {@link #energy}. */
    public net.neoforged.neoforge.transfer.energy.EnergyHandler energyHandler() {
        return new com.flatts.productivefrogs.content.transfer.ReceiveOnlyEnergyHandler(energy);
    }

    public int progress() {
        return progress;
    }

    // 26.1 port: the held stacks drop here (the BE still exists on the removal path), NOT in the
    // block's affectNeighborsAfterRemoval, which runs after the BE is gone. The handler is not a
    // vanilla Container, so the super default won't drop it - re-home the slot loop here.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            for (int slot = 0; slot < items.getSlots(); slot++) {
                ItemStack held = items.getStackInSlot(slot);
                if (!held.isEmpty()) {
                    Block.popResource(serverLevel, pos, held);
                }
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlembicBlockEntity be) {
        // Whole-lane gate (#253): an Alembic is inert when the EE lane is disabled.
        if (!com.flatts.productivefrogs.PFConfig.equivalenceEnabled()) {
            be.resetProgress();
            return;
        }
        ItemStack bucket = be.items.getStackInSlot(BUCKET_SLOT);
        ItemStack input = be.items.getStackInSlot(ITEM_SLOT);
        if (!bucket.is(Items.BUCKET) || !canSynthesize(level, input)) {
            be.resetProgress();
            return;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(input.getItem());
        ItemStack result = MimicSlimeBucketItem.forItem(itemId);
        if (!be.items.insertItem(OUTPUT_SLOT, result.copy(), true).isEmpty()) {
            return; // output blocked - hold progress.
        }
        if (be.energy.getEnergyStored() < RF_PER_TICK) {
            return; // no power - pause without losing progress.
        }
        be.energy.consume(RF_PER_TICK);
        be.progress++;
        be.setChanged();
        if (be.progress >= SYNTH_TIME) {
            // Airtight transaction: insert the output FIRST and only consume the
            // inputs if it fully landed. (The simulate above already guarantees this
            // within a single-threaded tick; doing the real insert first removes any
            // dependence on that and the silent-leftover-drop edge.)
            if (!be.items.insertItem(OUTPUT_SLOT, result.copy(), false).isEmpty()) {
                return;
            }
            be.items.extractItem(BUCKET_SLOT, 1, false);
            be.items.extractItem(ITEM_SLOT, 1, false);
            be.progress = 0;
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.5F, 0.8F);
            PFDebug.log(PFDebug.Area.ALEMBIC, () -> String.format("alembic @%s synthesized %s", pos, itemId));
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        items.serialize(output.child("Items"));
        output.putInt("Energy", energy.getEnergyStored());
        output.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Items").ifPresent(items::deserialize);
        energy.load(input.getIntOr("Energy", 0));
        progress = Math.max(0, Math.min(input.getIntOr("Progress", 0), SYNTH_TIME));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.alembic");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new AlembicMenu(containerId, playerInv, this, dataAccess);
    }
}
