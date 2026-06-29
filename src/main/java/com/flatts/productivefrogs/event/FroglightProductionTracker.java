package com.flatts.productivefrogs.event;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFCriterionTriggers;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import com.flatts.productivefrogs.registry.PFRegistries;
import java.util.EnumSet;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Grants the per-tier "you farmed this species" advancements (#183) by firing
 * {@link com.flatts.productivefrogs.advancement.FrogProducedTrigger} for each
 * {@link Category} of Froglight a player is holding.
 *
 * <p><b>Why an inventory scan, not the drop event:</b> the Froglight drop
 * ({@link FrogTongueDropHandler}) runs on a {@code LivingDeathEvent} with no
 * {@link ServerPlayer} - frogs are wild mobs and the Terrarium path deposits
 * into a block inventory - so there is no player to grant the advancement to at
 * drop time. The maintainer's rule is "having a Froglight of that species in
 * your inventory" (any acquisition path: ground pickup, pulled from a Hatch /
 * chest, crafted), which this scan implements uniformly.
 *
 * <p><b>Cost:</b> a 41-slot inventory scan once per second per online player.
 * {@code SimpleCriterionTrigger#trigger} short-circuits when no advancement is
 * listening, so once a player has earned all six per-tier nodes the scan grants
 * nothing and the trigger does no work - the cost is bounded and self-limiting.
 */
@EventBusSubscriber(modid = ProductiveFrogs.MOD_ID)
public final class FroglightProductionTracker {

    /** Scan cadence: once per second. Cheap, and the grant is not time-sensitive. */
    private static final int SCAN_INTERVAL_TICKS = 20;

    private FroglightProductionTracker() {
        // event handler, not instantiable
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % SCAN_INTERVAL_TICKS != 0) {
            return;
        }
        Registry<SlimeVariant> registry = player.level().registryAccess()
            .registry(PFRegistries.SLIME_VARIANT).orElse(null);
        if (registry == null) {
            return;
        }
        EnumSet<Category> produced = EnumSet.noneOf(Category.class);
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(PFItems.CONFIGURABLE_FROGLIGHT.get())) {
                continue;
            }
            Identifier variantId = stack.get(PFDataComponents.SLIME_VARIANT.get());
            if (variantId == null) {
                continue;
            }
            SlimeVariant variant = registry.get(variantId);
            if (variant != null) {
                produced.add(variant.category());
            }
        }
        for (Category category : produced) {
            PFCriterionTriggers.FROG_PRODUCED.get().trigger(player, category);
        }
    }
}
