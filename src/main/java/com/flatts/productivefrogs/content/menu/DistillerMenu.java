package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.DistillerBlockEntity;
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
import org.jetbrains.annotations.Nullable;

/**
 * Container menu for the {@link DistillerBlockEntity} - an input slot (a
 * Prismatic Froglight), an output slot (the rendered item, take-only), and the
 * standard player grid. The screen reads distill progress and the RF buffer
 * off the synced {@link ContainerData}.
 */
public class DistillerMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int INPUT_SLOT_X = 56;
    public static final int INPUT_SLOT_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    /** Network-side ctor - reconstructs from the position in the buffer. */
    public DistillerMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new SimpleContainerData(DistillerBlockEntity.DATA_COUNT));
    }

    /** Server-side ctor - used by {@link DistillerBlockEntity#createMenu}. */
    public DistillerMenu(int containerId, Inventory playerInv, @Nullable DistillerBlockEntity be, ContainerData data) {
        super(PFMenuTypes.DISTILLER.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            // Input: only a Prismatic Froglight may be placed.
            addSlot(new SlotItemHandler(be.items(), DistillerBlockEntity.INPUT_SLOT,
                    INPUT_SLOT_X, INPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return DistillerBlockEntity.isPrismatic(stack);
                }
            });
            // Output: take-only; the distill loop writes it.
            addSlot(new SlotItemHandler(be.items(), DistillerBlockEntity.OUTPUT_SLOT,
                    OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            SimpleContainer dummy = new SimpleContainer(2);
            addSlot(new Slot(dummy, 0, INPUT_SLOT_X, INPUT_SLOT_Y));
            addSlot(new Slot(dummy, 1, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
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
        return dataAccess.get(DistillerBlockEntity.DATA_PROGRESS);
    }

    public int getProgressTotal() {
        int total = dataAccess.get(DistillerBlockEntity.DATA_TOTAL);
        return total > 0 ? total : DistillerBlockEntity.DISTILL_TIME;
    }

    public int getEnergy() {
        return dataAccess.get(DistillerBlockEntity.DATA_ENERGY);
    }

    public int getEnergyCapacity() {
        int cap = dataAccess.get(DistillerBlockEntity.DATA_ENERGY_CAP);
        return cap > 0 ? cap : DistillerBlockEntity.ENERGY_CAPACITY;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.DISTILLER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Two container slots: 0 = input, 1 = output. Shift-click output to the
        // player; shift-click a Prismatic Froglight from the player into the
        // input; otherwise shuffle main <-> hotbar.
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int containerSlots = 2;
            int playerStart = containerSlots;
            int playerMainEnd = containerSlots + 27;
            int hotbarEnd = containerSlots + 36;
            if (slotIndex < containerSlots) {
                // Container -> player.
                if (!moveItemStackTo(stack, playerStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (DistillerBlockEntity.isPrismatic(stack)) {
                // A Froglight from the player -> the input slot.
                if (!moveItemStackTo(stack, DistillerBlockEntity.INPUT_SLOT,
                        DistillerBlockEntity.INPUT_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (slotIndex < playerMainEnd) {
                if (!moveItemStackTo(stack, playerMainEnd, hotbarEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!moveItemStackTo(stack, playerStart, playerMainEnd, false)) {
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
    private static DistillerBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof DistillerBlockEntity distiller ? distiller : null;
    }
}
