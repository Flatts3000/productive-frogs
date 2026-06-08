package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.MilkSpawnEconomy;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.block.SprinklerBlock;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Sprinkler block entity (phase 2): a placed Slime Milk source in a Terrarium
 * ceiling cell. It holds one bucket of milk (a {@link MilkCharge} stamped on by
 * the Controller's distribution) and runs the <b>same spawn economy</b> as a
 * hand-placed source - cadence and batch size via {@link MilkSpawnEconomy}, slime
 * creation via {@link SlimeMilkSourceBlock#createSlimeForVariant} - but rains its
 * variant's slimes DOWN into the cavity and is capped by the box's total slime
 * count rather than a radius. This reuses the shared economy + slime-creation
 * (not a fork); only the placement (cavity column below) and the crowd predicate
 * (all slimes in the cavity AABB vs {@code terrarium.slimeCap}) differ.
 *
 * <p>Right-clicking with an empty bucket drains the held milk back to its
 * per-variant bucket with catalysts intact ({@link #drainToBucket}). When a
 * non-infinite Sprinkler spends its last spawn it clears and can be refilled with
 * any variant the Controller currently funnels.
 */
public class SprinklerBlockEntity extends BlockEntity {

    private static final int UNINITIALIZED = -1;

    /**
     * Test override for the cavity slime cap (volatile for cross-thread
     * visibility, like {@link SlimeMilkSourceBlock#spawnCapOverride}).
     */
    @Nullable
    public static volatile Integer cavitySlimeCapOverride = null;

    @Nullable
    private ResourceLocation variantId;
    private int spawnsRemaining = UNINITIALIZED;
    private int spawnsCapacity = UNINITIALIZED;
    private int speedLevel;
    private int quantityLevel;
    private boolean infinite;

    private int intervalRemaining;
    private int intervalTotal;

    public SprinklerBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SPRINKLER.get(), pos, state);
    }

    public boolean isEmpty() {
        return variantId == null;
    }

    @Nullable
    public ResourceLocation getVariantId() {
        return variantId;
    }

    public int getSpawnsRemaining() {
        return Math.max(0, spawnsRemaining);
    }

    public int getSpeedLevel() {
        return speedLevel;
    }

    public int getQuantityLevel() {
        return quantityLevel;
    }

    /** Capacity high-water mark (the {@code N / cap} denominator), never below remaining. */
    public int getSpawnsCapacity() {
        return Math.max(spawnsCapacity, getSpawnsRemaining());
    }

    public boolean isInfinite() {
        return infinite;
    }

    /** Available for a fresh charge (holds nothing). */
    public boolean acceptsFreshCharge() {
        return variantId == null;
    }

    /** A candidate for top-up: holds {@code variant}, not infinite, and draining low. */
    public boolean wantsTopUp(ResourceLocation variant, int threshold) {
        return variant.equals(variantId) && !infinite && spawnsRemaining <= threshold;
    }

    /** Stamp a fresh charge of {@code variant} (replaces whatever was here). */
    public void loadCharge(ResourceLocation variant, MilkCharge charge) {
        this.variantId = variant;
        this.spawnsRemaining = clampSpawns(charge.spawnsRemaining());
        this.spawnsCapacity = clampSpawns(Math.max(charge.capacity(), this.spawnsRemaining));
        this.speedLevel = Mth.clamp(charge.speed(), 0, PFConfig.catalystMaxSpeedLevel());
        this.quantityLevel = Mth.clamp(charge.quantity(), 0, PFConfig.catalystMaxQuantityLevel());
        this.infinite = charge.infinite();
        resetInterval();
        sync();
    }

    /** Merge a charge into a matching-variant Sprinkler (top-up: pool budget, take the better stats). */
    public void mergeCharge(MilkCharge charge) {
        this.spawnsRemaining = clampSpawns(getSpawnsRemaining() + charge.spawnsRemaining());
        this.spawnsCapacity = clampSpawns(spawnsCapacity + charge.capacity());
        this.speedLevel = Math.max(speedLevel, Mth.clamp(charge.speed(), 0, PFConfig.catalystMaxSpeedLevel()));
        this.quantityLevel = Math.max(quantityLevel, Mth.clamp(charge.quantity(), 0, PFConfig.catalystMaxQuantityLevel()));
        this.infinite = this.infinite || charge.infinite();
        sync();
    }

    private static int clampSpawns(int v) {
        return Mth.clamp(v, 0, SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS);
    }

    /**
     * Apply one catalyst to the held milk, mirroring
     * {@link SlimeMilkSourceBlockEntity#applyCatalyst}. Returns false (consuming
     * nothing) when empty or the upgrade is already maxed/redundant. The
     * depletion/infinite gating on Count/Infinite is done by the caller.
     */
    public boolean applyCatalyst(MilkCatalyst catalyst) {
        if (variantId == null) {
            return false;
        }
        boolean applied = switch (catalyst) {
            case COUNT -> {
                if (infinite || spawnsRemaining >= SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS) {
                    yield false;
                }
                int added = PFConfig.catalystCountPer();
                spawnsRemaining = clampSpawns(spawnsRemaining + added);
                spawnsCapacity = clampSpawns(Math.max(spawnsCapacity, 0) + added);
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
            sync();
        }
        return applied;
    }

    /**
     * Absorb Slime Milk catalyst items dropped onto the Sprinkler from above (a
     * tossed stack, a dropper, or a hopper feeding the terrarium roof) - the
     * Sprinkler's analogue of a source block consuming catalysts dropped into its
     * pool. Same gating: catalysts globally enabled, holding milk, and Count/Infinite
     * only when depletion is on. A maxed upgrade leaves the item for the player.
     */
    private void absorbCatalystsFromAbove(ServerLevel level, BlockPos pos) {
        if (variantId == null || !PFConfig.milkCatalystsEnabled()) {
            return;
        }
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos.above()),
            e -> e.isAlive() && MilkCatalyst.fromStack(e.getItem()) != null);
        for (ItemEntity itemEntity : items) {
            ItemStack stack = itemEntity.getItem();
            MilkCatalyst catalyst = MilkCatalyst.fromStack(stack);
            if (catalyst == null) {
                continue;
            }
            // Count / Infinite are no-ops when depletion is globally off; leave them.
            if ((catalyst == MilkCatalyst.COUNT || catalyst == MilkCatalyst.INFINITE) && !depletionEnabled()) {
                continue;
            }
            if (!applyCatalyst(catalyst)) {
                continue;
            }
            stack.shrink(1);
            if (stack.isEmpty()) {
                itemEntity.discard();
            } else {
                itemEntity.setItem(stack);
            }
            level.playSound(null, pos, SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS,
                0.7F, 1.3F + level.getRandom().nextFloat() * 0.2F);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.0);
        }
    }

    /** Test seam: make the next {@link #serverTick} fire immediately. */
    public void primeForImmediateSpawn() {
        this.intervalTotal = 1;
        this.intervalRemaining = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SprinklerBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (be.isEmpty()) {
            setFilled(server, pos, state, false);
            return;
        }
        TerrariumManager.FormedTerrarium terrarium = TerrariumManager.owningSprinkler(server, pos);
        if (terrarium == null) {
            // Not part of a formed Terrarium (shell broken / not yet validated):
            // pause and keep contents - resume when it re-forms.
            return;
        }
        setFilled(server, pos, state, true);
        be.absorbCatalystsFromAbove(server, pos);

        boolean depleting = depletionEnabled() && !be.infinite;
        if (depleting && be.spawnsRemaining <= 0) {
            be.clear(server, pos, state);
            return;
        }
        if (be.intervalTotal <= 0) {
            be.startInterval(server);
        }
        if (be.intervalRemaining > 0) {
            be.intervalRemaining--;
            be.setChanged();
            return;
        }
        // Ready to fire. Cavity full -> pause without spending (restart interval).
        if (isCavityFull(server, terrarium)) {
            be.resetInterval();
            return;
        }
        // Spawn target (the cavity cell below) must be open, mirroring the source's
        // chooseSpawnPos non-blocking check - a slime dropped into a solid interior
        // cell would suffocate/clip. Pause without spending until it clears.
        if (level.getBlockState(pos.below()).blocksMotion()) {
            be.resetInterval();
            return;
        }
        int batch = MilkSpawnEconomy.batchQuantity(be.quantityLevel);
        for (int i = 0; i < batch; i++) {
            be.spawnInto(server, pos);
        }
        if (depleting) {
            be.spawnsRemaining = Math.max(0, be.spawnsRemaining - 1);
        }
        be.resetInterval();
        be.sync(); // push the new spawn count to clients (Jade reads the client BE)
        if (depleting && be.spawnsRemaining <= 0) {
            be.clear(server, pos, state);
        }
    }

    private void spawnInto(ServerLevel level, BlockPos pos) {
        if (variantId == null) {
            return;
        }
        // The cavity sits directly below a ceiling Sprinkler; drop into the cell
        // beneath it (slimes have no spawn-collision needs at size 1 and fall in).
        BlockPos target = pos.below();
        Slime slime = SlimeMilkSourceBlock.createSlimeForVariant(level, variantId);
        if (slime == null) {
            return;
        }
        slime.setSize(1, true);
        slime.moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5,
            level.getRandom().nextFloat() * 360F, 0F);
        level.addFreshEntity(slime);
    }

    private static boolean isCavityFull(ServerLevel level, TerrariumManager.FormedTerrarium terrarium) {
        Integer override = cavitySlimeCapOverride;
        int cap = override != null ? override : PFConfig.terrariumSlimeCap();
        return level.getEntitiesOfClass(Slime.class, terrarium.cavity()).size() >= cap;
    }

    private void startInterval(ServerLevel level) {
        this.intervalTotal = MilkSpawnEconomy.intervalTicks(speedLevel, level.getRandom());
        this.intervalRemaining = this.intervalTotal;
        setChanged();
    }

    private void resetInterval() {
        this.intervalTotal = 0;
        this.intervalRemaining = 0;
    }

    /** Empty the Sprinkler so it can take a fresh charge of any variant. */
    private void clear(Level level, BlockPos pos, BlockState state) {
        this.variantId = null;
        this.spawnsRemaining = UNINITIALIZED;
        this.spawnsCapacity = UNINITIALIZED;
        this.speedLevel = 0;
        this.quantityLevel = 0;
        this.infinite = false;
        resetInterval();
        // Flip the blockstate to empty FIRST, then sync the (now empty) BE so the
        // end-of-tick broadcast carries the final empty state - the Jade look-at
        // drops the milk line on the tick the last slime spawns.
        setFilled(level, pos, state, false);
        sync();
    }

    /**
     * Mark dirty AND push a block-entity update packet to tracking clients.
     * {@link #setChanged()} alone only flags the chunk for saving; it does not
     * re-send {@link #getUpdatePacket()}, so client-side reads (Jade, the FILLED
     * visual, the drip tint) would otherwise see stale values. Call this from
     * every path that mutates a client-visible field (variant / spawn count /
     * catalysts), but NOT from the per-tick interval countdown.
     */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Drain the held milk back to its per-variant bucket with catalysts intact
     * (mirrors {@code SlimeMilkSourceBlock#pickupBlock}). Returns EMPTY when the
     * Sprinkler holds nothing.
     */
    public ItemStack drainToBucket(Level level, BlockPos pos, BlockState state) {
        if (variantId == null) {
            return ItemStack.EMPTY;
        }
        ItemStack bucket = PFItems.slimeMilkBucket(variantId);
        if (bucket.isEmpty()) {
            return ItemStack.EMPTY;
        }
        bucket.set(PFDataComponents.SPAWNS_REMAINING.get(), getSpawnsRemaining());
        bucket.set(PFDataComponents.MILK_CAPACITY.get(), Math.max(spawnsCapacity, getSpawnsRemaining()));
        if (speedLevel > 0) {
            bucket.set(PFDataComponents.MILK_SPEED.get(), speedLevel);
        }
        if (quantityLevel > 0) {
            bucket.set(PFDataComponents.MILK_QUANTITY.get(), quantityLevel);
        }
        if (infinite) {
            bucket.set(PFDataComponents.MILK_INFINITE.get(), true);
        }
        clear(level, pos, state);
        return bucket;
    }

    private static boolean depletionEnabled() {
        Boolean override = SlimeMilkSourceBlock.depletionEnabledOverride;
        if (override != null) {
            return override;
        }
        return !PFConfig.SPEC.isLoaded() || PFConfig.DEPLETION_ENABLED.get();
    }

    private static void setFilled(Level level, BlockPos pos, BlockState state, boolean filled) {
        if (state.getBlock() instanceof SprinklerBlock && state.getValue(SprinklerBlock.FILLED) != filled) {
            level.setBlock(pos, state.setValue(SprinklerBlock.FILLED, filled), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
            tag.putInt("SpawnsRemaining", spawnsRemaining);
            tag.putInt("SpawnsCapacity", spawnsCapacity);
            tag.putInt("SpeedLevel", speedLevel);
            tag.putInt("QuantityLevel", quantityLevel);
            tag.putBoolean("Infinite", infinite);
            tag.putInt("IntervalRemaining", intervalRemaining);
            tag.putInt("IntervalTotal", intervalTotal);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Variant", Tag.TAG_STRING)) {
            variantId = ResourceLocation.tryParse(tag.getString("Variant"));
            spawnsRemaining = clampSpawns(tag.getInt("SpawnsRemaining"));
            spawnsCapacity = clampSpawns(Math.max(tag.getInt("SpawnsCapacity"), spawnsRemaining));
            speedLevel = Mth.clamp(tag.getInt("SpeedLevel"), 0, PFConfig.catalystMaxSpeedLevel());
            quantityLevel = Mth.clamp(tag.getInt("QuantityLevel"), 0, PFConfig.catalystMaxQuantityLevel());
            infinite = tag.getBoolean("Infinite");
            intervalTotal = Math.max(0, tag.getInt("IntervalTotal"));
            intervalRemaining = Math.max(0, Math.min(tag.getInt("IntervalRemaining"), intervalTotal));
        } else {
            variantId = null;
            spawnsRemaining = UNINITIALIZED;
            spawnsCapacity = UNINITIALIZED;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // sync variant + spawns for Jade + the FILLED visual
        return tag;
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
