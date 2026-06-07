package com.flatts.productivefrogs.particle;

import net.minecraft.client.particle.IParticleRenderType;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleType;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SparkleParticle extends Particle {

    private final ClientWorld world;

    public SparkleParticle(ClientWorld world, double x, double y, double z) {
        super(world, x, y, z);
        this.world = world;
        this.xd = (double) ((float) (Math.random() * 2.0F - 1.0F) * 0.1F);
        this.yd = (double) ((float) (Math.random() * 2.0F - 1.0F) * 0.1F);
        this.zd = (double) ((float) (Math.random() * 2.0F - 1.0F) * 0.1F);
        this.lifetime = (int) (Math.random() * 10.0D) + 10;
    }

    @Override
    public void renderParticle(ActiveRenderInfo renderInfo, MatrixStack matrixStack, IRenderTypeBuffer buffer, float partialTicks) {
        matrixStack.push();
        matrixStack.translate(this.getX(), this.getY(), this.getZ());
        matrixStack.mulPose(Vector3f.YN);
        matrixStack.mulPose(Vector3f.ZP);
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(-0.5D, -0.5D, 0.0D).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(0.5D, -0.5D, 0.0D).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(0.5D, 0.5D, 0.0D).color(255, 255, 255, 255).endVertex();
        bufferBuilder.vertex(-0.5D, 0.5D, 0.0D).color(255, 255, 255, 255).endVertex();
        bufferBuilder.end();
        BufferUploader.end(bufferBuilder);
        matrixStack.pop();
    }

    @Override
    public IParticleRenderType getRenderType() {
        return IParticleRenderType.CUSTOM;
    }

    @Override
    public void tick() {
        this.x += this.xd;
        this.y += this.yd;
        this.z += this.zd;
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }
}