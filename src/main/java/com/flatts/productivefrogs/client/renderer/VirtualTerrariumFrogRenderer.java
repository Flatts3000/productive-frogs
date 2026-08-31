package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluids;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Draws what the Virtual Terrarium is doing, inside its Display Dome.
 *
 * <p>Client-side phantoms (never in the world) are captured via the 26.1
 * extract/submit pipeline, mirroring {@code GrowingReplicaRenderer}. Renders
 * nothing when the Processor has no frog loaded or no dome above it - the dome IS
 * the glass case.
 *
 * <p><b>The frog is a {@link ResourceFrog}, not a vanilla Frog</b> (#382). That is
 * the whole trick: {@code ResourceFrogRenderer} already tints via a
 * {@code CategoryTintLayer} that reads {@code ResourceFrogRenderState.tint}, so
 * using PF's own entity gets the kind tint for free instead of needing a bespoke
 * tinted render path.
 *
 * <p>Two colours are in play and they are not the same thing. The loaded frog
 * stores its <b>kind</b> - what the frog IS, always known. The <b>variant</b>
 * (iron, diamond, ...) comes from the Slime Milk feedstock - what it is currently
 * PRODUCING. The dome shows the kind normally and switches to the variant only
 * <b>while the machine is actually working</b>, so it agrees with the GUI's
 * feedstock meter when running and never paints a frog a colour it cannot produce.
 * That gate matters: an Infernal frog with lapis milk is JAMMED, and tinting it
 * lapis would hide the species at the one moment the player needs it, since the
 * frog itself is out of sight in a slot.
 *
 * <p>A small slime appears beside the frog while the machine works, tinted by the
 * variant when there is one and by the frog's kind otherwise - so it fires for
 * Mimic Milk and Mob Slurry runs too, not only Slime Milk. That turns the dome
 * into a status readout at a glance, which is most of the value of a window on an
 * otherwise hidden machine.
 */
public class VirtualTerrariumFrogRenderer
        implements BlockEntityRenderer<VirtualTerrariumBlockEntity, VirtualTerrariumFrogRenderer.DomeRenderState> {

    private static final float SCALE = 0.6F;
    private static final double DOME_Y = 1.05;
    // The working indicator sits to one side of the frog so it never hides it, and
    // just ABOVE the block face - a Y below 1.0 buries it inside the Processor,
    // which is exactly what the first attempt did.
    private static final float SLIME_SCALE = 0.22F;
    private static final double SLIME_X = 0.78;
    private static final double SLIME_Z = 0.62;
    private static final double SLIME_Y = 1.02;

    private final EntityRenderDispatcher dispatcher;
    private Entity frogPhantom;
    private Entity slimePhantom;

    public VirtualTerrariumFrogRenderer(BlockEntityRendererProvider.Context ctx) {
        this.dispatcher = ctx.entityRenderer();
    }

    @Override
    public DomeRenderState createRenderState() {
        return new DomeRenderState();
    }

    @Override
    public void extractRenderState(VirtualTerrariumBlockEntity be, DomeRenderState state, float partialTick,
            Vec3 cameraPosition, ModelFeatureRenderer.CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPosition, breakProgress);
        state.active = false;
        state.slime = null;
        if (be.getLevel() == null || be.getInventory().getFrog().isEmpty() || !be.hasDome()) {
            return;
        }
        FrogKind kind = be.loadedFrogKind();
        if (kind == null) {
            return;
        }
        if (frogPhantom == null) {
            frogPhantom = PFEntities.RESOURCE_FROG.get().create(be.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        }
        if (!(frogPhantom instanceof ResourceFrog frog)) {
            return;
        }
        frog.setKind(kind);

        long time = be.getLevel().getGameTime();
        frog.tickCount = (int) time;
        // Face the frog toward the Processor's front (its horizontal FACING).
        float yaw = be.getBlockState()
            .getValue(com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock.FACING).toYRot();
        BlockPos pos = be.getBlockPos();
        placePhantom(frog, pos.getX() + 0.5, pos.getY() + DOME_Y, pos.getZ() + 0.5, yaw);

        // The variant tint means "currently producing THIS", so it is gated on the
        // machine actually working - not merely on the tank holding milk. Ungated it
        // lied in exactly the case a player needs the truth: an Infernal frog with
        // lapis milk is JAMMED (productive() requires the variant's category to match
        // the frog), and the dome would paint it lapis blue while nothing happened.
        // The frog's species is the one piece of state the dome uniquely exposes -
        // the frog itself is hidden in a slot - so it must not be overwritten by a
        // feedstock the frog cannot eat.
        boolean working = be.getBlockState()
            .getValue(com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock.WORKING);
        Identifier variantId = working ? feedstockVariant(be) : null;

        EntityRenderState replica = dispatcher.extractEntity(frog, partialTick);
        suppressShadow(replica);
        if (variantId != null && replica instanceof ResourceFrogRenderState frogState) {
            Integer argb = variantTint(variantId);
            if (argb != null) {
                frogState.tint = argb;
            }
        }
        state.replica = replica;
        state.active = true;

        // The working indicator. Shown for EVERY feedstock the machine can run on,
        // not just Slime Milk - a Midas frog on Mimic Milk and a Predator on Mob
        // Slurry are working just as much, and gating on a slime variant left the
        // "status at a glance" promise silently dead for both. Tinted by the variant
        // when there is one, else by the frog's own kind.
        if (!working) {
            return;
        }
        if (slimePhantom == null) {
            slimePhantom = PFEntities.RESOURCE_SLIME.get().create(be.getLevel(), EntitySpawnReason.MOB_SUMMONED);
        }
        if (!(slimePhantom instanceof ResourceSlime slime)) {
            return;
        }
        slime.setSize(1, false);
        slime.setVariant(variantId);
        slime.setCategory(kind.fallbackCategory());
        slime.tickCount = (int) time;
        placePhantom(slime, pos.getX() + SLIME_X, pos.getY() + SLIME_Y, pos.getZ() + SLIME_Z, yaw);
        EntityRenderState slimeState = dispatcher.extractEntity(slime, partialTick);
        suppressShadow(slimeState);
        state.slime = slimeState;
    }

    /**
     * Actually suppress a phantom's shadow.
     *
     * <p>Setting {@code shadowRadius = 0} does NOT: {@code finalizeRenderState}
     * populates {@code shadowPieces} during extraction, before this runs, and the
     * dispatcher only checks whether that list is empty. A zero radius then feeds
     * {@code -x/2/radius} in the shadow renderer, i.e. infinite/NaN UVs on a quad
     * that is still drawn. Clearing the list is the real suppression, and a phantom
     * sitting directly above the full-block Processor always collects a piece.
     */
    private static void suppressShadow(EntityRenderState state) {
        state.shadowRadius = 0.0F;
        state.shadowPieces.clear();
    }

    /** Park a phantom at a world position with no interpolation, so it never smears. */
    private static void placePhantom(Entity entity, double x, double y, double z, float yaw) {
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setOldPosAndRot();
        entity.yRotO = yaw;
        if (entity instanceof net.minecraft.world.entity.LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.setYHeadRot(yaw);
            living.yHeadRotO = yaw;
        }
    }

    /** The variant the tank is currently feeding on, or null when it holds no milk. */
    @Nullable
    private static Identifier feedstockVariant(VirtualTerrariumBlockEntity be) {
        FluidStack fluid = be.getFeedstock().getFluid();
        if (fluid.isEmpty() || !fluid.is(PFFluids.SLIME_MILK.get())) {
            return null;
        }
        return fluid.get(PFDataComponents.SLIME_VARIANT.get());
    }

    /**
     * Opaque ARGB for a variant's primary colour, or null when it cannot be resolved.
     *
     * <p>Resolves the variant directly rather than going through
     * {@code Tints.variantColor}, whose {@code -1} "unresolved" sentinel is
     * indistinguishable from opaque white - a datapack variant with
     * {@code primary_color: 16777215} would silently never tint. No shipped variant
     * hits that today, which is exactly why it would be missed.
     */
    @Nullable
    private static Integer variantTint(Identifier variantId) {
        ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        var registry = com.flatts.productivefrogs.registry.PFRegistries.variants(level.registryAccess());
        com.flatts.productivefrogs.data.SlimeVariant v =
            com.flatts.productivefrogs.registry.PFRegistries.variant(registry, variantId);
        return v == null ? null : (0xFF000000 | (v.primaryColor() & 0xFFFFFF));
    }

    @Override
    public void submit(DomeRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
        if (!state.active || state.replica == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, DOME_Y, 0.5);
        poseStack.scale(SCALE, SCALE, SCALE);
        dispatcher.submit(state.replica, camera, 0.0, 0.0, 0.0, poseStack, collector);
        poseStack.popPose();

        if (state.slime != null) {
            poseStack.pushPose();
            poseStack.translate(SLIME_X, SLIME_Y, SLIME_Z);
            poseStack.scale(SLIME_SCALE, SLIME_SCALE, SLIME_SCALE);
            dispatcher.submit(state.slime, camera, 0.0, 0.0, 0.0, poseStack, collector);
            poseStack.popPose();
        }
    }

    public static class DomeRenderState extends BlockEntityRenderState {
        public boolean active;
        public EntityRenderState replica;
        /** Null unless the machine is actively producing. */
        @Nullable
        public EntityRenderState slime;
    }
}
