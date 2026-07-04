package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.SummonReceptacleBlock;
import com.flatts.productivefrogs.content.multiblock.WitherAltarValidator;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * BlockEntity for the {@link SummonReceptacleBlock} (#247). Holds exactly one
 * of its block's {@linkplain SummonReceptacleBlock#accepted() accepted item}
 * (Soul Sand or a Wither Skeleton Skull). One BE class backs both receptacle blocks;
 * the accepted item is read from the block at construction. Insert-only automation
 * view (the summon spends the contents, so nothing pulls them back out). The
 * {@link SummonReceptacleBlock#FILLED} blockstate mirrors "has its item".
 */
public class SummonReceptacleBlockEntity extends BlockEntity {

    private final Item accepted;

    /**
     * The altar's ritual direction (way the receptacle wall faces), stamped by the Hatch
     * on validate and synced so the BER orients the held item back toward the arena. Defaults
     * to canonical (the pre-facing-aware orientation) so an un-stamped receptacle still renders
     * sensibly until the altar validates.
     */
    private Direction ritual = WitherAltarValidator.CANONICAL_RITUAL;

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
            SummonReceptacleBlockEntity.this.onChanged();
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

    public SummonReceptacleBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.WITHER_SUMMON_RECEPTACLE.get(), pos, state);
        this.accepted = state.getBlock() instanceof SummonReceptacleBlock b ? b.accepted() : Items.AIR;
    }

    /** The insert-only handler exposed to automation via {@code Capabilities.ItemHandler.BLOCK}. */
    public IItemHandler insertOnlyHandler() {
        return insertOnly;
    }

    /** The 26.1 {@code Capabilities.Item.BLOCK} view: insert-only over the single held slot. */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> heldResource() {
        return new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(held, new int[] {0}, true, false);
    }

    /** True when the receptacle holds its item. */
    public boolean isFilled() {
        return !held.getStackInSlot(0).isEmpty();
    }

    /** The altar's ritual direction (read by the renderer to orient the held item). */
    public Direction ritual() {
        return ritual;
    }

    /** Stamp the altar's ritual direction; sync to clients only on change. */
    public void setRitual(Direction dir) {
        if (this.ritual != dir && dir != null) {
            this.ritual = dir;
            onChanged();
        }
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

    // 26.1 port: the held item drops here (the BE still exists on the removal path), NOT in the
    // block's affectNeighborsAfterRemoval, which runs after the BE is gone. This BE is not a
    // vanilla Container, so the super default won't drop it - re-home the spill here.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level instanceof ServerLevel serverLevel) {
            ItemStack held = contents();
            if (!held.isEmpty()) {
                Containers.dropItemStack(serverLevel, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, held);
            }
        }
    }

    private void onChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            BlockState st = getBlockState();
            boolean filled = isFilled();
            if (st.hasProperty(SummonReceptacleBlock.FILLED)
                    && st.getValue(SummonReceptacleBlock.FILLED) != filled) {
                level.setBlock(worldPosition, st.setValue(SummonReceptacleBlock.FILLED, filled), Block.UPDATE_CLIENTS);
            } else {
                level.sendBlockUpdated(worldPosition, st, st, Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        held.serialize(output.child("Held"));
        output.putString("Ritual", ritual.getName());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Held").ifPresent(held::deserialize);
        String name = input.getStringOr("Ritual", "");
        Direction d = name.isEmpty() ? null : Direction.byName(name);
        this.ritual = d != null && d.getAxis().isHorizontal() ? d : WitherAltarValidator.CANONICAL_RITUAL;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
