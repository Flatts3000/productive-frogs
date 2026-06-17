package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.multiblock.TerrariumManager;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.content.menu.IncubatorMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
public class IncubatorBlockEntity extends BlockEntity implements MenuProvider {

    /** ContainerData indices for the status screen. */
    public static final int DATA_GROWTH_REMAINING = 0;
    public static final int DATA_GROWTH_TOTAL = 1;
    public static final int DATA_STATE = 2; // 0=empty, 1=growing, 2=waiting at cap
    public static final int DATA_CATEGORY = 3; // category ordinal, -1 when empty
    public static final int DATA_FROGS = 4; // live frogs in the cavity
    public static final int DATA_FROG_CAP = 5; // configured frog cap
    public static final int DATA_COUNT = 6;

    /** Test override for the frog cap (volatile for cross-thread visibility). */
    @Nullable
    public static volatile Integer frogCapOverride = null;

    @Nullable
    private Category category;
    /** Equivalence lane (#253): the incubating seed matures into a Midas frog. */
    private boolean midas;
    private boolean hasStats;
    private int appetite;
    private int bounty;
    private int reach;
    private int growthRemaining;
    private int growthTotal;
    private boolean pendingRelease;

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_GROWTH_REMAINING -> growthRemaining;
                case DATA_GROWTH_TOTAL -> growthTotal;
                case DATA_STATE -> category == null ? 0 : (pendingRelease ? 2 : 1);
                case DATA_CATEGORY -> category == null ? -1 : category.ordinal();
                case DATA_FROGS -> cavityFrogCount();
                case DATA_FROG_CAP -> frogCap();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_GROWTH_REMAINING -> growthRemaining = value;
                case DATA_GROWTH_TOTAL -> growthTotal = value;
                default -> { }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.INCUBATOR.get(), pos, state);
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    /** Room for a new seed (not currently incubating). */
    public boolean hasRoom() {
        return category == null;
    }

    /** Matured but held back at the frog cap (for the Jade look-at). */
    public boolean isWaitingForSpace() {
        return pendingRelease;
    }

    /** Actively growing a seed (not empty, not yet matured/waiting). */
    public boolean isIncubating() {
        return category != null && !pendingRelease && growthRemaining > 0;
    }

    /**
     * Speed up the current incubation by feeding a Sweetslime: shave
     * {@code terrarium.sweetslimeAcceleratePercent}% (default 10) of the full
     * lifecycle off the remaining time. No-op (returns false) when nothing is
     * incubating or the speed-up is config-disabled (percent 0).
     */
    public boolean accelerateWithSweetslime() {
        int percent = PFConfig.terrariumSweetslimeAcceleratePercent();
        if (!isIncubating() || percent <= 0) {
            return false; // nothing to accelerate, or the speed-up is config-disabled
        }
        int reduction = Math.max(1, growthTotal * percent / 100);
        growthRemaining = Math.max(0, growthRemaining - reduction);
        syncToClients();
        return true;
    }

    public int growthRemaining() {
        return growthRemaining;
    }

    public int growthTotal() {
        return growthTotal;
    }

    @Nullable
    public Category getCategory() {
        return category;
    }

    /** Seed from a frogspawn item (baseline stats - a plain egg carries none). */
    public boolean seedBaseline(Category category) {
        return seed(category, false, 0, 0, 0, false);
    }

    /** Seed from the in-cavity breeding redirect: a bred frog's pending-offspring stats. */
    public boolean seedFromBreeding(Category category, int appetite, int bounty, int reach) {
        return seed(category, true, appetite, bounty, reach, false);
    }

    /** Breeding redirect carrying the Midas marker (#253): matures into a Midas frog. */
    public boolean seedFromBreeding(Category category, int appetite, int bounty, int reach, boolean midas) {
        return seed(category, true, appetite, bounty, reach, midas);
    }

    private boolean seed(Category category, boolean hasStats, int appetite, int bounty, int reach, boolean midas) {
        if (this.category != null) {
            return false; // already incubating - one at a time
        }
        this.category = category;
        this.midas = midas;
        this.hasStats = hasStats;
        this.appetite = appetite;
        this.bounty = bounty;
        this.reach = reach;
        // Full egg -> frog lifecycle: the primed-frogspawn hatch delay plus tadpole
        // maturation, so an Incubator takes exactly as long as the natural
        // egg -> tadpole -> frog path. Both halves read from config (lifecycle.*).
        this.growthTotal = Math.max(1, PFConfig.hatchTicks() + PFConfig.tadpoleGrowthTicks());
        this.growthRemaining = this.growthTotal;
        this.pendingRelease = false;
        syncToClients();
        return true;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.productivefrogs.incubator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInv, Player player) {
        return new IncubatorMenu(containerId, playerInv, this, dataAccess);
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
                // Periodic client sync (~1/sec) so the Jade look-at percent updates;
                // the open GUI uses ContainerData and is already live each tick, so
                // we don't sendBlockUpdated every tick.
                if (be.growthRemaining % 20 == 0) {
                    be.syncToClients();
                } else {
                    be.setChanged();
                }
                return;
            }
            be.pendingRelease = true;
            be.syncToClients(); // matured -> "waiting" state flip
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
        frog.setMidas(midas);
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
        this.midas = false;
        this.hasStats = false;
        this.appetite = 0;
        this.bounty = 0;
        this.reach = 0;
        this.growthRemaining = 0;
        this.growthTotal = 0;
        this.pendingRelease = false;
        syncToClients();
    }

    /**
     * Mark dirty AND push a block-entity update to clients. Plain
     * {@link #setChanged()} only flags the chunk for saving, so the Jade look-at
     * (which reads the client BE) would otherwise show a frozen growth percent.
     * Not called every tick - only on seed, state flips, clear, and a periodic
     * growth tick.
     */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Live frogs in this Incubator's cavity (0 when not part of a formed Terrarium). */
    private int cavityFrogCount() {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        TerrariumManager.FormedTerrarium t = TerrariumManager.owningIncubator(server, worldPosition);
        return t == null ? 0 : frogCount(server, t);
    }

    private static int frogCount(ServerLevel level, TerrariumManager.FormedTerrarium terrarium) {
        // Count only frogs whose CENTER is inside the cavity, not merely overlapping
        // the box - so a frog standing against an outside wall (its bounding box
        // grazing the cavity boundary) is never counted toward the cap.
        var cavity = terrarium.cavity();
        return level.getEntitiesOfClass(ResourceFrog.class, cavity,
            f -> cavity.contains(f.getX(), f.getY(), f.getZ())).size();
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
            if (midas) {
                tag.putBoolean("Midas", true);
            }
            tag.putBoolean("HasStats", hasStats);
            tag.putInt("Appetite", appetite);
            tag.putInt("Bounty", bounty);
            tag.putInt("Reach", reach);
            tag.putInt("GrowthRemaining", growthRemaining);
            tag.putInt("GrowthTotal", growthTotal);
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
            midas = tag.getBoolean("Midas");
            hasStats = tag.getBoolean("HasStats");
            appetite = tag.getInt("Appetite");
            bounty = tag.getInt("Bounty");
            reach = tag.getInt("Reach");
            growthRemaining = Math.max(0, tag.getInt("GrowthRemaining"));
            growthTotal = Math.max(growthRemaining, tag.getInt("GrowthTotal"));
            pendingRelease = tag.getBoolean("PendingRelease");
        } else {
            category = null;
            midas = false;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries); // sync category + growth for Jade
        return tag;
    }

    @Override
    @Nullable
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
}
