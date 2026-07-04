package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.item.MilkCatalyst;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for the Mimic Milk source block (#253). Carries the synthesized
 * item id (the slimes this source spawns wear it; the fluid tints from it) and a
 * remaining-spawn budget plus the catalyst upgrades (Count / Speed / Quantity /
 * Endless), at parity with {@link SlimeMilkSourceBlockEntity} - players drop
 * catalysts into the pool to buff a Mimic source exactly like a species source
 * (the RF throttle at the Alembic still gates how much milk you can make upstream).
 */
public class MimicMilkSourceBlockEntity extends BlockEntity {

    /** Fallback spawn budget before COMMON config loads (mirrors the variant default). */
    public static final int DEFAULT_SPAWNS = 16;

    @Nullable
    private Identifier synthesizedItem;

    /** Remaining slimes this source will spawn before draining; -1 = not yet seeded. */
    private int spawnsRemaining = -1;
    /** Capacity high-water mark (the "N / cap" denominator); -1 = not yet seeded. Count raises it. */
    private int spawnsCapacity = -1;
    /** Speed catalyst level (faster spawn cadence). */
    private int speedLevel = 0;
    /** Quantity catalyst level (more slimes per spawn event). */
    private int quantityLevel = 0;
    /** Endless catalyst flag (never depletes). */
    private boolean infinite = false;

    public MimicMilkSourceBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.MIMIC_MILK_SOURCE.get(), pos, state);
    }

    /** Configured starting budget (depletionCount), or the fallback before config loads. */
    public static int defaultSpawns() {
        return PFConfig.SPEC.isLoaded() ? PFConfig.DEPLETION_COUNT.get() : DEFAULT_SPAWNS;
    }

    @Nullable
    public Identifier getSynthesizedItem() {
        return synthesizedItem;
    }

    /** Set the item this source spawns; seeds the spawn budget on first assignment. */
    public void setSynthesizedItem(@Nullable Identifier synthesizedItem) {
        boolean changed = !java.util.Objects.equals(this.synthesizedItem, synthesizedItem);
        this.synthesizedItem = synthesizedItem;
        if (synthesizedItem != null && spawnsRemaining < 0) {
            spawnsRemaining = defaultSpawns();
            spawnsCapacity = defaultSpawns();
        }
        if (changed) {
            setChanged();
            if (this.level != null) {
                this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    public int getSpawnsRemaining() {
        return spawnsRemaining < 0 ? defaultSpawns() : spawnsRemaining;
    }

    /** Total spawn budget (high-water mark); the "N / cap" denominator. Never below remaining. */
    public int getSpawnsCapacity() {
        int cap = spawnsCapacity < 0 ? defaultSpawns() : spawnsCapacity;
        return Math.max(cap, getSpawnsRemaining());
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
     * Restore the full budget + catalyst set from a re-placed bucket, clamped to
     * config bounds. Mirrors {@link SlimeMilkSourceBlockEntity#restoreUpgrades} so a
     * buffed Mimic source survives the world -> bucket -> world round-trip instead of
     * resetting to the default budget (the pre-v1.7 re-bucket reset, avoided here).
     */
    public void restoreUpgrades(int remaining, int capacity, int speed, int quantity, boolean infinite) {
        this.spawnsRemaining = Mth.clamp(remaining, 0, SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS);
        this.spawnsCapacity = Mth.clamp(Math.max(capacity, this.spawnsRemaining), 0, SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS);
        this.speedLevel = Mth.clamp(speed, 0, PFConfig.catalystMaxSpeedLevel());
        this.quantityLevel = Mth.clamp(quantity, 0, PFConfig.catalystMaxQuantityLevel());
        this.infinite = infinite;
        setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Spend one spawn from the budget. No-op when Endless. */
    public void decrementSpawns() {
        if (infinite) {
            return;
        }
        if (spawnsRemaining < 0) {
            spawnsRemaining = defaultSpawns();
        }
        if (spawnsRemaining > 0) {
            spawnsRemaining--;
            setChanged();
        }
    }

    /**
     * Apply one catalyst's effect, mirroring {@link SlimeMilkSourceBlockEntity#applyCatalyst}.
     * Returns true if it changed anything (so the caller consumes one item); false when
     * already maxed/redundant (the dropped item is left for the player). Bounds from
     * {@link PFConfig}; the Count cap reuses the shared {@link SlimeMilkSourceBlockEntity#MAX_STORED_SPAWNS}.
     */
    public boolean applyCatalyst(MilkCatalyst catalyst) {
        boolean applied = switch (catalyst) {
            case COUNT -> {
                if (spawnsRemaining < 0) {
                    spawnsRemaining = defaultSpawns();
                    spawnsCapacity = defaultSpawns();
                }
                if (spawnsRemaining >= SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS) {
                    yield false;
                }
                int added = PFConfig.catalystCountPer();
                spawnsRemaining = Mth.clamp(spawnsRemaining + added, 0, SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS);
                spawnsCapacity = Mth.clamp(spawnsCapacity + added, 0, SlimeMilkSourceBlockEntity.MAX_STORED_SPAWNS);
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

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (synthesizedItem != null) {
            output.putString("SynthesizedItem", synthesizedItem.toString());
        }
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
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        String item = input.getStringOr("SynthesizedItem", "");
        synthesizedItem = item.isEmpty() ? null : Identifier.tryParse(item);
        spawnsRemaining = input.getIntOr("SpawnsRemaining", -1);
        spawnsCapacity = input.getIntOr("SpawnsCapacity", -1);
        speedLevel = Math.max(0, input.getIntOr("SpeedLevel", 0));
        quantityLevel = Math.max(0, input.getIntOr("QuantityLevel", 0));
        infinite = input.getBooleanOr("Infinite", false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // Full save so the Jade look-at tooltip reads the live budget + catalyst
        // levels on the client (mirrors AlembicBlockEntity); a partial tag left the
        // client reading server-side defaults for remaining/cap/speed/quantity.
        return saveCustomOnly(registries);
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
