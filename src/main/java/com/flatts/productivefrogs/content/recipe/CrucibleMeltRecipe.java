package com.flatts.productivefrogs.content.recipe;

import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * The Froglight Crucible's melt mapping: one item ingredient (in practice a
 * {@code neoforge:components} ingredient matching a Configurable Froglight by
 * its {@code slime_variant} component, same shape as the v1.3 crush recipes)
 * -> a {@link FluidStack} result. Datapack-driven, so later fluids and
 * cross-mod outputs are pure JSON - no new Java per melt mapping.
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}):
 * <pre>{@code
 * {
 *   "type": "productivefrogs:crucible_melting",
 *   "ingredient": { "type": "neoforge:components", ... },
 *   "result": { "id": "minecraft:lava", "amount": 1000 }
 * }
 * }</pre>
 *
 * <p>The item-shaped {@link Recipe} surfaces (assemble / getResultItem) return
 * {@link ItemStack#EMPTY} - the output is fluid, read via {@link #result()} by
 * {@code CrucibleBlockEntity}. {@link #isSpecial()} is true so the recipe book
 * doesn't try to surface an empty-result recipe.
 */
public class CrucibleMeltRecipe implements Recipe<SingleRecipeInput> {

    private final Ingredient ingredient;
    private final FluidStack result;

    public CrucibleMeltRecipe(Ingredient ingredient, FluidStack result) {
        this.ingredient = ingredient;
        this.result = result;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /** The fluid this melt produces. Callers copy before mutating. */
    public FluidStack result() {
        return result;
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PFRecipeTypes.CRUCIBLE_MELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return PFRecipeTypes.CRUCIBLE_MELTING.get();
    }

    public static class Serializer implements RecipeSerializer<CrucibleMeltRecipe> {

        public static final MapCodec<CrucibleMeltRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CrucibleMeltRecipe::ingredient),
            FluidStack.CODEC.fieldOf("result").forGetter(CrucibleMeltRecipe::result)
        ).apply(instance, CrucibleMeltRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleMeltRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CrucibleMeltRecipe::ingredient,
            FluidStack.STREAM_CODEC, CrucibleMeltRecipe::result,
            CrucibleMeltRecipe::new
        );

        @Override
        public MapCodec<CrucibleMeltRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CrucibleMeltRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
