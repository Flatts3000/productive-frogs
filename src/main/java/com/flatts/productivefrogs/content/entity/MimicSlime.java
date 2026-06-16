package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFDataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Mimic Slime - the Equivalence lane's synthesized slime (#253). Unlike
 * {@link ResourceSlime} it carries no {@link Category} and no registered
 * {@code SlimeVariant}; instead it stores an arbitrary <b>item id</b> (the
 * {@link PFDataComponents#SYNTHESIZED_ITEM} the Alembic stamped), and its name
 * and tint are derived from that item at runtime.
 *
 * <p><b>Deliberately a sibling of vanilla {@link Slime}, NOT a subclass of
 * {@code ResourceSlime}.</b> That keeps the lane walled off with zero edits to
 * the existing six-species machinery: {@code ResourceFrogAttackablesSensor} and
 * {@code FrogTongueDropHandler} both gate on {@code instanceof ResourceSlime},
 * so a Mimic Slime is invisible to the species frogs by construction. Only the
 * new Midas frog (its own sensor/drop) eats it.
 *
 * <p>Always size 1; never splits (the size-{@literal >}1 branch is suppressed in
 * {@link #remove}). The duplication multiplier rides the milk spawn economy, not
 * slime splitting.
 */
public class MimicSlime extends Slime {

    /** Synthesized item id (e.g. {@code minecraft:redstone}), or empty for an un-stamped slime. */
    private static final EntityDataAccessor<String> DATA_ITEM_ID =
        SynchedEntityData.defineId(MimicSlime.class, EntityDataSerializers.STRING);

    public MimicSlime(EntityType<? extends MimicSlime> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ITEM_ID, "");
    }

    /** The carried item id, or {@code null} if un-stamped. */
    @Nullable
    public ResourceLocation getSynthesizedItem() {
        String s = this.entityData.get(DATA_ITEM_ID);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    public void setSynthesizedItem(@Nullable ResourceLocation itemId) {
        this.entityData.set(DATA_ITEM_ID, itemId == null ? "" : itemId.toString());
    }

    /** The carried {@link Item}, or {@code null} if un-stamped or unknown. */
    @Nullable
    public Item getSynthesizedItemAsItem() {
        ResourceLocation id = getSynthesizedItem();
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        // Mimic Slimes never split: zero the size before delegating so vanilla
        // Slime#remove's size>1 split branch fails (mirrors ResourceSlime).
        if (!this.level().isClientSide() && this.getSize() > 1 && this.isDeadOrDying()) {
            this.setSize(1, false);
        }
        super.remove(reason);
    }

    /**
     * A neutral prismatic splash rather than vanilla's green slimeball particle.
     * Fixed colour (not the per-item tint) because particles are emitted on the
     * server tick too, where the client-only sprite-average resolver isn't
     * available; the shell tint (client renderer) carries the per-item colour.
     */
    @Override
    protected ParticleOptions getParticleType() {
        return Category.dustParticle(0xF0F0FF);
    }

    @Override
    public Component getName() {
        if (this.hasCustomName()) {
            return super.getName();
        }
        String descriptionId = getType().getDescriptionId();
        Item item = getSynthesizedItemAsItem();
        if (item != null) {
            return Component.translatable(descriptionId + ".item",
                Component.translatable(item.getDescriptionId()));
        }
        return Component.translatable(descriptionId);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ResourceLocation id = getSynthesizedItem();
        if (id != null) {
            tag.putString("SynthesizedItem", id.toString());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SynthesizedItem", Tag.TAG_STRING)) {
            setSynthesizedItem(ResourceLocation.tryParse(tag.getString("SynthesizedItem")));
        }
    }
}
