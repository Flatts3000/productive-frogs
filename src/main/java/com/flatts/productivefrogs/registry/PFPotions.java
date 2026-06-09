package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.Potion;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom {@link Potion}s registered by Productive Frogs.
 *
 * <p>{@link #HOPPING} (#215) grants {@link PFEffects#HOPPING}. The {@code "hopping"}
 * name drives the item display key ({@code item.minecraft.potion.effect.hopping}
 * and the splash/lingering/arrow variants). The brewing mix (awkward + raw frog
 * legs -> this) is registered in {@code PFModBusEvents}.
 */
public final class PFPotions {

    public static final DeferredRegister<Potion> POTIONS =
        DeferredRegister.create(Registries.POTION, ProductiveFrogs.MOD_ID);

    /** 3 minutes of Hopping at level I. */
    public static final Holder<Potion> HOPPING = POTIONS.register(
        "hopping",
        () -> new Potion("hopping", new MobEffectInstance(PFEffects.HOPPING, 3600)));

    private PFPotions() {
        // registry holder
    }

    public static void register(IEventBus modEventBus) {
        POTIONS.register(modEventBus);
    }
}
