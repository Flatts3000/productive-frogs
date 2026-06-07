package com.flatts.productivefrogs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player != null) {
                for (Entity entity : player.level.getEntities()) {
                    if (entity instanceof FrogEntity) {
                        FrogEntity frog = (FrogEntity) entity;
                        if (frog.getStat1() == 10 && frog.getStat2() == 10 && frog.getStat3() == 10) {
                            // Add ambient sparkle particles
                            addSparkleParticles(frog);
                        }
                    }
                }
            }
        }
    }

    private void addSparkleParticles(FrogEntity frog) {
        // Add sparkle particles using the vanilla END_ROD particle
        Minecraft.getInstance().particleEngine.add(new SparkleParticle(frog.level, frog.getX(), frog.getY(), frog.getZ()));
    }
}