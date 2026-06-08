package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom particle types. {@link #SPRINKLER_DRIP} is a colour-carrying drip
 * (a {@link ColorParticleOption} type, like vanilla {@code ENTITY_EFFECT}) so a
 * filled Sprinkler can drip a milk droplet tinted to its variant - the dripstone
 * hang-and-fall look without the fixed water/lava colour.
 */
public final class PFParticles {

    private PFParticles() {
        // registry holder, not instantiable
    }

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
        DeferredRegister.create(Registries.PARTICLE_TYPE, ProductiveFrogs.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SPRINKLER_DRIP =
        PARTICLE_TYPES.register("sprinkler_drip", () -> new ParticleType<ColorParticleOption>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        });

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
