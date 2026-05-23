package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceFrog;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
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

        Category slimeCat = slime.getCategory();
        if (frog.getCategory() != slimeCat) {
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

        dropFroglightAtFrog(frog, slimeCat, slime.getVariantId());
    }

    /**
     * Spawn the correct Froglight item entity at the frog's position. Used by
     * both the tongue-kill path above and the player direct-feed path in
     * {@link ResourceFrog#mobInteract}. Variant-keyed
     * {@code configurable_froglight} wins when the slime (or bucket) carried
     * a {@code SlimeVariant}; otherwise the broad-strokes category Froglight
     * block falls back.
     *
     * <p>No category-match check here — callers verify that. This method
     * just emits the drop.
     */
    public static void dropFroglightAtFrog(ResourceFrog frog, Category category, @Nullable ResourceLocation variantId) {
        Level level = frog.level();
        if (level.isClientSide()) {
            return;
        }
        ItemStack froglight;
        if (variantId != null) {
            froglight = new ItemStack(PFItems.CONFIGURABLE_FROGLIGHT.get());
            froglight.set(PFDataComponents.SLIME_VARIANT.get(), variantId);
        } else {
            froglight = new ItemStack(PFBlocks.resourceFroglight(category));
        }
        Vec3 pos = frog.position();
        ItemEntity drop = new ItemEntity(level, pos.x, pos.y, pos.z, froglight);
        drop.setDefaultPickUpDelay();
        level.addFreshEntity(drop);
    }
}
