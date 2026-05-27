package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
 * happens) has {@link #hasStats} false, and the hatch falls back to a fresh
 * starter roll on each tadpole-to-frog maturation. There is no client sync -
 * the egg renders identically regardless of its pending stats, so the data is
 * server-only persisted state.
 */
public class PrimedFrogEggBlockEntity extends BlockEntity {

    private boolean hasStats;
    private int appetite;
    private int bounty;
    private int reach;

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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hasStats) {
            tag.putBoolean("HasStats", true);
            tag.putInt("Appetite", appetite);
            tag.putInt("Bounty", bounty);
            tag.putInt("Reach", reach);
        }
        if (hatchGameTime > 0) {
            tag.putLong("HatchGameTime", hatchGameTime);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hasStats = tag.getBoolean("HasStats");
        if (hasStats) {
            appetite = tag.getInt("Appetite");
            bounty = tag.getInt("Bounty");
            reach = tag.getInt("Reach");
        }
        hatchGameTime = tag.getLong("HatchGameTime");
    }
}
