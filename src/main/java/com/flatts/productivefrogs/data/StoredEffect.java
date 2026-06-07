package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Comparator;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import org.jetbrains.annotations.Nullable;

/**
 * The potion effect captured onto a Brewed Froglight (issue #162): one effect
 * + amplifier + an on/off flag. Duration is deliberately absent - a brewed
 * Froglight is a <b>permanent</b> source, so it re-applies the effect on a
 * fixed cadence rather than carrying the original potion's countdown.
 *
 * <p>Stored as the {@code productivefrogs:stored_effect} data component on the
 * item and mirrored onto {@code ConfigurableFroglightBlockEntity} when placed.
 * Orthogonal to the variant: a Froglight is variant-stamped AND (optionally)
 * effect-stamped; smelting / Crucible-melting outputs a fresh resource item
 * and so discards the effect for free.
 */
public record StoredEffect(Holder<MobEffect> effect, int amplifier, boolean enabled) {

    public static final Codec<StoredEffect> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        MobEffect.CODEC.fieldOf("effect").forGetter(StoredEffect::effect),
        Codec.intRange(0, 255).optionalFieldOf("amplifier", 0).forGetter(StoredEffect::amplifier),
        Codec.BOOL.optionalFieldOf("enabled", true).forGetter(StoredEffect::enabled)
    ).apply(instance, StoredEffect::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoredEffect> STREAM_CODEC = StreamCodec.composite(
        MobEffect.STREAM_CODEC, StoredEffect::effect,
        ByteBufCodecs.VAR_INT, StoredEffect::amplifier,
        ByteBufCodecs.BOOL, StoredEffect::enabled,
        StoredEffect::new);

    /** Re-up duration the aura/charm applies each pulse - longer than the pulse interval so the effect never visibly drops. */
    public static final int REAPPLY_DURATION_TICKS = 60;

    public StoredEffect withEnabled(boolean newEnabled) {
        return new StoredEffect(effect, amplifier, newEnabled);
    }

    /**
     * A fresh instance for application: hidden swirl (the source block/charm
     * draws its own particles), HUD icon shown so the carrier knows it's live,
     * not ambient.
     */
    public MobEffectInstance toInstance() {
        return new MobEffectInstance(effect, REAPPLY_DURATION_TICKS, amplifier, false, false, true);
    }

    /**
     * The ONE effect to capture from a slime's active effects (issue #162's
     * decided rule): highest amplifier, then longest remaining duration, then
     * alphabetically-first registry id - fully deterministic. Instantaneous
     * effects (healing/harming) are filtered (they never linger on a living
     * entity anyway, so this is belt-and-suspenders). Returns null when the
     * slime carries no capturable effect (the common, plain-froglight case).
     */
    @Nullable
    public static StoredEffect pick(Collection<MobEffectInstance> active) {
        return active.stream()
            .filter(e -> !e.getEffect().value().isInstantenous())
            .min(Comparator
                .comparingInt(MobEffectInstance::getAmplifier).reversed()
                .thenComparing(Comparator.comparingInt(MobEffectInstance::getDuration).reversed())
                .thenComparing(e -> e.getEffect().unwrapKey()
                    .map(k -> k.location().toString()).orElse("")))
            .map(e -> new StoredEffect(e.getEffect(), e.getAmplifier(), true))
            .orElse(null);
    }
}
