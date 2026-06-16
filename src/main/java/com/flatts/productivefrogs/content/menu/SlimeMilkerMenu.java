package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.SlimeMilkerBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SlimeMilkerInventory;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItems;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Container menu for the {@link SlimeMilkerBlockEntity}. Two slot widgets
 * (input + output) plus the standard 27 + 9 player inventory grid. The
 * cook-progress arrow on the screen reads from the BE's
 * {@link ContainerData} via {@code addDataSlots}.
 *
 * <p>Slot validity is enforced at two layers: {@link ResourceHandlerSlot}'s
 * {@code mayPlace} delegates to {@link SlimeMilkerInventory#isValid},
 * which only accepts SLIME_BUCKET in the input slot and never in the
 * output. Quick-move (shift-click) routes follow the vanilla furnace
 * pattern: input from hotbar/main → input slot, output → main
 * inventory/hotbar.
 *
 * <p>Both constructors exist because NeoForge's
 * {@code IMenuTypeExtension.create} expects the network-side ctor
 * {@code (id, inv, buf)} — that one looks up the milker's BlockEntity via
 * the position in the buffer, then chains to the BE-driven ctor that
 * actually wires the slots.
 */
public class SlimeMilkerMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    // Slot widget positions on the screen. The GUI is composed from vanilla
    // furnace.png with the fuel-system column painted out and the input slot
    // frame relocated to align vertically with the output (see
    // scripts/generate_slime_milker_gui.ps1). Vanilla furnace stacks input
    // (56, 17) above fuel (56, 53) and centres the result at (116, 35); the
    // milker has no fuel slot, so collapsing to a single input at y=35 puts
    // both slots on the same horizontal line with the arrow between them.
    public static final int INPUT_SLOT_X = 56;
    public static final int INPUT_SLOT_Y = 35;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    /** Network-side ctor — used when {@link MenuType} reconstructs on the client. */
    public SlimeMilkerMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new net.minecraft.world.inventory.SimpleContainerData(SlimeMilkerBlockEntity.DATA_COUNT));
    }

    /** Server-side ctor — used by {@link SlimeMilkerBlockEntity#createMenu}. */
    public SlimeMilkerMenu(int containerId, Inventory playerInv, SlimeMilkerBlockEntity be, ContainerData data) {
        super(PFMenuTypes.SLIME_MILKER.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            SlimeMilkerInventory inv = be.getInventory();
            // Input slot — Slime Bucket only; SlotItemHandler delegates
            // mayPlace to ItemStackHandler.isItemValid.
            addSlot(new SlotItemHandler(inv,
                SlimeMilkerBlockEntity.INPUT_SLOT, INPUT_SLOT_X, INPUT_SLOT_Y));
            // Output slot — Slime Milk Bucket. Override mayPlace to reject
            // all inserts (the cook loop writes via setStackInSlot which
            // bypasses this).
            addSlot(new SlotItemHandler(inv,
                SlimeMilkerBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            // Defensive fallback for the network-side ctor when the BE
            // hasn't synced yet — fill with a dummy container so the slot
            // indexes stay consistent and quick-move math doesn't blow up.
            // Mirror the real input filter (SLIME_BUCKET only) and the
            // OUTPUT slot's reject-all so the client doesn't briefly flash
            // a ghost item the server would then reject.
            SimpleContainer dummy = new SimpleContainer(SlimeMilkerBlockEntity.SLOT_COUNT);
            addSlot(new Slot(dummy, SlimeMilkerBlockEntity.INPUT_SLOT, INPUT_SLOT_X, INPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(PFItems.SLIME_BUCKET.get()) || stack.is(PFItems.MIMIC_SLIME_BUCKET.get());
                }
            });
            addSlot(new Slot(dummy, SlimeMilkerBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // Player inventory: 3 rows of 9 (slots 2..28 in our menu)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        // Hotbar: 1 row of 9 (slots 29..37)
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(dataAccess);
    }

    public int getCookProgress() {
        return dataAccess.get(SlimeMilkerBlockEntity.DATA_COOK_PROGRESS);
    }

    public int getCookTotal() {
        int total = dataAccess.get(SlimeMilkerBlockEntity.DATA_COOK_TOTAL);
        return total > 0 ? total : SlimeMilkerBlockEntity.COOK_TIME_TOTAL;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.SLIME_MILKER.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        // Vanilla furnace shape: shift-clicking
        // - input slot (0): move stack down into the player inv
        // - output slot (1): move stack down into the player inv
        // - player main (2..28): try input slot if stack is a SLIME_BUCKET,
        //   else try hotbar
        // - hotbar (29..37): try main inventory
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerMainStart = SlimeMilkerBlockEntity.SLOT_COUNT;
            int playerMainEnd = SlimeMilkerBlockEntity.SLOT_COUNT + 27;
            int hotbarEnd = SlimeMilkerBlockEntity.SLOT_COUNT + 36;

            if (slotIndex < SlimeMilkerBlockEntity.SLOT_COUNT) {
                // Container slot → player inventory
                if (!moveItemStackTo(stack, playerMainStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player inventory → container. Both a captured Slime Bucket and a
                // captured Mimic Slime Bucket (#253) shift-click into the input.
                if (stack.is(PFItems.SLIME_BUCKET.get()) || stack.is(PFItems.MIMIC_SLIME_BUCKET.get())) {
                    if (!moveItemStackTo(stack, SlimeMilkerBlockEntity.INPUT_SLOT,
                                         SlimeMilkerBlockEntity.INPUT_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex < playerMainEnd) {
                    // From main inventory → hotbar
                    if (!moveItemStackTo(stack, playerMainEnd, hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // From hotbar → main inventory
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

    private static SlimeMilkerBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SlimeMilkerBlockEntity milker ? milker : null;
    }
}
