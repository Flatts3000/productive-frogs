package com.flatts.productivefrogs.content.recipe;

import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
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
 * <p>The item-shaped {@link Recipe} surfaces (assemble) return
 * {@link ItemStack#EMPTY} - the output is fluid, read via {@link #result()} by
 * {@code CrucibleBlockEntity}. {@link #isSpecial()} is true so the recipe book
 * doesn't try to surface an empty-result recipe.
 */
public class CrucibleMeltRecipe implements Recipe<SingleRecipeInput> {

    // The result is stored as a fluid Holder + amount and the FluidStack is built lazily
    // in result(), NOT eagerly at codec-decode time. The FluidStack constructor throws
    // "Components not bound yet" when the fluid's default data components are not bound,
    // which is the case while the minimal GameTestServer loads datapacks (recipes parse
    // before component binding completes). Building at use time - when components are
    // always bound - sidesteps that without changing the JSON shape or runtime result.
    private static final Codec<Pair<Holder<Fluid>, Integer>> RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.FLUID.holderByNameCodec().fieldOf("id").forGetter(Pair::getFirst),
        ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(Pair::getSecond)
    ).apply(instance, Pair::of));

    public static final MapCodec<CrucibleMeltRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Ingredient.CODEC.fieldOf("ingredient").forGetter(CrucibleMeltRecipe::ingredient),
        RESULT_CODEC.fieldOf("result").forGetter(r -> Pair.of(r.resultFluid, r.resultAmount))
    ).apply(instance, (ingredient, result) -> new CrucibleMeltRecipe(ingredient, result.getFirst(), result.getSecond())));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleMeltRecipe> STREAM_CODEC = StreamCodec.composite(
        Ingredient.CONTENTS_STREAM_CODEC, CrucibleMeltRecipe::ingredient,
        ByteBufCodecs.holderRegistry(Registries.FLUID), r -> r.resultFluid,
        ByteBufCodecs.VAR_INT, r -> r.resultAmount,
        CrucibleMeltRecipe::new
    );

    private final Ingredient ingredient;
    private final Holder<Fluid> resultFluid;
    private final int resultAmount;

    public CrucibleMeltRecipe(Ingredient ingredient, Holder<Fluid> resultFluid, int resultAmount) {
        this.ingredient = ingredient;
        this.resultFluid = resultFluid;
        this.resultAmount = resultAmount;
    }

    public Ingredient ingredient() {
        return ingredient;
    }

    /** The fluid this melt produces (built fresh each call). Callers copy before mutating. */
    public FluidStack result() {
        return new FluidStack(resultFluid, resultAmount);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return ingredient.test(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    @Override
    public RecipeSerializer<CrucibleMeltRecipe> getSerializer() {
        return PFRecipeTypes.CRUCIBLE_MELTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<CrucibleMeltRecipe> getType() {
        return PFRecipeTypes.CRUCIBLE_MELTING.get();
    }
}
