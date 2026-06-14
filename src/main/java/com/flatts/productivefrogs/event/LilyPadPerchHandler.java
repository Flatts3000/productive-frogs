package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.util.PFDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Sweetslimed lily pad creation (#214, docs/lily_pad_perch.md). Right-clicking a
 * placed vanilla {@code minecraft:lily_pad} with a {@link PFItems#SWEETSLIME}
 * swaps it for a {@link com.flatts.productivefrogs.content.block.SweetslimedLilyPadBlock}
 * and consumes one Sweetslime - an event hook, not a crafting recipe, mirroring
 * {@link EggPrimerHandler} / {@link FrogspawnBottlingHandler}.
 *
 * <p>Config-gated ({@code lily_pad_perch.enabled}): when off the interaction is a
 * no-op. Note the Sweetslime coupling - Sweetslime is itself gated by
 * {@code frog_stats.enabled}, so with the stat layer off the perch is only reachable
 * with a creative/leftover Sweetslime (documented, intentional).
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class LilyPadPerchHandler {

    private LilyPadPerchHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!PFConfig.lilyPadPerchEnabled()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.LILY_PAD)) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(PFItems.SWEETSLIME.get())) {
            return;
        }

        // We're handling this - suppress vanilla.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
        if (level.isClientSide()) {
            return;
        }

        level.setBlockAndUpdate(pos, PFBlocks.SWEETSLIMED_LILY_PAD.get().defaultBlockState());
        if (!event.getEntity().getAbilities().instabuild) {
            held.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.8F, 1.2F);
        PFDebug.log(PFDebug.Area.LIFECYCLE, () -> String.format("perch: sweetslimed a lily pad at %s", pos));
    }
}
