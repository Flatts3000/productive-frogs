package com.flatts.productivefrogs.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererManager;
import net.minecraft.client.renderer.entity.model.FrogModel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ResourceFrogRenderer extends EntityRenderer<FrogEntity, FrogModel<FrogEntity>> {

    public ResourceFrogRenderer(EntityRendererManager renderManager) {
        super(renderManager, new FrogModel<>(), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(FrogEntity entity) {
        return new ResourceLocation("productivefrogs", "textures/entity/frog.png");
    }

    @Override
    protected void setupRotations(FrogEntity entity, float ageInTicks, float rotationYaw, float partialTicks) {
        super.setupRotations(entity, ageInTicks, rotationYaw, partialTicks);
        
        // Check if the frog has maxed stats
        if (entity.getStat1() == 10 && entity.getStat2() == 10 && entity.getStat3() == 10) {
            // Apply a subtle model scale-up
            this.model.scale(1.1F, 1.1F, 1.1F);
            
            // Add an emissive render layer
            this.addEmissiveLayer(entity);
        }
    }

    private void addEmissiveLayer(FrogEntity entity) {
        // Reuse the existing frog texture for the emissive layer
        this.model.renderEmissiveLayer(entity, this.getTextureLocation(entity));
    }
}