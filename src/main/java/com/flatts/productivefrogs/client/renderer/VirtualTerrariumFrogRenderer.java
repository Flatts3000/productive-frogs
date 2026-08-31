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
 * PRODUCING. The dome shows the species normally and switches to the variant only
 * <b>while the machine is actually working</b>, so it agrees with the GUI's feedstock
 * meter when running and never paints a frog a colour it cannot produce. That gate
 * matters: an Infernal frog with lapis milk is JAMMED, and tinting it lapis would
 * hide the species at the one moment the player needs it. The override rides on the
 * phantom via {@code ResourceFrog#setDisplayTintOverride}; the 26.1 line does the
 * same thing by overwriting the extracted render state, which this line does not have.
 *
 * <p>A small slime appears beside the frog while the machine works, tinted by the
 * variant when there is one and by the frog's species otherwise - so it fires for
 * Mimic Milk and Mob Slurry runs too, not only Slime Milk. That turns the dome into a
 * status readout at a glance, which is most of the value of a window on an otherwise
 * hidden machine.
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
        // The variant tint means "currently producing THIS", so it is gated on the
        // machine actually working - not merely on the tank holding milk. Ungated it
        // lied in exactly the case a player needs the truth: an Infernal frog with
        // lapis milk is JAMMED (productive() requires the variant's category to match
        // the frog), and the dome would paint it lapis blue while nothing happened.
        // The frog's species is the one piece of state the dome uniquely exposes -
        // the frog itself is hidden in a slot.
        boolean working = be.getBlockState().getValue(VirtualTerrariumProcessorBlock.WORKING);
        ResourceLocation variantId = working ? feedstockVariant(be) : null;
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

        // The working indicator. Shown for EVERY feedstock the machine can run on,
        // not just Slime Milk - a Midas frog on Mimic Milk and a Predator on Mob
        // Slurry are working just as much, and gating on a slime variant left the
        // "status at a glance" promise silently dead for both. Tinted by the variant
        // when there is one, else by the frog's own species.
        if (working) {
            if (slimePhantom == null) {
                slimePhantom = PFEntities.RESOURCE_SLIME.get().create(be.getLevel());
            }
            if (slimePhantom != null) {
                slimePhantom.setSize(1, false);
                slimePhantom.setVariant(variantId);
                slimePhantom.setCategory(category);
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
