package com.flatts.productivefrogs.entities;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;

public class ResourceFrogEntity extends LivingEntity {
    public ResourceFrogEntity(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    public void saveWithoutId(NbtCompound nbt) {
        super.saveWithoutId(nbt);
        // Add any additional data that needs to be saved
    }
}