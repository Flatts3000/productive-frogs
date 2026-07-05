package com.flatts.productivefrogs.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

/**
 * A milk droplet that falls from a filled Sprinkler into the cavity. It reuses
 * the vanilla drip texture (the dripstone-style droplet) but is tinted to the
 * carried {@link ColorParticleOption} colour, so each Sprinkler drips its milk
 * variant's hue instead of the fixed water/lava colours the vanilla dripstone
 * particles are locked to.
 */
public class SprinklerDripParticle extends SingleQuadParticle {

    protected SprinklerDripParticle(ClientLevel level, double x, double y, double z, ColorParticleOption color, TextureAtlasSprite sprite) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprite);
        this.setColor(color.getRed(), color.getGreen(), color.getBlue());
        this.gravity = 0.06F;       // hangs a moment, then accelerates down like a drip
        this.xd = 0.0;
        this.yd = 0.0;
        this.zd = 0.0;
        this.quadSize *= 0.5F;
        this.lifetime = 40;
        this.setSize(0.01F, 0.01F);
    }

    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    /** Client factory bound in {@code PFClientEvents.onRegisterParticleProviders}. */
    public static final class Provider implements ParticleProvider<ColorParticleOption> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public Particle createParticle(ColorParticleOption type, ClientLevel level,
                double x, double y, double z, double dx, double dy, double dz, RandomSource random) {
            return new SprinklerDripParticle(level, x, y, z, type, this.sprites.get(random));
        }
    }
}
