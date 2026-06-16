package com.flatts.productivefrogs.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime tint resolver for the Equivalence lane (#253). Computes a representative
 * colour for an arbitrary item by averaging the opaque pixels of its model's particle
 * sprite, so a synthesized Mimic Slime / Mimic Milk / Prismatic Froglight all wear the
 * source item's colour with no pre-registered {@code primary_color}. The shared
 * single-source-of-truth for the synthesized lane's tints (the runtime counterpart to a
 * variant's {@code primaryColor}).
 *
 * <p>Client-only: it reads the texture atlas, so it is only ever called from
 * {@code ItemColor}/{@code BlockColor}/fluid-tint handlers. Cached per {@link Item}
 * (the atlas sprite is stable for a resource-pack session). Falls back to opaque white
 * if a sprite has no readable opaque pixels.
 */
public final class SynthesizedTint {

    private static final int FALLBACK_ARGB = 0xFFFFFFFF;
    private static final int ALPHA_CUTOFF = 16;
    private static final Map<Item, Integer> CACHE = new ConcurrentHashMap<>();

    private SynthesizedTint() {
    }

    /** Opaque ARGB tint for {@code item}, computed once from its particle sprite and cached. */
    public static int colorFor(Item item) {
        return CACHE.computeIfAbsent(item, SynthesizedTint::sample);
    }

    /** Drop the cache (call on a resource reload so re-textured items re-sample). */
    public static void clearCache() {
        CACHE.clear();
    }

    private static int sample(Item item) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = new ItemStack(item);
            BakedModel model = mc.getItemRenderer().getModel(stack, mc.level, null, 0);
            TextureAtlasSprite sprite = model.getParticleIcon();
            SpriteContents contents = sprite.contents();
            NativeImage image = contents.getOriginalImage();
            int w = contents.width();
            int h = contents.height();
            long r = 0;
            long g = 0;
            long b = 0;
            long count = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // NativeImage packs pixels as ABGR (0xAABBGGRR).
                    int abgr = image.getPixelRGBA(x, y);
                    int a = (abgr >> 24) & 0xFF;
                    if (a < ALPHA_CUTOFF) {
                        continue;
                    }
                    b += (abgr >> 16) & 0xFF;
                    g += (abgr >> 8) & 0xFF;
                    r += abgr & 0xFF;
                    count++;
                }
            }
            if (count == 0) {
                return FALLBACK_ARGB;
            }
            int ar = (int) (r / count);
            int ag = (int) (g / count);
            int ab = (int) (b / count);
            return 0xFF000000 | (ar << 16) | (ag << 8) | ab;
        } catch (Exception e) {
            return FALLBACK_ARGB;
        }
    }
}
