package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFVariantMilk;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Draws what the Virtual Terrarium is doing, inside its Display Dome. Client-side
 * phantoms (never in the world) are fed to the vanilla {@link EntityRenderDispatcher},
 * positioned inside the dome and scaled down (the WitherAltarHatch display pattern).
 * Renders nothing when the Processor has no frog loaded or no Dome above.
 *
 * <p><b>The frog is a {@link ResourceFrog}, not a vanilla Frog</b> (#382). That is
 * the whole trick: {@code ResourceFrogRenderer} already tints via its
 * {@code CategoryTintLayer}, so using PF's own entity gets the species tint for free
 * instead of needing a bespoke tinted render path.
 *
 * <p>Two colours are in play and they are not the same thing. The loaded frog stores
 * its <b>category</b> - what the frog IS, always known. The <b>variant</b> (iron,
 * diamond, ...) comes from the Slime Milk feedstock - what it is currently PRODUCING,
 * known only while the tank has milk. This renderer prefers the variant, so the dome
 * and the GUI's feedstock meter agree, and falls back to the species tint when the
 * tank runs dry so an idle frog is never colourless. The override rides on the
 * phantom via {@code ResourceFrog#setDisplayTintOverride}; the 26.1 line does the
 * same thing by overwriting the extracted render state, which this line does not have.
 *
 * <p>A small tinted slime appears beside the frog only while the machine is actually
 * working, which turns the dome into a status readout at a glance - most of the value
 * of a window on an otherwise hidden machine.
 */
public class VirtualTerrariumFrogRenderer implements BlockEntityRenderer<VirtualTerrariumBlockEntity> {

    private static final float SCALE = 0.6F;
    private static final double DOME_Y = 1.05;
    // The working indicator sits to one side of the frog so it never hides it, and
    // just ABOVE the block face - a Y below 1.0 buries it inside the Processor.
    private static final float SLIME_SCALE = 0.22F;
    private static final double SLIME_X = 0.78;
    private static final double SLIME_Z = 0.62;
    private static final double SLIME_Y = 1.02;

    /** Client-side phantoms fed to the vanilla renderer; never in the world. */
    private ResourceFrog frogPhantom;
    private ResourceSlime slimePhantom;

    public VirtualTerrariumFrogRenderer(net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context ctx) {
        // no model parts; delegates to the entity dispatcher
    }

    @Override
    public void render(VirtualTerrariumBlockEntity be, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        // No frog, or no Display Dome above -> render nothing (the dome IS the glass case).
        if (be.getLevel() == null || be.getInventory().getFrog().isEmpty() || !be.hasDome()) {
            return;
        }
        Category category = be.loadedCategory();
        if (category == null) {
            return;
        }
        if (frogPhantom == null) {
            frogPhantom = PFEntities.RESOURCE_FROG.get().create(be.getLevel());
        }
        if (frogPhantom == null) {
            return;
        }
        frogPhantom.setCategory(category);
        frogPhantom.setMidas(be.loadedIsMidas());
        // Prefer the feedstock variant's colour over the species tint, so the dome
        // and the GUI's feedstock meter agree on what the machine is producing.
        ResourceLocation variantId = feedstockVariant(be);
        frogPhantom.setDisplayTintOverride(variantId == null ? null : variantTint(variantId));

        long time = be.getLevel().getGameTime();
        // Face the frog toward the Processor's front (its horizontal FACING).
        float yaw = be.getBlockState().getValue(VirtualTerrariumProcessorBlock.FACING).toYRot();
        aim(frogPhantom, (int) time, yaw);

        // The BER's packedLight is sampled at the Processor's own cell, but the
        // Processor is a full opaque block, so that light is 0 and the frog renders
        // pitch black. The frog actually sits one block up inside the glass Dome,
        // which propagates light - sample there so the frog is lit by its surroundings.
        int light = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above());

        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        pose.pushPose();
        pose.translate(0.5, DOME_Y, 0.5);
        pose.scale(SCALE, SCALE, SCALE);
        dispatcher.render(frogPhantom, 0.0, 0.0, 0.0, yaw, partialTick, pose, buffers, light);
        pose.popPose();

        // The working indicator. Only while actually producing, and only when the
        // feedstock names a variant - a tinted slime with nothing to tint by would
        // just be a grey blob.
        if (variantId != null && be.getBlockState().getValue(VirtualTerrariumProcessorBlock.WORKING)) {
            if (slimePhantom == null) {
                slimePhantom = PFEntities.RESOURCE_SLIME.get().create(be.getLevel());
            }
            if (slimePhantom != null) {
                slimePhantom.setSize(1, false);
                slimePhantom.setVariant(variantId);
                aim(slimePhantom, (int) time, yaw);
                pose.pushPose();
                pose.translate(SLIME_X, SLIME_Y, SLIME_Z);
                pose.scale(SLIME_SCALE, SLIME_SCALE, SLIME_SCALE);
                dispatcher.render(slimePhantom, 0.0, 0.0, 0.0, yaw, partialTick, pose, buffers, light);
                pose.popPose();
            }
        }
        dispatcher.setRenderShadow(true);
    }

    /** Point a phantom at the machine's front with no interpolation, so it never smears. */
    private static void aim(Entity entity, int tickCount, float yaw) {
        entity.tickCount = tickCount;   // advance the idle bob
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
    }

    /** The variant the tank is currently feeding on, or null when it holds no milk. */
    @Nullable
    private static ResourceLocation feedstockVariant(VirtualTerrariumBlockEntity be) {
        FluidStack fluid = be.getFeedstock().getFluid();
        if (fluid.isEmpty()) {
            return null;
        }
        // v1.8 per-variant fluids: identity IS the variant on this line.
        return PFVariantMilk.variantOf(fluid.getFluid());
    }

    /**
     * Opaque ARGB for a variant's primary colour, or null when it cannot be resolved
     * (a datapack variant this client has no entry for). Same lookup the GUI's
     * feedstock meter uses, so the dome and the screen cannot disagree.
     */
    @Nullable
    private static Integer variantTint(ResourceLocation variantId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        var registry = mc.level.registryAccess()
            .registry(com.flatts.productivefrogs.registry.PFRegistries.SLIME_VARIANT).orElse(null);
        com.flatts.productivefrogs.data.SlimeVariant v = registry == null ? null : registry.get(variantId);
        return v == null ? null : (0xFF000000 | (v.primaryColor() & 0xFFFFFF));
    }

    /** Keep the dome contents visible even when the Processor block is off-screen behind the dome. */
    @Override
    public boolean shouldRenderOffScreen(VirtualTerrariumBlockEntity be) {
        return true;
    }
}
