package com.flatts.productivefrogs.client.color;

import com.flatts.productivefrogs.data.SlimeVariant;
import com.flatts.productivefrogs.registry.PFRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * Shared colour helpers for the PF tint sources (the 26.1 data-driven
 * {@code BlockTintSource} / {@code ItemTintSource} pipeline). These mirror the
 * private helpers that lived on {@code PFClientEvents} under the legacy
 * (1.21.1) {@code RegisterColorHandlersEvent} lambda API.
 */
public final class Tints {

    private Tints() {
    }

    /**
     * Ensure the alpha byte is set to {@code 0xFF}. Tint sources return
     * ARGB-shaped {@code int}s; a raw 24-bit RGB value (alpha == 0) makes the
     * tinted layer render fully transparent. Source colours here
     * ({@code Category#tintRgb()}, {@code SlimeVariant#primaryColor()}) are all
     * 24-bit, so this normalises them.
     */
    public static int opaque(int rgb) {
        return 0xFF000000 | rgb;
    }

    /**
     * Darken an RGB triple by ~30% for use as a spawn egg's secondary (spotted)
     * colour. Matches the formula that used to ride the {@code SpawnEggItem}
     * constructor before 26.1 moved spawn-egg colour off the item.
     */
    public static int darker(int rgb) {
        int r = ((rgb >> 16) & 0xFF) * 70 / 100;
        int g = ((rgb >>  8) & 0xFF) * 70 / 100;
        int b =  (rgb        & 0xFF) * 70 / 100;
        return (r << 16) | (g << 8) | b;
    }

    /**
     * Opaque {@code primary_color} for a variant from the {@code slime_variant}
     * registry, or {@code -1} when the registry is not yet available (caller
     * picks a fallback). Prefers the passed client level; falls back to the
     * running client's level when {@code null} (e.g. a block tint source whose
     * world accessor is not a {@link ClientLevel}).
     */
    public static int variantColor(@Nullable ClientLevel level, Identifier variantId) {
        ClientLevel resolved = level != null ? level : Minecraft.getInstance().level;
        if (resolved == null) {
            return -1;
        }
        var registry = PFRegistries.variants(resolved.registryAccess());
        SlimeVariant variant = PFRegistries.variant(registry, variantId);
        return variant == null ? -1 : opaque(variant.primaryColor());
    }
}
