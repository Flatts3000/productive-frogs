package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.BogSlime;
import com.flatts.productivefrogs.content.entity.CaveSlime;
import com.flatts.productivefrogs.content.entity.GeodeSlime;
import com.flatts.productivefrogs.content.entity.InfernalSlime;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.content.entity.TideSlime;
import com.flatts.productivefrogs.content.entity.VoidSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
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
import org.jetbrains.annotations.Nullable;

/**
 * Slime infusion — species-locked per the V1.5 redesign
 * ({@code docs/species_as_category_redesign.md}). The handler accepts only PF
 * parent species (Bog/Cave/Geode/Tide/Infernal/Void Slime), only when the
 * held primer is an exact match for a {@link SlimeVariant#primerItem()} of
 * that species. Mismatches play a rejection feedback (denied sound + smoke
 * puff) and do not consume the primer.
 *
 * <p>Vanilla {@code minecraft:slime} and {@code minecraft:magma_cube} are
 * rejected: they're no longer parent species (per Q1=A). Already-infused
 * {@link ResourceSlime} targets are rejected (per Q3 = hard-reject; no
 * variant swapping).
 *
 * <p>The category-tag fallback ({@code primer/<category>}) is removed (per
 * Q4 = Path A): every infusion requires an exact 1:1 match with a variant's
 * {@code primer_item} field.
 *
 * <p>Symmetric design partner to {@link EggPrimerHandler}, which handles the
 * frogspawn-priming path (biome-agnostic, same variant-primer-only rule).
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class SlimeInfusionHandler {

    private SlimeInfusionHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();

        // Must be a Slime. Already-infused ResourceSlimes are hard-rejected
        // (Q3: no variant swapping).
        if (!(target instanceof Slime sourceSlime) || target instanceof ResourceSlime) {
            return;
        }

        // Species gate: only PF parent species can be infused. Vanilla slime
        // and magma cube fall out here (Q1=A: vanilla mobs are not parents).
        Category species = resolveParentSpecies(sourceSlime);
        if (species == null) {
            return;  // silent — vanilla mobs aren't a "wrong target", just not part of the system
        }

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        // Exact variant primer match required (Q4: Path A only — no category-tag fallback).
        Map.Entry<ResourceLocation, SlimeVariant> variantEntry = findVariantForHeldItem(event.getLevel(), held);
        if (variantEntry == null) {
            return;  // no primer match — fall through to vanilla interaction (no feedback)
        }

        // Cross-species rejection — primer's variant must belong to the slime's species.
        if (variantEntry.getValue().category() != species) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            rejectFeedback(event.getLevel(), sourceSlime);
            return;
        }

        // Happy path: we own this interaction.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (event.getLevel().isClientSide()) {
            return;
        }

        ResourceSlime resource = transformInPlace(sourceSlime, species);
        if (resource == null) {
            return;
        }
        resource.setVariant(variantEntry.getKey());

        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        event.getLevel().playSound(
            null, resource.blockPosition(),
            SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    /**
     * Resolve the parent-species category for a Slime entity. Returns the
     * matching {@link Category} for PF parent species
     * (Bog/Cave/Geode/Tide/Infernal/Void Slime), {@code null} for vanilla
     * {@code Slime} or {@code MagmaCube} or any non-parent slime subclass.
     *
     * <p>Uses {@code instanceof} dispatch on the PF subclasses (cheap and
     * order-independent — each PF species has its own subclass). The
     * datapack {@code parent_species} registry could also be consulted but
     * the instanceof shape is faster and avoids per-interaction registry
     * lookups.
     */
    @Nullable
    public static Category resolveParentSpecies(Slime slime) {
        if (slime instanceof BogSlime) return Category.BOG;
        if (slime instanceof CaveSlime) return Category.CAVE;
        if (slime instanceof GeodeSlime) return Category.GEODE;
        if (slime instanceof TideSlime) return Category.TIDE;
        if (slime instanceof VoidSlime) return Category.VOID;
        if (slime instanceof InfernalSlime) return Category.INFERNAL;
        return null;
    }

    /**
     * Cross-species rejection feedback (Q2): denied sound + smoke particles
     * at the slime's head position. Does not consume the primer item.
     */
    private static void rejectFeedback(net.minecraft.world.level.Level level, Slime slime) {
        level.playSound(
            null, slime.blockPosition(),
            SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.NEUTRAL,
            0.5F, 0.8F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(
                ParticleTypes.SMOKE,
                slime.getX(), slime.getEyeY(), slime.getZ(),
                6, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /**
     * Convert {@code sourceSlime} in place into a {@link ResourceSlime} of
     * the given {@code category}, copying size + HP + velocity. Returns the
     * new entity or {@code null} on conversion rejection.
     */
    public static ResourceSlime transformInPlace(Slime sourceSlime, Category category) {
        EntityType<ResourceSlime> target = PFEntities.RESOURCE_SLIME.get();
        if (!EventHooks.canLivingConvert(sourceSlime, target, ignored -> {})) {
            return null;
        }

        int size = sourceSlime.getSize();
        float health = sourceSlime.getHealth();
        Vec3 velocity = sourceSlime.getDeltaMovement();

        ResourceSlime resource = sourceSlime.convertTo(target, false);
        if (resource == null) {
            return null;
        }
        EventHooks.onLivingConvert(sourceSlime, resource);
        resource.setSize(size, false);
        resource.setCategory(category);
        resource.setHealth(health);
        resource.setDeltaMovement(velocity);
        resource.setPersistenceRequired();
        return resource;
    }

    /**
     * Look up the SlimeVariant whose {@code primer_item} matches the held
     * item. Returns the (id, variant) entry on hit, {@code null} on miss
     * (including registry-not-loaded).
     */
    private static Map.Entry<ResourceLocation, SlimeVariant> findVariantForHeldItem(
            net.minecraft.world.level.Level level, ItemStack stack) {
        Registry<SlimeVariant> registry = level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return SlimeVariant.findByPrimerItem(registry, itemId);
    }
}
