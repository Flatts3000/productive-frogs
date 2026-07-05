package com.flatts.productivefrogs.client;

import com.flatts.productivefrogs.util.PFDebug;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Runtime tint resolver for the Equivalence lane (#253). Computes a representative
 * colour for an arbitrary item, so a synthesized Mimic Slime / Mimic Milk /
 * Prismatic Froglight all wear the source item's colour with no pre-registered
 * {@code primary_color}. The runtime counterpart to a variant's {@code primaryColor}.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>Average the opaque pixels of the item model's particle sprite (works for
 *       flat item textures like redstone).</li>
 *   <li>If that yields nothing readable AND the item is a {@link BlockItem}, fall
 *       back to the block's <b>map colour</b> (block-atlas sprites don't always
 *       expose a readable original image - this is why a birch-log mimic was
 *       rendering white).</li>
 * </ol>
 *
 * <p>Client-only (reads the texture atlas). Successful results are cached per
 * {@link Item}; <b>failures are NOT cached</b>, so a sample that fails before the
 * atlas is ready is retried next frame rather than poisoning the cache with white.
 */
public final class SynthesizedTint {

    private static final int FALLBACK_ARGB = 0xFFFFFFFF;
    private static final int ALPHA_CUTOFF = 16;
    private static final Map<Item, Integer> CACHE = new ConcurrentHashMap<>();

    private SynthesizedTint() {
    }

    /** Opaque ARGB tint for {@code item}. Cached once a real (non-fallback) colour resolves. */
    public static int colorFor(Item item) {
        Integer cached = CACHE.get(item);
        if (cached != null) {
            return cached;
        }
        int colour = sample(item);
        if (colour != FALLBACK_ARGB) {
            CACHE.put(item, colour);
        }
        return colour;
    }

    /** Drop the cache (call on a resource reload so re-textured items re-sample). */
    public static void clearCache() {
        CACHE.clear();
    }

    private static int sample(Item item) {
        int fromSprite = sampleSprite(item);
        if (fromSprite != FALLBACK_ARGB) {
            return fromSprite;
        }
        // Block-atlas sprites often don't expose a readable original image; use the
        // block's map colour as a reliable representative tint instead.
        if (item instanceof BlockItem blockItem) {
            int col = blockItem.getBlock().defaultMapColor().col;
            if (col != 0) {
                final int result = 0xFF000000 | col;
                PFDebug.logOnce(PFDebug.Area.TINT, "synth/" + item,
                    () -> String.format("SynthesizedTint %s -> map-colour #%08X", item, result));
                return result;
            }
        }
        PFDebug.logOnce(PFDebug.Area.TINT, "synth/" + item,
            () -> String.format("SynthesizedTint %s -> FALLBACK (no readable sprite, no map colour)", item));
        return FALLBACK_ARGB;
    }

    /** Average the opaque pixels of the item's particle sprite, or FALLBACK if unreadable. */
    private static int sampleSprite(Item item) {
        try {
            Minecraft mc = Minecraft.getInstance();
            ItemStack stack = new ItemStack(item);
            // 26.1: item models resolve through the ItemModelResolver into an
            // ItemStackRenderState; the representative texture is a layer's
            // particle Material baked off the atlas.
            ItemStackRenderState renderState = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(renderState, stack, ItemDisplayContext.GUI,
                mc.level, null, 0);
            Material.Baked particle = renderState.pickParticleMaterial(RandomSource.create());
            if (particle == null) {
                return FALLBACK_ARGB;
            }
            TextureAtlasSprite sprite = particle.sprite();
            SpriteContents contents = sprite.contents();
            NativeImage image = contents.getOriginalImage();
            if (image == null) {
                return FALLBACK_ARGB;
            }
            int w = contents.width();
            int h = contents.height();
            long r = 0;
            long g = 0;
            long b = 0;
            long count = 0;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    // 26.1: NativeImage#getPixel returns ARGB (0xAARRGGBB).
                    int argb = image.getPixel(x, y);
                    int a = (argb >> 24) & 0xFF;
                    if (a < ALPHA_CUTOFF) {
                        continue;
                    }
                    r += (argb >> 16) & 0xFF;
                    g += (argb >> 8) & 0xFF;
                    b += argb & 0xFF;
                    count++;
                }
            }
            if (count == 0) {
                return FALLBACK_ARGB;
            }
            int ar = (int) (r / count);
            int ag = (int) (g / count);
            int ab = (int) (b / count);
            final int result = 0xFF000000 | (ar << 16) | (ag << 8) | ab;
            PFDebug.logOnce(PFDebug.Area.TINT, "synth/" + item,
                () -> String.format("SynthesizedTint %s -> sprite-average #%08X", item, result));
            return result;
        } catch (Exception e) {
            return FALLBACK_ARGB;
        }
    }
}
