package com.flatts.productivefrogs.advancement;

import com.flatts.productivefrogs.data.Category;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * Criterion trigger for the per-tier "you farmed this species" advancements
 * (issue #183). Fires for a player who holds a Froglight of a given
 * {@link Category} in their inventory; each of the six per-tier advancements
 * keys its instance to one category via the {@code category} field.
 *
 * <p>Why a custom trigger rather than vanilla {@code inventory_changed}: a
 * Froglight is a single item ({@code configurable_froglight}) whose species is a
 * {@code slime_variant} registry lookup carried in a data component, not part of
 * the item id - so no vanilla item predicate can match "any Cave Froglight"
 * without enumerating every Cave variant and regenerating on every roster
 * change. Keying on {@link Category} is intrinsic to the resource and needs no
 * regeneration as variants grow.
 *
 * <p>The trigger is fired from {@code FroglightInventoryTrigger} (a throttled
 * server-side inventory scan), which is where the player attribution lives - the
 * Froglight drop itself ({@code FrogTongueDropHandler}) has no player, since
 * frogs are wild mobs.
 */
public class FrogProducedTrigger extends SimpleCriterionTrigger<FrogProducedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Fire for {@code player} having produced a Froglight of {@code category}. */
    public void trigger(ServerPlayer player, Category category) {
        this.trigger(player, instance -> instance.matches(category));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Category category)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
            Category.CODEC.fieldOf("category").forGetter(TriggerInstance::category)
        ).apply(builder, TriggerInstance::new));

        public boolean matches(Category produced) {
            return this.category == produced;
        }
    }
}
