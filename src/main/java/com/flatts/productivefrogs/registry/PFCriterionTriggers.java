package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.advancement.FrogProducedTrigger;
import java.util.function.Supplier;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom advancement criterion triggers. The only one today is
 * {@code frog_produced} - the per-tier farming advancements (issue #183) key to
 * it by {@link com.flatts.productivefrogs.data.Category}. Registered against the
 * vanilla {@link Registries#TRIGGER_TYPE} registry via {@link DeferredRegister},
 * the same idiom every other PF registry uses (see {@link PFRecipeTypes}).
 */
public final class PFCriterionTriggers {

    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
        DeferredRegister.create(Registries.TRIGGER_TYPE, ProductiveFrogs.MOD_ID);

    public static final Supplier<FrogProducedTrigger> FROG_PRODUCED =
        TRIGGERS.register("frog_produced", FrogProducedTrigger::new);

    /** The predation-system milestones (#281 Phase 5): predator/apex bred, mob/boss farmed. */
    public static final Supplier<com.flatts.productivefrogs.advancement.PredationMilestoneTrigger> PREDATION_MILESTONE =
        TRIGGERS.register("predation_milestone", com.flatts.productivefrogs.advancement.PredationMilestoneTrigger::new);

    private PFCriterionTriggers() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        TRIGGERS.register(modEventBus);
    }
}
