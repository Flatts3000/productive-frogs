package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.InvWrapper;

/**
 * End Dragon Altar Hatch block entity (#249). Same role as the Terrarium Hatch -
 * an output inventory you open like a chest and pipe items out of - but a distinct
 * block (different recipe) and decoupled from the Terrarium: the dragon-altar
 * summon deposits the dragon's drops (one dragon's breath, the config egg) here,
 * and XP spawns as orbs at the hatch. Plain 27-slot chest GUI ({@link ChestMenu})
 * so no custom screen is needed; pipes pull via an {@link InvWrapper} item handler.
 */
public class EndDragonAltarHatchBlockEntity extends BaseContainerBlockEntity {

    public static final int SIZE = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    private final InvWrapper itemHandler = new InvWrapper(this);

    public EndDragonAltarHatchBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.END_DRAGON_ALTAR_HATCH.get(), pos, state);
    }

    /** Pipe/hopper view over the chest inventory. */
    public InvWrapper itemHandler() {
        return itemHandler;
    }

    /**
     * Deposit a dragon-altar drop, spilling any overflow as a returned leftover
     * (the summon decides what to do with it). Used by the summon state machine.
     */
    public ItemStack deposit(ItemStack stack) {
        return ItemHandlerHelper.insertItem(itemHandler, stack, false);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> list) {
        this.items = list;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.productivefrogs.end_dragon_altar_hatch");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
    }
}
