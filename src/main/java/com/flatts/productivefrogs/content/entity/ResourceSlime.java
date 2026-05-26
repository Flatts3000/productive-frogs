package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bucketable;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.scores.PlayerTeam;

/**
 * The Resource Slime — Productive Frogs' category-locked slime variant. Mirrors
 * vanilla {@link Slime} behavior wholesale (movement, jump, push, sound,
 * spawn-rule eligibility) and adds a {@link Category} field that propagates
 * through split children so a {@code BOG} resource slime always splits into
 * smaller {@code BOG} resource slimes.
 *
 * <p>One entity type, six categories (Category-keyed), with per-resource
 * variants layered on via the data-driven {@code SlimeVariant} registry —
 * {@code iron} vs {@code copper} both resolve their parent category through
 * the registry (see {@code docs/architecture.md}). The variant id rides along
 * as a second synced data field beside the category; the category is the
 * cheap fallback that callers can read without a registry lookup.
 *
 * <p>Direct-kill drops (per design Q10): slimeballs only, vanilla parity.
 * Behavior lives in
 * {@code data/productivefrogs/loot_table/entities/resource_slime.json}, which
 * mirrors vanilla {@code minecraft:entities/slime}'s "size-1 only" condition.
 * Loot table customization has to go through JSON in 1.21.x — {@code Mob#getLootTable}
 * is {@code final}, so an override-by-subclass path isn't available.
 */
public class ResourceSlime extends Slime implements Bucketable {

    private static final EntityDataAccessor<Integer> DATA_CATEGORY =
        SynchedEntityData.defineId(ResourceSlime.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_FROM_BUCKET =
        SynchedEntityData.defineId(ResourceSlime.class, EntityDataSerializers.BOOLEAN);
    /**
     * Variant id (e.g. {@code productivefrogs:iron}) or empty string for
     * "category-only" slimes that pre-date the {@link SlimeVariant} registry.
     * Stored as String so it works without a custom EntityDataSerializer for
     * ResourceLocation. Category is still kept as fast-path on {@link #DATA_CATEGORY};
     * the variant is the source of truth when present.
     */
    private static final EntityDataAccessor<String> DATA_VARIANT_ID =
        SynchedEntityData.defineId(ResourceSlime.class, EntityDataSerializers.STRING);

    public ResourceSlime(EntityType<? extends ResourceSlime> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CATEGORY, Category.BOG.ordinal());
        builder.define(DATA_FROM_BUCKET, false);
        builder.define(DATA_VARIANT_ID, "");
    }

    public Category getCategory() {
        // Defensive: synced data is an int and could be corrupted by modded
        // packets or save migration. fromOrdinalOrDefault falls back to BOG
        // rather than crashing on out-of-range ordinals.
        return Category.fromOrdinalOrDefault(this.entityData.get(DATA_CATEGORY));
    }

    public void setCategory(Category category) {
        this.entityData.set(DATA_CATEGORY, category.ordinal());
    }

    /**
     * Variant identifier, or {@code null} for "category-only" slimes that
     * pre-date the {@link SlimeVariant} registry (still valid — they fall
     * back to the broad category visuals + name).
     */
    @org.jetbrains.annotations.Nullable
    public ResourceLocation getVariantId() {
        String s = this.entityData.get(DATA_VARIANT_ID);
        if (s.isEmpty()) return null;
        return ResourceLocation.tryParse(s);
    }

    /**
     * Set the variant on this slime. Also updates the cached {@link Category}
     * by looking up the variant's parent — keeps the two synced data fields
     * consistent and lets cheap getCategory() callers skip a registry lookup.
     */
    public void setVariant(@org.jetbrains.annotations.Nullable ResourceLocation variantId) {
        if (variantId == null) {
            this.entityData.set(DATA_VARIANT_ID, "");
            return;
        }
        this.entityData.set(DATA_VARIANT_ID, variantId.toString());

        // Eagerly sync category from the variant data — if the registry has
        // the variant, use its category; otherwise leave whatever category
        // was previously set (defensive against an unknown variant id).
        SlimeVariant variant = lookupVariant(variantId);
        if (variant != null) {
            setCategory(variant.category());
        }
    }

    /**
     * Resolve the variant record from the level's registry access. Returns
     * {@code null} when the variant id isn't in the registry — datapack
     * removed since save, or modded variant whose mod is no longer loaded.
     */
    @org.jetbrains.annotations.Nullable
    public SlimeVariant getVariant() {
        ResourceLocation id = getVariantId();
        return id == null ? null : lookupVariant(id);
    }

    @org.jetbrains.annotations.Nullable
    private SlimeVariant lookupVariant(ResourceLocation id) {
        Registry<SlimeVariant> registry = this.level().registryAccess().registry(PFRegistries.SLIME_VARIANT).orElse(null);
        return registry == null ? null : registry.get(id);
    }

    /**
     * Override vanilla's hardcoded {@code ParticleTypes.ITEM_SLIME} (which
     * carries the green slimeball texture) so each variant emits a splash
     * tinted with its own colour. Resolution order:
     *
     * <ol>
     *   <li>Variant's {@code primary_color} when this slime carries a known
     *       SlimeVariant — gives per-resource tints like iron-silver,
     *       copper-orange, gold-yellow.</li>
     *   <li>Category tint ({@link Category#tintRgb}) when no variant is set
     *       or the variant id isn't in the registry — covers the broad
     *       6-category fallback path used by split-discovery and the
     *       parent-species slimes.</li>
     * </ol>
     *
     * <p>Vanilla's splash loop in {@code Slime#tick} reads this method once
     * per particle, so the colour is sampled at emission time and reflects
     * any runtime category/variant changes.
     *
     * <p>The particle type switches from {@code ITEM_SLIME} (slimeball icon)
     * to {@link net.minecraft.core.particles.DustParticleOptions} (colored
     * dust speck) — slightly different shape than vanilla, but the colour
     * carries the per-variant signal that ITEM_SLIME couldn't.
     */
    @Override
    protected net.minecraft.core.particles.ParticleOptions getParticleType() {
        ResourceLocation variantId = getVariantId();
        SlimeVariant variant = variantId == null ? null : lookupVariant(variantId);
        int rgb = variant != null ? variant.primaryColor() : getCategory().tintRgb();
        return Category.dustParticle(rgb);
    }

    /**
     * Category-aware display name. When the slime carries a SlimeVariant that
     * still resolves in the registry, builds
     * {@code entity.productivefrogs.resource_slime.<variant_path>} — e.g.
     * "Iron Slime". When the variant id is absent OR resolves to {@code null}
     * (datapack/mod removed since save), falls back to the broad category
     * name ({@code entity.productivefrogs.resource_slime.<category_id>}) —
     * e.g. "Bog Slime". The variant-resolution check avoids showing a
     * raw translation key in the overlay when the lang entry doesn't exist.
     */
    @Override
    public net.minecraft.network.chat.Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        String descriptionId = getType().getDescriptionId();
        SlimeVariant variant = getVariant();
        if (variant != null) {
            ResourceLocation variantId = getVariantId();
            // variantId is non-null when getVariant() is non-null (the lookup
            // started from the id), but the explicit null-check makes the
            // contract obvious to readers.
            if (variantId != null) {
                return net.minecraft.network.chat.Component.translatable(
                    descriptionId + "." + variantId.getPath()
                );
            }
        }
        return net.minecraft.network.chat.Component.translatable(
            descriptionId + "." + getCategory().id()
        );
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Category", net.minecraft.nbt.Tag.TAG_STRING)) {
            try {
                setCategory(Category.valueOf(tag.getString("Category")));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in save data — leave default.
            }
        }
        setFromBucket(tag.contains("FromBucket", net.minecraft.nbt.Tag.TAG_BYTE)
            && tag.getBoolean("FromBucket"));
        // Variant load: read AFTER Category so setVariant's category sync
        // overrides the Category field with the registry's authoritative value.
        if (tag.contains("Variant", net.minecraft.nbt.Tag.TAG_STRING)) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("Variant"));
            if (id != null) {
                setVariant(id);
            }
        }
    }

    /**
     * Re-implement vanilla {@link Slime#remove} so split offspring inherit the
     * parent's category. Vanilla's split runs in {@code Slime.remove}'s
     * {@code convertTo} lambda which only sets size + position — by overriding
     * here and zeroing size before delegating to {@code super}, we suppress the
     * vanilla split (its {@code size > 1} guard fails) while preserving the
     * rest of {@code Mob#remove}'s cleanup chain.
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        int originalSize = this.getSize();
        if (!this.level().isClientSide() && originalSize > 1 && this.isDeadOrDying()) {
            splitWithCategory(originalSize);
            this.setSize(1, false);
        }
        super.remove(reason);
    }

    private void splitWithCategory(int originalSize) {
        float width = this.getDimensions(this.getPose()).width();
        float halfWidth = width / 2.0F;
        int childSize = originalSize / 2;
        int childCount = 2 + this.random.nextInt(3);
        Category category = getCategory();
        ResourceLocation variantId = getVariantId();
        boolean persistent = this.isPersistenceRequired();
        boolean noAi = this.isNoAi();

        for (int l = 0; l < childCount; l++) {
            float xOff = (l % 2 - 0.5F) * halfWidth;
            float zOff = (l / 2 - 0.5F) * halfWidth;
            ResourceSlime child = (ResourceSlime) this.getType().create(this.level());
            if (child == null) continue;
            if (persistent) child.setPersistenceRequired();
            child.setNoAi(noAi);
            child.setInvulnerable(this.isInvulnerable());
            child.setSize(childSize, true);
            // Set category first as a fallback, then variant — setVariant
            // will sync category from the registry if the variant resolves;
            // if the registry isn't loaded yet, the category fallback keeps
            // the child consistent with the parent.
            child.setCategory(category);
            if (variantId != null) {
                child.setVariant(variantId);
            }
            child.moveTo(
                this.getX() + xOff,
                this.getY() + 0.5,
                this.getZ() + zOff,
                this.random.nextFloat() * 360.0F,
                0.0F
            );
            this.level().addFreshEntity(child);
        }
    }

    // Direct-kill drops are handled via the entity's default loot table at
    // data/productivefrogs/loot_table/entities/resource_slime.json — a copy
    // of vanilla slime's loot (slimeballs from size-1 only). Per design Q10,
    // the resource conversion lives in the frog tongue path, not in direct
    // combat. We can't override getLootTable() in code because Mob declares
    // it final in 1.21.x.

    // ---------------------------------------------------------------------
    // Bucketable — size-1 capture/release with category preservation
    // ---------------------------------------------------------------------

    @Override
    public boolean fromBucket() {
        return this.entityData.get(DATA_FROM_BUCKET);
    }

    @Override
    public void setFromBucket(boolean fromBucket) {
        this.entityData.set(DATA_FROM_BUCKET, fromBucket);
    }

    /**
     * Right-click handler. Only size-1 slimes are bucketable per design; larger
     * slimes split via the standard slime mechanic and the player buckets the
     * offspring.
     *
     * <p>Capture uses an <b>empty</b> bucket, not a water bucket. Vanilla
     * {@link Bucketable#bucketMobPickup} hardcodes {@code WATER_BUCKET} (the
     * fish/axolotl/tadpole convention), which reads wrong for a non-aquatic
     * slime — a player reaches for an empty bucket and nothing happens. So we
     * don't bridge to {@code bucketMobPickup}; {@link #tryEmptyBucketCapture}
     * is a minimal re-implementation keyed on the empty bucket instead.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (this.getSize() == 1) {
            InteractionResult captured = tryEmptyBucketCapture(player, hand);
            if (captured != null) {
                return captured;
            }
        }
        return super.mobInteract(player, hand);
    }

    /**
     * Capture a size-1 Resource Slime with an empty bucket ({@link Items#BUCKET})
     * into a Slime Bucket. Mirrors vanilla {@link Bucketable#bucketMobPickup}
     * exactly — pickup sound, {@link #saveToBucketTag} NBT write, filled-result
     * handling that respects creative/stack semantics, and the FILLED_BUCKET
     * advancement trigger — but matches on the empty bucket rather than the
     * water bucket. Returns {@code null} when the held item is not an empty
     * bucket, so the caller falls through to vanilla interaction.
     */
    @org.jetbrains.annotations.Nullable
    private InteractionResult tryEmptyBucketCapture(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(Items.BUCKET) || !this.isAlive()) {
            return null;
        }
        this.playSound(this.getPickupSound(), 1.0F, 1.0F);
        ItemStack filled = this.getBucketItemStack();
        this.saveToBucketTag(filled);
        ItemStack result = ItemUtils.createFilledResult(held, player, filled, false);
        player.setItemInHand(hand, result);
        Level level = this.level();
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            CriteriaTriggers.FILLED_BUCKET.trigger(serverPlayer, filled);
        }
        this.discard();
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    @SuppressWarnings("deprecation")
    public void saveToBucketTag(ItemStack stack) {
        // Bucketable.saveDefaultDataToBucketTag is @Deprecated (1.21.11) but
        // still functional and is the vanilla source-of-truth for the standard
        // mob-bucket NBT shape (NoAI, Silent, NoGravity, Glowing, Invulnerable,
        // Health, plus the CUSTOM_NAME copy). Inlining would diverge from
        // vanilla's set whenever Mojang adjusts it. The deprecation isn't
        // forRemoval, so suppress is the cheaper path.
        Bucketable.saveDefaultDataToBucketTag(this, stack);
        Category category = getCategory();
        ResourceLocation variantId = getVariantId();
        CustomData.update(DataComponents.BUCKET_ENTITY_DATA, stack, tag -> {
            tag.putString("Category", category.name());
            if (variantId != null) {
                tag.putString("Variant", variantId.toString());
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void loadFromBucketTag(CompoundTag tag) {
        // See saveToBucketTag — same deprecation rationale.
        Bucketable.loadDefaultDataFromBucketTag(this, tag);
        if (tag.contains("Category", net.minecraft.nbt.Tag.TAG_STRING)) { try { setCategory(Category.valueOf(tag.getString("Category"))); } catch (IllegalArgumentException ignored) {} }
        // Read Variant AFTER Category so setVariant's category sync wins when
        // the variant resolves in the registry, but the category fallback
        // remains correct if the variant id is unknown.
        if (tag.contains("Variant", net.minecraft.nbt.Tag.TAG_STRING)) { ResourceLocation id = ResourceLocation.tryParse(tag.getString("Variant")); if (id != null) setVariant(id); }
        // Flag the slime as bucket-originated so it survives chunk reloads
        // without despawning — bucket-released mobs are conceptually
        // player-placed and need persistence parity with named mobs.
        setFromBucket(true);
    }

    @Override
    public ItemStack getBucketItemStack() {
        return new ItemStack(PFItems.SLIME_BUCKET.get());
    }

    @Override
    public SoundEvent getPickupSound() {
        return SoundEvents.BUCKET_FILL_FISH;
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Category", getCategory().name());
        // The category save is handled above; persist the fromBucket flag too
        // so a slime released from a bucket doesn't despawn on chunk reload.
        tag.putBoolean("FromBucket", fromBucket());
        ResourceLocation variantId = getVariantId();
        if (variantId != null) {
            tag.putString("Variant", variantId.toString());
        }
    }
    // NOTE: the readAdditional override above intentionally lives in this class
    // unchanged from the original — the FromBucket flag is restored from
    // bucket NBT via loadFromBucketTag, which is wired by MobBucketItem on
    // release. World-saved slimes don't need it (they spawn normally).
}
