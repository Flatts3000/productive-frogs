package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * The six resource categories Productive Frogs is built around.
 *
 * <p>Each category has a dedicated Primed Frog Egg block + item, a primer tag,
 * and (eventually) Resource Frog / Resource Slime variants. Tier ordering
 * follows {@code docs/categories_and_tiers.md}.
 *
 * <p>Per the V1 visual design (locked in conversation), each category drives a
 * single ARGB tint applied via BlockColor / ItemColor / entity render tint —
 * no per-category bespoke textures. The tint values are picked to read clearly
 * against a vanilla frogspawn/jelly base.
 */
public enum Category implements StringRepresentable {
    METALLIC(0x808088),
    MINERAL(0xB5651D),
    GEM(0x7EE8FA),
    AQUATIC(0x70C7B8),
    INFERNAL(0xC73E1D),
    ARCANE(0x9070D0);

    public static final Codec<Category> CODEC = StringRepresentable.fromEnum(Category::values);
    public static final StreamCodec<ByteBuf, Category> STREAM_CODEC =
        ByteBufCodecs.idMapper(ordinal -> values()[ordinal], Category::ordinal);

    private final int rgb;

    Category(int rgb) {
        this.rgb = rgb;
    }

    /** Lowercase id used in registry paths, tag paths, lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Item-tag path for items that prime this category. */
    public String primerTagPath() {
        return "primer/" + id();
    }

    /** Registry name for this category's Primed Frog Egg block / item. */
    public String primedEggItemName() {
        return id() + "_frog_egg";
    }

    /** Fully-opaque ARGB int (0xFFRRGGBB) for tint handlers. */
    public int tintArgb() {
        return 0xFF000000 | this.rgb;
    }

    /** RGB only (no alpha) — for BlockColor handlers that expect 0xRRGGBB. */
    public int tintRgb() {
        return this.rgb;
    }

    /**
     * Subtly tinted gray ARGB for surfaces where the full {@link #tintArgb()}
     * would be too saturated — currently the variant-less Resource Slime
     * outer shell. Each channel is a weighted blend:
     * <pre>
     *   channel = (light_gray * {@value SHELL_GRAY_WEIGHT_PERCENT}%
     *              + category  * {@value SHELL_TINT_WEIGHT_PERCENT}%) / 100
     * </pre>
     * where {@code light_gray} = ({@value SHELL_GRAY_R}, {@value SHELL_GRAY_G},
     * {@value SHELL_GRAY_B}). So AQUATIC slimes look cooler-gray, INFERNAL
     * warmer-gray, etc., without going full red/orange/cyan.
     *
     * <p>Per the polish item in {@code docs/backlog.md}: <i>"Could tighten
     * the gray tone per-category (cooler gray for AQUATIC, warmer for
     * INFERNAL) for visual variety."</i>
     */
    public int shellTintArgb() {
        int r = (SHELL_GRAY_R * SHELL_GRAY_WEIGHT_PERCENT + ((this.rgb >> 16) & 0xFF) * SHELL_TINT_WEIGHT_PERCENT) / 100;
        int g = (SHELL_GRAY_G * SHELL_GRAY_WEIGHT_PERCENT + ((this.rgb >>  8) & 0xFF) * SHELL_TINT_WEIGHT_PERCENT) / 100;
        int b = (SHELL_GRAY_B * SHELL_GRAY_WEIGHT_PERCENT +  (this.rgb        & 0xFF) * SHELL_TINT_WEIGHT_PERCENT) / 100;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // Anchor for shellTintArgb's blend. 200,200,200 reads as light-gray —
    // close enough to vanilla's slime-jelly tone that the per-category
    // shift stays subtle.
    private static final int SHELL_GRAY_R = 200;
    private static final int SHELL_GRAY_G = 200;
    private static final int SHELL_GRAY_B = 200;
    private static final int SHELL_GRAY_WEIGHT_PERCENT = 70;
    private static final int SHELL_TINT_WEIGHT_PERCENT = 30;

    @Override
    public String getSerializedName() {
        return id();
    }
}
