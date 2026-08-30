package com.flatts.productivefrogs.client.model;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.block.entity.ConfigurableFroglightBlockEntity;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.block.CustomUnbakedBlockStateModel;
import org.jetbrains.annotations.Nullable;

/**
 * A block state model that picks its geometry from the {@code slime_variant} a
 * block entity is carrying.
 *
 * <p>Why this exists: every Configurable Froglight variant normally renders one
 * shared model - vanilla's ochre froglight sprites - multiplied by the variant's
 * {@code primary_color}. A multiply can only ever produce a flat color, so a
 * variant whose whole identity is "more than one hue at once" (Rainbow) cannot be
 * expressed that way. It needs its own baked texture, rendered untinted.
 *
 * <p>A blockstate file maps only <i>blockstate properties</i> to models, and the
 * variant lives in the block entity, so the blockstate alone can never reach it.
 * This model closes that gap: it reads the variant out of the
 * {@link net.neoforged.neoforge.model.data.ModelData} published by
 * {@link ConfigurableFroglightBlockEntity} and forwards to the matching child
 * model, falling back to the shared tinted one.
 *
 * <p>It is deliberately not a Rainbow special case. The variant-to-model map comes
 * from the blockstate JSON, so any future variant that ships its own art is one
 * JSON entry and no Java.
 *
 * <p>Threading: {@link Baked#collectParts} runs on a meshing worker against a
 * snapshot of the world, so it only ever reads model data - never the block entity
 * - and tolerates that data being absent or stale, per the contract on NeoForge's
 * {@code BlockStateModelExtension}.
 */
public final class VariantBlockStateModel {

    /** Blockstate JSON discriminator: {@code "type": "productivefrogs:variant_model"}. */
    public static final Identifier ID =
        Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "variant_model");

    private VariantBlockStateModel() {
    }

    /**
     * The unbaked form, decoded straight out of the blockstate file.
     *
     * @param fallback rendered by any variant without its own entry - the shared
     *                 tinted froglight, which is all 39 tint-driven variants
     * @param variants variant id to the model that variant renders instead
     */
    public record Unbaked(BlockStateModel.Unbaked fallback,
                          Map<Identifier, BlockStateModel.Unbaked> variants)
        implements CustomUnbakedBlockStateModel {

        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                BlockStateModel.Unbaked.CODEC.fieldOf("fallback").forGetter(Unbaked::fallback),
                Codec.unboundedMap(Identifier.CODEC, BlockStateModel.Unbaked.CODEC)
                    .fieldOf("variants").forGetter(Unbaked::variants)
            ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<? extends CustomUnbakedBlockStateModel> codec() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            fallback.resolveDependencies(resolver);
            for (BlockStateModel.Unbaked model : variants.values()) {
                model.resolveDependencies(resolver);
            }
        }

        @Override
        public BlockStateModel bake(ModelBaker baker) {
            Map<Identifier, BlockStateModel> baked = new LinkedHashMap<>();
            variants.forEach((id, model) -> baked.put(id, model.bake(baker)));
            return new Baked(fallback.bake(baker), Map.copyOf(baked));
        }
    }

    /** The baked form: forwards every query to whichever child the variant selects. */
    public static final class Baked implements BlockStateModel {

        private final BlockStateModel fallback;
        private final Map<Identifier, BlockStateModel> variants;

        Baked(BlockStateModel fallback, Map<Identifier, BlockStateModel> variants) {
            this.fallback = fallback;
            this.variants = variants;
        }

        /** The variant this position renders, or null when it renders the fallback. */
        @Nullable
        private Identifier variantAt(BlockAndTintGetter level, BlockPos pos) {
            Identifier variant = level.getModelData(pos)
                .get(ConfigurableFroglightBlockEntity.VARIANT_MODEL_PROPERTY);
            return variant != null && variants.containsKey(variant) ? variant : null;
        }

        private BlockStateModel modelAt(BlockAndTintGetter level, BlockPos pos) {
            Identifier variant = variantAt(level, pos);
            return variant == null ? fallback : variants.get(variant);
        }

        @Override
        public void collectParts(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                 RandomSource random, List<BlockStateModelPart> parts) {
            modelAt(level, pos).collectParts(level, pos, state, random, parts);
        }

        /**
         * The geometry cache key, delegated to whichever child was selected.
         *
         * <p>Overriding this at all is mandatory once {@link #collectParts} varies
         * by position: the default returns null, meaning "not implemented", and the
         * renderer would reuse one position's quads at another.
         *
         * <p>Delegating rather than inventing a key matters because children decode
         * with {@code BlockStateModel.Unbaked.CODEC}, which accepts three shapes -
         * a single variant, a WEIGHTED LIST, and a nested custom model. A weighted
         * list's geometry varies per position (its own key is drawn through
         * {@code random}), so a key of our own that depended only on the variant
         * would claim stability the child does not have, and every position sharing
         * that variant would render one position's quads. Delegation is also what
         * the interface javadoc prescribes for a model that forwards to one other.
         *
         * <p>This cannot collide across the three axis entries either: each is a
         * separate baked child instance, and {@code SingleVariant} keys on itself.
         */
        @Override
        public Object createGeometryKey(BlockAndTintGetter level, BlockPos pos, BlockState state,
                                        RandomSource random) {
            return modelAt(level, pos).createGeometryKey(level, pos, state, random);
        }

        @Override
        public Material.Baked particleMaterial(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            return modelAt(level, pos).particleMaterial(level, pos, state);
        }

        @Override
        public int materialFlags(BlockAndTintGetter level, BlockPos pos, BlockState state) {
            return modelAt(level, pos).materialFlags(level, pos, state);
        }

        // The three world-less overloads below are the deprecated half of the
        // interface, still called where there is no position to resolve against.
        // The fallback is the only sane answer there.

        @SuppressWarnings("deprecation")
        @Override
        public void collectParts(RandomSource random, List<BlockStateModelPart> output) {
            fallback.collectParts(random, output);
        }

        @SuppressWarnings("deprecation")
        @Override
        public Material.Baked particleMaterial() {
            return fallback.particleMaterial();
        }

        @SuppressWarnings("deprecation")
        @Override
        public int materialFlags() {
            return fallback.materialFlags();
        }
    }
}
