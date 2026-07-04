package com.flatts.productivefrogs.content.transfer;

import java.util.Objects;
import java.util.function.Predicate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Forward adapter from a legacy single-tank {@link FluidTank} to the 26.1
 * {@link ResourceHandler}&lt;{@link FluidResource}&gt; capability surface.
 * NeoForge ships only the reverse bridge ({@code IFluidHandler.of(ResourceHandler)}),
 * so the port hand-writes this one and wraps it at the capability-registration
 * boundary, leaving the appliance tanks and their cook loops untouched.
 *
 * <p><b>Transaction semantics (why snapshot-before-mutate).</b> The 26.1 transfer
 * API has no simulate flag: an insert/extract mutates the tank immediately and the
 * move is unwound by aborting the {@link TransactionContext}. Read-your-writes is
 * required, so this adapter mutates the tank's {@link FluidStack} in place now
 * rather than deferring to commit. A single {@link SnapshotJournal} copies the
 * tank's stack on first touch in a transaction ({@code createSnapshot}) and writes
 * it back if the transaction aborts ({@code revertToSnapshot}); the change callback
 * runs once on commit via {@code onRootCommit}, so an aborted probe leaves no
 * spurious {@code setChanged} / sync. {@code FluidTank.setFluid} fires no callback
 * of its own, which is exactly why the journal owns that responsibility.
 *
 * <p>{@code allowInsert} / {@code allowExtract} gate the two directions: the
 * Crucible exposes an extract-only tank, the Casting Mold a fill-only one. An
 * optional {@code insertFilter} layers a recipe-level acceptance check on top of
 * the tank's own {@code isFluidValid} (the Mold's molten-only gate).
 */
public final class FluidTankResourceHandler implements ResourceHandler<FluidResource> {

    private final FluidTank tank;
    private final Predicate<FluidStack> insertFilter;
    private final boolean allowInsert;
    private final boolean allowExtract;
    private final Runnable onChanged;
    private final TankJournal journal = new TankJournal();

    public FluidTankResourceHandler(
            FluidTank tank,
            Predicate<FluidStack> insertFilter,
            boolean allowInsert,
            boolean allowExtract,
            Runnable onChanged) {
        this.tank = tank;
        this.insertFilter = insertFilter;
        this.allowInsert = allowInsert;
        this.allowExtract = allowExtract;
        this.onChanged = onChanged;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public FluidResource getResource(int index) {
        Objects.checkIndex(index, size());
        return FluidResource.of(tank.getFluid());
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, size());
        return tank.getFluid().getAmount();
    }

    @Override
    public long getCapacityAsLong(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        return tank.getCapacity();
    }

    @Override
    public boolean isValid(int index, FluidResource resource) {
        Objects.checkIndex(index, size());
        return allowInsert && accepts(resource);
    }

    private boolean accepts(FluidResource resource) {
        FluidStack probe = resource.toStack(1);
        if (!tank.isFluidValid(probe)) {
            return false;
        }
        return insertFilter == null || insertFilter.test(probe);
    }

    @Override
    public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!allowInsert || !accepts(resource)) {
            return 0;
        }
        FluidStack current = tank.getFluid();
        if (!current.isEmpty() && !resource.matches(current)) {
            return 0;
        }
        int accepted = Math.min(amount, tank.getCapacity() - current.getAmount());
        if (accepted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        if (current.isEmpty()) {
            tank.setFluid(resource.toStack(accepted));
        } else {
            FluidStack grown = current.copy();
            grown.setAmount(current.getAmount() + accepted);
            tank.setFluid(grown);
        }
        return accepted;
    }

    @Override
    public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (!allowExtract) {
            return 0;
        }
        FluidStack current = tank.getFluid();
        if (current.isEmpty() || !resource.matches(current)) {
            return 0;
        }
        int extracted = Math.min(amount, current.getAmount());
        if (extracted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        if (extracted >= current.getAmount()) {
            tank.setFluid(FluidStack.EMPTY);
        } else {
            FluidStack shrunk = current.copy();
            shrunk.setAmount(current.getAmount() - extracted);
            tank.setFluid(shrunk);
        }
        return extracted;
    }

    /** Single-tank journal: snapshot the stack, revert it on abort, fire the change callback once on commit. */
    private final class TankJournal extends SnapshotJournal<FluidStack> {

        @Override
        protected FluidStack createSnapshot() {
            return tank.getFluid().copy();
        }

        @Override
        protected void revertToSnapshot(FluidStack snapshot) {
            tank.setFluid(snapshot);
        }

        @Override
        protected void onRootCommit(FluidStack originalState) {
            if (onChanged != null) {
                onChanged.run();
            }
        }
    }
}
