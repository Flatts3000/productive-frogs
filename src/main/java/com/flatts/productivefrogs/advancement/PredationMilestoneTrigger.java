package com.flatts.productivefrogs.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Criterion trigger for the predation-system milestones (#281 Phase 5):
 * first predator bred, first apex bred, first mob farmed, first boss farmed
 * via the altar loop. One trigger, keyed by a {@link Milestone} field, mirrors
 * the {@link FrogProducedTrigger} shape.
 *
 * <p><b>Attribution:</b> like Froglight production, these events happen to
 * autonomous frogs/altars with no interacting player on the code path - so the
 * milestone awards every player within {@link #AWARD_RADIUS} of the event
 * ({@link #awardNearby}). The player who built the farm is standing at it.
 */
public class PredationMilestoneTrigger extends SimpleCriterionTrigger<PredationMilestoneTrigger.TriggerInstance> {

    /** How close (blocks) a player must be to the event to earn the milestone. */
    private static final double AWARD_RADIUS = 32.0;

    /** The four predation milestones (#281 Phase 5). */
    public enum Milestone implements StringRepresentable {
        PREDATOR_BRED("predator_bred"),
        APEX_BRED("apex_bred"),
        MOB_FARMED("mob_farmed"),
        BOSS_FARMED("boss_farmed");

        public static final Codec<Milestone> CODEC = StringRepresentable.fromEnum(Milestone::values);

        private final String id;

        Milestone(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Fire for {@code player} having hit {@code milestone}. */
    public void trigger(ServerPlayer player, Milestone milestone) {
        this.trigger(player, instance -> instance.matches(milestone));
    }

    /**
     * Award {@code milestone} to every player within {@link #AWARD_RADIUS} of
     * {@code at} - the standard attribution for autonomous frog/altar events.
     */
    public void awardNearby(ServerLevel level, Vec3 at, Milestone milestone) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class,
                AABB.ofSize(at, AWARD_RADIUS * 2, AWARD_RADIUS * 2, AWARD_RADIUS * 2))) {
            trigger(player, milestone);
        }
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Milestone milestone)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
            Milestone.CODEC.fieldOf("milestone").forGetter(TriggerInstance::milestone)
        ).apply(builder, TriggerInstance::new));

        public boolean matches(Milestone hit) {
            return this.milestone == hit;
        }
    }
}
