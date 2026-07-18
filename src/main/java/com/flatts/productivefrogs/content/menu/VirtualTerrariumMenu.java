package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumInventory;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the Virtual Terrarium Processor: the frog slot, the output row,
 * the player inventory, and the cycle progress/interval data for the GUI duration bar.
 */
public class VirtualTerrariumMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int FROG_SLOT_X = 26;
    public static final int FROG_SLOT_Y = 35;
    public static final int OUTPUT_START_X = 62;
    public static final int OUTPUT_Y = 35;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    public VirtualTerrariumMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolve(playerInv, buf.readBlockPos()),
            new SimpleContainerData(VirtualTerrariumBlockEntity.DATA_COUNT));
    }

    public VirtualTerrariumMenu(int containerId, Inventory playerInv, VirtualTerrariumBlockEntity be, ContainerData data) {
        super(PFMenuTypes.VIRTUAL_TERRARIUM.get(), containerId);
        this.access = be == null ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            VirtualTerrariumInventory inv = be.getInventory();
            addSlot(new SlotItemHandler(inv, VirtualTerrariumInventory.FROG_SLOT, FROG_SLOT_X, FROG_SLOT_Y));
            for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
                addSlot(new SlotItemHandler(inv, VirtualTerrariumInventory.OUTPUT_START + i,
                    OUTPUT_START_X + i * 18, OUTPUT_Y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        } else {
            SimpleContainer dummy = new SimpleContainer(VirtualTerrariumInventory.SLOT_COUNT);
            addSlot(new Slot(dummy, VirtualTerrariumInventory.FROG_SLOT, FROG_SLOT_X, FROG_SLOT_Y));
            for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
                addSlot(new Slot(dummy, VirtualTerrariumInventory.OUTPUT_START + i, OUTPUT_START_X + i * 18, OUTPUT_Y) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(dataAccess);
    }

    public int getProgress() {
        return dataAccess.get(VirtualTerrariumBlockEntity.DATA_PROGRESS);
    }

    public int getInterval() {
        int i = dataAccess.get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        return i > 0 ? i : 200;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.VIRTUAL_TERRARIUM.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerStart = VirtualTerrariumInventory.SLOT_COUNT;
            int playerMainEnd = playerStart + 27;
            int hotbarEnd = playerStart + 36;
            if (slotIndex < VirtualTerrariumInventory.SLOT_COUNT) {
                if (!moveItemStackTo(stack, playerStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (VirtualTerrariumInventory.isFrogNet(stack)) {
                if (!moveItemStackTo(stack, VirtualTerrariumInventory.FROG_SLOT,
                        VirtualTerrariumInventory.FROG_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex < playerMainEnd) {
                if (!moveItemStackTo(stack, playerMainEnd, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, playerStart, playerMainEnd, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == copy.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return copy;
    }

    private static VirtualTerrariumBlockEntity resolve(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof VirtualTerrariumBlockEntity vt ? vt : null;
    }
}
