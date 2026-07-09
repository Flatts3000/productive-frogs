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
    // Vanilla 2-row generic-container layout (so the slots align with the
    // generic_54.png chest texture the screen blits).
    private static final int OUTPUT_X = 8;
    private static final int OUTPUT_Y = 18;
    private static final int INVENTORY_X = 8;
    private static final int INVENTORY_Y = 67;
    private static final int HOTBAR_Y = 125;

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

    /**
     * The destination slot range {@code [start, end)} for a shift-click that
     * originates at {@code sourceIndex}. The range MUST NOT contain the source
     * slot: an overlapping range makes vanilla {@code moveItemStackTo} merge a
     * stackable stack into its own slot (the froglight dupe - the stack doubles
     * on every shift-click). Hatch slots are output-only, so they move into the
     * whole player region; a player slot moves into the OTHER player region
     * (main rows and hotbar swap) so it never targets itself.
     *
     * @param sourceIndex the shift-clicked slot index
     * @return {@code {start, end}} destination range (end exclusive)
     */
    static int[] shiftClickDestRange(int sourceIndex) {
        int playerStart = SLOTS;        // 18: first player (main-inventory) slot
        int mainEnd = SLOTS + 27;       // 45: end of the 3 main rows / start of the hotbar
        int playerEnd = SLOTS + 36;     // 54: end of the hotbar
        if (sourceIndex < SLOTS) {
            return new int[] {playerStart, playerEnd}; // hatch (output) -> whole player region
        }
        if (sourceIndex < mainEnd) {
            return new int[] {mainEnd, playerEnd};     // main rows -> hotbar (never itself)
        }
        return new int[] {playerStart, mainEnd};        // hotbar -> main rows (never itself)
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int[] dest = shiftClickDestRange(slotIndex);
            // Hatch -> player fills from the far end (reverse); player shuffles forward.
            boolean reverse = slotIndex < SLOTS;
            if (!moveItemStackTo(stack, dest[0], dest[1], reverse)) {
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
    private static HatchBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof HatchBlockEntity hatch ? hatch : null;
    }
}
