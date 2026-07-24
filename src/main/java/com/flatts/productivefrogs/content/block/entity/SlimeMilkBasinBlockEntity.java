package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The Slime Milk Basin's engine: a container block that holds one bucket's worth
 * of Slime Milk INSIDE the block (never as a world fluid) and runs the shared
 * {@link MilkSpawnEconomy} - budget, catalysts, cadence - spawning that variant's
 * Resource Slimes into the 26 adjacent cells.
 *
 * <p><b>Additive.</b> The placeable {@link SlimeMilkSourceBlock} is unchanged and
 * stays; the Basin is the automation-friendly form of the same thing. Spawning
 * goes through {@link SlimeMilkSourceBlock#createSlimeForVariant} - the one seam
 * the source and the Terrarium already share (sentinels, custom {@code
 * spawn_entity}, category resolution) - so the two can never drift.
 *
 * <p>Deltas from the placed source, all deliberate:
 * <ul>
 *   <li><b>The block persists.</b> A depleted source drains to air; a depleted
 *       Basin just empties, ready for the next bucket (by hand or by pipe) - that
 *       is the Basin's whole reason to exist as an automation block.</li>
 *   <li><b>BE-tick countdown</b> (the Churn's shape) instead of scheduled block
 *       ticks - the Basin is an EntityBlock with a ticker anyway.</li>
 *   <li><b>Spawn placement is horizontal, then above, then below</b> - the source
 *       prefers below before above. Any of the 26 adjacent cells is eligible and
 *       the first the slime actually FITS in wins (collision-checked), so water
 *       counts as free space and a waterlogged Basin spawns straight into the
 *       surrounding pool without disturbing it.</li>
 *   <li><b>Catalysts apply by right-click</b> as well as by drop-in (a solid block
 *       has no pool to drop items into); a charged bucket's carried catalyst stats
 *       also apply on fill and drain back onto the bucket - the same component
 *       round-trip as the source.</li>
 *   <li><b>Boss-tier milk is refused.</b> A {@code spawn_catalyst} variant is
 *       altar-gated (#184) and the Basin cannot reproduce the source's six-face
 *       gate, exactly like the Terrarium Controller - so it must not become an
 *       altar bypass.</li>
 * </ul>
 *
 * <p>On this line the variant rides the <b>fluid identity</b> (v1.8 per-variant
 * milk fluids), not a component, so the pipe intake resolves it with
 * {@link PFVariantMilk#variantOf} and the catalyst/budget set still travels on the
 * {@link FluidStack}'s components via {@link MilkCharge}.
 */
public class SlimeMilkBasinBlockEntity extends BlockEntity {

    /** Clamp on stored spawns - shared with the milk source BE so the two agree. */
    public static final int MAX_STORED_SPAWNS = SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS;

    /** One bucket in, one bucket out: the Basin is a single-charge container. */
    public static final int CAPACITY_MB = 1000;

    /**
     * Offsets to the 26 neighbours in the Basin's preference order: the horizontal
     * ring first (cardinals then diagonals), then the plane above, then below.
     * Horizontal-first keeps spawns beside the Basin, where a frog at the same
     * level can reach them, before stacking vertically.
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
    private ResourceLocation containedVariant;

    private int spawnsRemaining = 0;
    private int spawnsCapacity = 0;
    private int speedLevel = 0;
    private int quantityLevel = 0;
    private boolean infinite = false;

    /** Countdown to the next spawn event; a 0 total means no interval has started. */
    private int intervalRemaining = 0;
    private int intervalTotal = 0;

    private final BasinFluidIntake fluidIntake = new BasinFluidIntake();

    public SlimeMilkBasinBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLIME_MILK_BASIN.get(), pos, state);
    }

    // ---- charge state ---------------------------------------------------

    @Nullable
    public ResourceLocation getContainedVariant() {
        return containedVariant;
    }

    public boolean isCharged() {
        return containedVariant != null;
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

    /** The variant a Slime Milk bucket carries, or null when it isn't one. */
    @Nullable
    public static ResourceLocation variantFromBucket(ItemStack stack) {
        return stack.getItem() instanceof SlimeMilkBucketItem milk ? milk.variantId() : null;
    }

    /**
     * Whether this Basin may hold {@code variant} at all. Boss-tier
     * ({@code spawn_catalyst}) variants are altar-gated and refused outright -
     * the Basin can't reproduce the source's six-face catalyst gate, so it must
     * not become an altar bypass (#184). Same ruling, same seam, as
     * {@code TerrariumControllerBlockEntity#canAccept}.
     */
    public boolean acceptsVariant(Level level, ResourceLocation variant) {
        return !SlimeMilkSourceBlock.variantRequiresCatalyst(level, variant);
    }

    /**
     * Charge from a bucket's components: the variant (which rides the item
     * identity on this line) plus the same budget/catalyst set the milk source
     * round-trips. Only meaningful while empty.
     */
    public void chargeFromBucket(ResourceLocation variant, ItemStack bucket) {
        MilkCharge charge = MilkCharge.fromBucket(bucket);
        charge(variant, charge.spawnsRemaining(), charge.capacity(),
            charge.speed(), charge.quantity(), charge.infinite());
    }

    /** Charge directly (the bucket path, the pipe path, and tests). */
    public void charge(ResourceLocation variant, int remaining, int capacity,
            int speed, int quantity, boolean infinite) {
        this.containedVariant = variant;
        this.spawnsRemaining = Mth.clamp(remaining, 0, MAX_STORED_SPAWNS);
        this.spawnsCapacity = Mth.clamp(Math.max(capacity, this.spawnsRemaining), 0, MAX_STORED_SPAWNS);
        this.speedLevel = Mth.clamp(speed, 0, PFConfig.catalystMaxSpeedLevel());
        this.quantityLevel = Mth.clamp(quantity, 0, PFConfig.catalystMaxQuantityLevel());
        this.infinite = infinite;
        this.intervalRemaining = 0;
        this.intervalTotal = 0;
        setChanged();
        syncToClients();
    }

    /**
     * Drain the charge back into a freshly-minted bucket (the empty-bucket
     * right-click), stamping the current budget and catalysts so nothing is lost
     * - the Basin-side twin of {@code SlimeMilkSourceBlock#pickupBlock}. Empties
     * the Basin. Returns EMPTY when uncharged or the variant has no milk fluid.
     */
    public ItemStack drainToBucket() {
        if (containedVariant == null) {
            return ItemStack.EMPTY;
        }
        net.minecraft.world.item.Item bucketItem = PFVariantMilk.bucket(containedVariant);
        if (bucketItem == null) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = new ItemStack(bucketItem);
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
        containedVariant = null;
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
     * Apply one catalyst - right-click parity with dropping it into a source pool.
     * Same rules as {@link SlimeMilkSourceBlockEntity#applyCatalyst}: returns false
     * when redundant or already maxed, so the caller leaves the item for the player
     * rather than eating it for no effect.
     */
    public boolean applyCatalyst(MilkCatalyst catalyst) {
        if (containedVariant == null) {
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

    /** The block ticker's body: countdown, then one spawn event per interval. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, SlimeMilkBasinBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (be.containedVariant == null) {
            be.resetInterval();
            return;
        }
        ResourceLocation variant = be.containedVariant;
        boolean depleting = depletionEnabled() && !be.infinite;
        if (depleting && be.spawnsRemaining <= 0) {
            // Spent: empty the Basin. The BLOCK persists - that is the point.
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "basin @%s: %s charge depleted, emptied", pos, variant));
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
            // Countdown ticks are pure in-memory state - no setChanged here, or a
            // charged Basin dirties its chunk 20x a second. Losing a partial
            // countdown on an unclean stop just restarts the interval.
            be.intervalRemaining--;
            return;
        }
        // The expensive gates run ONCE per spawn event, not every tick. The
        // defensive variant re-check covers a datapack reload turning a held
        // variant boss-tier: the Basin goes inert, keeping its charge.
        if (!be.acceptsVariant(serverLevel, variant)) {
            be.resetInterval();
            return;
        }
        // Density cap: pause WITHOUT spending budget so an Endless or Rapid Basin
        // can't flood the server faster than frogs eat.
        if (PFConfig.spawnCapEnabled() && SlimeMilkSourceBlock.isAreaCrowded(serverLevel, pos, variant)) {
            PFDebug.logOnce(PFDebug.Area.MILK_SOURCE, "basincap#" + pos, () -> String.format(
                "basin @%s: paused, >= %d nearby %s slimes (cap)", pos, PFConfig.maxNearbySlimes(), variant));
            be.resetInterval();
            return;
        }

        int batch = MilkSpawnEconomy.batchQuantity(be.quantityLevel);
        int spawned = 0;
        for (int i = 0; i < batch; i++) {
            if (be.spawnOne(serverLevel, pos, variant)) {
                spawned++;
            }
        }
        if (spawned == 0) {
            // Nowhere anything fits - pause without spending, retry next interval.
            be.resetInterval();
            return;
        }
        if (depleting) {
            // One budget per EVENT, not per slime, so Quantity is strictly
            // additive to throughput (the source's rule).
            be.spawnsRemaining = Math.max(0, be.spawnsRemaining - 1);
            if (be.spawnsRemaining <= 0) {
                PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                    "basin @%s: %s charge depleted, emptied", pos, variant));
                be.clearCharge();
                return;
            }
        }
        be.resetInterval();
        be.setChanged();
        // No client sync per spawn event: this line has no Basin renderer, so
        // nothing on the client reads the draining budget (Jade pulls it as
        // server data on its own interval). Syncing here would be a packet per
        // interval per Basin per nearby player for no visible change. Charging
        // and emptying still sync - those change the Basin's identity.
    }

    /**
     * Spawn one slime into the first preference-ordered adjacent cell it actually
     * fits in (collision-checked, so water cells count as free). Returns false
     * when the slime can't be created or nothing fits.
     */
    private boolean spawnOne(ServerLevel level, BlockPos pos, ResourceLocation variant) {
        Slime slime = SlimeMilkSourceBlock.createSlimeForVariant(level, variant);
        if (slime == null) {
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "basin @%s: slime create failed for variant=%s (skip)", pos, variant));
            return false;
        }
        slime.setSize(1, true);
        for (int[] off : SPAWN_OFFSETS) {
            BlockPos cell = pos.offset(off[0], off[1], off[2]);
            if (level.getBlockState(cell).blocksMotion()) {
                continue;
            }
            slime.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5,
                level.getRandom().nextFloat() * 360F, 0F);
            if (!level.noCollision(slime)) {
                continue;
            }
            level.addFreshEntity(slime);
            PFDebug.log(PFDebug.Area.MILK_SOURCE, () -> String.format(
                "basin @%s: spawned %s slime at %s", pos, variant, cell));
            return true;
        }
        slime.discard();
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
     * Test seam: arm the countdown so the NEXT {@link #serverTick} fires the spawn
     * event immediately. The real interval is config-scale and would blow a
     * GameTest's tick budget, exactly like the milk source tests driving
     * {@code block.tick} directly.
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

    private void syncToClients() {
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ---- pipe fill (Capabilities.FluidHandler.BLOCK) ---------------------

    /**
     * Fill-only fluid intake for pipes: accepts exactly one bucket's worth
     * (1000 mB) of any per-variant Slime Milk while EMPTY, reading the variant off
     * the FLUID identity (v1.8) and the budget/catalysts off the stack's
     * components. The Terrarium Controller's funnel pattern.
     *
     * <p>Drain is a no-op by design - the empty-bucket right-click is the hand
     * drain; automation refills a Basin, it doesn't siphon one.
     */
    public IFluidHandler fluidHandler() {
        return fluidIntake;
    }

    private final class BasinFluidIntake implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            if (containedVariant == null) {
                return FluidStack.EMPTY;
            }
            var fluid = PFVariantMilk.sourceFluid(containedVariant);
            return fluid == null ? FluidStack.EMPTY : new FluidStack(fluid, CAPACITY_MB);
        }

        @Override
        public int getTankCapacity(int tank) {
            return CAPACITY_MB;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            if (containedVariant != null || level == null) {
                return false;
            }
            ResourceLocation variant = PFVariantMilk.variantOf(stack.getFluid());
            return variant != null && acceptsVariant(level, variant);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (containedVariant != null || resource.getAmount() < CAPACITY_MB || level == null) {
                return 0;
            }
            ResourceLocation variant = PFVariantMilk.variantOf(resource.getFluid());
            if (variant == null || !acceptsVariant(level, variant)) {
                return 0;
            }
            if (action.execute()) {
                MilkCharge charge = MilkCharge.fromFluid(resource);
                charge(variant, charge.spawnsRemaining(), charge.capacity(),
                    charge.speed(), charge.quantity(), charge.infinite());
            }
            return CAPACITY_MB;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    // ---- serialization ----------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (containedVariant != null) {
            tag.putString("Contained", containedVariant.toString());
            tag.putInt("SpawnsRemaining", spawnsRemaining);
            tag.putInt("SpawnsCapacity", spawnsCapacity);
            if (speedLevel > 0) {
                tag.putInt("SpeedLevel", speedLevel);
            }
            if (quantityLevel > 0) {
                tag.putInt("QuantityLevel", quantityLevel);
            }
            if (infinite) {
                tag.putBoolean("Infinite", true);
            }
            tag.putInt("IntervalRemaining", intervalRemaining);
            tag.putInt("IntervalTotal", intervalTotal);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        containedVariant = tag.contains("Contained", Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString("Contained")) : null;
        spawnsRemaining = Mth.clamp(tag.getInt("SpawnsRemaining"), 0, MAX_STORED_SPAWNS);
        spawnsCapacity = Mth.clamp(tag.getInt("SpawnsCapacity"), 0, MAX_STORED_SPAWNS);
        speedLevel = Math.max(0, tag.getInt("SpeedLevel"));
        quantityLevel = Math.max(0, tag.getInt("QuantityLevel"));
        infinite = tag.getBoolean("Infinite");
        intervalTotal = Math.max(0, tag.getInt("IntervalTotal"));
        intervalRemaining = Math.max(0, Math.min(tag.getInt("IntervalRemaining"), intervalTotal));
    }

    /**
     * Sync the held variant only - what the client needs to tell a charged Basin
     * from an empty one. The budget deliberately stays server-side: this line has
     * no Basin renderer to draw a falling fluid level, and Jade reads the counts
     * as server data on its own interval. (If a renderer is ever added, the
     * budget goes here and the spawn loop syncs again.)
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        if (containedVariant != null) {
            tag.putString("Contained", containedVariant.toString());
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
