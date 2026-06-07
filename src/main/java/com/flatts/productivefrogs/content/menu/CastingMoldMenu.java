package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity;
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
 * Container menu for the {@link CastingMoldBlockEntity} - one output slot (the
 * cast ingots; no input slots, the input is fluid) plus the standard player
 * grid. The screen reads cast progress and buffered mB from the synced
 * {@link ContainerData}, and the fluid TYPE for the gauge straight off the
 * (client-synced) BlockEntity via {@link #blockEntity()}.
 */
public class CastingMoldMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;
    @Nullable
    private final CastingMoldBlockEntity blockEntity;

    /** Network-side ctor - reconstructs from the position in the buffer. */
    public CastingMoldMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new SimpleContainerData(CastingMoldBlockEntity.DATA_COUNT));
    }

    /** Server-side ctor - used by {@link CastingMoldBlockEntity#createMenu}. */
    public CastingMoldMenu(int containerId, Inventory playerInv, @Nullable CastingMoldBlockEntity be, ContainerData data) {
        super(PFMenuTypes.CASTING_MOLD.get(), containerId);
        this.blockEntity = be;
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            // Output only - the solidify loop writes via insertItem internally;
            // players (and shift-clicks) may only take.
            addSlot(new SlotItemHandler(be.output(), CastingMoldBlockEntity.OUTPUT_SLOT,
                    OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            SimpleContainer dummy = new SimpleContainer(1);
            addSlot(new Slot(dummy, 0, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
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

    /** The mold BE (client-synced), for the screen's fluid gauge. Null defensively. */
    @Nullable
    public CastingMoldBlockEntity blockEntity() {
        return blockEntity;
    }

    public int getProgress() {
        return dataAccess.get(CastingMoldBlockEntity.DATA_PROGRESS);
    }

    public int getProgressTotal() {
        int total = dataAccess.get(CastingMoldBlockEntity.DATA_TOTAL);
        return total > 0 ? total : CastingMoldBlockEntity.CAST_TIME;
    }

    public int getFluidAmount() {
        return dataAccess.get(CastingMoldBlockEntity.DATA_FLUID_AMOUNT);
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.CASTING_MOLD.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // One container slot (output, index 0): shift-click moves it to the
        // player; player-side shift-clicks only shuffle main <-> hotbar (there
        // is no insertable container slot).
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerMainStart = 1;
            int playerMainEnd = 1 + 27;
            int hotbarEnd = 1 + 36;
            if (slotIndex == 0) {
                if (!moveItemStackTo(stack, playerMainStart, hotbarEnd, true)) {
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
    private static CastingMoldBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof CastingMoldBlockEntity mold ? mold : null;
    }
}
