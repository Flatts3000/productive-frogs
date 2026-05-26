package com.flatts.productivefrogs.content.menu;

import com.flatts.productivefrogs.content.block.entity.SpawneryBlockEntity;
import com.flatts.productivefrogs.content.block.entity.SpawneryInventory;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItemTags;
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

/**
 * Container menu for the {@link SpawneryBlockEntity}. Four slot widgets - bottle,
 * fuel, primer, output - plus the standard 27 + 9 player inventory grid. The
 * cook-progress arrow and burn flame read from the BE's {@link ContainerData} via
 * {@code addDataSlots}.
 *
 * <p>Layout is furnace-derived: bottle over fuel on the left (flame between them),
 * primer over output on the right, arrow bridging the two columns. Slot validity is
 * enforced by {@link SpawneryInventory#isItemValid}; the output slot rejects placement.
 *
 * <p>Two ctors as required by {@code IMenuTypeExtension.create}: the network ctor
 * {@code (id, inv, buf)} reads the BlockPos to resolve the BE on the client, then
 * chains to the BE-driven ctor that wires the slots.
 */
public class SpawneryMenu extends AbstractContainerMenu {

    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 84;
    public static final int HOTBAR_Y = 142;

    public static final int BOTTLE_SLOT_X = 56;
    public static final int BOTTLE_SLOT_Y = 17;
    public static final int FUEL_SLOT_X = 56;
    public static final int FUEL_SLOT_Y = 53;
    public static final int PRIMER_SLOT_X = 116;
    public static final int PRIMER_SLOT_Y = 17;
    public static final int OUTPUT_SLOT_X = 116;
    public static final int OUTPUT_SLOT_Y = 35;

    private static final int DEFAULT_COOK_TOTAL = 200;

    private final ContainerLevelAccess access;
    private final ContainerData dataAccess;

    /** Network-side ctor - used when {@link net.minecraft.world.inventory.MenuType} reconstructs on the client. */
    public SpawneryMenu(int containerId, Inventory playerInv, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInv, resolveBlockEntity(playerInv, buf.readBlockPos()),
            new SimpleContainerData(SpawneryBlockEntity.DATA_COUNT));
    }

    /** Server-side ctor - used by {@link SpawneryBlockEntity#createMenu}. */
    public SpawneryMenu(int containerId, Inventory playerInv, SpawneryBlockEntity be, ContainerData data) {
        super(PFMenuTypes.SPAWNERY.get(), containerId);
        this.access = be == null
            ? ContainerLevelAccess.NULL
            : ContainerLevelAccess.create(be.getLevel(), be.getBlockPos());
        this.dataAccess = data;

        if (be != null) {
            SpawneryInventory inv = be.getInventory();
            addSlot(new SlotItemHandler(inv, SpawneryBlockEntity.BOTTLE_SLOT, BOTTLE_SLOT_X, BOTTLE_SLOT_Y));
            addSlot(new SlotItemHandler(inv, SpawneryBlockEntity.FUEL_SLOT, FUEL_SLOT_X, FUEL_SLOT_Y));
            addSlot(new SlotItemHandler(inv, SpawneryBlockEntity.PRIMER_SLOT, PRIMER_SLOT_X, PRIMER_SLOT_Y));
            addSlot(new SlotItemHandler(inv, SpawneryBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        } else {
            // Defensive fallback when the BE hasn't synced yet: a dummy container
            // that keeps slot indexes consistent and mirrors the real validity
            // filters so the client doesn't flash a ghost item the server rejects.
            SimpleContainer dummy = new SimpleContainer(SpawneryBlockEntity.SLOT_COUNT);
            addSlot(new Slot(dummy, SpawneryBlockEntity.BOTTLE_SLOT, BOTTLE_SLOT_X, BOTTLE_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.GLASS_BOTTLE);
                }
            });
            addSlot(new Slot(dummy, SpawneryBlockEntity.FUEL_SLOT, FUEL_SLOT_X, FUEL_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(Items.SLIME_BALL);
                }
            });
            addSlot(new Slot(dummy, SpawneryBlockEntity.PRIMER_SLOT, PRIMER_SLOT_X, PRIMER_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return SpawneryInventory.isValidPrimer(stack);
                }
            });
            addSlot(new Slot(dummy, SpawneryBlockEntity.OUTPUT_SLOT, OUTPUT_SLOT_X, OUTPUT_SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        // Player inventory: 3 rows of 9 (slots 4..30).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                    INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        // Hotbar: 1 row of 9 (slots 31..39).
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, INVENTORY_X + col * 18, HOTBAR_Y));
        }

        addDataSlots(dataAccess);
    }

    public int getCookProgress() {
        return dataAccess.get(SpawneryBlockEntity.DATA_COOK_PROGRESS);
    }

    public int getCookTotal() {
        int total = dataAccess.get(SpawneryBlockEntity.DATA_COOK_TOTAL);
        return total > 0 ? total : DEFAULT_COOK_TOTAL;
    }

    public int getBurnTime() {
        return dataAccess.get(SpawneryBlockEntity.DATA_BURN_TIME);
    }

    public int getBurnDuration() {
        int dur = dataAccess.get(SpawneryBlockEntity.DATA_BURN_DURATION);
        return dur > 0 ? dur : DEFAULT_COOK_TOTAL;
    }

    @Override
    public boolean stillValid(Player player) {
        return AbstractContainerMenu.stillValid(access, player, PFBlocks.SPAWNERY.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            copy = stack.copy();
            int playerMainStart = SpawneryBlockEntity.SLOT_COUNT;
            int playerMainEnd = SpawneryBlockEntity.SLOT_COUNT + 27;
            int hotbarEnd = SpawneryBlockEntity.SLOT_COUNT + 36;

            if (slotIndex < SpawneryBlockEntity.SLOT_COUNT) {
                // Container slot -> player inventory.
                if (!moveItemStackTo(stack, playerMainStart, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // Player inventory -> the slot that accepts this item.
                if (stack.is(Items.GLASS_BOTTLE)) {
                    if (!moveItemStackTo(stack, SpawneryBlockEntity.BOTTLE_SLOT,
                                         SpawneryBlockEntity.BOTTLE_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (stack.is(Items.SLIME_BALL)) {
                    // Slime ball is valid as fuel AND as the vanilla-frogspawn primer.
                    // Prefer fuel; fall through to the primer slot when fuel is full,
                    // so the "one ball in each" vanilla workflow works via shift-click.
                    if (!moveItemStackTo(stack, SpawneryBlockEntity.FUEL_SLOT,
                                         SpawneryBlockEntity.FUEL_SLOT + 1, false)
                        && !moveItemStackTo(stack, SpawneryBlockEntity.PRIMER_SLOT,
                                            SpawneryBlockEntity.PRIMER_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (PFItemTags.primerCategory(stack) != null) {
                    if (!moveItemStackTo(stack, SpawneryBlockEntity.PRIMER_SLOT,
                                         SpawneryBlockEntity.PRIMER_SLOT + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (slotIndex < playerMainEnd) {
                    // Main inventory -> hotbar.
                    if (!moveItemStackTo(stack, playerMainEnd, hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    // Hotbar -> main inventory.
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

    private static SpawneryBlockEntity resolveBlockEntity(Inventory playerInv, BlockPos pos) {
        Level level = playerInv.player.level();
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof SpawneryBlockEntity spawnery ? spawnery : null;
    }
}
