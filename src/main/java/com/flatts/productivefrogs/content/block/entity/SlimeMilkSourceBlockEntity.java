package com.flatts.productivefrogs.content.block.entity;

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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for {@link com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock}.
 * Stores a single field, the {@link com.flatts.productivefrogs.data.SlimeVariant}
 * identifier, so one generic {@code slime_milk} fluid/block can spawn the right
 * variant slime and tint per-variant, instead of one registered block per
 * variant. Same shape as {@link ConfigurableFroglightBlockEntity}.
 *
 * <p>The variant is written on placement from the Slime Milk bucket's
 * {@code SLIME_VARIANT} data component (see
 * {@code com.flatts.productivefrogs.content.item.SlimeMilkBucketItem}), persisted
 * via {@link #saveAdditional}/{@link #loadAdditional}, synced to the client via
 * {@link #getUpdateTag}/{@link #getUpdatePacket} (the in-world fluid tint reads it
 * through the position-aware {@code getTintColor} in {@code PFClientEvents}), and
 * exposed as an implicit component so re-bucketing a source stamps the variant
 * back onto the bucket.
 *
 * <p>A {@code null} variant is the defensive fallback for milk that spread from a
 * source (fluid spreading does not copy BlockEntities) or was placed by
 * {@code /setblock} without a variant: such milk renders with the default tint
 * and spawns nothing.
 */
public class SlimeMilkSourceBlockEntity extends BlockEntity {

    @Nullable
    private ResourceLocation variantId;

    public SlimeMilkSourceBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.SLIME_MILK_SOURCE.get(), pos, state);
    }

    @Nullable
    public ResourceLocation getVariantId() {
        return variantId;
    }

    /** Set the variant, mark for save, and push a block-update so the client retints. Server-side only. */
    public void setVariantId(@Nullable ResourceLocation variantId) {
        if (Objects.equals(this.variantId, variantId)) {
            return;
        }
        this.variantId = variantId;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (variantId != null) {
            builder.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.variantId = components.get(PFDataComponents.SLIME_VARIANT.get());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Variant", Tag.TAG_STRING)) {
            variantId = ResourceLocation.tryParse(tag.getString("Variant"));
        } else {
            variantId = null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
