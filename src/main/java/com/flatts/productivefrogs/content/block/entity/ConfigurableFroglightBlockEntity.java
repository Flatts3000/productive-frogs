package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * BlockEntity for {@link com.flatts.productivefrogs.content.block.ConfigurableFroglightBlock}.
 * Stores a single field — the {@link com.flatts.productivefrogs.data.SlimeVariant}
 * identifier — that drives the per-variant in-world tint via
 * {@link com.flatts.productivefrogs.client.PFClientEvents}'s registered
 * {@code BlockColor}.
 *
 * <p>The variant is written on placement from the item's {@code SLIME_VARIANT}
 * data component (see {@link com.flatts.productivefrogs.content.item.ConfigurableFroglightItem}'s
 * {@code updateCustomBlockEntityTag} override), persisted to disk via
 * {@link #saveAdditional}/{@link #loadAdditional}, and synced to the client via
 * {@link #getUpdateTag} (initial chunk send) and {@link #getUpdatePacket} (mid-life
 * mutations — currently none, but the client-sync path is wired so a future
 * recipe / tool could rewrite the variant in place).
 *
 * <p>When the block is broken the loot table copies the BE's variant back into
 * the dropped item's {@code SLIME_VARIANT} component (see
 * {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}),
 * so the variant survives the round-trip.
 */
public class ConfigurableFroglightBlockEntity extends BlockEntity {

    /**
     * Variant identifier (e.g. {@code productivefrogs:iron}), or {@code null}
     * for an unstamped block. Null is the defensive fallback — if a player
     * uses {@code /setblock} with no item NBT or the placement path forgets
     * to write the tag, the block still loads and renders with the
     * configurable-froglight model + the default tint.
     */
    @Nullable
    private ResourceLocation variantId;

    public ConfigurableFroglightBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.CONFIGURABLE_FROGLIGHT.get(), pos, state);
    }

    @Nullable
    public ResourceLocation getVariantId() {
        return variantId;
    }

    /**
     * Set the variant + mark the BE for save + push a block-update so the
     * client receives the new tint via {@link #getUpdatePacket}. Callers must
     * be on the server — client-side mutations would be overwritten by the
     * next sync.
     */
    public void setVariantId(@Nullable ResourceLocation variantId) {
        if (java.util.Objects.equals(this.variantId, variantId)) {
            return;
        }
        this.variantId = variantId;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /**
     * Expose the variant as an implicit data component so the vanilla
     * loot-function {@code minecraft:copy_components} with source
     * {@code block_entity} can copy it into the dropped item (see the
     * loot table at
     * {@code data/productivefrogs/loot_table/blocks/configurable_froglight.json}).
     * This also makes creative pick-block automatically stamp the variant
     * on the picked item — vanilla calls this when building the
     * {@code getCloneItemStack} result.
     */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (variantId != null) {
            builder.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        }
    }

    /**
     * Inverse of {@link #collectImplicitComponents}: when a player places
     * a variant-stamped item, vanilla calls this with the item's components
     * before our explicit {@code updateCustomBlockEntityTag} runs. Pulling
     * the variant in here keeps the BE state consistent in the rare path
     * where vanilla constructs a BE from components alone (e.g. a /clone
     * with components blocks moved into world via a tool that doesn't run
     * the item-use code path).
     *
     * <p>Assigns the component value directly — including null — so this
     * method is a true inverse of {@link #collectImplicitComponents}. If
     * the incoming map omits SLIME_VARIANT and the BE previously held one,
     * we clear it rather than silently retaining the stale variant. Catches
     * the /clone-with-components edge case where a player clones an
     * unstamped Froglight onto an already-stamped one and expects the
     * clone to win.
     */
    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        this.variantId = components.get(PFDataComponents.SLIME_VARIANT.get());
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        if (variantId != null) {
            out.putString("Variant", variantId.toString());
        }
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        variantId = in.getString("Variant")
            .map(ResourceLocation::tryParse)
            .orElse(null);
    }

    /**
     * Initial chunk sync. The client needs the variant to pick the right
     * tint at first render — without this, the block would flash with the
     * default tint until the BE caught up via a per-tick packet.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider lookup) {
        CompoundTag tag = super.getUpdateTag(lookup);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
        return tag;
    }

    /**
     * Mid-life mutation sync — fires after {@link #setVariantId} via the
     * sendBlockUpdated call in that setter. The default
     * {@code ClientboundBlockEntityDataPacket.create} reads
     * {@link #getUpdateTag} for the payload, so the variant ships along.
     */
    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
