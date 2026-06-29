package com.flatts.productivefrogs.data;

import com.flatts.productivefrogs.PFConfig;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Per-resource subdivision within a {@link Category}. One {@code SlimeVariant}
 * = one species of Resource Slime (iron_slime, copper_slime, redstone_slime,
 * ...). Variants are loaded from a datapack registry — JSONs live at
 * {@code data/<datapack_ns>/productivefrogs/slime_variant/<name>.json}.
 *
 * <p>Design lifted from Productive Bees' {@code ConfigurableBee} pattern (see
 * {@code docs/productive_bees_analysis.md} §1) — but with a {@link Codec} from
 * day one rather than hand-rolled JSON→CompoundTag conversion, which is the
 * #1 anti-pattern called out in §5a of the analysis.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code primerItem} — the item that, when right-clicked on a vanilla
 *       slime, transforms it into this variant. Mirrors the per-category
 *       primer tag pattern but at the variant level (iron_ingot → iron_slime
 *       specifically, not just the CAVE category).</li>
 *   <li>{@code category} — parent category. Determines which Resource Frog
 *       can eat this slime and which default discovery pool the variant
 *       belongs to.</li>
 *   <li>{@code primaryColor} / {@code secondaryColor} — RGB tints (0xRRGGBB).
 *       Drive inventory icon and entity-render tinting. Distinct from
 *       {@link Category#tintRgb()} so variants within the same category can
 *       look different (iron silver vs copper orange vs gold yellow within
 *       the CAVE category).</li>
 *   <li>{@code weight} — relative weight for random discovery pool picks
 *       (per {@code docs/slime_sourcing.md} §V1 Configurability). Higher
 *       weight = more likely to spawn. Default 1. <b>Weight 0 = prime-only</b>:
 *       the variant never appears in split-discovery and can only be created
 *       by priming with its item — the boss-tier tier (#172/#173: the frog
 *       loop amplifies a resource the player has earned, it never bypasses
 *       the boss).</li>
 *       <li>{@code innerBlock} — optional vanilla block id whose texture is
 *       shown inside the Resource Slime. {@code scripts/generate_resource_slime_textures.py}
 *       bakes a downscaled copy of this block's texture onto the inner-cube
 *       faces of the per-variant slime texture ({@code <variant>_resource_slime.png}),
 *       which the slime renders as part of its translucent body; the outer
 *       shell is tinted by {@code primaryColor}. (The v1.0.1 attempt to draw a
 *       live block model in a separate render pass was removed: the slime's
 *       translucent shell depth-culled it, so it never appeared.) When absent,
 *       the variant falls back to the plain per-category slime texture.
 *       <br><b>Identifier format:</b> a plain block id (namespace +
 *       path, no {@code textures/} prefix, no {@code .png}). Example:
 *       {@code "minecraft:iron_block"}.</li>
 *   <li>{@code spawnEntity} - optional EntityType id that a Slime Milk source of
 *       this variant spawns instead of the default {@code ResourceSlime}. The
 *       type must be a {@code Slime} subclass; absent (the normal case) means a
 *       variant-stamped {@code ResourceSlime}. This is the data-driven extension
 *       point for cross-mod variants whose "parent" is a modded slime; the two
 *       built-in specials (vanilla green slime / magma cube) are handled by
 *       sentinel ids in {@code SlimeMilkSourceBlock} rather than registry
 *       entries, on purpose, since they are not real resource variants (no
 *       primer, no froglight, no spawn egg).</li>
 * </ul>
 */
public record SlimeVariant(
    Optional<Identifier> primerItem,
    Optional<TagKey<Item>> primerTag,
    Category category,
    int primaryColor,
    int secondaryColor,
    int weight,
    Optional<Identifier> innerBlock,
    Optional<Identifier> spawnEntity,
    boolean spawnCatalyst
) {

    /**
     * Back-compat constructor without {@code spawnCatalyst} (defaults false).
     * Lets the pre-#184 8-arg call sites (tests, any hand-construction) stand
     * unchanged - only the codec and boss variants exercise the new field.
     */
    public SlimeVariant(Optional<Identifier> primerItem, Optional<TagKey<Item>> primerTag,
            Category category, int primaryColor, int secondaryColor, int weight,
            Optional<Identifier> innerBlock, Optional<Identifier> spawnEntity) {
        this(primerItem, primerTag, category, primaryColor, secondaryColor, weight,
            innerBlock, spawnEntity, false);
    }

    /**
     * Codec used by the datapack registry for both JSON loading and client
     * sync (NeoForge's {@code DataPackRegistryEvent.NewRegistry} takes a
     * {@code Codec<T>} for the network side, not a separate StreamCodec —
     * it wraps the codec with its own byte-level serialization).
     *
     * <p>{@code weight} is constrained to {@code [0, Integer.MAX_VALUE]} so
     * datapack input can't produce negative weights that would skew the
     * discovery-pool random pick. The field is optional and defaults to 1 —
     * simple variants don't need to specify it. Weight 0 is the deliberate
     * prime-only marker: {@link #pickWeighted} skips such variants entirely
     * (v1.14, #172/#173 — dragon egg, nether star, etc. must be earned once
     * before the loop can farm them).
     *
     * <p>{@code primary_color} / {@code secondary_color} are constrained to
     * {@code [0, 0xFFFFFF]} (24-bit RGB) so a datapack can't supply a negative
     * or alpha-bearing value that would render as a garbage tint.
     *
     * <p>{@code inner_block} (v1.0.1+): the vanilla block id rendered inside
     * the Resource Slime. Optional; the inner-block render pass is skipped
     * when absent or unresolvable. Format: a plain block id (no
     * {@code textures/} prefix, no {@code .png}). Example:
     * {@code "minecraft:iron_block"}.
     */
    public static final Codec<SlimeVariant> CODEC = RecordCodecBuilder.<SlimeVariant>create(
        instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("primer_item").forGetter(SlimeVariant::primerItem),
            TagKey.codec(Registries.ITEM).optionalFieldOf("primer_tag").forGetter(SlimeVariant::primerTag),
            Category.CODEC.fieldOf("category").forGetter(SlimeVariant::category),
            Codec.intRange(0, 0xFFFFFF).fieldOf("primary_color").forGetter(SlimeVariant::primaryColor),
            Codec.intRange(0, 0xFFFFFF).fieldOf("secondary_color").forGetter(SlimeVariant::secondaryColor),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("weight", 1).forGetter(SlimeVariant::weight),
            Identifier.CODEC.optionalFieldOf("inner_block").forGetter(SlimeVariant::innerBlock),
            Identifier.CODEC.optionalFieldOf("spawn_entity").forGetter(SlimeVariant::spawnEntity),
            // #184: when true, the variant's Slime Milk source spawns nothing
            // until the matching catalyst block surrounds it on all 6 faces
            // (the boss-tier altar gate). Generic field; the catalyst BLOCKS
            // are hardcoded to the four boss resources in PFBlocks.
            Codec.BOOL.optionalFieldOf("spawn_catalyst", false).forGetter(SlimeVariant::spawnCatalyst)
        ).apply(instance, SlimeVariant::new)
    ).comapFlatMap(SlimeVariant::requirePrimer, Function.<SlimeVariant>identity());

    /**
     * Boundary validation for the codec: every variant must declare at least
     * one primer ({@code primer_item} or {@code primer_tag}). A variant with
     * neither can never be intentionally primed by a player, yet it would still
     * enter the random discovery pool ({@link #pickWeighted}) — a silent footgun
     * on the datapack-override path that {@code scripts/generate_cross_mod_variants.ps1}
     * guards but a hand-authored datapack JSON does not. Failing the decode here
     * surfaces the mistake as a datapack load error naming the offending file,
     * rather than a mystery unprimeable slime appearing at runtime.
     */
    private static DataResult<SlimeVariant> requirePrimer(SlimeVariant variant) {
        if (variant.primerItem().isPresent() || variant.primerTag().isPresent()) {
            return DataResult.success(variant);
        }
        return DataResult.error(() ->
            "SlimeVariant must define primer_item or primer_tag (a variant with neither can never be primed)");
    }

    /**
     * Whether this variant is enabled under the current config (#203). A pack can
     * force-off an individual variant, its whole category, or the boss tier; a
     * disabled variant is treated as if it weren't in the registry at every
     * resolution site (priming, split-discovery, JEI, creative tab) while its
     * registry entry stays put (save-safe). Delegates to
     * {@link PFConfig#variantEnabled} - which fails open before the config loads,
     * so this is non-breaking by default. The id is passed in because the record
     * doesn't carry its own registry key.
     */
    public boolean isEnabled(Identifier id) {
        // Per-variant / category / boss gate (#203), then the per-integration
        // force-off (#204). Both fail open before the config loads. The
        // integration dimension lives here (not as a parameter on variantEnabled)
        // so every resolution site that calls isEnabled(id) picks it up for free.
        return PFConfig.variantEnabled(id, this.category, this.weight)
            && !PFConfig.integrationDisabled(id);
    }

    /**
     * Find the variant primed by the given held stack, or {@code null}. Matches
     * a variant's exact {@code primer_item} (by item id) OR its {@code primer_tag}
     * (by tag membership). The tag path is what lets a single cross-mod variant
     * (e.g. {@code primer_tag: c:ingots/tin}) be primed by any mod's tin ingot
     * without hardcoding a specific mod's item - see {@code docs/cross_mod_compat.md}.
     *
     * <p><b>Precedence:</b> an exact {@code primer_item} match always wins over a
     * {@code primer_tag} match (specific item beats common tag), and the result
     * is deterministic regardless of registry iteration order. This matters once
     * a datapack overlaps a tag-driven variant with an item-driven one (e.g. a
     * pack adds {@code c:ingots/tin} while a first-party {@code tin} already
     * exists): the exact item wins predictably. Two variants matching purely by
     * overlapping {@code primer_tag} still resolve to the first in registry
     * order — a tag-vs-tag collision is a datapack authoring conflict, not a
     * case the resolver can disambiguate.
     *
     * <p>Linear scan over the registry; trivial next to the right-click handler.
     */
    @Nullable
    public static Map.Entry<Identifier, SlimeVariant> findByPrimer(
            Registry<SlimeVariant> registry, ItemStack stack) {
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        Map.Entry<Identifier, SlimeVariant> tagMatch = null;
        for (Map.Entry<net.minecraft.resources.ResourceKey<SlimeVariant>, SlimeVariant> entry : registry.entrySet()) {
            SlimeVariant variant = entry.getValue();
            Identifier loc = entry.getKey().identifier();
            // A config-disabled variant (#203) is unprimable: skip it entirely so
            // priming its resource does nothing (and a disabled exact-item variant
            // can't shadow an enabled tag match below).
            if (!variant.isEnabled(loc)) {
                continue;
            }
            // Exact primer_item match wins immediately — specific beats general.
            if (variant.primerItem().filter(itemId::equals).isPresent()) {
                return Map.entry(loc, variant);
            }
            // Remember the first tag match but keep scanning for an exact item match.
            if (tagMatch == null && variant.primerTag().map(stack::is).orElse(false)) {
                tagMatch = Map.entry(loc, variant);
            }
        }
        return tagMatch;
    }

    /**
     * True if {@code stack} primes {@code variant}: it is in the variant's
     * {@code primer_tag} (tag membership, resolved at runtime where tags are
     * loaded), or its item id equals the variant's exact {@code primer_item}.
     */
    public static boolean primerMatches(SlimeVariant variant, ItemStack stack) {
        if (variant.primerTag().map(stack::is).orElse(false)) {
            return true;
        }
        if (variant.primerItem().isPresent()) {
            return variant.primerItem().get().equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
        return false;
    }

    /**
     * Exact {@code primer_item}-only match by item id (ignores {@code primer_tag}).
     * Retained for callers/tests that resolve by a known canonical item id; the
     * infusion + egg-priming handlers use {@link #findByPrimer} so tag-driven
     * cross-mod variants resolve too.
     */
    @Nullable
    public static Map.Entry<Identifier, SlimeVariant> findByPrimerItem(
            Registry<SlimeVariant> registry, Identifier itemId) {
        for (Map.Entry<net.minecraft.resources.ResourceKey<SlimeVariant>, SlimeVariant> entry : registry.entrySet()) {
            // Skip config-disabled variants (#203) - a disabled resource is unprimable.
            if (!entry.getValue().isEnabled(entry.getKey().identifier())) {
                continue;
            }
            if (entry.getValue().primerItem().filter(id -> id.equals(itemId)).isPresent()) {
                return Map.entry(entry.getKey().identifier(), entry.getValue());
            }
        }
        return null;
    }

    /**
     * Weighted random pick from all variants whose category matches the given
     * one. Returns {@code null} when the registry has no variants for that
     * category (e.g., during early development before variants for a category
     * have been shipped, or when a datapack ships none for a category).
     *
     * <p>Used by {@code SlimeSplitDiscoveryHandler} to pick the specific
     * variant a passively-discovered Resource Slime becomes — the parent
     * species's default category determines the pool; {@code weight} biases
     * the pick within it.
     */
    @Nullable
    public static Map.Entry<Identifier, SlimeVariant> pickWeighted(
            Registry<SlimeVariant> registry, Category category, RandomSource random) {
        List<Map.Entry<Identifier, SlimeVariant>> pool = new ArrayList<>();
        // Accumulate as long so a datapack with many high-weight variants
        // (each capped at Integer.MAX_VALUE individually) can't overflow.
        // RandomSource doesn't expose nextLong-with-bound directly, so cap
        // total + roll at Integer.MAX_VALUE — anything beyond is silly anyway.
        long totalWeight = 0L;
        for (Map.Entry<net.minecraft.resources.ResourceKey<SlimeVariant>, SlimeVariant> entry : registry.entrySet()) {
            if (entry.getValue().category() != category) continue;
            // Weight 0 = prime-only (#172/#173): excluded from the pool
            // entirely, so it can neither be rolled nor returned by the
            // defensive fallback, and an all-zero category cleanly yields null
            // (no nextInt(0) crash).
            if (entry.getValue().weight() <= 0) continue;
            // Config-disabled variants (#203) drop out of the discovery pool too;
            // a fully-disabled category then yields an empty pool -> null, exactly
            // like a category with no variants.
            if (!entry.getValue().isEnabled(entry.getKey().identifier())) continue;
            pool.add(Map.entry(entry.getKey().identifier(), entry.getValue()));
            totalWeight += entry.getValue().weight();
        }
        if (pool.isEmpty()) {
            return null;
        }
        int cap = (int) Math.min(totalWeight, Integer.MAX_VALUE);
        int roll = random.nextInt(cap);
        long cumulative = 0L;
        for (Map.Entry<Identifier, SlimeVariant> entry : pool) {
            cumulative += entry.getValue().weight();
            if (roll < cumulative) {
                return entry;
            }
        }
        // Shouldn't reach — defensive fallback to first.
        return pool.get(0);
    }
}
