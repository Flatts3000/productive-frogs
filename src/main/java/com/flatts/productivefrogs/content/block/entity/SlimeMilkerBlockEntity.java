package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.block.SlimeMilkerBlock;
import com.flatts.productivefrogs.content.menu.SlimeMilkerMenu;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
 * <p>Hopper I/O is wired via {@link SlimeMilkerInventory}, exposed as
 * {@code Capabilities.ItemHandler.BLOCK} in {@code PFModBusEvents} (the 1.21.1
 * capability id). The side-aware capability provider returns the insert-only
 * INPUT view for the top + horizontal faces and the extract-only OUTPUT view
 * for the bottom face, mirroring the vanilla furnace's "input from above,
 * output from below" hopper convention.
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

    // Slot validity (input = SLIME_BUCKET, output = reject all) and the
    // setChanged() hook live on SlimeMilkerInventory; this BE just owns
    // the lifecycle and the cook loop.
    private final SlimeMilkerInventory inventory = new SlimeMilkerInventory(this::setChanged);

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

    /**
     * Inventory accessor for the menu, the drop-on-break loop in
     * {@link SlimeMilkerBlock#playerWillDestroy}, the capability provider
     * in {@code PFModBusEvents}, and GameTests.
     */
    public SlimeMilkerInventory getInventory() {
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
        // The output to produce: a variant Slime Milk bucket for a captured Slime
        // Bucket, OR (Equivalence lane, #253) a Mimic Milk bucket for a captured
        // Mimic Slime Bucket. Null = invalid/fail-closed input.
        ItemStack milkResult = milkResultFor(pos, input);
        if (milkResult == null) {
            be.resetProgress();
            setWorking(level, pos, state, false);
            return;
        }
        ItemStack output = be.inventory.getStackInSlot(OUTPUT_SLOT);
        if (!output.isEmpty()) {
            // Slime Milk Buckets are stacksTo(1), so the output slot has to
            // be drained before another cook can start. Don't reset progress
            // — players (and hoppers) will pull the bucket out and the cook
            // resumes from where it paused. Matches the vanilla furnace
            // behavior where a full output pauses the cook. WORKING goes
            // false during the stall so the textures don't keep animating
            // a frozen press.
            setWorking(level, pos, state, false);
            return;
        }
        if (be.cookProgress == 0) {
            PFDebug.log(PFDebug.Area.MILKER, () -> String.format(
                "milker @%s: start cooking %s", pos, input.getItem()));
        }
        be.cookProgress++;
        be.setChanged();
        if (be.cookProgress >= COOK_TIME_TOTAL) {
            // The input bucket is stacksTo(1), so consuming one always empties the
            // input slot. The output is the milk bucket computed at the top (variant
            // milk, or a component-carrying Mimic Milk bucket for the EE lane).
            be.inventory.setStackInSlot(INPUT_SLOT, ItemStack.EMPTY);
            be.inventory.setStackInSlot(OUTPUT_SLOT, milkResult);
            be.cookProgress = 0;
            setWorking(level, pos, state, false);
            PFDebug.log(PFDebug.Area.MILKER, () -> String.format(
                "milker @%s: produced %s", pos, milkResult.getItem()));
            level.playSound(
                null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS,
                0.8F, 1.2F + level.getRandom().nextFloat() * 0.2F
            );
        } else {
            // Mid-cook: drive the WORKING blockstate so clients render the
            // slime-visible textures. setWorking is a no-op if the property
            // is already true, so this doesn't spam neighbor updates.
            setWorking(level, pos, state, true);
        }
    }

    /**
     * The milk bucket a given input produces, or {@code null} for an invalid /
     * fail-closed input. A captured Slime Bucket yields its variant's milk
     * (v1.8 per-variant fluids); a captured Mimic Slime Bucket (Equivalence lane,
     * #253) yields a Mimic Milk bucket carrying the same synthesized item.
     */
    private static ItemStack milkResultFor(BlockPos pos, ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        if (input.is(PFItems.SLIME_BUCKET.get())) {
            Identifier variantId = SlimeMilkerBlock.readBucketVariantId(input);
            if (variantId == null) {
                PFDebug.logOnce(PFDebug.Area.MILKER, "failclosed#" + pos,
                    () -> String.format("milker @%s fail-closed: input bucket carries no variant", pos));
                return null;
            }
            Item milkBucketItem = PFVariantMilk.bucket(variantId);
            if (milkBucketItem == null) {
                PFDebug.logOnce(PFDebug.Area.MILKER, "nomilk#" + pos,
                    () -> String.format("milker @%s fail-closed: no per-variant milk fluid for %s", pos, variantId));
                return null;
            }
            return new ItemStack(milkBucketItem);
        }
        if (input.is(PFItems.MIMIC_SLIME_BUCKET.get())) {
            Identifier itemId = input.get(
                com.flatts.productivefrogs.registry.PFDataComponents.SYNTHESIZED_ITEM.get());
            if (itemId == null) {
                return null;
            }
            return com.flatts.productivefrogs.content.item.MimicMilkBucketItem.forItem(itemId);
        }
        return null;
    }

    /**
     * Toggle the {@link SlimeMilkerBlock#WORKING} blockstate property and
     * sync to clients. No-op when the state already matches — keeps
     * neighbor-update spam off the network in the common per-tick case.
     */
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (!(state.getBlock() instanceof SlimeMilkerBlock)) {
            return;
        }
        if (state.getValue(SlimeMilkerBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(SlimeMilkerBlock.WORKING, working),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
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
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CookProgress", cookProgress);
        net.minecraft.nbt.CompoundTag invTag = new net.minecraft.nbt.CompoundTag();
        inventory.serialize(invTag);
        tag.put("Inventory", invTag);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Clamp on load: a tampered/old save could carry a negative or
        // overlarge value. A negative cookProgress would never reach
        // COOK_TIME_TOTAL until it wrapped through Integer.MAX_VALUE, stalling
        // the block "working" forever; clamp into [0, COOK_TIME_TOTAL].
        int loaded = tag.contains("CookProgress", net.minecraft.nbt.Tag.TAG_INT)
            ? tag.getInt("CookProgress") : 0;
        cookProgress = Math.max(0, Math.min(loaded, COOK_TIME_TOTAL));
        if (tag.contains("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            inventory.deserialize(tag.getCompound("Inventory"));
        }
    }

    // Client sync: without these, a closed milker's inventory + cook progress
    // aren't sent on chunk load, so info-HUD mods (Jade/WTHIT) read stale/empty
    // contents until the GUI is opened. saveAdditional carries everything we
    // need; reuse it for the update tag.
    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        net.minecraft.nbt.CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
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
        return Component.translatable("block.productivefrogs.slime_milker");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SlimeMilkerMenu(containerId, playerInv, this, dataAccess);
    }
}
