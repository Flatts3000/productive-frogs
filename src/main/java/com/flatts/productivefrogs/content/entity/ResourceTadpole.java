package com.flatts.productivefrogs.content.entity;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.entity.animal.frog.Tadpole;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The Resource Tadpole — a Productive Frogs tadpole that matures into a
 * {@link ResourceFrog} of its locked {@link Category}, rather than into a
 * vanilla biome-determined Frog.
 *
 * <p>Inherits vanilla {@link Tadpole} behavior wholesale: brain, AI, bucket
 * tag, slimeball-acceleration, water-survival timer, sounds. The only override
 * is {@link #ageUp()} (exposed via access transformer) which spawns a
 * Resource Frog of the matching category at maturation instead of a vanilla
 * Frog.
 *
 * <p>Variant is locked at the egg-priming step. Unlike vanilla where biome
 * decides the frog variant on maturation, our category is carried from the
 * Primed Frog Egg → Tadpole → Frog without biome roulette.
 */
public class ResourceTadpole extends Tadpole {

    private static final EntityDataAccessor<Integer> DATA_CATEGORY =
        SynchedEntityData.defineId(ResourceTadpole.class, EntityDataSerializers.INT);

    public ResourceTadpole(EntityType<? extends ResourceTadpole> type, Level level) {
        super((EntityType<? extends AbstractFish>) (EntityType<?>) type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CATEGORY, Category.METALLIC.ordinal());
    }

    public Category getCategory() {
        return Category.values()[this.entityData.get(DATA_CATEGORY)];
    }

    public void setCategory(Category category) {
        this.entityData.set(DATA_CATEGORY, category.ordinal());
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        out.putString("Category", getCategory().name());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        in.getString("Category").ifPresent(name -> {
            try {
                setCategory(Category.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // Unknown category in save data — leave default.
            }
        });
    }

    /**
     * Override the vanilla maturation hook (made overridable via access
     * transformer). Mirrors vanilla's logic but converts into a
     * {@link ResourceFrog} instead of {@code minecraft:frog}, carrying the
     * category forward.
     */
    @Override
    public void ageUp() {
        EntityType<ResourceFrog> target = PFEntities.RESOURCE_FROG.get();
        if (!EventHooks.canLivingConvert(this, target, timer -> {})) {
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        Category category = getCategory();
        this.convertTo(target, ConversionParams.single(this, false, false), frog -> {
            EventHooks.onLivingConvert(this, frog);
            frog.setCategory(category);
            frog.finalizeSpawn(
                serverLevel,
                serverLevel.getCurrentDifficultyAt(frog.blockPosition()),
                EntitySpawnReason.CONVERSION,
                null
            );
            frog.setPersistenceRequired();
            frog.fudgePositionAfterSizeChange(this.getDimensions(this.getPose()));
            this.playSound(SoundEvents.TADPOLE_GROW_UP, 0.15F, 1.0F);
        });
    }
}
