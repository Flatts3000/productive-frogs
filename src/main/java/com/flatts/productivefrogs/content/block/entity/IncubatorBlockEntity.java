package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.Nullable;

/**
 * Incubator block entity (phase 4): the Terrarium's frog seed point. Insert
 * frogspawn (a Frog Egg item) or accept bred offspring (the
 * {@code LayCategoryFrogspawn} redirect writes pending-offspring stats here), and
 * it grows the lineage and releases a {@link ResourceFrog} into the cavity with
 * stats preserved - applied AFTER {@code finalizeSpawn}, the exact final hop of
 * the v1.5 stat chain that {@code ResourceTadpole.ageUp} uses. At the frog cap it
 * holds the matured frog and releases it as space frees.
 */
public class IncubatorBlockEntity extends BlockEntity {

    /** Test override for the frog cap (volatile for cross-thread visibility). */
    @Nullable
    public static volatile Integer frogCapOverride = null;

    @Nullable
    private Category category;
    private boolean hasStats;
    private int appetite;
    private int bounty;
    private int reach;
    private int growthRemaining;
    private boolean pendingRelease;

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.INCUBATOR.get(), pos, state);
    }

    /** Room for a new seed (not currently incubating). */
    public boolean hasRoom() {
        return category == null;
    }

    @Nullable
    public Category getCategory() {
        return category;
    }

    /** Seed from a frogspawn item (baseline stats - a plain egg carries none). */
    public boolean seedBaseline(Category category) {
        return seed(category, false, 0, 0, 0);
    }

    /** Seed from the in-cavity breeding redirect: a bred frog's pending-offspring stats. */
    public boolean seedFromBreeding(Category category, int appetite, int bounty, int reach) {
        return seed(category, true, appetite, bounty, reach);
    }

    private boolean seed(Category category, boolean hasStats, int appetite, int bounty, int reach) {
        if (this.category != null) {
            return false; // already incubating - one at a time
        }
        this.category = category;
        this.hasStats = hasStats;
        this.appetite = appetite;
        this.bounty = bounty;
        this.reach = reach;
        this.growthRemaining = Math.max(1, PFConfig.tadpoleGrowthTicks());
        this.pendingRelease = false;
        setChanged();
        return true;
    }

    /** Test seam: skip the growth wait so the next tick releases. */
    public void primeForImmediateRelease() {
        this.growthRemaining = 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (!(level instanceof ServerLevel server) || be.category == null) {
            return;
        }
        TerrariumManager.FormedTerrarium terrarium = TerrariumManager.owningIncubator(server, pos);
        if (terrarium == null) {
            return; // not part of a formed Terrarium: pause, keep the seed
        }
        if (!be.pendingRelease) {
            if (be.growthRemaining > 0) {
                be.growthRemaining--;
                be.setChanged();
                return;
            }
            be.pendingRelease = true;
        }
        // Matured: release into the cavity unless the frog cap is reached (then hold).
        if (frogCount(server, terrarium) >= frogCap()) {
            return;
        }
        be.releaseFrog(server, pos, state, terrarium);
        be.clear();
    }

    private void releaseFrog(ServerLevel level, BlockPos pos, BlockState state, TerrariumManager.FormedTerrarium terrarium) {
        ResourceFrog frog = PFEntities.RESOURCE_FROG.get().create(level);
        if (frog == null) {
            return;
        }
        frog.setCategory(category);
        // Spawn into the cavity cell the Incubator faces (inward = FACING.opposite()).
        Direction inward = state.hasProperty(BlockStateProperties.FACING)
            ? state.getValue(BlockStateProperties.FACING).getOpposite() : Direction.UP;
        BlockPos spawn = pos.relative(inward);
        frog.moveTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0F, 0.0F);
        frog.finalizeSpawn(level, level.getCurrentDifficultyAt(spawn), MobSpawnType.CONVERSION, null);
        // Stats AFTER finalizeSpawn, so inherited values override the baseline -
        // identical to ResourceTadpole.ageUp.
        if (hasStats) {
            frog.setStats(appetite, bounty, reach);
        }
        frog.setPersistenceRequired();
        level.addFreshEntityWithPassengers(frog);
    }

    private void clear() {
        this.category = null;
        this.hasStats = false;
        this.appetite = 0;
        this.bounty = 0;
        this.reach = 0;
        this.growthRemaining = 0;
        this.pendingRelease = false;
        setChanged();
    }

    private static int frogCount(ServerLevel level, TerrariumManager.FormedTerrarium terrarium) {
        return level.getEntitiesOfClass(ResourceFrog.class, terrarium.cavity()).size();
    }

    private static int frogCap() {
        Integer override = frogCapOverride;
        return override != null ? override : PFConfig.terrariumFrogCap();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (category != null) {
            tag.putString("Category", category.name());
            tag.putBoolean("HasStats", hasStats);
            tag.putInt("Appetite", appetite);
            tag.putInt("Bounty", bounty);
            tag.putInt("Reach", reach);
            tag.putInt("GrowthRemaining", growthRemaining);
            tag.putBoolean("PendingRelease", pendingRelease);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Category", Tag.TAG_STRING)) {
            try {
                category = Category.valueOf(tag.getString("Category"));
            } catch (IllegalArgumentException e) {
                category = null;
            }
            hasStats = tag.getBoolean("HasStats");
            appetite = tag.getInt("Appetite");
            bounty = tag.getInt("Bounty");
            reach = tag.getInt("Reach");
            growthRemaining = Math.max(0, tag.getInt("GrowthRemaining"));
            pendingRelease = tag.getBoolean("PendingRelease");
        } else {
            category = null;
        }
    }
}
