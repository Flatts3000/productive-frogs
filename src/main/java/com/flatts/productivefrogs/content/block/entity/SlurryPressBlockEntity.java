package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.SlurryPressBlock;
import com.flatts.productivefrogs.content.item.EntityNetItem;
import com.flatts.productivefrogs.content.item.MobSlurryBucketItem;
import com.flatts.productivefrogs.content.menu.SlurryPressMenu;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the {@link SlurryPressBlock} (#281, predation Phase 3):
 * presses a netted mob into a bucket. A filled Ender Net plus an empty bucket
 * convert, over a flat press cycle, into a {@code SLURRIED_ENTITY}-stamped Mob
 * Slurry bucket - and the emptied Ender Net is handed back in the second
 * output (the net is a tool, never consumed; the Churn's
 * spent-container-return posture).
 *
 * <p>A flat timer, not the milk spawn economy: the Press is a one-shot
 * conversion (mob -&gt; bucket), so the economy - budget, catalysts, cadence -
 * lives downstream on the Basin the slurry is poured into, exactly where it
 * lives for milk (on the placed source / Basin, not the Milker).
 *
 * <p><b>Boss rejection</b>: a mob in {@code c:bosses}
 * ({@link Tags.EntityTypes#BOSSES}) can be caught and relocated by the Ender
 * Net but never pressed - checked at slot-insert time
 * ({@link SlurryPressInventory#isItemValid}) AND re-checked here each tick, so
 * a tampered net stalls inert (nothing consumed) rather than producing boss
 * slurry. The whole appliance idles when {@code predators.enabled} is off.
 */
public class SlurryPressBlockEntity extends BlockEntity implements MenuProvider {

    /** Indices into the {@link #dataAccess} ContainerData for menu sync. */
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_TOTAL = 1;
    public static final int DATA_COUNT = 2;

    /** Flat press cycle, in ticks (the Milker's cook-length neighbourhood). */
    public static final int PRESS_TICKS = 100;

    private int progress = 0;

    private final SlurryPressInventory inventory = new SlurryPressInventory(this::setChanged);

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_PROGRESS -> progress;
                case DATA_TOTAL -> PRESS_TICKS;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_PROGRESS) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SlurryPressBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLURRY_PRESS.get(), pos, state);
    }

    public SlurryPressInventory getInventory() {
        return inventory;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public int getProgress() {
        return progress;
    }

    /**
     * Whether a filled Ender Net's captured mob may be pressed: a known
     * EntityType that is NOT a boss ({@code c:bosses}). Shared by the slot
     * filter and the tick re-check.
     */
    public static boolean isPressable(ItemStack netStack) {
        EntityType<?> type = EntityNetItem.capturedType(netStack);
        return type != null && !type.builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SlurryPressBlockEntity be) {
        // The predation master switch idles the whole appliance (holds
        // progress, consumes nothing) - the v1.18 config posture.
        if (!PFConfig.predatorsEnabled()) {
            setWorking(level, pos, state, false);
            return;
        }
        ItemStack net = be.inventory.getStackInSlot(SlurryPressInventory.NET_SLOT);
        ItemStack buckets = be.inventory.getStackInSlot(SlurryPressInventory.BUCKET_SLOT);

        // Full validity every tick: filled net, pressable (non-boss) mob, an
        // empty bucket, and BOTH output slots free. Furnace stall semantics -
        // a blocked press holds its progress and resumes where it paused; an
        // invalid input (e.g. a tampered boss net) resets it.
        if (!(net.getItem() instanceof com.flatts.productivefrogs.content.item.EnderNetItem)
                || !EntityNetItem.isFilled(net) || !isPressable(net)) {
            be.resetProgress();
            setWorking(level, pos, state, false);
            return;
        }
        if (buckets.isEmpty() || !buckets.is(Items.BUCKET)
                || !be.inventory.getStackInSlot(SlurryPressInventory.SLURRY_OUTPUT_SLOT).isEmpty()
                || !be.inventory.getStackInSlot(SlurryPressInventory.NET_OUTPUT_SLOT).isEmpty()) {
            setWorking(level, pos, state, false);
            return;
        }

        if (be.progress < PRESS_TICKS) {
            be.progress++;
            be.setChanged();
            setWorking(level, pos, state, true);
            return;
        }

        // Press complete: consume one bucket, empty the net into the slurry
        // bucket, hand the emptied net back. Insert outputs BEFORE consuming
        // inputs is unnecessary here - both outputs were verified empty above,
        // so the transaction cannot half-fail.
        EntityType<?> type = EntityNetItem.capturedType(net);
        if (type == null) {
            be.resetProgress();
            setWorking(level, pos, state, false);
            return;
        }
        Identifier typeId = EntityType.getKey(type);
        be.inventory.extractItem(SlurryPressInventory.BUCKET_SLOT, 1, false);
        ItemStack emptiedNet = net.copyWithCount(1);
        emptiedNet.remove(DataComponents.CUSTOM_DATA);
        be.inventory.setStackInSlot(SlurryPressInventory.NET_SLOT, ItemStack.EMPTY);
        be.inventory.setStackInSlot(SlurryPressInventory.SLURRY_OUTPUT_SLOT, MobSlurryBucketItem.forEntity(typeId));
        be.inventory.setStackInSlot(SlurryPressInventory.NET_OUTPUT_SLOT, emptiedNet);
        be.resetProgress();
        be.setChanged();
        setWorking(level, pos, state, true);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS,
            0.8F, 0.7F + level.getRandom().nextFloat() * 0.2F);
        PFDebug.log(PFDebug.Area.CHURN, () -> String.format(
            "press @%s: pressed %s into slurry", pos, typeId));
    }

    private void resetProgress() {
        if (progress != 0) {
            progress = 0;
            setChanged();
        }
    }

    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (!(state.getBlock() instanceof SlurryPressBlock)) {
            return;
        }
        if (state.getValue(SlurryPressBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(SlurryPressBlock.WORKING, working),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // -------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", progress);
        inventory.serialize(output.child("Inventory"));
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput input) {
        super.loadAdditional(input);
        // Clamp on load so a tampered/old save can't stall or skip the cycle.
        progress = Math.max(0, Math.min(input.getIntOr("Progress", 0), PRESS_TICKS));
        input.child("Inventory").ifPresent(inventory::deserialize);
    }

    // Client sync for chunk load (Jade reads contents without opening the GUI).
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    // -------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.slurry_press");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SlurryPressMenu(containerId, playerInv, this, dataAccess);
    }
}
