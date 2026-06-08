package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.SlimeChurnBlock;
import com.flatts.productivefrogs.content.item.SlimeBucketItem;
import com.flatts.productivefrogs.content.item.SlimeMilkBucketItem;
import com.flatts.productivefrogs.content.menu.SlimeChurnMenu;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity backing the {@link SlimeChurnBlock} (#187): the inverse of the
 * Slime Milker. Takes a per-variant Slime Milk bucket plus empty buckets and
 * produces captured variant Slime Buckets, running the <b>placed-source spawn
 * economy</b> ({@link MilkSpawnEconomy}) rather than a flat cook timer - so a
 * milk bucket spent in the Churn yields exactly what hand-placing it would,
 * catalysts included, just bucketed instead of loose.
 *
 * <p>The economy maps onto the appliance like this:
 * <ul>
 *   <li><b>Cadence</b>: an internal countdown initialized from
 *       {@link MilkSpawnEconomy#intervalTicks} (Speed catalyst applies). The
 *       placed source uses {@code scheduleTick} with the same math.</li>
 *   <li><b>Budget</b>: the milk bucket's own {@code SPAWNS_REMAINING}
 *       component is decremented in place - one per spawn <b>event</b> - so
 *       the bucket visibly drains in its tooltip and a half-spent bucket can
 *       be pulled out and placed in-world to finish there. Components are
 *       seeded on first processing, mirroring the source's
 *       {@code seedIfUnset}. {@code MILK_INFINITE} (or depletion config-off)
 *       means no decrement, ever.</li>
 *   <li><b>Quantity</b>: one event produces a batch of
 *       {@link MilkSpawnEconomy#batchQuantity} slime buckets. Slime Buckets
 *       stack to 1, so the batch drains through the single output slot one
 *       per tick via {@link #pendingBatch} - the budget is paid once at fire
 *       time and the paid-for remainder persists (NBT) until emitted.</li>
 *   <li><b>Pause-without-waste</b> (the source's pause semantics): no empty
 *       bucket, output occupied, or spent-container slot blocked all pause
 *       the churn with the budget untouched. Nothing is ever lost.</li>
 * </ul>
 *
 * <p>Depletion: when the budget hits 0 the milk bucket's empty container
 * ({@code minecraft:bucket}) is moved to {@link SlimeChurnInventory#EMPTY_OUTPUT_SLOT}
 * (the second output, #187) and the milk slot clears for the next bucket.
 */
public class SlimeChurnBlockEntity extends BlockEntity implements MenuProvider {

    /** Indices into the {@link #dataAccess} ContainerData for menu sync. */
    public static final int DATA_INTERVAL_PROGRESS = 0;
    public static final int DATA_INTERVAL_TOTAL = 1;
    public static final int DATA_COUNT = 2;

    /** Countdown to the next spawn event; 0 with a non-zero total = ready to fire. */
    private int intervalRemaining = 0;
    /** Length of the current interval (0 = no interval started). */
    private int intervalTotal = 0;

    /** Paid-for batch still to emit (Quantity catalyst > output stacksTo(1)). */
    private int pendingBatch = 0;
    @Nullable private ResourceLocation pendingVariant;
    @Nullable private Category pendingCategory;

    private final SlimeChurnInventory inventory = new SlimeChurnInventory(this::setChanged);

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_INTERVAL_PROGRESS -> intervalTotal - intervalRemaining;
                case DATA_INTERVAL_TOTAL -> intervalTotal;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            // Client-side sync writes; progress is derived, total is authoritative.
            if (index == DATA_INTERVAL_TOTAL) {
                intervalTotal = value;
            } else if (index == DATA_INTERVAL_PROGRESS) {
                intervalRemaining = intervalTotal - value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public SlimeChurnBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLIME_CHURN.get(), pos, state);
    }

    public SlimeChurnInventory getInventory() {
        return inventory;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    public int getIntervalRemaining() {
        return intervalRemaining;
    }

    public int getIntervalTotal() {
        return intervalTotal;
    }

    public int getPendingBatch() {
        return pendingBatch;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SlimeChurnBlockEntity be) {
        // 1. A paid-for batch outranks everything: emit one per tick as the
        //    output slot frees. The next interval doesn't start until the
        //    batch drains, mirroring "one event at a time" at the source.
        if (be.pendingBatch > 0) {
            boolean emitted = be.tryEmitOne(pos);
            setWorking(level, pos, state, emitted);
            return;
        }

        ItemStack milk = be.inventory.getStackInSlot(SlimeChurnInventory.MILK_SLOT);
        if (!(milk.getItem() instanceof SlimeMilkBucketItem milkItem)) {
            be.resetInterval();
            setWorking(level, pos, state, false);
            return;
        }
        ResourceLocation variantId = milkItem.variantId();
        SlimeVariant variant = level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).map(r -> r.get(variantId)).orElse(null);
        if (variant == null) {
            // Bucket of a variant the registry doesn't know (removed datapack
            // entry) - fail closed, like the Milker's no-variant input.
            PFDebug.logOnce(PFDebug.Area.CHURN, "novariant#" + pos,
                () -> String.format("churn @%s fail-closed: unknown variant %s", pos, variantId));
            be.resetInterval();
            setWorking(level, pos, state, false);
            return;
        }

        // 2. Seed the budget components on first processing so the bucket
        //    drains visibly and survives being pulled out half-spent
        //    (mirrors SlimeMilkSourceBlockEntity.seedIfUnset).
        seedBudgetIfAbsent(milk, be);

        boolean depleting = depletionEnabled() && !isInfinite(milk);
        if (depleting && remaining(milk) <= 0) {
            // Spent bucket still in the slot (retire was blocked earlier, or a
            // 0-remaining bucket was inserted): keep trying to retire it.
            be.tryRetireSpentMilk(pos);
            setWorking(level, pos, state, false);
            return;
        }

        // 3. Countdown. Start a fresh interval if none is running.
        if (be.intervalTotal <= 0) {
            be.startInterval(level, milk);
        }
        if (be.intervalRemaining > 0) {
            be.intervalRemaining--;
            be.setChanged();
            setWorking(level, pos, state, true);
            return;
        }

        // 4. Ready to fire. Hold here (budget untouched) until the event can
        //    actually realize: one empty bucket + a free slime-output slot.
        if (!be.canEmitOne()) {
            setWorking(level, pos, state, false);
            return;
        }

        // 5. Fire the spawn event: pay one budget, queue the batch, emit the
        //    first slime bucket this tick.
        be.pendingBatch = MilkSpawnEconomy.batchQuantity(quantityLevel(milk));
        be.pendingVariant = variantId;
        be.pendingCategory = variant.category();
        if (depleting) {
            int newRemaining = Math.max(0, remaining(milk) - 1);
            milk.set(PFDataComponents.SPAWNS_REMAINING.get(), newRemaining);
        }
        PFDebug.log(PFDebug.Area.CHURN, () -> String.format(
            "churn @%s: fired %s event, batch=%d, remaining=%d", pos, variantId,
            be.pendingBatch, remaining(milk)));
        be.tryEmitOne(pos);
        if (depleting && remaining(milk) <= 0) {
            be.tryRetireSpentMilk(pos);
        }
        be.resetInterval();
        be.setChanged();
        setWorking(level, pos, state, true);
        level.playSound(null, pos, SoundEvents.BUCKET_FILL_FISH, SoundSource.BLOCKS,
            0.8F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    /**
     * Emit one slime bucket from the pending batch: consume one empty bucket,
     * write one variant-stamped Slime Bucket to the output. Returns false
     * (and leaves the batch intact) when the containers or output slot aren't
     * available - the paid-for batch waits, it is never voided.
     */
    private boolean tryEmitOne(BlockPos pos) {
        if (pendingBatch <= 0) {
            return false;
        }
        if (pendingVariant == null || pendingCategory == null) {
            // Unrecoverable pending state (corrupt save) - drop it rather than
            // stall the churn forever.
            PFDebug.logOnce(PFDebug.Area.CHURN, "badpending#" + pos,
                () -> String.format("churn @%s: dropped %d pending with no variant", pos, pendingBatch));
            pendingBatch = 0;
            setChanged();
            return false;
        }
        if (!canEmitOne()) {
            return false;
        }
        inventory.extractItem(SlimeChurnInventory.BUCKET_SLOT, 1, false);
        inventory.setStackInSlot(SlimeChurnInventory.SLIME_OUTPUT_SLOT,
            SlimeBucketItem.forVariant(pendingCategory, pendingVariant));
        pendingBatch--;
        if (pendingBatch <= 0) {
            pendingVariant = null;
            pendingCategory = null;
        }
        setChanged();
        return true;
    }

    /** One empty bucket available AND the slime output slot free. */
    private boolean canEmitOne() {
        ItemStack buckets = inventory.getStackInSlot(SlimeChurnInventory.BUCKET_SLOT);
        if (buckets.isEmpty() || !buckets.is(Items.BUCKET)) {
            return false;
        }
        return inventory.getStackInSlot(SlimeChurnInventory.SLIME_OUTPUT_SLOT).isEmpty();
    }

    /**
     * Move a spent milk bucket's empty container to the EMPTY_OUTPUT slot and
     * clear the milk slot. Returns false (milk bucket stays, retried next
     * tick) when the container output is full - pause, don't void.
     */
    private boolean tryRetireSpentMilk(BlockPos pos) {
        ItemStack out = inventory.getStackInSlot(SlimeChurnInventory.EMPTY_OUTPUT_SLOT);
        if (out.isEmpty()) {
            inventory.setStackInSlot(SlimeChurnInventory.EMPTY_OUTPUT_SLOT, new ItemStack(Items.BUCKET));
        } else if (out.is(Items.BUCKET) && out.getCount() < out.getMaxStackSize()) {
            inventory.setStackInSlot(SlimeChurnInventory.EMPTY_OUTPUT_SLOT,
                out.copyWithCount(out.getCount() + 1));
        } else {
            PFDebug.logOnce(PFDebug.Area.CHURN, "retireblocked#" + pos,
                () -> String.format("churn @%s: spent-container output full, milk retire paused", pos));
            return false;
        }
        inventory.setStackInSlot(SlimeChurnInventory.MILK_SLOT, ItemStack.EMPTY);
        PFDebug.log(PFDebug.Area.CHURN, () -> String.format(
            "churn @%s: milk depleted, returned empty container", pos));
        return true;
    }

    private void startInterval(Level level, ItemStack milk) {
        intervalTotal = MilkSpawnEconomy.intervalTicks(speedLevel(milk), level.getRandom());
        intervalRemaining = intervalTotal;
        setChanged();
    }

    private void resetInterval() {
        if (intervalTotal != 0 || intervalRemaining != 0) {
            intervalTotal = 0;
            intervalRemaining = 0;
            setChanged();
        }
    }

    // -------------------------------------------------------------------
    // Milk-bucket component economy (the same component set the placed
    // source round-trips; see SlimeMilkBucketItem / docs/slime_milk_catalysts.md)
    // -------------------------------------------------------------------

    private static void seedBudgetIfAbsent(ItemStack milk, SlimeChurnBlockEntity be) {
        if (milk.get(PFDataComponents.SPAWNS_REMAINING.get()) == null) {
            int seed = defaultSpawnCount();
            milk.set(PFDataComponents.SPAWNS_REMAINING.get(), seed);
            Integer capacity = milk.get(PFDataComponents.MILK_CAPACITY.get());
            if (capacity == null || capacity < seed) {
                milk.set(PFDataComponents.MILK_CAPACITY.get(), seed);
            }
            be.setChanged();
        }
    }

    private static int remaining(ItemStack milk) {
        Integer value = milk.get(PFDataComponents.SPAWNS_REMAINING.get());
        return value != null ? value : defaultSpawnCount();
    }

    private static int speedLevel(ItemStack milk) {
        Integer value = milk.get(PFDataComponents.MILK_SPEED.get());
        return value != null ? value : 0;
    }

    private static int quantityLevel(ItemStack milk) {
        Integer value = milk.get(PFDataComponents.MILK_QUANTITY.get());
        return value != null ? value : 0;
    }

    private static boolean isInfinite(ItemStack milk) {
        return Boolean.TRUE.equals(milk.get(PFDataComponents.MILK_INFINITE.get()));
    }

    private static boolean depletionEnabled() {
        return !PFConfig.SPEC.isLoaded() || PFConfig.DEPLETION_ENABLED.get();
    }

    private static int defaultSpawnCount() {
        return PFConfig.SPEC.isLoaded() ? PFConfig.DEPLETION_COUNT.get() : 16;
    }

    /**
     * Toggle the {@link SlimeChurnBlock#WORKING} blockstate property and sync
     * to clients. No-op when the state already matches.
     */
    private static void setWorking(Level level, BlockPos pos, BlockState state, boolean working) {
        if (!(state.getBlock() instanceof SlimeChurnBlock)) {
            return;
        }
        if (state.getValue(SlimeChurnBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(SlimeChurnBlock.WORKING, working),
                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    // -------------------------------------------------------------------
    // Serialization
    // -------------------------------------------------------------------

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("IntervalRemaining", intervalRemaining);
        tag.putInt("IntervalTotal", intervalTotal);
        if (pendingBatch > 0 && pendingVariant != null && pendingCategory != null) {
            tag.putInt("PendingBatch", pendingBatch);
            tag.putString("PendingVariant", pendingVariant.toString());
            tag.putString("PendingCategory", pendingCategory.name());
        }
        net.minecraft.nbt.CompoundTag invTag = new net.minecraft.nbt.CompoundTag();
        inventory.serialize(invTag);
        tag.put("Inventory", invTag);
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Clamp on load: tampered/old saves must not stall the churn (a
        // negative remaining would never reach 0) - same posture as the
        // Milker's cookProgress clamp.
        intervalTotal = Math.max(0, tag.getInt("IntervalTotal"));
        intervalRemaining = Math.max(0, Math.min(tag.getInt("IntervalRemaining"), intervalTotal));
        pendingBatch = Math.max(0, tag.getInt("PendingBatch"));
        pendingVariant = tag.contains("PendingVariant", net.minecraft.nbt.Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString("PendingVariant")) : null;
        pendingCategory = null;
        if (tag.contains("PendingCategory", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                pendingCategory = Category.valueOf(tag.getString("PendingCategory"));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (pendingVariant == null || pendingCategory == null) {
            pendingBatch = 0;
        }
        if (tag.contains("Inventory", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            inventory.deserialize(tag.getCompound("Inventory"));
        }
    }

    // Client sync for chunk load (Jade/WTHIT read contents without opening
    // the GUI) - same posture as the Milker.
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
        return Component.translatable("block.productivefrogs.slime_churn");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new SlimeChurnMenu(containerId, playerInv, this, dataAccess);
    }
}
