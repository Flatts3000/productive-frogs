package com.flatts.productivefrogs.content.transfer;

import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Forward adapter from a legacy receive-only RF buffer to the 26.1
 * {@link EnergyHandler} capability surface. NeoForge ships only the reverse bridge
 * ({@code IEnergyStorage.of(EnergyHandler)}), so the port hand-writes this one and
 * wraps it at the capability-registration boundary, leaving the Alembic / Distiller
 * {@code EnergyStorage} subclasses and their distill loops untouched.
 *
 * <p><b>Why an indirection interface.</b> The 26.1 transfer API has no simulate
 * flag, so to support read-your-writes plus abort-rollback the adapter must read
 * and <em>write</em> the stored energy directly. A receive-only
 * {@code net.neoforged.neoforge.energy.EnergyStorage} cannot be reverted through
 * its public API (its {@code extractEnergy} is gated to zero), and its {@code energy}
 * field is {@code protected} and out of this package's reach. So the backing buffer
 * implements {@link Source} - a thin get/set window onto its energy - which the
 * adapter snapshots and rolls back. Forward mutation goes through {@link Source#setEnergy}
 * directly (bypassing the buffer's own {@code receiveEnergy} so nothing fires mid
 * transaction); the change callback runs once on commit via {@code onRootCommit}.
 *
 * <p>These buffers are receive-only by design (cables fill them; the machine spends
 * the energy internally), so {@link #extract} is always a no-op. Insert is clamped
 * by both the remaining capacity and the buffer's per-operation receive limit.
 */
public final class ReceiveOnlyEnergyHandler implements EnergyHandler {

    /** The get/set window a backing RF buffer exposes so this adapter can journal it. */
    public interface Source {
        int currentEnergy();

        void setEnergy(int amount);

        int energyCapacity();

        /** Maximum energy accepted in a single {@link EnergyHandler#insert} call. */
        int maxInsertPerOp();

        /** Fired once, on root-transaction commit, when the stored energy actually changed. */
        void onEnergyCommitted();
    }

    private final Source source;
    private final EnergyJournal journal = new EnergyJournal();

    public ReceiveOnlyEnergyHandler(Source source) {
        this.source = source;
    }

    @Override
    public long getAmountAsLong() {
        return source.currentEnergy();
    }

    @Override
    public long getCapacityAsLong() {
        return source.energyCapacity();
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        int room = Math.min(source.energyCapacity() - source.currentEnergy(), Math.min(amount, source.maxInsertPerOp()));
        if (room <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        source.setEnergy(source.currentEnergy() + room);
        return room;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        // Receive-only: cables cannot pull power back out; the machine spends it internally.
        return 0;
    }

    private final class EnergyJournal extends SnapshotJournal<Integer> {

        @Override
        protected Integer createSnapshot() {
            return source.currentEnergy();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            source.setEnergy(snapshot);
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (originalState != source.currentEnergy()) {
                source.onEnergyCommitted();
            }
        }
    }
}
