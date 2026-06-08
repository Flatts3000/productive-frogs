package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.SlimeChurnBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeChurnInventory;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
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
 * Container menu for the {@link SlimeChurnBlockEntity}. Four machine slots
 * (milk + empty buckets on the left, slime buckets + spent containers on the
 * right) plus the standard 27 + 9 player grid. The interval-progress arrow
 * reads from the BE's {@link ContainerData} via {@code addDataSlots}.
 *
 * <p>Same dual-constructor shape as {@code SlimeMilkerMenu}: the network-side
 * ctor resolves the BE from the BlockPos in the buffer, the server-side ctor
 * wires the live inventory.
 */
public class SlimeChurnMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    // Furnace-family layout: two stacked inputs on the left (milk above,
    // empty buckets below, mirroring vanilla's input-over-fuel), two stacked
    // outputs on the right (slime buckets above, spent containers below),
    // progress arrow between.
    public static final int MILK_SLOT_X = 56;
    public static final int MILK_SLOT_Y = 17;
    public static final int BUCKET_SLOT_X = 56;
    public static final int BUCKET_SLOT_Y = 53;
    public static final int SLIME_OUT_X = 116;
    public static final int SLIME_OUT_Y = 17;
    public static final int EMPTY_OUT_X = 116;
    public static final int EMPTY_OUT_Y = 53;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    /** Network-side ctor - used when the MenuType reconstructs on the client. */
    public SlimeChurnMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new net.minecraft.world.inventory.SimpleContainerData(SlimeChurnBlockEntity.DATA_COUNT));
    }

    /** Server-side ctor - used by {@link SlimeChurnBlockEntity#createMenu}. */
    public SlimeChurnMenu(int containerId, Inventory playerInv, SlimeChurnBlockEntity be, ContainerData data) {
        super(PFMenuTypes.SLIME_CHURN.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            SlimeChurnInventory inv = be.getInventory();
            // Inputs delegate mayPlace to the inventory's per-slot validity.
            addSlot(new SlotItemHandler(inv, SlimeChurnInventory.MILK_SLOT, MILK_SLOT_X, MILK_SLOT_Y));
            addSlot(new SlotItemHandler(inv, SlimeChurnInventory.BUCKET_SLOT, BUCKET_SLOT_X, BUCKET_SLOT_Y));
            // Outputs reject inserts (the churn loop writes via setStackInSlot).
            addSlot(new SlotItemHandler(inv, SlimeChurnInventory.SLIME_OUTPUT_SLOT, SLIME_OUT_X, SLIME_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            addSlot(new SlotItemHandler(inv, SlimeChurnInventory.EMPTY_OUTPUT_SLOT, EMPTY_OUT_X, EMPTY_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            // Network-side fallback before the BE syncs: dummy container with
            // matching filters so the client never flashes a ghost item the
            // server would reject.
            SimpleContainer dummy = new SimpleContainer(SlimeChurnInventory.SLOT_COUNT);
            addSlot(new Slot(dummy, SlimeChurnInventory.MILK_SLOT, MILK_SLOT_X, MILK_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof SlimeMilkBucketItem;
                }
            });
            addSlot(new Slot(dummy, SlimeChurnInventory.BUCKET_SLOT, BUCKET_SLOT_X, BUCKET_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.BUCKET);
                }
            });
            addSlot(new Slot(dummy, SlimeChurnInventory.SLIME_OUTPUT_SLOT, SLIME_OUT_X, SLIME_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
            addSlot(new Slot(dummy, SlimeChurnInventory.EMPTY_OUTPUT_SLOT, EMPTY_OUT_X, EMPTY_OUT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // Player inventory: 3 rows of 9 (menu slots 4..30)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        // Hotbar: 1 row of 9 (menu slots 31..39)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(dataAccess);
    }

    public int getIntervalProgress() {
        return dataAccess.get(SlimeChurnBlockEntity.DATA_INTERVAL_PROGRESS);
    }

    public int getIntervalTotal() {
        return dataAccess.get(SlimeChurnBlockEntity.DATA_INTERVAL_TOTAL);
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.SLIME_CHURN.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Furnace-family shift-click: container slots move down into the
        // player inventory; milk buckets from the player route to the milk
        // slot, empty buckets to the bucket slot, everything else hops
        // between main inventory and hotbar.
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerMainStart = SlimeChurnInventory.SLOT_COUNT;
            int playerMainEnd = SlimeChurnInventory.SLOT_COUNT + 27;
            int hotbarEnd = SlimeChurnInventory.SLOT_COUNT + 36;

            if (slotIndex < SlimeChurnInventory.SLOT_COUNT) {
                if (!moveItemStackTo(stack, playerMainStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (stack.getItem() instanceof SlimeMilkBucketItem) {
                    if (!moveItemStackTo(stack, SlimeChurnInventory.MILK_SLOT,
                                         SlimeChurnInventory.MILK_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (stack.is(Items.BUCKET)) {
                    if (!moveItemStackTo(stack, SlimeChurnInventory.BUCKET_SLOT,
                                         SlimeChurnInventory.BUCKET_SLOT + 1, false)) {
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

    private static SlimeChurnBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SlimeChurnBlockEntity churn ? churn : null;
    }
}
