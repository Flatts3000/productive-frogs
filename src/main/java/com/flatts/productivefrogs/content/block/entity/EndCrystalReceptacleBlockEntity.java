package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.EndCrystalReceptacleBlock;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * BlockEntity for the {@link EndCrystalReceptacleBlock} (#249). Holds exactly one
 * End Crystal. The {@link EndCrystalReceptacleBlock#FILLED} blockstate mirrors
 * "has a crystal" (driving the block texture + the on-top crystal render), and an
 * insert-only {@link IItemHandler} lets hoppers/pipes feed crystals in - the
 * dragon-altar summon consumes them, so nothing pulls them back out.
 */
public class EndCrystalReceptacleBlockEntity extends BlockEntity {

    private final ItemStackHandler crystal = new ItemStackHandler(1) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(Items.END_CRYSTAL);
        }

        @Override
        protected void onContentsChanged(int slot) {
            EndCrystalReceptacleBlockEntity.this.onCrystalChanged();
        }
    };

    /**
     * Insert-only automation view: crystals go in via hoppers/pipes; extraction
     * is blocked so a hopper under the altar can't steal a primed crystal before
     * the summon spends it.
     */
    private final IItemHandler insertOnly = new IItemHandler() {
        @Override
        public int getSlots() {
            return crystal.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return crystal.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return crystal.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return crystal.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return crystal.isItemValid(slot, stack);
        }
    };

    public EndCrystalReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.END_CRYSTAL_RECEPTACLE.get(), pos, state);
    }

    /** The insert-only handler exposed to automation via {@code Capabilities.ItemHandler.BLOCK}. */
    public IItemHandler insertOnlyHandler() {
        return insertOnly;
    }

    /** True when a crystal is held. */
    public boolean isFilled() {
        return !crystal.getStackInSlot(0).isEmpty();
    }

    /** Place one End Crystal if empty. Returns true if it took one. */
    public boolean tryInsert(ItemStack held) {
        if (isFilled() || !held.is(Items.END_CRYSTAL)) {
            return false;
        }
        crystal.setStackInSlot(0, held.copyWithCount(1));
        return true;
    }

    /** Pop the held crystal out (for right-click retrieval); EMPTY if none. */
    public ItemStack extract() {
        ItemStack out = crystal.getStackInSlot(0).copy();
        crystal.setStackInSlot(0, ItemStack.EMPTY);
        return out;
    }

    /** Spend the held crystal (the summon consumes it). */
    public void consume() {
        crystal.setStackInSlot(0, ItemStack.EMPTY);
    }

    /** The held crystal, for drop-on-break. */
    public ItemStack contents() {
        return crystal.getStackInSlot(0);
    }

    private void onCrystalChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState st = getBlockState();
            boolean filled = isFilled();
            if (st.hasProperty(EndCrystalReceptacleBlock.FILLED)
                    && st.getValue(EndCrystalReceptacleBlock.FILLED) != filled) {
                level.setBlock(worldPosition, st.setValue(EndCrystalReceptacleBlock.FILLED, filled), Block.UPDATE_CLIENTS);
            } else {
                level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Crystal", crystal.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Crystal", Tag.TAG_COMPOUND)) {
            crystal.deserializeNBT(registries, tag.getCompound("Crystal"));
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
