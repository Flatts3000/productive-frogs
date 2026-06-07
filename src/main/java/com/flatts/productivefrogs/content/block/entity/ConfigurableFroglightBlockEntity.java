package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.data.StoredEffect;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
// Note: BlockEntity.DataComponentInput is protected so cannot be imported;
// the applyImplicitComponents override uses it via parent-class scoping.
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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

    /**
     * The captured potion effect (#162), or null for a plain Froglight. When
     * present and {@link StoredEffect#enabled()}, {@link #serverTick} applies it
     * to every living entity in {@link #AURA_RADIUS} on a {@link #AURA_PULSE_TICKS}
     * cadence, and the client draws effect-colored swirl particles.
     */
    @Nullable
    private StoredEffect effect;

    /** Aura reach in blocks. */
    public static final double AURA_RADIUS = 8.0;
    /** Ticks between aura applications (re-up cadence; the effect duration outlasts it so it never drops). */
    public static final int AURA_PULSE_TICKS = 40;
    /** Ticks between client particle bursts while the aura is on (a calm, occasional wisp). */
    public static final int PARTICLE_INTERVAL_TICKS = 12;

    public ConfigurableFroglightBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.CONFIGURABLE_FROGLIGHT.get(), pos, state);
    }

    @Nullable
    public ResourceLocation getVariantId() {
        return variantId;
    }

    /** The captured effect (client + Jade read this off the synced BE), or null. */
    @Nullable
    public StoredEffect getEffect() {
        return effect;
    }

    /** True when this Froglight carries an effect that is currently toggled on. */
    public boolean isAuraActive() {
        return effect != null && effect.enabled();
    }

    /**
     * Set/replace the captured effect (server-side; mirrors {@link #setVariantId}'s
     * save + client-sync). Null clears it back to a plain Froglight.
     */
    public void setEffect(@Nullable StoredEffect effect) {
        if (java.util.Objects.equals(this.effect, effect)) {
            return;
        }
        this.effect = effect;
        setChanged();
        if (this.level != null) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    /**
     * Flip the aura on/off (the right-click toggle). No-op (returns false) on a
     * plain Froglight. Returns the new enabled state so the block can pick the
     * activate vs deactivate sound.
     */
    public boolean toggleAura() {
        if (effect == null) {
            return false;
        }
        setEffect(effect.withEnabled(!effect.enabled()));
        return effect.enabled();
    }

    /**
     * Aura tick: while enabled, re-apply the captured effect to every living
     * entity in range every {@link #AURA_PULSE_TICKS} ticks. Affects ALL living
     * entities (#162 decision) - players and mobs alike, so a Poison/Wither
     * Froglight is a perimeter tool. Plain or toggled-off Froglights early-out
     * on a single null/flag check (this BE type now ticks on every placed
     * Froglight, decorative included - the guard keeps that cost to one branch).
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, ConfigurableFroglightBlockEntity be) {
        StoredEffect e = be.effect;
        if (e == null || !e.enabled() || level.getGameTime() % AURA_PULSE_TICKS != 0L) {
            return;
        }
        AABB area = new AABB(pos).inflate(AURA_RADIUS);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            entity.addEffect(e.toInstance());
        }
    }

    /**
     * Client particle stream while the aura is on (#162). A steady few
     * effect-colored swirl particles every {@link #PARTICLE_INTERVAL_TICKS}
     * ticks rising off the top - deterministic and continuous, unlike the
     * random {@code animateTick} (which fires too sparsely to read as on).
     * Silent while off / on a plain Froglight, giving a clear on/off tell.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, ConfigurableFroglightBlockEntity be) {
        StoredEffect e = be.effect;
        if (e == null || !e.enabled() || level.getGameTime() % PARTICLE_INTERVAL_TICKS != 0L) {
            return;
        }
        // MobEffect.getColor() is 0x00RRGGBB (no alpha); ColorParticleOption
        // reads the top byte as alpha, so without this OR the swirl spawns fully
        // transparent (the invisible-aura bug). Force opaque.
        ColorParticleOption particle = ColorParticleOption.create(
            ParticleTypes.ENTITY_EFFECT, 0xFF000000 | e.effect().value().getColor());
        // One gentle wisp per burst, drifting up slowly.
        double x = pos.getX() + 0.5 + (level.random.nextDouble() - 0.5) * 1.0;
        double y = pos.getY() + 0.9 + level.random.nextDouble() * 0.3;
        double z = pos.getZ() + 0.5 + (level.random.nextDouble() - 0.5) * 1.0;
        level.addParticle(particle, x, y, z, 0.0, 0.01, 0.0);
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
        if (effect != null) {
            builder.set(PFDataComponents.STORED_EFFECT.get(), effect);
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
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        this.variantId = components.get(PFDataComponents.SLIME_VARIANT.get());
        this.effect = components.get(PFDataComponents.STORED_EFFECT.get());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
        writeEffect(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Variant", Tag.TAG_STRING)) {
            variantId = ResourceLocation.tryParse(tag.getString("Variant"));
        } else {
            variantId = null;
        }
        readEffect(tag);
    }

    /**
     * StoredEffect persists/syncs as a {@code StoredEffect} sub-tag via its
     * codec. The effect's MobEffect.CODEC encodes by registry name (a plain
     * string) so {@link NbtOps} is sufficient - no RegistryOps needed.
     */
    private void writeEffect(CompoundTag tag) {
        if (effect != null) {
            StoredEffect.CODEC.encodeStart(NbtOps.INSTANCE, effect)
                .resultOrPartial(err -> {})
                .ifPresent(encoded -> tag.put("StoredEffect", encoded));
        }
    }

    private void readEffect(CompoundTag tag) {
        if (tag.contains("StoredEffect", Tag.TAG_COMPOUND)) {
            effect = StoredEffect.CODEC.parse(NbtOps.INSTANCE, tag.getCompound("StoredEffect"))
                .resultOrPartial(err -> {})
                .orElse(null);
        } else {
            effect = null;
        }
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
        // Effect rides the initial chunk sync too, so the client can draw aura
        // particles and Jade can name the effect without a separate packet.
        writeEffect(tag);
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
