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
    public static final int INVENTORY_Y = 98;
    public static final int HOTBAR_Y = 156;

    public static final int FROG_SLOT_X = 26;
    public static final int FROG_SLOT_Y = 40;
    // Output slots are a 3x2 grid (3 columns, 2 rows).
    public static final int OUTPUT_START_X = 74;
    public static final int OUTPUT_Y = 20;
    public static final int OUTPUT_COLS = 3;
    // Upgrade slots are a vertical column on the right.
    public static final int UPGRADE_X = 150;
    public static final int UPGRADE_START_Y = 18;

    /** clickMenuButton id: fill the feedstock tank from the cursor-held bucket. */
    public static final int FILL_FEEDSTOCK = 0;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;
    private final VirtualTerrariumBlockEntity blockEntity;

    public VirtualTerrariumMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolve(playerInv, buf.readBlockPos()),
            new SimpleContainerData(VirtualTerrariumBlockEntity.DATA_COUNT));
    }

    public VirtualTerrariumMenu(int containerId, Inventory playerInv, VirtualTerrariumBlockEntity be, ContainerData data) {
        super(PFMenuTypes.VIRTUAL_TERRARIUM.get(), containerId);
        this.access = be == null ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;
        this.blockEntity = be;

        if (be != null) {
            VirtualTerrariumInventory inv = be.getInventory();
            addSlot(new SlotItemHandler(inv, VirtualTerrariumInventory.FROG_SLOT, FROG_SLOT_X, FROG_SLOT_Y));
            for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
                addSlot(new SlotItemHandler(inv, VirtualTerrariumInventory.OUTPUT_START + i,
                    outputX(i), outputY(i)) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
            for (int i = 0; i < VirtualTerrariumInventory.UPGRADE_COUNT; i++) {
                addSlot(new SlotItemHandler(inv, VirtualTerrariumInventory.UPGRADE_START + i,
                    UPGRADE_X, UPGRADE_START_Y + i * 18));
            }
        } else {
            SimpleContainer dummy = new SimpleContainer(VirtualTerrariumInventory.SLOT_COUNT);
            addSlot(new Slot(dummy, VirtualTerrariumInventory.FROG_SLOT, FROG_SLOT_X, FROG_SLOT_Y));
            for (int i = 0; i < VirtualTerrariumInventory.OUTPUT_COUNT; i++) {
                addSlot(new Slot(dummy, VirtualTerrariumInventory.OUTPUT_START + i, outputX(i), outputY(i)) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
            for (int i = 0; i < VirtualTerrariumInventory.UPGRADE_COUNT; i++) {
                addSlot(new Slot(dummy, VirtualTerrariumInventory.UPGRADE_START + i, UPGRADE_X, UPGRADE_START_Y + i * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(com.flatts.productivefrogs.registry.PFItemTags.VIRTUAL_TERRARIUM_UPGRADE);
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

    private static int outputX(int i) {
        return OUTPUT_START_X + (i % OUTPUT_COLS) * 18;
    }

    private static int outputY(int i) {
        return OUTPUT_Y + (i / OUTPUT_COLS) * 18;
    }

    public int getProgress() {
        return dataAccess.get(VirtualTerrariumBlockEntity.DATA_PROGRESS);
    }

    public int getInterval() {
        int i = dataAccess.get(VirtualTerrariumBlockEntity.DATA_INTERVAL);
        return i > 0 ? i : 200;
    }

    /** Buffered feedstock in mB (synced ContainerData), for the GUI fluid slot. */
    public int getFeedstockAmount() {
        return dataAccess.get(VirtualTerrariumBlockEntity.DATA_FEEDSTOCK);
    }

    /** Buffered product fluid (molten or Liquid Experience) in mB, for the output tank gauge. */
    public int getProductAmount() {
        return dataAccess.get(VirtualTerrariumBlockEntity.DATA_PRODUCT);
    }

    /** Stored RF, reassembled from the two synced ContainerData shorts, for the energy meter. */
    public int getEnergyStored() {
        int lo = dataAccess.get(VirtualTerrariumBlockEntity.DATA_ENERGY_LO) & 0xFFFF;
        int hi = dataAccess.get(VirtualTerrariumBlockEntity.DATA_ENERGY_HI) & 0xFFFF;
        return (hi << 16) | lo;
    }

    /** The client-synced BlockEntity, or null - for the gauge fluid types + upgrade state. */
    public VirtualTerrariumBlockEntity blockEntity() {
        return blockEntity;
    }

    /**
     * Fill the feedstock tank from the cursor-held bucket (the in-GUI fluid-slot click).
     * Server-authoritative; returns the empty bucket to the cursor. Mirrors the block's
     * right-click fill.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != FILL_FEEDSTOCK || blockEntity == null) {
            return false;
        }
        ItemStack carried = getCarried();
        if (!VirtualTerrariumBlockEntity.isFeedstockBucket(carried)) {
            return false;
        }
        ItemStack empty = blockEntity.fillFromBucket(carried);
        if (empty.isEmpty()) {
            return false;
        }
        carried.shrink(1);
        if (carried.isEmpty()) {
            setCarried(empty);
        } else if (!player.getInventory().add(empty)) {
            player.drop(empty, false);
        }
        return true;
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
            } else if (stack.is(com.flatts.productivefrogs.registry.PFItemTags.VIRTUAL_TERRARIUM_UPGRADE)) {
                if (!moveItemStackTo(stack, VirtualTerrariumInventory.UPGRADE_START,
                        VirtualTerrariumInventory.UPGRADE_START + VirtualTerrariumInventory.UPGRADE_COUNT, false)) {
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
