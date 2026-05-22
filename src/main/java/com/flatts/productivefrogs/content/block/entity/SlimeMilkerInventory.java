package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFItems;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Two-slot inventory backing the Slime Milker BlockEntity. Built on
 * {@link ItemStacksResourceHandler} (NeoForge 1.21.11's replacement for
 * the legacy {@code ItemStackHandler}) so the BE can expose its storage
 * as the new {@link ResourceHandler}{@code <ItemResource>} capability
 * and hoppers / pipe mods can push/pull through {@code Capabilities.Item.BLOCK}.
 *
 * <p>Slot semantics (mirrored in {@link SlimeMilkerBlockEntity}):
 * <ul>
 *   <li>{@code INPUT_SLOT} accepts only the {@code productivefrogs:slime_bucket}
 *       item. The cook loop pulls from here.</li>
 *   <li>{@code OUTPUT_SLOT} accepts nothing via {@link #isValid} — only the
 *       cook loop writes to it (via the public {@code set} on the parent
 *       class, which bypasses {@code isValid}). Players and hoppers can
 *       extract but never insert.</li>
 * </ul>
 *
 * <p>The {@link #inputView()} / {@link #outputView()} accessors expose
 * side-restricted single-slot views for the {@code Capabilities.Item.BLOCK}
 * registration in {@code PFModBusEvents}: top + horizontal sides return
 * the insert-only INPUT view, the bottom returns the extract-only OUTPUT
 * view. This mirrors the vanilla furnace's "input from above, output from
 * below" hopper convention without our needing to be a
 * {@link net.minecraft.world.WorldlyContainer}.
 *
 * <p>ItemStack-style {@code getStackInSlot}/{@code setStackInSlot}
 * accessors are kept as a convenience for the BE's tick loop, the
 * inventory-drop code in {@code playerWillDestroy}, and existing
 * GameTests — they wrap the resource-level {@code set} and the protected
 * backing list, respectively.
 */
public class SlimeMilkerInventory extends ItemStacksResourceHandler {

    public static final int INPUT_SLOT = 0;
    public static final int OUTPUT_SLOT = 1;
    public static final int SLOT_COUNT = 2;

    private final Runnable onChanged;

    // Cached views, returned from inputView() / outputView(). The block
    // capability provider in PFModBusEvents resolves these every time
    // a hopper queries the BE — caching the two SidedView instances on
    // construction keeps the hot path allocation-free and lets external
    // callers hold a stable handler reference for the lifetime of the BE.
    private final ResourceHandler<ItemResource> inputView;
    private final ResourceHandler<ItemResource> outputView;

    public SlimeMilkerInventory(Runnable onChanged) {
        super(SLOT_COUNT);
        this.onChanged = onChanged;
        this.inputView = new SidedView(this, INPUT_SLOT, true, false);
        this.outputView = new SidedView(this, OUTPUT_SLOT, false, true);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (resource.isEmpty()) {
            return false;
        }
        // INPUT accepts Slime Buckets only; OUTPUT accepts nothing —
        // the cook loop writes the milk bucket through `set`, which
        // bypasses this check by design.
        return index == INPUT_SLOT && resource.is(PFItems.SLIME_BUCKET.get());
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        onChanged.run();
    }

    /**
     * ItemStack view of the slot, used by the BE's tick loop and the
     * drop-on-break code. Reads through the protected {@code stacks}
     * list — no copy, the returned stack is the live backing instance.
     */
    public ItemStack getStackInSlot(int index) {
        Objects.checkIndex(index, size());
        return stacks.get(index);
    }

    /**
     * ItemStack-typed write, routed through the resource-level {@code set}
     * so {@code onContentsChanged} fires and the chunk gets marked dirty.
     * Bypasses {@link #isValid} by design — the cook loop needs to write
     * the output milk bucket into a slot whose {@code isValid} otherwise
     * rejects all inserts.
     */
    public void setStackInSlot(int index, ItemStack stack) {
        set(index, ItemResource.of(stack), stack.getCount());
    }

    /**
     * Insert-only view of the input slot. Returned for any side except
     * {@link net.minecraft.core.Direction#DOWN} — hoppers above and on
     * the horizontal faces use this to push Slime Buckets in. Cached
     * (one instance per BE), see the field comment.
     */
    public ResourceHandler<ItemResource> inputView() {
        return inputView;
    }

    /**
     * Extract-only view of the output slot. Returned for
     * {@link net.minecraft.core.Direction#DOWN} — a hopper below the
     * milker pulls finished Slime Milk buckets through this. Cached
     * (one instance per BE), see the field comment.
     */
    public ResourceHandler<ItemResource> outputView() {
        return outputView;
    }

    /**
     * Single-slot delegating view that restricts insert and/or extract.
     * Translates index {@code 0} of the view to {@code targetIndex} on
     * the underlying inventory; everything else passes through. Used
     * to back the side-aware {@code Capabilities.Item.BLOCK} provider.
     */
    private static final class SidedView implements ResourceHandler<ItemResource> {

        private final SlimeMilkerInventory delegate;
        private final int targetIndex;
        private final boolean allowInsert;
        private final boolean allowExtract;

        SidedView(SlimeMilkerInventory delegate, int targetIndex, boolean allowInsert, boolean allowExtract) {
            this.delegate = delegate;
            this.targetIndex = targetIndex;
            this.allowInsert = allowInsert;
            this.allowExtract = allowExtract;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, 1);
            return delegate.getResource(targetIndex);
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, 1);
            return delegate.getAmountAsLong(targetIndex);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, 1);
            return delegate.getCapacityAsLong(targetIndex, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, 1);
            // No insert allowed → no resource is valid for insertion.
            return allowInsert && delegate.isValid(targetIndex, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, 1);
            if (!allowInsert) {
                return 0;
            }
            return delegate.insert(targetIndex, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, 1);
            if (!allowExtract) {
                return 0;
            }
            return delegate.extract(targetIndex, resource, amount, transaction);
        }
    }
}
