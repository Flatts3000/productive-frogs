package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Four-slot inventory backing the Slime Churn BlockEntity, on
 * {@link ItemStackHandler} so the BE can expose it as the
 * {@code Capabilities.ItemHandler.BLOCK} capability for hoppers / pipe mods
 * (1.21.1 API). Two inputs, two outputs (#187).
 *
 * <p>Slot semantics:
 * <ul>
 *   <li>{@link #MILK_SLOT} accepts only a per-variant Slime Milk bucket
 *       ({@link SlimeMilkBucketItem}) - the variant + spawn-budget source.</li>
 *   <li>{@link #BUCKET_SLOT} accepts only empty {@code minecraft:bucket}s -
 *       the capture containers, one consumed per slime produced.</li>
 *   <li>{@link #SLIME_OUTPUT_SLOT} rejects inserts; the churn loop writes the
 *       produced variant Slime Buckets via {@link #setStackInSlot}.</li>
 *   <li>{@link #EMPTY_OUTPUT_SLOT} rejects inserts; receives the spent empty
 *       milk container when an input milk bucket depletes.</li>
 * </ul>
 *
 * <p>Side-restricted views via {@link #inputView()} / {@link #outputView()}
 * back the side-aware capability provider in {@code PFModBusEvents}: the
 * bottom face returns the extract-only view over both output slots; every
 * other face returns the insert-only view over both input slots, which routes
 * each pushed item to the slot whose {@link #isItemValid} accepts it (milk
 * buckets to the milk slot, empties to the bucket slot).
 */
public class SlimeChurnInventory extends ItemStackHandler {

    public static final int MILK_SLOT = 0;
    public static final int BUCKET_SLOT = 1;
    public static final int SLIME_OUTPUT_SLOT = 2;
    public static final int EMPTY_OUTPUT_SLOT = 3;
    public static final int SLOT_COUNT = 4;

    private final Runnable onChanged;
    private final IItemHandler inputView;
    private final IItemHandler outputView;

    public SlimeChurnInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new MultiSlotItemView(this, new int[] {MILK_SLOT, BUCKET_SLOT}, true, false);
        this.outputView = new MultiSlotItemView(this, new int[] {SLIME_OUTPUT_SLOT, EMPTY_OUTPUT_SLOT}, false, true);
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return switch (slot) {
            // A per-variant Slime Milk bucket, or (Equivalence lane, #253) a Mimic
            // Milk bucket - the Churn captures either back into slime buckets.
            case MILK_SLOT -> stack.getItem() instanceof SlimeMilkBucketItem
                || stack.getItem() instanceof com.flatts.productivefrogs.content.item.MimicMilkBucketItem;
            case BUCKET_SLOT -> stack.is(Items.BUCKET);
            default -> false; // output slots reject inserts
        };
    }

    @Override
    protected void onContentsChanged(int slot) {
        onChanged.run();
    }

    public IItemHandler inputView() {
        return inputView;
    }

    public IItemHandler outputView() {
        return outputView;
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} input view: insert-only over both input slots (per-slot validity routes each item). */
    private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> inputResourceCached;

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> inputResource() {
        // Cached: one handler = one SnapshotJournal. A fresh handler per capability
        // lookup would give two lookups in one transaction independent journals over
        // the same state, and an abort then restores the LAST journal's snapshot -
        // leaking the first mutation (review finding).
        if (inputResourceCached == null) {
            inputResourceCached = new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(
            this, new int[] {MILK_SLOT, BUCKET_SLOT}, true, false);
        }
        return inputResourceCached;
    }

    /** 26.1 {@code Capabilities.Item.BLOCK} output view: extract-only over both output slots. */
    private net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> outputResourceCached;

    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> outputResource() {
        // Cached: one handler = one SnapshotJournal. A fresh handler per capability
        // lookup would give two lookups in one transaction independent journals over
        // the same state, and an abort then restores the LAST journal's snapshot -
        // leaking the first mutation (review finding).
        if (outputResourceCached == null) {
            outputResourceCached = new com.flatts.productivefrogs.content.transfer.RestrictedItemResourceHandler(
            this, new int[] {SLIME_OUTPUT_SLOT, EMPTY_OUTPUT_SLOT}, false, true);
        }
        return outputResourceCached;
    }

    // 26.1: ItemStackHandler implements ValueIOSerializable; the BE hands us the
    // ValueOutput/ValueInput child (legacy serializeNBT(RegistryAccess) is gone).
    public void serialize(ValueOutput output) {
        super.serialize(output);
    }

    public void deserialize(ValueInput input) {
        super.deserialize(input);
    }

}
