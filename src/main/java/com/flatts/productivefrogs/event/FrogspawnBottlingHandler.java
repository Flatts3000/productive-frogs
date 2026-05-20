package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

        // Consume one empty bottle from the held stack (creative skips).
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        // Consume the frogspawn block.
        level.removeBlock(pos, false);

        // Give the player a Frog Egg item (the "bottle of frogspawn"); drop as
        // an item entity at the centered block position if inventory is full.
        ItemStack frogEgg = new ItemStack(PFItems.FROG_EGG.get());
        if (!player.addItem(frogEgg)) {
            ItemEntity drop = new ItemEntity(
                level,
                pos.getX() + 0.5,
                pos.getY() + 0.5,
                pos.getZ() + 0.5,
                frogEgg
            );
            drop.setDefaultPickUpDelay();
            level.addFreshEntity(drop);
        }

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
