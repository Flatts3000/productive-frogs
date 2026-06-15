package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.WitherSummonReceptacleBlock;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * BlockEntity for the {@link WitherSummonReceptacleBlock} (#247). Holds exactly one
 * of its block's {@linkplain WitherSummonReceptacleBlock#accepted() accepted item}
 * (Soul Sand or a Wither Skeleton Skull). One BE class backs both receptacle blocks;
 * the accepted item is read from the block at construction. Insert-only automation
 * view (the summon spends the contents, so nothing pulls them back out). The
 * {@link WitherSummonReceptacleBlock#FILLED} blockstate mirrors "has its item".
 */
public class WitherSummonReceptacleBlockEntity extends BlockEntity {

    private final Item accepted;

    private final ItemStackHandler held = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(accepted);
        }

        @Override
        protected void onContentsChanged(int slot) {
            WitherSummonReceptacleBlockEntity.this.onChanged();
        }
    };

    /** Insert-only view: pipes feed the item in; the summon spends it, so extraction is blocked. */
    private final IItemHandler insertOnly = new IItemHandler() {
        @Override
        public int getSlots() {
            return held.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return held.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return held.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return held.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return held.isItemValid(slot, stack);
        }
    };

    public WitherSummonReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.WITHER_SUMMON_RECEPTACLE.get(), pos, state);
        this.accepted = state.getBlock() instanceof WitherSummonReceptacleBlock b ? b.accepted() : Items.AIR;
    }

    /** The insert-only handler exposed to automation via {@code Capabilities.ItemHandler.BLOCK}. */
    public IItemHandler insertOnlyHandler() {
        return insertOnly;
    }

    /** True when the receptacle holds its item. */
    public boolean isFilled() {
        return !held.getStackInSlot(0).isEmpty();
    }

    /** Place one of the accepted item if empty. Returns true if it took one. */
    public boolean tryInsert(ItemStack stack) {
        if (isFilled() || !stack.is(accepted)) {
            return false;
        }
        held.setStackInSlot(0, stack.copyWithCount(1));
        return true;
    }

    /** Pop the held item out (for right-click retrieval); EMPTY if none. */
    public ItemStack extract() {
        ItemStack out = held.getStackInSlot(0).copy();
        held.setStackInSlot(0, ItemStack.EMPTY);
        return out;
    }

    /** Spend the held item (the summon consumes it). */
    public void consume() {
        held.setStackInSlot(0, ItemStack.EMPTY);
    }

    /** The held item, for drop-on-break. */
    public ItemStack contents() {
        return held.getStackInSlot(0);
    }

    private void onChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState st = getBlockState();
            boolean filled = isFilled();
            if (st.hasProperty(WitherSummonReceptacleBlock.FILLED)
                    && st.getValue(WitherSummonReceptacleBlock.FILLED) != filled) {
                level.setBlock(worldPosition, st.setValue(WitherSummonReceptacleBlock.FILLED, filled), Block.UPDATE_CLIENTS);
            } else {
                level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Held", held.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Held", Tag.TAG_COMPOUND)) {
            held.deserializeNBT(registries, tag.getCompound("Held"));
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
