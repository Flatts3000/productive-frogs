package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for the Mimic Milk source block (#253). Carries the synthesized
 * item id (the slimes this source spawns wear it; the fluid tints from it) and a
 * remaining-spawn budget. Deliberately leaner than {@link SlimeMilkSourceBlockEntity}:
 * no catalyst upgrades / density cap / infinite flag (the EE lane's throttle is
 * RF at the Alembic, not catalysts) - those can be layered on later if wanted.
 */
public class MimicMilkSourceBlockEntity extends BlockEntity {

    /** Fallback spawn budget before COMMON config loads (mirrors the variant default). */
    public static final int DEFAULT_SPAWNS = 16;

    @Nullable
    private ResourceLocation synthesizedItem;

    /** Remaining slimes this source will spawn before draining; -1 = not yet seeded. */
    private int spawnsRemaining = -1;

    public MimicMilkSourceBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.MIMIC_MILK_SOURCE.get(), pos, state);
    }

    /** Configured starting budget (depletionCount), or the fallback before config loads. */
    public static int defaultSpawns() {
        return PFConfig.SPEC.isLoaded() ? PFConfig.DEPLETION_COUNT.get() : DEFAULT_SPAWNS;
    }

    @Nullable
    public ResourceLocation getSynthesizedItem() {
        return synthesizedItem;
    }

    /** Set the item this source spawns; seeds the spawn budget on first assignment. */
    public void setSynthesizedItem(@Nullable ResourceLocation synthesizedItem) {
        boolean changed = !java.util.Objects.equals(this.synthesizedItem, synthesizedItem);
        this.synthesizedItem = synthesizedItem;
        if (synthesizedItem != null && spawnsRemaining < 0) {
            spawnsRemaining = defaultSpawns();
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

    /** Spend one spawn from the budget. */
    public void decrementSpawns() {
        if (spawnsRemaining < 0) {
            spawnsRemaining = defaultSpawns();
        }
        if (spawnsRemaining > 0) {
            spawnsRemaining--;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (synthesizedItem != null) {
            tag.putString("SynthesizedItem", synthesizedItem.toString());
        }
        tag.putInt("SpawnsRemaining", spawnsRemaining);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        synthesizedItem = tag.contains("SynthesizedItem", Tag.TAG_STRING)
            ? ResourceLocation.tryParse(tag.getString("SynthesizedItem"))
            : null;
        spawnsRemaining = tag.contains("SpawnsRemaining", Tag.TAG_INT) ? tag.getInt("SpawnsRemaining") : -1;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (synthesizedItem != null) {
            tag.putString("SynthesizedItem", synthesizedItem.toString());
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
