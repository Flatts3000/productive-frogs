package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.entity.ResourceSlime;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.ParentSpeciesEntry;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFEntities;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        Map.Entry<Identifier, SlimeVariant> variantEntry = findVariantForHeldItem(event.getLevel(), held);
        if (variantEntry == null) {
            return;  // no primer match — fall through to vanilla interaction (no feedback)
        }

        // Cross-species rejection — primer's variant must belong to the slime's species.
        if (variantEntry.getValue().category() != species) {
            event.setCancellationResult(InteractionResult.FAIL);
            event.setCanceled(true);
            rejectFeedback(event.getLevel(), sourceSlime);
            if (!event.getLevel().isClientSide()) {
                PFDebug.log(PFDebug.Area.INFUSION, () -> String.format(
                    "reject: primer variant %s (%s) does not match target species %s",
                    variantEntry.getKey(), variantEntry.getValue().category(), species));
            }
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
        PFDebug.log(PFDebug.Area.INFUSION, () -> String.format(
            "infuse: species=%s variant=%s size=%d", species, variantEntry.getKey(), resource.getSize()));

        Player player = event.getEntity();
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        event.getLevel().playSound(
            null, resource.blockPosition(),
            SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.8F, 1.2F);
    }

    /**
     * Resolve the parent-species category for a Slime entity via the
     * {@link PFRegistries#PARENT_SPECIES} datapack registry. Returns the
     * matching {@link Category} for a registered parent species, or
     * {@code null} for vanilla {@code Slime}/{@code MagmaCube} (deliberately
     * absent from the registry per V1.5) or any slime type nobody has wired in.
     *
     * <p>Shares the {@link ParentSpeciesEntry#categoryFor} lookup with
     * {@code SlimeSplitDiscoveryHandler}, so a single {@code parent_species}
     * JSON wires a (modded) slime into both infusion and split-discovery. The
     * 6-entry scan on a right-click path is negligible.
     */
    @Nullable
    public static Category resolveParentSpecies(Slime slime) {
        Identifier typeId = BuiltInRegistries.ENTITY_TYPE.getKey(slime.getType());
        return ParentSpeciesEntry.categoryFor(
            PFRegistries.parentSpeciesLookup(slime.level().registryAccess()), typeId);
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

        int size = sourceSlime.getSize();
        float health = sourceSlime.getHealth();
        Vec3 velocity = sourceSlime.getDeltaMovement();

        // 26.1: convertTo takes ConversionParams + an AfterConversion callback and
        // fires the living-conversion event internally (the old manual
        // EventHooks.canLivingConvert/onLivingConvert guard is now redundant). It
        // returns null if the conversion is cancelled. The post-convert setup runs
        // in the callback, before the new mob is added to the world.
        return sourceSlime.convertTo(
            target,
            ConversionParams.single(sourceSlime, false, false),
            EntitySpawnReason.CONVERSION,
            converted -> {
                converted.setSize(size, false);
                converted.setCategory(category);
                converted.setHealth(health);
                converted.setDeltaMovement(velocity);
                converted.setPersistenceRequired();
            });
    }

    /**
     * Look up the SlimeVariant whose {@code primer_item} matches the held
     * item. Returns the (id, variant) entry on hit, {@code null} on miss
     * (including registry-not-loaded).
     */
    private static Map.Entry<Identifier, SlimeVariant> findVariantForHeldItem(
            net.minecraft.world.level.Level level, ItemStack stack) {
        return SlimeVariant.findByPrimer(PFRegistries.variants(level.registryAccess()), stack);
    }
}
