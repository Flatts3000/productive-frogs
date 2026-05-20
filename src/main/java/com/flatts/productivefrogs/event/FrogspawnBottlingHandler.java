package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.PrimedFrogEggBlock;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles right-clicking vanilla {@code minecraft:frogspawn} OR any
 * {@link PrimedFrogEggBlock} with an empty glass bottle: the held bottle
 * transforms in-place into a {@code productivefrogs:frog_egg}, and the target
 * block is consumed.
 *
 * <p>When the target is a Primed Frog Egg block, the bottle's
 * {@link PFDataComponents#CONTAINED_CATEGORY} data component is set to the
 * block's category. Placing the bottle later places the matching primed block.
 * Vanilla frogspawn produces a bottle with the component absent, which places
 * back to {@code minecraft:frogspawn}.
 *
 * <p>Mirrors the vanilla water-bottle / fish-bucket capture pattern via
 * {@link ItemUtils#createFilledResult}.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class FrogspawnBottlingHandler {

    private FrogspawnBottlingHandler() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(Items.GLASS_BOTTLE)) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // Two valid bottling targets: vanilla frogspawn (category null) or any
        // of our Primed Frog Egg blocks (category set on the block instance).
        Category category;
        if (state.is(Blocks.FROGSPAWN)) {
            category = null;
        } else if (state.getBlock() instanceof PrimedFrogEggBlock primed) {
            category = primed.getCategory();
        } else {
            return;
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();

        level.removeBlock(pos, false);

        ItemStack filled = new ItemStack(PFItems.FROG_EGG.get());
        if (category != null) {
            filled.set(PFDataComponents.CONTAINED_CATEGORY.get(), category);
        }
        ItemStack result = ItemUtils.createFilledResult(held, player, filled);
        player.setItemInHand(hand, result);

        level.playSound(
            null,
            pos,
            SoundEvents.BOTTLE_FILL,
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );
    }
}
