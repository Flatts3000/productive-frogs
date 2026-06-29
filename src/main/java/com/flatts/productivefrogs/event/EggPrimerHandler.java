package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFRegistries;
import com.flatts.productivefrogs.util.PFDebug;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Frog Egg priming — biome-agnostic, variant-primer-driven per the V1.5
 * redesign ({@code docs/species_as_category_redesign.md}).
 *
 * <p>Right-clicking vanilla {@code minecraft:frogspawn} with any item that is
 * a {@link SlimeVariant#primerItem()} on a shipped slime variant converts the
 * frogspawn to a Primed Frog Egg of the variant's species. The egg later
 * hatches into species-keyed tadpoles (Cave Tadpoles for an Iron-primed egg,
 * etc.) — the variant identity itself is not preserved on tadpoles or frogs.
 *
 * <p>The Q4 = Path A decision applies: only exact variant primer matches
 * count. The previous {@code primer/<category>} item-tag fallback is removed.
 *
 * <p>Symmetric design partner to {@link SlimeInfusionHandler}. Asymmetric
 * design point: slime infusion requires a PF parent slime as the target;
 * egg priming accepts vanilla frogspawn anywhere. Both gate functions
 * differ on purpose — see the spec's "Why the asymmetry" section.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class EggPrimerHandler {

    private EggPrimerHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.FROGSPAWN)) {
            return;
        }

        ItemStack held = event.getItemStack();
        if (held.isEmpty()) {
            return;
        }

        // Equivalence lane (#253): a Princess's Kiss primes frogspawn into a Midas
        // egg (the fairy-tale motif inverted - kissing the unhatched spawn awakens
        // a frog of legend). The Kiss isn't a slime variant, so it's a dedicated
        // case ahead of the variant lookup. MIDAS_FROG_EGG is its own block (named
        // "Midas Egg"); its onPlace stamps the midas marker so it hatches Midas.
        // The Kiss's existing frog->villager use (on a live frog) is unaffected.
        // Gated on the EE lane (#253): with the lane off, the Kiss does NOT prime a
        // Midas egg (falls through), so the whole lane is unreachable in survival.
        if (held.is(com.flatts.productivefrogs.registry.PFItems.PRINCESS_KISS.get())
                && com.flatts.productivefrogs.PFConfig.equivalenceEnabled()) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            if (level.isClientSide()) {
                return;
            }
            level.setBlockAndUpdate(pos, PFBlocks.MIDAS_FROG_EGG.get().defaultBlockState());
            PFDebug.log(PFDebug.Area.EGG, () -> String.format("prime: frogspawn at %s + Princess's Kiss -> Midas egg", pos));
            if (!event.getEntity().getAbilities().instabuild) {
                held.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.6F, 1.4F);
            return;
        }

        // Exact variant primer match required (Q4: Path A only).
        Map.Entry<Identifier, SlimeVariant> variantEntry = findVariantForHeldItem(level, held);
        if (variantEntry == null) {
            return;  // no primer match — silent no-op (no rejection feedback for frogspawn)
        }
        Category species = variantEntry.getValue().category();

        // We're handling this. Suppress vanilla behavior.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        // Replace vanilla frogspawn with the matching species's primed egg.
        level.setBlockAndUpdate(pos, PFBlocks.primedEgg(species).defaultBlockState());
        PFDebug.log(PFDebug.Area.EGG, () -> String.format(
            "prime: frogspawn at %s + primer %s -> primed egg species=%s", pos, variantEntry.getKey(), species));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, 0.6F, 1.0F);
    }

    private static Map.Entry<Identifier, SlimeVariant> findVariantForHeldItem(
            Level level, ItemStack stack) {
        Registry<SlimeVariant> registry = level.registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) return null;
        return SlimeVariant.findByPrimer(registry, stack);
    }
}
