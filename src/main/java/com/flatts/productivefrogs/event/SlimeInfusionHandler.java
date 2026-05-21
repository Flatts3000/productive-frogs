package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.PFTags;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
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

        // Variant lookup wins when the held item matches a shipped variant's
        // primer_item. Falls back to category-only infusion when the item is
        // in a primer tag but doesn't map to any specific variant (e.g.,
        // blaze_powder is in primer/infernal but isn't a variant primer in
        // V1 — gives a category-only INFERNAL slime).
        Map.Entry<Identifier, SlimeVariant> variantEntry = findVariantForHeldItem(event.getLevel(), held);
        Category category;
        if (variantEntry != null) {
            category = variantEntry.getValue().category();
        } else {
            category = matchPrimerCategory(held);
            if (category == null) {
                return;
            }
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
        if (variantEntry != null) {
            resource.setVariant(variantEntry.getKey());
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
     * Convert {@code sourceSlime} in place into a {@link ResourceSlime} of the
     * given {@code category}, copying size + HP + velocity. Returns the new
     * entity, or {@code null} if conversion was rejected (event veto or entity
     * already removed).
     *
     * <p>Uses vanilla {@code Mob#convertTo} + NeoForge {@code EventHooks} for the
     * same reason {@link com.flatts.productivefrogs.content.entity.ResourceTadpole#ageUp}
     * does: it preserves more entity state than a manual create+discard, fires
     * the {@code LivingConvertEvent} hooks that other mods may listen on, and
     * handles the source discard via {@code ConversionType.SINGLE} automatically.
     *
     * <p>Exposed as a static helper so both the event handler and tests can
     * exercise the transformation without duplicating positioning code.
     * Server-side only — caller is responsible for the {@code !isClientSide}
     * guard.
     */
    public static ResourceSlime transformInPlace(Slime sourceSlime, Category category) {
        EntityType<ResourceSlime> target = PFEntities.RESOURCE_SLIME.get();
        if (!EventHooks.canLivingConvert(sourceSlime, target, ignored -> {})) {
            return null;
        }

        int size = sourceSlime.getSize();
        float health = sourceSlime.getHealth();
        Vec3 velocity = sourceSlime.getDeltaMovement();

        return sourceSlime.convertTo(
            target,
            ConversionParams.single(sourceSlime, false, false),
            resource -> {
                EventHooks.onLivingConvert(sourceSlime, resource);
                resource.setSize(size, false);
                resource.setCategory(category);
                resource.setHealth(health);
                resource.setDeltaMovement(velocity);
                resource.setPersistenceRequired();
            }
        );
    }

    /**
     * Look up the SlimeVariant whose {@code primer_item} matches the held
     * item. Returns the (id, variant) entry on hit, {@code null} on miss
     * (including when the registry isn't yet loaded — early init, missing
     * datapack, etc).
     */
    private static Map.Entry<Identifier, SlimeVariant> findVariantForHeldItem(
            net.minecraft.world.level.Level level, ItemStack stack) {
        Registry<SlimeVariant> registry = level.registryAccess()
            .lookup(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) return null;
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SlimeVariant.findByPrimerItem(registry, itemId);
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
