package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.menu.HatchMenu;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Hatch block entity (phase 3): the Terrarium's froglight output. Inside a formed
 * Terrarium the frog-eats-slime drop is redirected straight into this inventory
 * (no item entity ever spawns - see {@code FrogTongueDropHandler}), and when it is
 * {@linkplain #isFull() full} frogs stop eating (backpressure - the sensor refuses
 * prey). Pipes/hoppers pull from the outward face; right-clicking collects the
 * contents by hand (a GUI lands in the ship phase).
 */
public class HatchBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOTS = 18;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // The Terrarium only ever deposits Froglights; reject foreign inserts.
            return stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get());
        }
    };

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.HATCH.get(), pos, state);
    }

    /** Pipe/hopper view (insert is froglight-validated; extract pulls the output). */
    public IItemHandler inventory() {
        return inventory;
    }

    /** Insert a froglight; returns true only if it fully fit (the caller drops nothing otherwise). */
    public boolean insert(ItemStack froglight) {
        return ItemHandlerHelper.insertItem(inventory, froglight, false).isEmpty();
    }

    /** Full = every slot occupied. Drives the eat-backpressure: frogs stop eating a full Hatch. */
    public boolean isFull() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Hand-collect: move all stored froglights into the player (or drop what won't fit). */
    public void collectInto(Player player) {
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                inventory.setStackInSlot(i, ItemStack.EMPTY);
            }
        }
    }

    /** Test seam: occupy every slot so {@link #isFull()} is true. */
    public void fillForTest() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get()));
        }
    }

    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.hatch");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new HatchMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Inventory", Tag.TAG_COMPOUND)) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }
}
