package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.PFTags;
import com.flatts.productivefrogs.registry.PFEntities;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles right-clicking a vanilla {@code Slime} (or {@code MagmaCube}, which
 * extends Slime) while holding any item in a {@code productivefrogs:primer/<category>}
 * tag — in-place transformation into a {@link ResourceSlime} of the matching
 * category. Same position, size, HP, and velocity; primer item consumed.
 *
 * <p>This is the "infusion = immediate transformation" model from
 * {@code docs/slime_sourcing.md} Path 2. There's no NBT-tracked infusion
 * state — the entity type itself encodes the variant, so re-infusion of an
 * already-resource slime is a no-op (the check rejects {@code ResourceSlime}
 * targets up front).
 *
 * <p>Symmetric design partner to {@link EggPrimerHandler}, which handles the
 * frogspawn-priming path.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class SlimeInfusionHandler {

    private SlimeInfusionHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();

        // Must be a vanilla slime/magma cube; skip already-infused ResourceSlimes
        // (per design: once transformed, the slime is committed to its variant).
        if (!(target instanceof Slime sourceSlime) || target instanceof ResourceSlime) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        Category category = matchPrimerCategory(held);
        if (category == null) {
            return;
        }

        // We're handling this. Suppress vanilla behavior and any other listeners.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (event.getLevel().isClientSide()) {
            return;
        }

        ResourceSlime resource = transformInPlace(sourceSlime, category);
        if (resource == null) {
            return;
        }

        // Consume one primer (creative skips, matching EggPrimerHandler).
        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        event.getLevel().playSound(
            null,
            resource.blockPosition(),
            SoundEvents.SLIME_SQUISH,
            SoundSource.NEUTRAL,
            0.8F,
            1.2F
        );
    }

    /**
     * Spawn a {@link ResourceSlime} of {@code category} at {@code sourceSlime}'s
     * position, copying size / HP / velocity, and discard the source. Returns
     * the new entity, or {@code null} if entity creation failed.
     *
     * <p>Exposed as a static helper so both the event handler and tests can
     * exercise the transformation without duplicating positioning code.
     * Server-side only — caller is responsible for the {@code !isClientSide}
     * guard.
     */
    public static ResourceSlime transformInPlace(Slime sourceSlime, Category category) {
        Level level = sourceSlime.level();
        ResourceSlime resource = PFEntities.RESOURCE_SLIME.get().create(level, EntitySpawnReason.CONVERSION);
        if (resource == null) {
            return null;
        }
        // Snap before setSize — setSize uses the entity's current position to
        // refresh dimensions, so order matters slightly.
        resource.snapTo(sourceSlime.getX(), sourceSlime.getY(), sourceSlime.getZ(),
            sourceSlime.getYRot(), sourceSlime.getXRot());
        resource.setSize(sourceSlime.getSize(), false);
        resource.setCategory(category);
        resource.setHealth(sourceSlime.getHealth());
        resource.setDeltaMovement(sourceSlime.getDeltaMovement());
        resource.setPersistenceRequired();

        // Remove the source first to avoid both entities briefly occupying the
        // same tile and triggering vanilla's stuck-entity nudge.
        sourceSlime.discard();
        level.addFreshEntity(resource);
        return resource;
    }

    /**
     * First category whose primer tag contains the held item. Returns null if
     * the item isn't a primer for any category.
     *
     * <p>If a future item ends up in multiple primer tags (which would be a
     * tag-config bug — primer pools should be disjoint), the first match in
     * {@link Category} declaration order wins. Same behavior as
     * {@link EggPrimerHandler}.
     */
    private static Category matchPrimerCategory(ItemStack stack) {
        for (Category cat : Category.values()) {
            if (stack.is(PFTags.PRIMER_BY_CATEGORY.get(cat))) {
                return cat;
            }
        }
        return null;
    }
}
