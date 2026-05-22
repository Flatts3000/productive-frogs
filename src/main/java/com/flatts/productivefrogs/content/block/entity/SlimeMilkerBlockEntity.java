package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.SlimeMilkerBlock;
import com.flatts.productivefrogs.content.menu.SlimeMilkerMenu;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFFluidTypes;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * BlockEntity backing the {@link SlimeMilkerBlock}. Two-slot inventory
 * (input + output) plus a tick-counted cook progress, modelled on
 * {@link net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity}
 * but stripped of the fuel slot — the slime IS the input, no separate
 * fuel resource needed. Closes the V1 redesign open issue in
 * {@code docs/known_issues.md}.
 *
 * <p>Cook semantics: while the input slot holds a Slime Bucket carrying a
 * known SlimeVariant and the output slot is empty, the {@code cookProgress}
 * counter advances by one per tick until it reaches {@link #COOK_TIME_TOTAL}
 * (100 ticks = 5 s). On completion the input bucket is consumed and the
 * matching variant-typed Slime Milk bucket is written to the output slot.
 *
 * <p>Hopper I/O is deferred. NeoForge 1.21.11 replaced the legacy
 * {@code Capabilities.ItemHandler.BLOCK} (returning {@code IItemHandler})
 * with {@code Capabilities.Item.BLOCK} (returning
 * {@code ResourceHandler<ItemResource>}), and per NeoForge's transfer-
 * rework notes there is no adapter that wraps the legacy handler as the
 * new one. Migrating the BE's storage to the new API is its own PR;
 * tracked as a follow-up in {@code docs/known_issues.md}.
 */
public class SlimeMilkerBlockEntity extends BlockEntity implements MenuProvider {

    /** Slot indices, shared with {@link SlimeMilkerMenu}. */
    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    /** Ticks per cook. 100t = 5s per design Q9b in {@code known_issues.md}. */
    public static final int COOK_TIME_TOTAL = 100;

    /** Indices into the {@link #dataAccess} ContainerData for menu sync. */
    public static final int DATA_COOK_PROGRESS = 0;
    public static final int DATA_COOK_TOTAL = 1;
    public static final int DATA_COUNT = 2;

    private int cookProgress = 0;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            // Players can only place a Slime Bucket in the input slot via
            // the GUI; the output slot is pickup-only. The hopper-aware
            // capability wrapper that will pin this rule for automation is
            // tracked as a follow-up in docs/known_issues.md.
            return slot == INPUT_SLOT && stack.is(PFItems.SLIME_BUCKET.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            // setChanged() marks the chunk dirty when the GUI mutates a
            // slot — without this the BE save can lag a tick or two behind
            // the visible state, which shows up in QA as "I put a bucket
            // in, /reload, my bucket is gone".
            setChanged();
        }
    };

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_COOK_PROGRESS -> cookProgress;
                case DATA_COOK_TOTAL -> COOK_TIME_TOTAL;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_COOK_PROGRESS) {
                cookProgress = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SlimeMilkerBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLIME_MILKER.get(), pos, state);
    }

    /** Inventory accessor for hoppers / capability wrappers. */
    public IItemHandlerModifiable getInventory() {
        return inventory;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public int getCookProgress() {
        return cookProgress;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SlimeMilkerBlockEntity be) {
        ItemStack input = be.inventory.getStackInSlot(INPUT_SLOT);
        if (input.isEmpty() || !input.is(PFItems.SLIME_BUCKET.get())) {
            be.resetProgress();
            return;
        }
        String variant = SlimeMilkerBlock.readBucketVariant(input);
        if (variant == null || !PFFluidTypes.VARIANTS.contains(variant)) {
            // Bucket has no Variant component (vanilla slime bucket / empty)
            // or carries an unknown variant — fail closed, just like the
            // original right-click flow did before this redesign.
            be.resetProgress();
            return;
        }
        ItemStack output = be.inventory.getStackInSlot(OUTPUT_SLOT);
        if (!output.isEmpty()) {
            // Slime Milk Buckets are stacksTo(1), so the output slot has to
            // be drained before another cook can start. Don't reset progress
            // — players (and hoppers) will pull the bucket out and the cook
            // resumes from where it paused. Matches the vanilla furnace
            // behavior where a full output pauses the cook.
            return;
        }
        be.cookProgress++;
        be.setChanged();
        if (be.cookProgress >= COOK_TIME_TOTAL) {
            BucketItem outputBucket = PFItems.MILK_BUCKETS.get(variant).get();
            be.inventory.extractItem(INPUT_SLOT, 1, false);
            be.inventory.setStackInSlot(OUTPUT_SLOT, new ItemStack(outputBucket));
            be.cookProgress = 0;
            level.playSound(
                null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS,
                0.8F, 1.2F + level.getRandom().nextFloat() * 0.2F
            );
        }
    }

    private void resetProgress() {
        if (cookProgress != 0) {
            cookProgress = 0;
            setChanged();
        }
    }

    // -------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("CookProgress", cookProgress);
        inventory.serialize(out.child("Inventory"));
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        cookProgress = in.getIntOr("CookProgress", 0);
        in.child("Inventory").ifPresent(inventory::deserialize);
    }

    // -------------------------------------------------------------------
    // MenuProvider
    // -------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.slime_milker");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SlimeMilkerMenu(containerId, playerInv, this, dataAccess);
    }
}
