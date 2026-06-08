package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.HatchBlockEntity;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * Container menu for the {@link HatchBlockEntity}: its 18-slot froglight output
 * inventory (2 rows of 9, extract-only for the player - the Terrarium fills it,
 * you take from it) plus the player grid.
 */
public class HatchMenu extends AbstractContainerMenu {

    private static final int COLS = 9;
    private static final int ROWS = 2;
    private static final int SLOTS = COLS * ROWS;
    private static final int OUTPUT_X = 8;
    private static final int OUTPUT_Y = 18;
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 58;
    private static final int HOTBAR_Y = 116;

    private final ContainerLevelAccess access;

    public HatchMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()));
    }

    public HatchMenu(int containerId, Inventory playerInv, @Nullable HatchBlockEntity be) {
        super(PFMenuTypes.HATCH.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());

        IItemHandler handler = be != null ? be.inventory() : null;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = col + row * COLS;
                int x = OUTPUT_X + col * 18;
                int y = OUTPUT_Y + row * 18;
                if (handler != null) {
                    addSlot(new SlotItemHandler(handler, index, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false; // output only - the Terrarium fills it
                        }
                    });
                } else {
                    SimpleContainer dummy = new SimpleContainer(1);
                    addSlot(new Slot(dummy, 0, x, y) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            return false;
                        }
                    });
                }
            }
        }

        InvWrapper playerWrap = new InvWrapper(playerInv);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(playerWrap, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new SlotItemHandler(playerWrap, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.HATCH.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerStart = SLOTS;
            int playerEnd = SLOTS + 36;
            if (slotIndex < SLOTS) {
                // From the hatch -> into the player inventory.
                if (!moveItemStackTo(stack, playerStart, playerEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player slots only shuffle among themselves (nothing inserts into
                // the output-only hatch).
                if (!moveItemStackTo(stack, playerStart, playerEnd, false)) {
                    return ItemStack.EMPTY;
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

    @Nullable
    private static HatchBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof HatchBlockEntity hatch ? hatch : null;
    }
}
