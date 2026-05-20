package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.PFTags;
import com.flatts.productivefrogs.registry.PFBlocks;
import net.minecraft.core.BlockPos;
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
 * Handles right-clicking vanilla {@code minecraft:frogspawn} with any item in
 * a {@code productivefrogs:primer/<category>} tag.
 *
 * <p>The vanilla frogspawn block is replaced in-place with the matching
 * Primed Frog Egg block, and one of the held item is consumed. This is the
 * one-time "what category does this egg belong to" gate per category — the
 * primed block is then a category-locked egg awaiting hatch (future feature).
 *
 * <p>If the held item is in multiple primer tags simultaneously (which it
 * shouldn't be — tags are disjoint by design), the first match in
 * {@link Category} declaration order wins. This is also the natural tier
 * order: metallic, mineral, gem, aquatic, infernal, arcane.
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

        Category category = matchPrimerCategory(held);
        if (category == null) {
            return;
        }

        // We're going to handle this. Suppress vanilla behavior.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();

        // Replace vanilla frogspawn with the matching category's primed egg.
        level.setBlockAndUpdate(pos, PFBlocks.primedEgg(category).defaultBlockState());

        // Consume one primer (creative skips).
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // Feedback — bubble-pop sound has the right "absorbed" feel without
        // committing to a bespoke audio asset.
        level.playSound(
            null,
            pos,
            SoundEvents.BUBBLE_COLUMN_BUBBLE_POP,
            SoundSource.BLOCKS,
            0.6F,
            1.0F
        );
    }

    /**
     * Find the first category whose primer tag contains the given item. Returns
     * null if no category matches (the item isn't a primer).
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
