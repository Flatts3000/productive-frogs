package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.HatchMenu;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Hatch block entity (phase 3): the Terrarium's froglight output. Inside a formed
 * Terrarium the frog-eats-slime drop is redirected straight into this inventory
 * (no item entity ever spawns - see {@code FrogTongueDropHandler}), and when it is
 * {@linkplain #isFull() full} frogs stop eating (backpressure - the sensor refuses
 * prey). Pipes/hoppers pull from the outward face; right-clicking collects the
 * contents by hand (a GUI lands in the ship phase).
 */
public class HatchBlockEntity extends BlockEntity implements MenuProvider {

    public static final int SLOTS = 18;

    /**
     * What the Hatch accepts and auto-collects: modded + vanilla Froglights plus
     * the slimeball / magma-cream byproducts. Pack-extensible via
     * {@code data/productivefrogs/tags/item/hatch_collectible.json}.
     */
    public static final TagKey<Item> HATCH_COLLECTIBLE =
        TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "hatch_collectible"));

    private int tickCounter;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // The modded Froglight (the direct frog drop) is always accepted by
            // identity - it must work even before datapack tags bind (unit tests).
            // The tag extends acceptance to vanilla Froglights + the slimeball /
            // magma-cream byproducts; reject anything else (e.g. hopper junk).
            return stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get()) || stack.is(HATCH_COLLECTIBLE);
        }
    };

    public HatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.HATCH.get(), pos, state);
    }

    /** Pipe/hopper view (insert is froglight-validated; extract pulls the output). */
    public IItemHandler inventory() {
        return inventory;
    }

    /** The 26.1 {@code Capabilities.Item.BLOCK} view over the full chest inventory (read/insert/extract). */
    private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> inventoryResourceCached;

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> inventoryResource() {
        // Cached: one handler = one SnapshotJournal. A fresh handler per capability
        // lookup would give two lookups in one transaction independent journals over
        // the same state, and an abort then restores the LAST journal's snapshot -
        // leaking the first mutation (review finding).
        if (inventoryResourceCached == null) {
            inventoryResourceCached = com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler.ofAll(inventory, true, true);
        }
        return inventoryResourceCached;
    }

    /** Insert a froglight; returns true only if it fully fit (the caller drops nothing otherwise). */
    public boolean insert(ItemStack froglight) {
        return ItemHandlerHelper.insertItem(inventory, froglight, false).isEmpty();
    }

    /**
     * Auto-collect loose {@link #HATCH_COLLECTIBLE} items from the formed
     * Terrarium's cavity - slimeballs, magma cream, and vanilla/modded Froglights
     * that land as item entities (the direct frog-eat drop never spawns an entity;
     * this catches everything else). Throttled, formed-only, and respects the
     * full-Hatch backpressure: a full Hatch leaves items on the ground, never
     * voids them.
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HatchBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (++be.tickCounter < PFConfig.terrariumHatchVacuumIntervalTicks()) {
            return;
        }
        be.tickCounter = 0;
        if (be.isFull()) {
            return;
        }
        TerrariumManager.FormedTerrarium terrarium = TerrariumManager.owningHatch(server, pos);
        if (terrarium == null) {
            return; // only vacuums inside a formed Terrarium
        }
        List<ItemEntity> items = server.getEntitiesOfClass(ItemEntity.class, terrarium.cavity(),
            e -> e.isAlive() && e.getItem().is(HATCH_COLLECTIBLE));
        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem();
            ItemStack remainder = ItemHandlerHelper.insertItem(be.inventory, stack, false);
            if (remainder.getCount() == stack.getCount()) {
                continue; // nothing fit
            }
            if (remainder.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(remainder);
            }
            if (be.isFull()) {
                break;
            }
        }
    }

    /** Full = every slot occupied. Drives the eat-backpressure: frogs stop eating a full Hatch. */
    public boolean isFull() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Test seam: occupy every slot so {@link #isFull()} is true. */
    public void fillForTest() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get()));
        }
    }

    public boolean isEmpty() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Occupied slots, for the Jade look-at fill readout. */
    public int fillCount() {
        int n = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.getStackInSlot(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries); // sync the inventory so Jade can count it
    }

    @Override
    @Nullable
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.hatch");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new HatchMenu(containerId, playerInv, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        inventory.serialize(output.child("Inventory"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Inventory").ifPresent(inventory::deserialize);
    }
}
