package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
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
 * Handles right-clicking vanilla {@code minecraft:frogspawn} with an empty
 * glass bottle: the held bottle transforms in-place into a
 * {@code productivefrogs:frog_egg} (a glass bottle filled with frogspawn),
 * and the frogspawn block is consumed.
 *
 * <p>Mirrors the vanilla water-bottle / fish-bucket capture pattern — the
 * glass bottle is NOT a separate consumed cost; it becomes the container.
 * The reverse operation (placing the Frog Egg back to release vanilla
 * frogspawn) lives on the {@link com.flatts.productivefrogs.content.item.FrogEggItem}
 * item itself.
 *
 * <p>On match:
 * <ul>
 *   <li>The held glass bottle stack shrinks by 1 (non-creative).</li>
 *   <li>The targeted frogspawn block is replaced with air.</li>
 *   <li>One Frog Egg item is added to the player's inventory (or dropped at
 *       the block position if inventory is full).</li>
 *   <li>A bottle-fill sound plays at the block position.</li>
 *   <li>The event is consumed so vanilla doesn't double-process.</li>
 * </ul>
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
        if (!state.is(Blocks.FROGSPAWN)) {
            return;
        }

        // We're going to handle this. Suppress vanilla behavior on both sides.
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);

        // Mechanics only run server-side. The client just gets the sound + a
        // swing animation via the SUCCESS result.
        if (level.isClientSide()) {
            return;
        }

        Player player = event.getEntity();
        InteractionHand hand = event.getHand();

        // Consume the frogspawn block.
        level.removeBlock(pos, false);

        // Use vanilla ItemUtils.createFilledResult so the bottle-to-egg
        // transition matches the water-bottle / fish-bucket pattern exactly:
        //   - shrink the empty stack (skipped in creative)
        //   - if the empty stack is now fully consumed, the held slot becomes
        //     the new Frog Egg (the "transform in place" feel)
        //   - otherwise, the new Frog Egg lands in the next free inventory
        //     slot (or drops at the player's feet if full)
        ItemStack filled = new ItemStack(PFItems.FROG_EGG.get());
        ItemStack result = ItemUtils.createFilledResult(held, player, filled);
        player.setItemInHand(hand, result);

        // Feedback — reuse vanilla bottle-fill sound for thematic consistency.
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
