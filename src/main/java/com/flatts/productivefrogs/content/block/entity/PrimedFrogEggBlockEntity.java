package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for a {@link com.flatts.productivefrogs.content.block.PrimedFrogEggBlock}.
 * Carries the <em>pending offspring stats</em> a breeding pair computed at
 * conception so they survive the frogspawn intermediary and reach the hatched
 * Resource Tadpoles.
 *
 * <p>This is the load-bearing piece of the stat-carry chain
 * (docs/frog_breeding.md, "Carrying stats through the frogspawn intermediary").
 * A plain frogspawn block holds no entity data, so when a Resource Frog lays an
 * egg the {@link com.flatts.productivefrogs.content.entity.ai.LayCategoryFrogspawn}
 * behavior writes the already-computed offspring stats into this BE. When the
 * block ticks and hatches (minutes later, with no frog reference available), the
 * block reads the stats back and stamps them onto each spawned tadpole.
 *
 * <p>The category itself does <em>not</em> live here - it is encoded by the
 * block instance (one block per species), exactly as it was before this BE
 * existed. This BE only carries the three breeding stats.
 *
 * <p>Stats are optional: an egg placed by means other than breeding (creative
 * placement, {@code /setblock}, a vanilla-style natural lay if that ever
 * happens) has {@link #hasStats} false, and the hatchlings mature into baseline
 * (1/1/1) frogs on each tadpole-to-frog maturation. There is no client sync -
 * the egg renders identically regardless of its pending stats, so the data is
 * server-only persisted state.
 */
public class PrimedFrogEggBlockEntity extends BlockEntity {

    private boolean hasStats;
    private int appetite;
    private int bounty;
    private int reach;

    // The kind this egg hatches (#281) - overrides the carrier block's species
    // when set. Stamped by the lay behavior (a designated cross stamps its
    // predator; a Midas lay / the Kiss-priming handler stamps Midas, folding in
    // the pre-2.0 midas boolean). Null = the carrier block decides (a non-bred /
    // creative placement hatches the block's own species). Server-only state.
    @Nullable
    private FrogKind kind;

    // Absolute level game-time the egg is scheduled to hatch, stamped at
    // placement by PrimedFrogEggBlock#onPlace. Lets the Jade readout show a
    // live hatch countdown (the scheduled tick itself lives in the level's tick
    // scheduler, which holds no queryable "ticks remaining"). 0 = unset (an egg
    // from a save predating this field, or one placed by an odd path); the
    // countdown is simply hidden then.
    private long hatchGameTime;

    public PrimedFrogEggBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.PRIMED_FROG_EGG.get(), pos, state);
    }

    /** Whether this egg carries bred offspring stats (vs. a non-bred placement). */
    public boolean hasStats() {
        return hasStats;
    }

    /** The kind this egg hatches, or null when the carrier block decides (#281). */
    @Nullable
    public FrogKind getKind() {
        return kind;
    }

    /** Stamp the hatch kind (server-side; persisted). Null clears back to carrier-block default. */
    public void setKind(@Nullable FrogKind kind) {
        this.kind = kind;
        setChanged();
    }

    /** Whether this egg hatches Midas (#253). Derived from the kind. */
    public boolean isMidas() {
        return kind instanceof FrogKind.Midas;
    }

    /** Legacy sugar for the Kiss-prime path and old callers: true stamps the Midas kind. */
    public void setMidas(boolean midas) {
        if (midas) {
            setKind(FrogKind.MIDAS);
        } else if (isMidas()) {
            setKind(null);
        }
    }

    /** Absolute level game-time the egg hatches, or 0 if unknown. Drives the Jade countdown. */
    public long getHatchGameTime() {
        return hatchGameTime;
    }

    /** Record the scheduled hatch game-time so the Jade tooltip can count down to it. */
    public void setHatchGameTime(long gameTime) {
        this.hatchGameTime = gameTime;
        setChanged();
    }

    public int getAppetite() {
        return appetite;
    }

    public int getBounty() {
        return bounty;
    }

    public int getReach() {
        return reach;
    }

    /**
     * Stamp the pending offspring stats onto this egg. Called server-side from
     * the lay behavior immediately after the block is placed. Marks the BE dirty
     * so the stats persist if the chunk unloads before the egg hatches.
     */
    public void setPendingStats(int appetite, int bounty, int reach) {
        this.hasStats = true;
        this.appetite = appetite;
        this.bounty = bounty;
        this.reach = reach;
        setChanged();
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (hasStats) {
            output.putBoolean("HasStats", true);
            output.putInt("Appetite", appetite);
            output.putInt("Bounty", bounty);
            output.putInt("Reach", reach);
        }
        if (hatchGameTime > 0) {
            output.putLong("HatchGameTime", hatchGameTime);
        }
        if (kind != null) {
            output.putString("Kind", kind.id());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        hasStats = input.getBooleanOr("HasStats", false);
        if (hasStats) {
            appetite = input.getIntOr("Appetite", 0);
            bounty = input.getIntOr("Bounty", 0);
            reach = input.getIntOr("Reach", 0);
        }
        hatchGameTime = input.getLongOr("HatchGameTime", 0L);
        // "Kind" id, with the legacy pre-2.0 "Midas" boolean folded in. No
        // legacy "Category" read here - the carrier block already encodes the
        // species, so only an explicit override is ever persisted.
        kind = input.getString("Kind").map(FrogKind::byId)
            .orElseGet(() -> input.getBooleanOr("Midas", false) ? FrogKind.MIDAS : null);
    }
}
