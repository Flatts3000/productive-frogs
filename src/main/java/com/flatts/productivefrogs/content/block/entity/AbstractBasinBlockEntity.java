package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * The shared Basin engine (#281, predation Phase 3): a waterloggable container
 * block that holds one bucket's worth of a spawning fluid INSIDE the block
 * (never as a world fluid) and runs the {@link MilkSpawnEconomy} - budget,
 * catalysts, cadence - spawning into the 26 adjacent cells. The Mob Slurry
 * Basin and the Slime Milk Basin are the two concrete flavours; the parity
 * principle ("slurry and milk mostly share features") is enforced by this
 * class BEING the shared feature set. Subclasses supply only what differs:
 * which bucket charges it, what entity a spawn event produces, and what
 * counts as "crowded".
 *
 * <p>Deltas from the placed milk source, all deliberate:
 * <ul>
 *   <li><b>The block persists.</b> A depleted source drains to air; a depleted
 *       Basin just empties, ready for the next bucket (by hand or pipe) - that
 *       is the Basin's whole reason to exist as an automation block.</li>
 *   <li><b>BE-tick countdown</b> (the Churn's shape) instead of scheduled
 *       block ticks - the Basin is an EntityBlock with a ticker anyway.</li>
 *   <li><b>Spawn placement order is horizontal, then above, then below</b>
 *       (maintainer ruling) - the source prefers below before above. Any of
 *       the 26 adjacent cells is eligible; the first cell the spawned entity
 *       actually FITS in (collision-checked, so tall mobs and water cells
 *       both work) wins. Water counts as free - a waterlogged Basin in a pool
 *       spawns straight into the surrounding water without disturbing it.</li>
 *   <li><b>Catalysts apply by right-click</b> (a solid block has no pool to
 *       drop items into); a charged bucket's carried catalyst stats also apply
 *       on fill, and drain back onto the bucket - the same component
 *       round-trip as the source.</li>
 * </ul>
 */
public abstract class AbstractBasinBlockEntity extends BlockEntity {

    /** Clamp on stored spawns (the milk source BE's bound). */
    public static final int MAX_STORED_SPAWNS = SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS;

    /**
     * Offsets to the 26 neighbours in the Basin's preference order (maintainer
     * ruling): the horizontal ring first (cardinals then diagonals), then the
     * above plane, then the below plane.
     */
    private static final int[][] SPAWN_OFFSETS = {
        {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
        {1, 0, 1}, {1, 0, -1}, {-1, 0, 1}, {-1, 0, -1},
        {0, 1, 0}, {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1},
        {1, 1, 1}, {1, 1, -1}, {-1, 1, 1}, {-1, 1, -1},
        {0, -1, 0}, {1, -1, 0}, {-1, -1, 0}, {0, -1, 1}, {0, -1, -1},
        {1, -1, 1}, {1, -1, -1}, {-1, -1, 1}, {-1, -1, -1},
    };

    @Nullable
    private Identifier containedKey;

    private int spawnsRemaining = 0;
    private int spawnsCapacity = 0;
    private int speedLevel = 0;
    private int quantityLevel = 0;
    private boolean infinite = false;

    /** Countdown to the next spawn event; 0 with a 0 total = no interval started. */
    private int intervalRemaining = 0;
    private int intervalTotal = 0;

    protected AbstractBasinBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ---- what the flavours define --------------------------------------

    /** The key a matching charged bucket carries, or null when {@code stack} isn't one. */
    @Nullable
    public abstract Identifier keyFromBucket(ItemStack stack);

    /** Whether this Basin may hold {@code key} at all (e.g. boss milk/slurry is refused). */
    public abstract boolean acceptsKey(ServerLevel level, Identifier key);

    /** Mint the charged bucket for {@link #drainToBucket} (key stamped; budget added by the caller). */
    protected abstract ItemStack mintBucket(Identifier key);

    /** The fluid this Basin accepts through its pipe-fill capability. */
    public abstract net.minecraft.world.level.material.Fluid pipeFluid();

    /** The component the pipe fluid carries its key on. */
    public abstract net.minecraft.core.component.DataComponentType<Identifier> pipeKeyComponent();

    /** Create (unpositioned) the entity one spawn produces, or null to skip. */
    @Nullable
    protected abstract Entity createSpawnEntity(ServerLevel level, Identifier key);

    /** Whether the area is already saturated with this Basin's produce (pause, don't spend). */
    protected abstract boolean isCrowded(ServerLevel level, BlockPos pos, Identifier key);

    /** Config master for this Basin's whole system (predators / general). */
    protected abstract boolean systemEnabled();

    // ---- charge state ---------------------------------------------------

    @Nullable
    public Identifier getContainedKey() {
        return containedKey;
    }

    public boolean isCharged() {
        return containedKey != null;
    }

    public int getSpawnsRemaining() {
        return spawnsRemaining;
    }

    public int getSpawnsCapacity() {
        return Math.max(spawnsCapacity, spawnsRemaining);
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public int getQuantityLevel() {
        return quantityLevel;
    }

    public boolean isInfinite() {
        return infinite;
    }

    /**
     * Charge from a bucket's components: the key plus the same budget/catalyst
     * component set the milk source round-trips ({@code seedIfUnset} semantics
     * for an unstamped, freshly-produced bucket). Only callable while empty.
     */
    public void chargeFrom(Identifier key, ItemStack bucket) {
        Integer remaining = bucket.get(PFDataComponents.SPAWNS_REMAINING.get());
        Integer capacity = bucket.get(PFDataComponents.MILK_CAPACITY.get());
        Integer speed = bucket.get(PFDataComponents.MILK_SPEED.get());
        Integer quantity = bucket.get(PFDataComponents.MILK_QUANTITY.get());
        Boolean inf = bucket.get(PFDataComponents.MILK_INFINITE.get());
        charge(key,
            remaining != null ? remaining : MilkSpawnEconomy.defaultSpawnCount(),
            capacity != null ? capacity : MilkSpawnEconomy.defaultSpawnCount(),
            speed != null ? speed : 0,
            quantity != null ? quantity : 0,
            Boolean.TRUE.equals(inf));
    }

    /** Charge directly (the player bucket path + tests). Only meaningful while empty. */
    public void charge(Identifier key, int remaining, int capacity, int speed, int quantity, boolean infinite) {
        chargeRaw(key, remaining, capacity, speed, quantity, infinite);
        setChanged();
        syncToClients();
    }

    /**
     * Field-only charge for the TRANSACTIONAL pipe intake: no {@code setChanged}
     * and no client sync here - those are irreversible side effects that must not
     * escape an aborted transaction, so the intake's journal fires them once in
     * {@code onRootCommit} (review finding: the old path synced pre-commit and
     * then double-synced on commit).
     */
    private void chargeRaw(Identifier key, int remaining, int capacity, int speed, int quantity, boolean infinite) {
        this.containedKey = key;
        this.spawnsRemaining = Mth.clamp(remaining, 0, MAX_STORED_SPAWNS);
        this.spawnsCapacity = Mth.clamp(Math.max(capacity, this.spawnsRemaining), 0, MAX_STORED_SPAWNS);
        this.speedLevel = Mth.clamp(speed, 0, PFConfig.catalystMaxSpeedLevel());
        this.quantityLevel = Mth.clamp(quantity, 0, PFConfig.catalystMaxQuantityLevel());
        this.infinite = infinite;
        this.intervalRemaining = 0;
        this.intervalTotal = 0;
    }

    /**
     * Drain the charge back into a freshly-minted bucket (the empty-bucket
     * right-click), stamping the key + current budget so nothing is lost -
     * the Basin-side twin of {@code SlimeMilkSourceBlock.pickupBlock}.
     * Empties the Basin. Returns EMPTY when uncharged.
     */
    public ItemStack drainToBucket() {
        if (containedKey == null) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = mintBucket(containedKey);
        bucket.set(PFDataComponents.SPAWNS_REMAINING.get(), spawnsRemaining);
        bucket.set(PFDataComponents.MILK_CAPACITY.get(), getSpawnsCapacity());
        if (speedLevel > 0) {
            bucket.set(PFDataComponents.MILK_SPEED.get(), speedLevel);
        }
        if (quantityLevel > 0) {
            bucket.set(PFDataComponents.MILK_QUANTITY.get(), quantityLevel);
        }
        if (infinite) {
            bucket.set(PFDataComponents.MILK_INFINITE.get(), true);
        }
        clearCharge();
        return bucket;
    }

    private void clearCharge() {
        containedKey = null;
        spawnsRemaining = 0;
        spawnsCapacity = 0;
        speedLevel = 0;
        quantityLevel = 0;
        infinite = false;
        intervalRemaining = 0;
        intervalTotal = 0;
        setChanged();
        syncToClients();
    }

    /**
     * Apply one catalyst (right-click parity with dropping it into a source
     * pool). Same rules as {@link SlimeMilkSourceBlockEntity#applyCatalyst}:
     * returns false when redundant/maxed so the caller leaves the item.
     */
    public boolean applyCatalyst(MilkCatalyst catalyst) {
        if (containedKey == null) {
            return false;
        }
        boolean applied = switch (catalyst) {
            case COUNT -> {
                if (spawnsRemaining >= MAX_STORED_SPAWNS) {
                    yield false;
                }
                int added = PFConfig.catalystCountPer();
                spawnsRemaining = Mth.clamp(spawnsRemaining + added, 0, MAX_STORED_SPAWNS);
                spawnsCapacity = Mth.clamp(spawnsCapacity + added, 0, MAX_STORED_SPAWNS);
                yield true;
            }
            case SPEED -> {
                if (speedLevel >= PFConfig.catalystMaxSpeedLevel()) {
                    yield false;
                }
                speedLevel++;
                yield true;
            }
            case QUANTITY -> {
                if (quantityLevel >= PFConfig.catalystMaxQuantityLevel()) {
                    yield false;
                }
                quantityLevel++;
                yield true;
            }
            case INFINITE -> {
                if (infinite) {
                    yield false;
                }
                infinite = true;
                yield true;
            }
        };
        if (applied) {
            setChanged();
            syncToClients();
        }
        return applied;
    }

    // ---- the spawn loop --------------------------------------------------

    /** The shared serverTick body; each flavour's block ticker delegates here. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, AbstractBasinBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!be.systemEnabled() || be.containedKey == null) {
            be.resetInterval();
            return;
        }
        Identifier key = be.containedKey;
        boolean depleting = depletionEnabled() && !be.infinite;
        if (depleting && be.spawnsRemaining <= 0) {
            // Spent: empty the Basin (the block persists - that is the point).
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "basin @%s: %s charge depleted, emptied", pos, key));
            be.clearCharge();
            return;
        }
        if (be.intervalTotal <= 0) {
            be.intervalTotal = MilkSpawnEconomy.intervalTicks(be.speedLevel, level.getRandom());
            be.intervalRemaining = be.intervalTotal;
            be.setChanged();
            return;
        }
        if (be.intervalRemaining > 0) {
            // Countdown ticks are pure in-memory state: no setChanged here (it
            // dirtied the chunk 20x/sec per basin - review finding). Losing a
            // partial countdown on an unclean stop just restarts the interval.
            be.intervalRemaining--;
            return;
        }
        // The expensive gates run ONCE per spawn event, not every tick (the
        // milk source's scheduled-tick posture; review finding: these ran 20x/sec
        // per charged basin). Defensive key re-check (datapack reload / tampered
        // NBT): an unacceptable key goes inert, charge kept. Density cap: pause
        // WITHOUT spending budget.
        if (!be.acceptsKey(serverLevel, key)) {
            be.resetInterval();
            return;
        }
        if (PFConfig.spawnCapEnabled() && be.isCrowded(serverLevel, pos, key)) {
            be.resetInterval();
            return;
        }

        // Fire: spawn the batch (Quantity catalyst), pay ONE budget per event.
        int batch = MilkSpawnEconomy.batchQuantity(be.quantityLevel);
        int spawned = 0;
        for (int i = 0; i < batch; i++) {
            if (be.spawnOne(serverLevel, pos, key)) {
                spawned++;
            }
        }
        if (spawned == 0) {
            // Nowhere to fit anything - pause without spending, retry next interval.
            be.resetInterval();
            return;
        }
        if (depleting) {
            be.spawnsRemaining = Math.max(0, be.spawnsRemaining - 1);
            if (be.spawnsRemaining <= 0) {
                PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                    "basin @%s: %s charge depleted, emptied", pos, key));
                be.clearCharge();
                return;
            }
        }
        be.resetInterval();
        be.setChanged();
        // Sync the drained budget so the rendered fluid level drops with it
        // (one packet per spawn EVENT - the interval cadence, not per tick).
        be.syncToClients();
    }

    /**
     * Spawn one entity into the first preference-ordered adjacent cell it
     * actually fits in (collision-checked, so a 3-block enderman needs the
     * headroom and an axolotl can land in a water cell). Returns false when
     * the entity can't be created or nothing fits.
     */
    private boolean spawnOne(ServerLevel level, BlockPos pos, Identifier key) {
        Entity entity = createSpawnEntity(level, key);
        if (entity == null) {
            return false;
        }
        for (int[] off : SPAWN_OFFSETS) {
            BlockPos cell = pos.offset(off[0], off[1], off[2]);
            if (level.getBlockState(cell).blocksMotion()) {
                continue;
            }
            entity.snapTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5,
                level.getRandom().nextFloat() * 360F, 0F);
            if (!level.noCollision(entity)) {
                continue;
            }
            level.addFreshEntity(entity);
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "basin @%s: spawned %s at %s", pos, key, cell));
            return true;
        }
        entity.discard();
        return false;
    }

    private void resetInterval() {
        if (intervalTotal != 0 || intervalRemaining != 0) {
            intervalTotal = 0;
            intervalRemaining = 0;
            setChanged();
        }
    }

    /**
     * Test seam: arm the countdown so the NEXT {@link #serverTick} fires the
     * spawn event immediately (the GameTest equivalent of the milk source
     * tests calling {@code block.tick} directly - the random interval is
     * config-scale and would blow the test budget).
     */
    @org.jetbrains.annotations.VisibleForTesting
    public void forceReadyToFire() {
        intervalTotal = 1;
        intervalRemaining = 0;
        setChanged();
    }

    /** Shares the source's depletion switch INCLUDING its GameTest override hook. */
    public static boolean depletionEnabled() {
        Boolean override = SlimeMilkSourceBlock.depletionEnabledOverride;
        if (override != null) {
            return override;
        }
        return !PFConfig.SPEC.isLoaded() || PFConfig.DEPLETION_ENABLED.get();
    }

    protected void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- pipe fill (Capabilities.Fluid.BLOCK) ----------------------------

    /**
     * Fill-only fluid intake for pipes: accepts exactly one bucket's worth
     * (1000 mB) of the matching component-carrying fluid while EMPTY, reading
     * the key + budget/catalyst components off the {@code FluidResource} (the
     * Terrarium Controller's funnel pattern). Drain is a no-op - by hand the
     * empty-bucket right-click drains; automation refills, it doesn't siphon.
     */
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> fluidResource() {
        return new BasinFluidIntake();
    }

    /** Snapshot for transaction rollback of everything the intake mutates. */
    private record IntakeSnapshot(@Nullable Identifier key, int remaining, int capacity,
                                  int speed, int quantity, boolean infinite) {}

    private final class BasinFluidIntake
            implements net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.fluid.FluidResource> {

        private final IntakeJournal journal = new IntakeJournal();

        @Override
        public int size() {
            return 1;
        }

        @Override
        public net.neoforged.neoforge.transfer.fluid.FluidResource getResource(int index) {
            return net.neoforged.neoforge.transfer.fluid.FluidResource.EMPTY;
        }

        @Override
        public long getAmountAsLong(int index) {
            return 0L;
        }

        @Override
        public long getCapacityAsLong(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return 1000L;
        }

        @Override
        public boolean isValid(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource) {
            return resource.getFluid() == pipeFluid() && resource.get(pipeKeyComponent()) != null;
        }

        @Override
        public int insert(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            if (amount < 1000 || containedKey != null || !(level instanceof ServerLevel serverLevel)) {
                return 0;
            }
            if (resource.getFluid() != pipeFluid()) {
                return 0;
            }
            Identifier key = resource.get(pipeKeyComponent());
            if (key == null || !acceptsKey(serverLevel, key)) {
                return 0;
            }
            journal.updateSnapshots(transaction);
            Integer remaining = resource.get(PFDataComponents.SPAWNS_REMAINING.get());
            Integer capacity = resource.get(PFDataComponents.MILK_CAPACITY.get());
            Integer speed = resource.get(PFDataComponents.MILK_SPEED.get());
            Integer quantity = resource.get(PFDataComponents.MILK_QUANTITY.get());
            Boolean inf = resource.get(PFDataComponents.MILK_INFINITE.get());
            chargeRaw(key,
                remaining != null ? remaining : MilkSpawnEconomy.defaultSpawnCount(),
                capacity != null ? capacity : MilkSpawnEconomy.defaultSpawnCount(),
                speed != null ? speed : 0,
                quantity != null ? quantity : 0,
                Boolean.TRUE.equals(inf));
            return 1000;
        }

        @Override
        public int extract(int index, net.neoforged.neoforge.transfer.fluid.FluidResource resource, int amount,
                net.neoforged.neoforge.transfer.transaction.TransactionContext transaction) {
            return 0;
        }

        private final class IntakeJournal
                extends net.neoforged.neoforge.transfer.transaction.SnapshotJournal<IntakeSnapshot> {

            @Override
            protected IntakeSnapshot createSnapshot() {
                return new IntakeSnapshot(containedKey, spawnsRemaining, spawnsCapacity,
                    speedLevel, quantityLevel, infinite);
            }

            @Override
            protected void revertToSnapshot(IntakeSnapshot snapshot) {
                containedKey = snapshot.key();
                spawnsRemaining = snapshot.remaining();
                spawnsCapacity = snapshot.capacity();
                speedLevel = snapshot.speed();
                quantityLevel = snapshot.quantity();
                infinite = snapshot.infinite();
            }

            @Override
            protected void onRootCommit(IntakeSnapshot originalState) {
                setChanged();
                syncToClients();
            }
        }
    }

    // ---- serialization ----------------------------------------------------

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (containedKey != null) {
            output.putString("Contained", containedKey.toString());
            output.putInt("SpawnsRemaining", spawnsRemaining);
            output.putInt("SpawnsCapacity", spawnsCapacity);
            if (speedLevel > 0) {
                output.putInt("SpeedLevel", speedLevel);
            }
            if (quantityLevel > 0) {
                output.putInt("QuantityLevel", quantityLevel);
            }
            if (infinite) {
                output.putBoolean("Infinite", true);
            }
            output.putInt("IntervalRemaining", intervalRemaining);
            output.putInt("IntervalTotal", intervalTotal);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String contained = input.getStringOr("Contained", "");
        containedKey = contained.isEmpty() ? null : Identifier.tryParse(contained);
        spawnsRemaining = Mth.clamp(input.getIntOr("SpawnsRemaining", 0), 0, MAX_STORED_SPAWNS);
        spawnsCapacity = Mth.clamp(input.getIntOr("SpawnsCapacity", 0), 0, MAX_STORED_SPAWNS);
        speedLevel = Math.max(0, input.getIntOr("SpeedLevel", 0));
        quantityLevel = Math.max(0, input.getIntOr("QuantityLevel", 0));
        infinite = input.getBooleanOr("Infinite", false);
        intervalTotal = Math.max(0, input.getIntOr("IntervalTotal", 0));
        intervalRemaining = Math.max(0, Math.min(input.getIntOr("IntervalRemaining", 0), intervalTotal));
    }

    // Sync the contained key + the budget to clients: the BasinRenderer draws
    // the held fluid's surface at a height proportional to remaining/capacity
    // (maintainer ruling: the Basin shows the fluid that's in it).
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        if (containedKey != null) {
            tag.putString("Contained", containedKey.toString());
            tag.putInt("SpawnsRemaining", spawnsRemaining);
            tag.putInt("SpawnsCapacity", spawnsCapacity);
            if (infinite) {
                tag.putBoolean("Infinite", true);
            }
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
