package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.AlembicBlockEntity;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Container menu for the {@link AlembicBlockEntity} - a bucket slot, an item
 * slot, an output slot (take-only), and the player grid. The screen reads
 * progress + the RF buffer off the synced {@link ContainerData}.
 */
public class AlembicMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int BUCKET_SLOT_X = 38;
    public static final int ITEM_SLOT_X = 68;
    public static final int SLOT_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    public AlembicMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new SimpleContainerData(AlembicBlockEntity.DATA_COUNT));
    }

    public AlembicMenu(int containerId, Inventory playerInv, @Nullable AlembicBlockEntity be, ContainerData data) {
        super(PFMenuTypes.ALEMBIC.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            addSlot(new SlotItemHandler(be.items(), AlembicBlockEntity.BUCKET_SLOT, BUCKET_SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.BUCKET);
                }
            });
            addSlot(new SlotItemHandler(be.items(), AlembicBlockEntity.ITEM_SLOT, ITEM_SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !stack.is(Items.BUCKET);
                }
            });
            addSlot(new SlotItemHandler(be.items(), AlembicBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            SimpleContainer dummy = new SimpleContainer(3);
            addSlot(new Slot(dummy, 0, BUCKET_SLOT_X, SLOT_Y));
            addSlot(new Slot(dummy, 1, ITEM_SLOT_X, SLOT_Y));
            addSlot(new Slot(dummy, 2, OUTPUT_SLOT_X, SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(dataAccess);
    }

    public int getProgress() {
        return dataAccess.get(AlembicBlockEntity.DATA_PROGRESS);
    }

    public int getProgressTotal() {
        int total = dataAccess.get(AlembicBlockEntity.DATA_TOTAL);
        return total > 0 ? total : AlembicBlockEntity.SYNTH_TIME;
    }

    public int getEnergy() {
        return dataAccess.get(AlembicBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        int cap = dataAccess.get(AlembicBlockEntity.DATA_ENERGY_CAP);
        return cap > 0 ? cap : AlembicBlockEntity.ENERGY_CAPACITY;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.ALEMBIC.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int containerSlots = 3;
            int playerStart = containerSlots;
            int playerMainEnd = containerSlots + 27;
            int hotbarEnd = containerSlots + 36;
            if (slotIndex < containerSlots) {
                if (!moveItemStackTo(stack, playerStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.is(Items.BUCKET)) {
                if (!moveItemStackTo(stack, AlembicBlockEntity.BUCKET_SLOT, AlembicBlockEntity.BUCKET_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (moveItemStackTo(stack, AlembicBlockEntity.ITEM_SLOT, AlembicBlockEntity.ITEM_SLOT + 1, false)) {
                // moved into the item slot
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

    @Nullable
    private static AlembicBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof AlembicBlockEntity alembic ? alembic : null;
    }
}
