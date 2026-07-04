package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.menu.DistillerMenu;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * BlockEntity backing the Distiller - the back of the Equivalence lane (#253):
 * a {@code Prismatic Froglight} (a {@code configurable_froglight} carrying the
 * {@link PFDataComponents#SYNTHESIZED_ITEM} component) plus RF renders the
 * carried item back out. Deliberately a machine, NOT a smelting recipe - the
 * output is computed from the input's component in code, so there's no need for
 * a dynamic recipe type whose result is derived from the ingredient.
 *
 * <p><b>PF's first RF-consuming machine.</b> Existing appliances are
 * fuel/heat-driven (the Milker/Spawnery burn slime balls; the Crucible/Mold run
 * off heat-from-below); the Distiller exposes a receive-only
 * {@link net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage#BLOCK}
 * buffer that power cables fill, and the distill loop spends it. The pattern
 * here is the template the Alembic (the lane's front) reuses.
 *
 * <p><b>Transactional</b>: the input is consumed and the output produced on the
 * same tick the progress timer completes; if the output slot can't accept the
 * item, progress holds and nothing is spent.
 */
public class DistillerBlockEntity extends BlockEntity implements MenuProvider {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;

    /** Ticks to render one Prismatic Froglight back to its item. */
    public static final int DISTILL_TIME = 100;

    /** RF buffer capacity. */
    public static final int ENERGY_CAPACITY = 100_000;

    /** Max RF accepted per tick from a connected cable. */
    public static final int ENERGY_MAX_RECEIVE = 5_000;

    /** RF spent each tick while distilling (one extraction = {@code RF_PER_TICK * DISTILL_TIME}). */
    public static final int RF_PER_TICK = 200;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_ENERGY = 2;
    public static final int DATA_ENERGY_CAP = 3;
    public static final int DATA_COUNT = 4;

    private int progress = 0;

    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Slot 0 takes ONLY a synthesized Prismatic Froglight; slot 1 is the
            // output (internal writes only, see the menu's mayPlace gate).
            return slot != INPUT_SLOT || isPrismatic(stack);
        }
    };

    /** Insert-only view over the input slot (hoppers/cables on the sides). */
    private final IItemHandler inputView = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(INPUT_SLOT);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return items.insertItem(INPUT_SLOT, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(INPUT_SLOT);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return items.isItemValid(INPUT_SLOT, stack);
        }
    };

    /** Extract-only view over the output slot (a hopper below). */
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

    /**
     * Receive-only RF buffer. {@code maxExtract = 0} so cables can't pull power
     * back out; the distill loop spends it internally via {@link #consume(int)}.
     */
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

        /** Internal spend by the distill loop (bypasses the zero maxExtract). */
        void consume(int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }

        /** Restore on load, clamped to capacity. */
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
                case DATA_TOTAL -> DISTILL_TIME;
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

    public DistillerBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.DISTILLER.get(), pos, state);
    }

    /** A Distiller input is a Prismatic Froglight: a configurable Froglight carrying a synthesized item. */
    public static boolean isPrismatic(ItemStack stack) {
        return stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get())
            && stack.has(PFDataComponents.SYNTHESIZED_ITEM.get());
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

    public EnergyStorage energyStorage() {
        return energy;
    }

    public int progress() {
        return progress;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, DistillerBlockEntity be) {
        // Whole-lane gate (#253): a Distiller is inert when the EE lane is disabled.
        if (!com.flatts.productivefrogs.PFConfig.equivalenceEnabled()) {
            be.resetProgress();
            return;
        }
        ItemStack in = be.items.getStackInSlot(INPUT_SLOT);
        ResourceLocation itemId = DistillerBlockEntity.isPrismatic(in)
            ? in.get(PFDataComponents.SYNTHESIZED_ITEM.get())
            : null;
        if (itemId == null) {
            be.resetProgress();
            return;
        }
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null) {
            be.resetProgress();
            return;
        }
        // Type only - no components carried forward (EE-style; the synthesized
        // Froglight stored just the item id).
        ItemStack result = new ItemStack(item);
        if (!be.items.insertItem(OUTPUT_SLOT, result.copy(), true).isEmpty()) {
            // Output full or holds a different item - hold progress, spend nothing.
            return;
        }
        if (be.energy.getEnergyStored() < RF_PER_TICK) {
            // No power - pause without losing progress (furnace-style).
            return;
        }
        be.energy.consume(RF_PER_TICK);
        be.progress++;
        be.setChanged();
        if (be.progress >= DISTILL_TIME) {
            // Airtight transaction: emit the item FIRST, consume the Froglight only if
            // it fully landed (removes any dependence on the same-tick simulate above
            // and the silent-leftover-drop edge).
            if (!be.items.insertItem(OUTPUT_SLOT, result.copy(), false).isEmpty()) {
                return;
            }
            be.items.extractItem(INPUT_SLOT, 1, false);
            be.progress = 0;
            level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.4F, 1.2F);
            PFDebug.log(PFDebug.Area.DISTILLER, () -> String.format(
                "distiller @%s rendered %s", pos, item));
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items", Tag.TAG_COMPOUND)) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        if (tag.contains("Energy", Tag.TAG_INT)) {
            energy.load(tag.getInt("Energy"));
        }
        int loaded = tag.contains("Progress", Tag.TAG_INT) ? tag.getInt("Progress") : 0;
        progress = Math.max(0, Math.min(loaded, DISTILL_TIME));
    }

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

    // -------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.distiller");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new DistillerMenu(containerId, playerInv, this, dataAccess);
    }
}
