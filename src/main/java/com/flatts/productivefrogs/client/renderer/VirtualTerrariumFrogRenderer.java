package com.flatts.productivefrogs.client.renderer;

import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.FrogKind;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFFluids;
import com.mojang.blaze3d.vertex.PoseStack;
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
 * <p>Two things are shown and they answer different questions. The <b>frog</b> is
 * always its own kind's colour - what the frog IS. The <b>slime</b> beside it
 * appears only while the machine is working and carries the feedstock variant -
 * what it is currently PRODUCING. Two elements, two pieces of information, no
 * redundancy.
 *
 * <p>The frog briefly took the variant tint while working instead, and that was
 * wrong: it made the frog redundant with the slime, and it hid the one thing only
 * the dome can tell you, since the frog itself is out of sight in a slot. A Bog
 * frog fed rainbow milk rendered magenta and stopped looking like a Bog frog.
 *
 * <p>The slime falls back to the frog's kind colour when the feedstock names no
 * variant, so it fires for Mimic Milk and Mob Slurry runs too, not only Slime
 * Milk. That turns the dome into a status readout at a glance, which is most of
 * the value of a window on an otherwise hidden machine.
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

        // The frog is ALWAYS its own kind's colour. It used to switch to the
        // feedstock variant while working, which was wrong twice over: the slime
        // beside it already shows what is being produced, so the variant was
        // redundant, and it hid the one thing only the dome can tell you - a Bog
        // frog fed rainbow milk rendered magenta and stopped looking like a Bog
        // frog. Species on the frog, production on the slime; two elements, two
        // pieces of information.
        boolean working = be.getBlockState()
            .getValue(com.flatts.productivefrogs.content.block.VirtualTerrariumProcessorBlock.WORKING);

        EntityRenderState replica = dispatcher.extractEntity(frog, partialTick);
        suppressShadow(replica);
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
        Identifier variantId = feedstockVariant(be);
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
