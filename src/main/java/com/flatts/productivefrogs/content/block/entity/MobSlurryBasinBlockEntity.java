package com.flatts.productivefrogs.content.block.entity;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.content.item.MobSlurryBucketItem;
import com.flatts.productivefrogs.event.PredationTeleportHandler;
import com.flatts.productivefrogs.registry.PFBlockEntities;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

/**
 * The Mob Slurry Basin's engine (#281, predation Phase 3): charged with a Mob
 * Slurry bucket, it respawns that mob into the adjacent cells on the milk
 * spawn economy, for a Predator Frog to farm.
 *
 * <p><b>Every Basin-spawned mob carries the teleport lock</b>
 * ({@link PredationTeleportHandler#disableTeleport} - maintainer ruling:
 * teleporting entities spawned by slurry have teleportation disabled by
 * default). That is the settled no-enclosures answer for enderman/shulker;
 * it is a no-op for mobs that never teleport, so it is stamped uniformly.
 *
 * <p>Boss mobs ({@code c:bosses}) are refused defensively - the Slurry Press
 * already refuses to produce boss slurry, so this only matters for tampered
 * NBT.
 */
public class MobSlurryBasinBlockEntity extends AbstractBasinBlockEntity {

    public MobSlurryBasinBlockEntity(BlockPos pos, BlockState state) {
        super(PFBlockEntities.MOB_SLURRY_BASIN.get(), pos, state);
    }

    @Override
    @Nullable
    public Identifier keyFromBucket(ItemStack stack) {
        return stack.getItem() instanceof MobSlurryBucketItem ? MobSlurryBucketItem.entityOf(stack) : null;
    }

    @Override
    public boolean acceptsKey(ServerLevel level, Identifier key) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        return type != null && !type.builtInRegistryHolder().is(Tags.EntityTypes.BOSSES);
    }

    @Override
    protected ItemStack mintBucket(Identifier key) {
        return MobSlurryBucketItem.forEntity(key);
    }

    @Override
    public Fluid pipeFluid() {
        return PFFluids.MOB_SLURRY.get();
    }

    @Override
    public DataComponentType<Identifier> pipeKeyComponent() {
        return PFDataComponents.SLURRIED_ENTITY.get();
    }

    @Override
    @Nullable
    protected Entity createSpawnEntity(ServerLevel level, Identifier key) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) {
            return null;
        }
        Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
        if (entity != null) {
            // The teleport lock, stamped at spawn time (persistent attachment) -
            // wild mobs never carry it, so vanilla behaviour elsewhere is untouched.
            PredationTeleportHandler.disableTeleport(entity);
        }
        return entity;
    }

    @Override
    protected boolean isCrowded(ServerLevel level, BlockPos pos, Identifier key) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(key).orElse(null);
        if (type == null) {
            return true; // unknown key never spawns, so "crowded" is the safe pause
        }
        AABB box = new AABB(pos).inflate(PFConfig.spawnCapRadius());
        int count = level.getEntities(type, box, e -> e instanceof LivingEntity living && living.isAlive()).size();
        Integer capOverride = com.flatts.productivefrogs.content.block.SlimeMilkSourceBlock.spawnCapOverride;
        int cap = capOverride != null ? capOverride : PFConfig.maxNearbySlimes();
        return count >= cap;
    }

    @Override
    protected boolean systemEnabled() {
        return PFConfig.predatorsEnabled();
    }
}
