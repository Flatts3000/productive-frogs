package com.flatts.productivefrogs.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.world.World;

public class CustomFrogEntity extends FrogEntity {

    public CustomFrogEntity(EntityType<? extends FrogEntity> entityType, World world) {
        super(entityType, world);
    }

    public int getStat1() {
        // Replace with actual stat retrieval logic
        return 10;
    }

    public int getStat2() {
        // Replace with actual stat retrieval logic
        return 10;
    }

    public int getStat3() {
        // Replace with actual stat retrieval logic
        return 10;
    }
}