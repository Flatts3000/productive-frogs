package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.SlurryPressBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlurryPressInventory;
import com.flatts.productivefrogs.content.item.EnderNetItem;
import com.flatts.productivefrogs.content.item.EntityNetItem;
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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the {@link SlurryPressBlockEntity} (#281, Phase 3). Four
 * machine slots in the Churn's furnace-family layout (filled Ender Net over
 * empty buckets on the left, Slurry bucket over the returned net on the
 * right) plus the standard player grid. The press-progress arrow reads from
 * the BE's {@link ContainerData}.
 */
public class SlurryPressMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int NET_SLOT_X = 56;
    public static final int NET_SLOT_Y = 17;
    public static final int BUCKET_SLOT_X = 56;
    public static final int BUCKET_SLOT_Y = 53;
    public static final int SLURRY_OUT_X = 116;
    public static final int SLURRY_OUT_Y = 17;
    public static final int NET_OUT_X = 116;
    public static final int NET_OUT_Y = 53;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    /** Network-side ctor - used when the MenuType reconstructs on the client. */
    public SlurryPressMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new net.minecraft.world.inventory.SimpleContainerData(SlurryPressBlockEntity.DATA_COUNT));
    }

    /** Server-side ctor - used by {@link SlurryPressBlockEntity#createMenu}. */
    public SlurryPressMenu(int containerId, Inventory playerInv, SlurryPressBlockEntity be, ContainerData data) {
        super(PFMenuTypes.SLURRY_PRESS.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            SlurryPressInventory inv = be.getInventory();
            addSlot(new SlotItemHandler(inv, SlurryPressInventory.NET_SLOT, NET_SLOT_X, NET_SLOT_Y));
            addSlot(new SlotItemHandler(inv, SlurryPressInventory.BUCKET_SLOT, BUCKET_SLOT_X, BUCKET_SLOT_Y));
            addSlot(new SlotItemHandler(inv, SlurryPressInventory.SLURRY_OUTPUT_SLOT, SLURRY_OUT_X, SLURRY_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            addSlot(new SlotItemHandler(inv, SlurryPressInventory.NET_OUTPUT_SLOT, NET_OUT_X, NET_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            // Network-side fallback before the BE syncs, with matching filters.
            SimpleContainer dummy = new SimpleContainer(SlurryPressInventory.SLOT_COUNT);
            addSlot(new Slot(dummy, SlurryPressInventory.NET_SLOT, NET_SLOT_X, NET_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof EnderNetItem && EntityNetItem.isFilled(stack);
                }
            });
            addSlot(new Slot(dummy, SlurryPressInventory.BUCKET_SLOT, BUCKET_SLOT_X, BUCKET_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.BUCKET);
                }
            });
            addSlot(new Slot(dummy, SlurryPressInventory.SLURRY_OUTPUT_SLOT, SLURRY_OUT_X, SLURRY_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            addSlot(new Slot(dummy, SlurryPressInventory.NET_OUTPUT_SLOT, NET_OUT_X, NET_OUT_Y) {
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
        return dataAccess.get(SlurryPressBlockEntity.DATA_PROGRESS);
    }

    public int getProgressTotal() {
        return dataAccess.get(SlurryPressBlockEntity.DATA_TOTAL);
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.SLURRY_PRESS.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerMainStart = SlurryPressInventory.SLOT_COUNT;
            int playerMainEnd = SlurryPressInventory.SLOT_COUNT + 27;
            int hotbarEnd = SlurryPressInventory.SLOT_COUNT + 36;

            if (slotIndex < SlurryPressInventory.SLOT_COUNT) {
                if (!moveItemStackTo(stack, playerMainStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (stack.getItem() instanceof EnderNetItem && EntityNetItem.isFilled(stack)) {
                    if (!moveItemStackTo(stack, SlurryPressInventory.NET_SLOT,
                                         SlurryPressInventory.NET_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (stack.is(Items.BUCKET)) {
                    if (!moveItemStackTo(stack, SlurryPressInventory.BUCKET_SLOT,
                                         SlurryPressInventory.BUCKET_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex < playerMainEnd) {
                    if (!moveItemStackTo(stack, playerMainEnd, hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!moveItemStackTo(stack, playerMainStart, playerMainEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                }
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

    private static SlurryPressBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SlurryPressBlockEntity press ? press : null;
    }
}
