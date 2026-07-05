package com.flatts.productivefrogs.content.transfer;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Forward adapter from the legacy {@link IItemHandlerModifiable} storage to the
 * 26.1 {@link ResourceHandler}&lt;{@link ItemResource}&gt; capability surface.
 * NeoForge ships only the reverse bridge ({@code IItemHandler.of(ResourceHandler)}),
 * so the port hand-writes this one and wraps it at the capability-registration
 * boundary, leaving every {@code ItemStackHandler} / menu / cook loop untouched.
 *
 * <p><b>Transaction semantics (why snapshot-before-mutate).</b> The 26.1 transfer
 * API has no simulate flag: an insert/extract mutates the backing immediately and
 * the move is unwound by aborting the {@link TransactionContext}. A consumer is
 * allowed to read its own in-progress writes back before the transaction closes
 * (read-your-writes), so this adapter must mutate the real backing now, not defer
 * to commit. Each exposed index owns a {@link SnapshotJournal} that copies the
 * root slot's {@link ItemStack} on first touch in a transaction
 * ({@code createSnapshot}) and writes it back verbatim if the transaction aborts
 * ({@code revertToSnapshot}); on commit the snapshot is simply dropped. The actual
 * item movement is delegated to the root's {@code insertItem} / {@code extractItem}
 * (which already enforce slot validity, stack limits and component-aware merging),
 * so the adapter only layers the snapshot on top - the smallest correct surface
 * and the one least likely to introduce a dupe/loss bug. The root's
 * {@code onContentsChanged -> setChanged} fires from those calls (and again from
 * the revert), so no {@code onRootCommit} change callback is needed here.
 *
 * <p>Exposed index {@code i} maps to root slot {@code slots[i]}. {@code allowInsert}
 * / {@code allowExtract} gate the two directions so an output-only view rejects
 * inserts and an input-only view rejects extractions, mirroring the legacy
 * side-restricted views.
 */
public final class RestrictedItemResourceHandler implements ResourceHandler<ItemResource> {

    private final IItemHandlerModifiable root;
    private final int[] slots;
    private final boolean allowInsert;
    private final boolean allowExtract;
    private final SlotJournal[] journals;
    /**
     * Optional per-root-commit callback for owners whose change reaction has
     * WORLD side effects (blockstate writes, client sync). Those must not fire
     * from the mid-transaction mutate/revert (an aborted pipe probe would strobe
     * the world - review finding); owners guard their change callback while a
     * transaction is open and receive this once on commit instead.
     */
    @org.jetbrains.annotations.Nullable
    private final Runnable onRootCommit;

    public RestrictedItemResourceHandler(IItemHandlerModifiable root, int[] slots, boolean allowInsert, boolean allowExtract) {
        this(root, slots, allowInsert, allowExtract, null);
    }

    public RestrictedItemResourceHandler(IItemHandlerModifiable root, int[] slots, boolean allowInsert, boolean allowExtract,
            @org.jetbrains.annotations.Nullable Runnable onRootCommit) {
        this.root = root;
        this.slots = slots.clone();
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
        this.onRootCommit = onRootCommit;
        this.journals = new SlotJournal[this.slots.length];
        for (int i = 0; i < this.slots.length; i++) {
            this.journals[i] = new SlotJournal(this.slots[i]);
        }
    }

    /**
     * Wrap every slot of the root, in order, with the given direction flags.
     * Used for chest-style inventories (the dragon / wither altar hatches).
     */
    public static RestrictedItemResourceHandler ofAll(IItemHandlerModifiable root, boolean allowInsert, boolean allowExtract) {
        int[] all = new int[root.getSlots()];
        for (int i = 0; i < all.length; i++) {
            all[i] = i;
        }
        return new RestrictedItemResourceHandler(root, all, allowInsert, allowExtract);
    }

    @Override
    public int size() {
        return slots.length;
    }

    @Override
    public ItemResource getResource(int index) {
        Objects.checkIndex(index, size());
        return ItemResource.of(root.getStackInSlot(slots[index]));
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, size());
        return root.getStackInSlot(slots[index]).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        Objects.checkIndex(index, size());
        int slotLimit = root.getSlotLimit(slots[index]);
        if (resource.isEmpty()) {
            return slotLimit;
        }
        return Math.min(slotLimit, resource.getMaxStackSize());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Objects.checkIndex(index, size());
        return allowInsert && root.isItemValid(slots[index], resource.toStack(1));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!allowInsert) {
            return 0;
        }
        int rootSlot = slots[index];
        // Probe how much actually fits (validity + stacking + slot limit) without committing.
        ItemStack remainder = root.insertItem(rootSlot, resource.toStack(amount), true);
        int accepted = amount - remainder.getCount();
        if (accepted <= 0) {
            return 0;
        }
        journals[index].updateSnapshots(transaction);
        root.insertItem(rootSlot, resource.toStack(accepted), false);
        return accepted;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!allowExtract) {
            return 0;
        }
        int rootSlot = slots[index];
        ItemStack current = root.getStackInSlot(rootSlot);
        if (current.isEmpty() || !resource.matches(current)) {
            return 0;
        }
        ItemStack probe = root.extractItem(rootSlot, amount, true);
        if (probe.isEmpty()) {
            return 0;
        }
        journals[index].updateSnapshots(transaction);
        return root.extractItem(rootSlot, amount, false).getCount();
    }

    /**
     * Per-exposed-slot journal over the modifiable root. The snapshot is a copy of
     * the root slot's stack; reverting writes it back verbatim. No
     * {@code onRootCommit} is needed because the delegated {@code insertItem} /
     * {@code extractItem} (and the revert's {@code setStackInSlot}) already fire the
     * root's change callback.
     */
    private final class SlotJournal extends SnapshotJournal<ItemStack> {

        private final int rootSlot;

        private SlotJournal(int rootSlot) {
            this.rootSlot = rootSlot;
        }

        @Override
        protected void onRootCommit(ItemStack originalState) {
            if (onRootCommit != null) {
                onRootCommit.run();
            }
        }

        @Override
        protected ItemStack createSnapshot() {
            return root.getStackInSlot(rootSlot).copy();
        }

        @Override
        protected void revertToSnapshot(ItemStack snapshot) {
            root.setStackInSlot(rootSlot, snapshot);
        }
    }
}
