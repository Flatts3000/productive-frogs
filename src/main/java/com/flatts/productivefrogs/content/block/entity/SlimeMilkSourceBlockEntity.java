package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for {@link SlimeMilkSourceBlock}. Owns the source's spawn economy:
 * the variant it spawns plus the catalyst-driven upgrades (remaining spawn count,
 * speed level, quantity level, and the infinite-count flag).
 *
 * <p><b>Why the counter lives here and not in the blockstate (v1.7 change):</b>
 * the depletion counter used to be a {@code SPAWNS_REMAINING} blockstate
 * {@code IntegerProperty} capped at 16. Count catalysts raise a source's
 * remaining spawns without an upper bound, which a finite-range blockstate
 * property cannot represent, so the whole spawn economy moved onto this BE. As a
 * side benefit it shrinks the block's state-combination count. See
 * {@code docs/slime_milk_catalysts.md}.
 *
 * <p>All four upgrade fields round-trip through the Slime Milk bucket: written on
 * placement from the bucket's data components by
 * {@code SlimeMilkBucketItem#checkExtraContent}, and read back onto the bucket by
 * {@code SlimeMilkSourceBlock#pickupBlock}. They persist via
 * {@link #saveAdditional}/{@link #loadAdditional} and are exposed as implicit
 * components so {@code /data} and creative tooling can see them.
 *
 * <p>A {@code null} variant is the defensive fallback for milk that spread from a
 * source (fluid spreading does not copy BlockEntities) or was placed by
 * {@code /setblock} without a variant: such milk renders with the default tint
 * and spawns nothing.
 */
public class SlimeMilkSourceBlockEntity extends BlockEntity {

    /** Sentinel: spawnsRemaining has not been seeded from config yet. */
    private static final int UNINITIALIZED = -1;

    /** Clamp on stored spawns so Count catalysts can't overflow the int / NBT. */
    public static final int MAX_STORED_SPAWNS = 1_000_000;

    @Nullable
    private Identifier variantId;

    private int spawnsRemaining = UNINITIALIZED;
    private int spawnsCapacity = UNINITIALIZED;
    private int speedLevel = 0;
    private int quantityLevel = 0;
    private boolean infinite = false;

    public SlimeMilkSourceBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLIME_MILK_SOURCE.get(), pos, state);
    }

    @Nullable
    public Identifier getVariantId() {
        return variantId;
    }

    /**
     * Set the variant, mark for save, and push a block-update so the client
     * retints. Server-side only. Seeds the spawn counter to the configured
     * default the first time a real variant is attached, so a freshly-placed
     * source starts with a full budget without needing a carried component.
     */
    public void setVariantId(@Nullable Identifier variantId) {
        boolean changed = !Objects.equals(this.variantId, variantId);
        this.variantId = variantId;
        if (variantId != null) {
            seedIfUnset();
        }
        if (!changed) {
            return;
        }
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    // ---- spawn economy -------------------------------------------------

    /** Seed the spawn counter + capacity from {@link PFConfig#DEPLETION_COUNT} if not yet set. */
    public void seedIfUnset() {
        if (spawnsRemaining == UNINITIALIZED) {
            spawnsRemaining = defaultSpawnCount();
            spawnsCapacity = spawnsRemaining;
            setChanged();
        }
    }

    private static int defaultSpawnCount() {
        int base = PFConfig.SPEC.isLoaded()
            ? PFConfig.DEPLETION_COUNT.get()
            : SlimeMilkSourceBlock.MAX_SPAWNS_REMAINING;
        return Mth.clamp(base, 0, MAX_STORED_SPAWNS);
    }

    /** Remaining spawns before this source drains (only meaningful when depletion is on and not infinite). */
    public int getSpawnsRemaining() {
        return spawnsRemaining == UNINITIALIZED ? defaultSpawnCount() : spawnsRemaining;
    }

    /** Set the remaining-spawn counter directly (clamped). Capacity tracks the high-water mark. Used by tests + setup. */
    public void setSpawnsRemaining(int remaining) {
        this.spawnsRemaining = Mth.clamp(remaining, 0, MAX_STORED_SPAWNS);
        this.spawnsCapacity = Math.max(getSpawnsCapacity(), this.spawnsRemaining);
        setChanged();
    }

    /**
     * Total spawn budget (high-water mark): the source's capacity, which Count
     * catalysts raise above the configured default. The "N / cap" denominator in
     * Jade and the bucket tooltip; stays put as {@code spawnsRemaining} drains.
     * Never reported below the current remaining count.
     */
    public int getSpawnsCapacity() {
        int cap = spawnsCapacity == UNINITIALIZED ? defaultSpawnCount() : spawnsCapacity;
        return Math.max(cap, getSpawnsRemaining());
    }

    /** Decrement the remaining-spawn counter by one (floored at 0). No-op when infinite. */
    public void decrementSpawns() {
        if (infinite) {
            return;
        }
        seedIfUnset();
        if (spawnsRemaining > 0) {
            spawnsRemaining--;
            setChanged();
        }
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
     * Apply one catalyst's effect to this source. Returns {@code true} if it
     * changed anything (so the caller consumes one item) and {@code false} when
     * the upgrade is already maxed/redundant (so the dropped item is left for the
     * player to retrieve rather than silently eaten). All bounds are read from
     * {@link PFConfig}.
     */
    public boolean applyCatalyst(MilkCatalyst catalyst) {
        boolean applied = switch (catalyst) {
            case COUNT -> {
                seedIfUnset();
                if (spawnsRemaining >= MAX_STORED_SPAWNS) {
                    yield false;
                }
                int added = PFConfig.catalystCountPer();
                spawnsRemaining = Mth.clamp(spawnsRemaining + added, 0, MAX_STORED_SPAWNS);
                // Capacity grows with the budget so the "N / cap" denominator
                // rises with the catalyst rather than the remaining count. Add to
                // the raw field (seedIfUnset just set it) - NOT getSpawnsCapacity(),
                // which maxes with the already-incremented remaining and would
                // double-count the addition.
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
        }
        return applied;
    }

    // ---- bucket round-trip restore (from SlimeMilkBucketItem) -----------

    /**
     * Restore the full upgrade set from a re-placed bucket. Values are clamped to
     * current config bounds. Pushes a block update so a re-placed <b>infinite</b>
     * source starts its client-side glint immediately - {@code setChanged()} alone
     * leaves the client BE's {@code infinite} flag stale until the next chunk load,
     * because this runs after the placement's initial sync.
     */
    public void restoreUpgrades(int spawnsRemaining, int spawnsCapacity, int speedLevel, int quantityLevel, boolean infinite) {
        this.spawnsRemaining = Mth.clamp(spawnsRemaining, 0, MAX_STORED_SPAWNS);
        this.spawnsCapacity = Mth.clamp(Math.max(spawnsCapacity, this.spawnsRemaining), 0, MAX_STORED_SPAWNS);
        this.speedLevel = Mth.clamp(speedLevel, 0, PFConfig.catalystMaxSpeedLevel());
        this.quantityLevel = Mth.clamp(quantityLevel, 0, PFConfig.catalystMaxQuantityLevel());
        this.infinite = infinite;
        setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (variantId != null) {
            builder.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
            builder.set(PFDataComponents.SPAWNS_REMAINING.get(), getSpawnsRemaining());
            builder.set(PFDataComponents.MILK_CAPACITY.get(), getSpawnsCapacity());
            if (speedLevel > 0) {
                builder.set(PFDataComponents.MILK_SPEED.get(), speedLevel);
            }
            if (quantityLevel > 0) {
                builder.set(PFDataComponents.MILK_QUANTITY.get(), quantityLevel);
            }
            if (infinite) {
                builder.set(PFDataComponents.MILK_INFINITE.get(), true);
            }
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.variantId = components.get(PFDataComponents.SLIME_VARIANT.get());
        Integer remaining = components.get(PFDataComponents.SPAWNS_REMAINING.get());
        if (remaining != null) {
            this.spawnsRemaining = Mth.clamp(remaining, 0, MAX_STORED_SPAWNS);
        }
        Integer capacity = components.get(PFDataComponents.MILK_CAPACITY.get());
        if (capacity != null) {
            this.spawnsCapacity = Mth.clamp(Math.max(capacity, getSpawnsRemaining()), 0, MAX_STORED_SPAWNS);
        }
        Integer speed = components.get(PFDataComponents.MILK_SPEED.get());
        this.speedLevel = speed != null ? Mth.clamp(speed, 0, PFConfig.catalystMaxSpeedLevel()) : 0;
        Integer quantity = components.get(PFDataComponents.MILK_QUANTITY.get());
        this.quantityLevel = quantity != null ? Mth.clamp(quantity, 0, PFConfig.catalystMaxQuantityLevel()) : 0;
        Boolean inf = components.get(PFDataComponents.MILK_INFINITE.get());
        this.infinite = Boolean.TRUE.equals(inf);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
        if (spawnsRemaining != UNINITIALIZED) {
            tag.putInt("SpawnsRemaining", spawnsRemaining);
        }
        if (spawnsCapacity != UNINITIALIZED) {
            tag.putInt("SpawnsCapacity", spawnsCapacity);
        }
        if (speedLevel > 0) {
            tag.putInt("SpeedLevel", speedLevel);
        }
        if (quantityLevel > 0) {
            tag.putInt("QuantityLevel", quantityLevel);
        }
        if (infinite) {
            tag.putBoolean("Infinite", true);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        variantId = tag.contains("Variant", Tag.TAG_STRING)
            ? Identifier.tryParse(tag.getString("Variant")) : null;
        spawnsRemaining = tag.contains("SpawnsRemaining", Tag.TAG_INT)
            ? Mth.clamp(tag.getInt("SpawnsRemaining"), 0, MAX_STORED_SPAWNS) : UNINITIALIZED;
        spawnsCapacity = tag.contains("SpawnsCapacity", Tag.TAG_INT)
            ? Mth.clamp(tag.getInt("SpawnsCapacity"), 0, MAX_STORED_SPAWNS) : UNINITIALIZED;
        speedLevel = tag.contains("SpeedLevel", Tag.TAG_INT) ? Math.max(0, tag.getInt("SpeedLevel")) : 0;
        quantityLevel = tag.contains("QuantityLevel", Tag.TAG_INT) ? Math.max(0, tag.getInt("QuantityLevel")) : 0;
        infinite = tag.getBoolean("Infinite");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
        // Sync the infinite flag to the client so the source can render its
        // ambient glint (SlimeMilkSourceBlock#animateTick). The other upgrade
        // levels stay server-side (Jade reads them via server data).
        if (infinite) {
            tag.putBoolean("Infinite", true);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
