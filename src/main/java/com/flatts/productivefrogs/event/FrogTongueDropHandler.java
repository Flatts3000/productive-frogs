package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.FrogStats;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.StoredEffect;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

/**
 * When a {@link ResourceFrog} kills a {@link ResourceSlime} of its matching
 * category — i.e., it just ate it via tongue — drop a category-matching
 * Resource Froglight at the frog's position. Vanilla hoppers under the frog
 * can collect the item entity, mirroring the vanilla "frog eats magma cube
 * → froglight" production loop.
 *
 * <p>Implementation note: vanilla wires the magma-cube → froglight drop
 * through the magma cube's loot table with a {@code source_entity → frog/variant}
 * predicate. We can't use that pattern here because our frog's category lives
 * on a SynchedEntityData accessor, not on a {@code minecraft:frog/variant}
 * component the predicate registry can read. {@link LivingDeathEvent} runs
 * in code where we have full access to the entity state and can branch on
 * the actual category — simpler than registering a custom entity sub-predicate
 * for a single use site.
 *
 * <p>The category-match check in this handler is technically redundant given
 * the {@link com.flatts.productivefrogs.content.entity.ai.ResourceFrogAttackablesSensor}
 * already prevents frogs from targeting off-category slimes. We keep it as a
 * defensive guard against future code paths that might bypass the sensor
 * (player direct-feeding via the Slime Bucket, mod compat).
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class FrogTongueDropHandler {

    private FrogTongueDropHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onResourceSlimeKilled(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ResourceSlime slime)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof ResourceFrog frog)) {
            return;
        }

        if (frog.getCategory() != slime.getCategory()) {
            if (!frog.level().isClientSide()) {
                PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
                    "no drop: frog category=%s != slime category=%s", frog.getCategory(), slime.getCategory()));
            }
            return;
        }

        // The vanilla Frog.canEat predicate (which our sensor inherits via
        // super) already restricts tongue targets to size-1 slimes, so this
        // guard is defensive: skip the drop for any larger slime that somehow
        // reached this handler (mod hook, manual /damage command, etc.) — the
        // production loop is keyed to size-1 prey only.
        if (slime.getSize() != 1) {
            return;
        }

        if (frog.level().isClientSide()) {
            return;
        }

        // The kill IS the eat - start the Appetite-scaled hunting cooldown that
        // gates how soon the frog can target its next slime (docs/frog_breeding.md).
        frog.startEatCooldown();
        // ...and it trains the frog: eating slimes is the core loop AND the way a
        // frog levels its stats toward its talent ceiling (docs/frog_stats_redesign.md).
        frog.addTrainingXp(PFConfig.trainingXpPerEat());
        // Brewed Froglights (#162): if the slime was carrying a potion effect
        // (splashed/lingered onto it before the frog ate it), capture the one
        // effect by the decided rule and stamp it on the drop. Gated by config
        // (#195): when disabled, the Froglight drops plain.
        StoredEffect captured = PFConfig.brewedFroglightsEnabled()
            ? StoredEffect.pick(slime.getActiveEffects()) : null;
        dropFroglightAtFrog(frog, slime.getVariantId(), captured);
    }

    /**
     * Spawn a variant-stamped {@code configurable_froglight} item entity at the
     * frog's position. Used by both the tongue-kill path above and the player
     * direct-feed path in {@link ResourceFrog#mobInteract}.
     *
     * <p>V1.5: {@link ResourceSlime} always carries a variant. If somehow we
     * reach this code path with a null {@code variantId} (legacy save or a
     * bug), drop nothing — the kill is treated as a "wasted" eat and the
     * player gets only the vanilla slime-ball death drop from the source mob.
     *
     * <p>No category-match check here — callers verify that. This method just
     * emits the drop.
     */
    public static void dropFroglightAtFrog(ResourceFrog frog, @Nullable ResourceLocation variantId) {
        dropFroglightAtFrog(frog, variantId, null);
    }

    /**
     * Variant + optional captured potion effect overload (#162). A non-null
     * {@code captured} stamps every dropped Froglight with the
     * {@code STORED_EFFECT} component - all drops in a Bounty multi-drop share
     * the same captured effect. The player direct-feed path passes null (a
     * bucketed slime carries no live effects).
     */
    public static void dropFroglightAtFrog(ResourceFrog frog, @Nullable ResourceLocation variantId,
            @Nullable StoredEffect captured) {
        Level level = frog.level();
        if (level.isClientSide()) {
            return;
        }
        if (variantId == null) {
            PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
                "drop skipped: frog category=%s had null variant (wasted eat)", frog.getCategory()));
            return;
        }
        // Bounty multiplies the yield: 1 Froglight at low Bounty up to
        // bountyMaxDrops at the cap (FrogStats step curve). Each ItemEntity gets
        // the constructor's natural random spread so the stack scatters.
        int count = FrogStats.bountyDropCount(frog.effectiveBounty(), PFConfig.bountyMaxDrops(), PFConfig.statCap());

        // Terrarium override (#185): inside a formed Terrarium the Froglight is
        // deposited straight into the Hatch inventory - no item entity, ever.
        // A full Hatch is backpressure: stop depositing rather than spill into
        // the world (the sensor also refuses prey at this point, so a frog
        // normally won't have eaten - this is the safety net).
        com.flatts.productivefrogs.content.multiblock.TerrariumManager.FormedTerrarium terrarium =
            com.flatts.productivefrogs.content.multiblock.TerrariumManager.containing(level, frog.position());
        if (terrarium != null) {
            com.flatts.productivefrogs.content.block.entity.HatchBlockEntity hatch =
                level.getBlockEntity(terrarium.hatchPos())
                    instanceof com.flatts.productivefrogs.content.block.entity.HatchBlockEntity h ? h : null;
            for (int i = 0; i < count && hatch != null; i++) {
                if (!hatch.insert(buildFroglight(variantId, captured))) {
                    break; // Hatch full - drop nothing more, spawn no entity
                }
            }
            PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
                "terrarium drop: frog category=%s -> Hatch %s (variant=%s)",
                frog.getCategory(), terrarium.hatchPos(), variantId));
            return;
        }

        Vec3 pos = frog.position();
        for (int i = 0; i < count; i++) {
            ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, buildFroglight(variantId, captured));
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }
        PFDebug.log(PFDebug.Area.TONGUE, () -> String.format(
            "drop: frog category=%s bounty=%d -> %d x configurable_froglight variant=%s effect=%s at %s",
            frog.getCategory(), frog.getBounty(), count, variantId,
            captured == null ? "none" : captured.effect().unwrapKey().map(k -> k.location().toString()).orElse("?"),
            frog.blockPosition()));
    }

    /**
     * Build a variant-stamped (and optionally brewed) {@code configurable_froglight}
     * item - the single froglight-construction point, shared by the frog tongue
     * drop here and the Froglight weapon (#212), so variant + effect stamping lives
     * in one place.
     */
    public static ItemStack buildFroglight(ResourceLocation variantId, @Nullable StoredEffect captured) {
        ItemStack froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
        froglight.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        if (captured != null) {
            froglight.set(PFDataComponents.STORED_EFFECT.get(), captured);
        }
        return froglight;
    }
}
