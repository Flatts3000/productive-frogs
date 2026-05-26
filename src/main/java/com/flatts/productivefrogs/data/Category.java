package com.flatts.productivefrogs.data;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Locale;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.joml.Vector3f;

/**
 * The six parent slime species Productive Frogs is built around. Each species
 * is the canonical source of slimes for its category of resources — Cave Slimes
 * yield ores, Geode Slimes yield gems, etc.
 *
 * <p>Per the V1.5 species-as-category redesign (see
 * {@code docs/species_as_category_redesign.md}): the enum constants are now
 * named for the species, not abstract categories. Each species drives a single
 * ARGB tint applied via BlockColor / ItemColor / entity render tint, and
 * each has a dedicated Primed Frog Egg block + item, Resource Frog +
 * Resource Tadpole entity, and N Resource Slime variants.
 */
public enum Category implements StringRepresentable {
    // Declared in canonical player-progression order (see docs/canonical_ordering.md):
    // CAVE -> GEODE -> BOG -> TIDE -> INFERNAL -> VOID. This order drives every
    // surface that iterates Category.values() (creative tabs, JEI, the per-species
    // DeferredRegister insertion). Reordering is save-safe: persistence is by name
    // (StringRepresentable / Category.CODEC, and the entities' NBT writes
    // getCategory().name()); ordinal() is used only for transient network sync
    // (STREAM_CODEC + synced data), regenerated each session from name-based data.
    CAVE(0xB5651D),
    GEODE(0x7EE8FA),
    BOG(0x6A8540),
    TIDE(0x70C7B8),
    INFERNAL(0xC73E1D),
    VOID(0x9070D0);

    public static final Codec<Category> CODEC = StringRepresentable.fromEnum(Category::values);
    public static final StreamCodec<ByteBuf, Category> STREAM_CODEC =
        ByteBufCodecs.idMapper(Category::byOrdinalOrThrow, Category::ordinal);

    // Cached enum constants. values() clones its array on every call, so the
    // hot-path ordinal decoders (byOrdinalOrThrow on every packet decode,
    // fromOrdinalOrDefault in entity synced-data getters read each render/AI
    // tick) index into this shared copy instead. Never mutate it.
    private static final Category[] VALUES = values();

    private final int rgb;

    /**
     * Decode a wire ordinal to a Category, rejecting out-of-range values with a
     * {@link DecoderException} instead of an {@code ArrayIndexOutOfBoundsException}.
     * {@code ByteBufCodecs.idMapper} does no bounds check before invoking this, so a
     * crafted or version-skewed packet (e.g. a modified client sending an item with an
     * out-of-range {@code contained_category} ordinal) would otherwise crash the decode
     * thread. A {@code DecoderException} is caught by the network pipeline and closes the
     * connection cleanly.
     */
    private static Category byOrdinalOrThrow(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            throw new DecoderException("Unknown Category ordinal: " + ordinal);
        }
        return VALUES[ordinal];
    }

    /**
     * Decode a synced/saved ordinal to a Category, falling back to {@link #BOG} on
     * out-of-range input rather than throwing. Used by entity synched-data getters
     * (DATA_CATEGORY is a raw int serializer) where a lenient fallback is preferable to
     * a client crash on a corrupt save or version-skew sync.
     */
    public static Category fromOrdinalOrDefault(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return BOG;
        }
        return VALUES[ordinal];
    }

    /**
     * Splat particle tinted with an arbitrary RGB. Used by {@code ResourceSlime}, which
     * prefers its variant's primary colour over the category tint when present.
     */
    public static DustParticleOptions dustParticle(int rgb) {
        Vector3f color = new Vector3f(
            ((rgb >> 16) & 0xFF) / 255.0F,
            ((rgb >>  8) & 0xFF) / 255.0F,
            (rgb         & 0xFF) / 255.0F);
        return new DustParticleOptions(color, 1.0F);
    }

    /** Splat particle tinted with this category's colour. Used by the parent species. */
    public DustParticleOptions tintParticle() {
        return dustParticle(this.rgb);
    }

    Category(int rgb) {
        this.rgb = rgb;
    }

    /** Lowercase id used in registry paths, lang keys. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
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
     * {@value SHELL_GRAY_B}). So TIDE slimes look cooler-gray, INFERNAL
     * warmer-gray, etc., without going full red/orange/cyan.
     *
     * <p>Per the polish item in {@code docs/backlog.md}: <i>"Could tighten
     * the gray tone per-category (cooler gray for TIDE, warmer for
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
