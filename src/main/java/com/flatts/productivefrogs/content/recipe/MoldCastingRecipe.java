package com.flatts.productivefrogs.content.recipe;

import com.flatts.productivefrogs.registry.PFRecipeTypes;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * The Casting Mold's solidify mapping: a sized fluid ingredient (by
 * {@code c:molten_<metal>} TAG, so AllTheOres molten casts here exactly like
 * PF's own - the second half of the ATM interop) -> an item result. 90 mB ->
 * 1 ingot for the wave-2 roster; nugget/block casts are future data on the
 * same type.
 *
 * <p>JSON shape ({@code data/<ns>/recipe/<name>.json}):
 * <pre>{@code
 * {
 *   "type": "productivefrogs:mold_casting",
 *   "fluid": { "tag": "c:molten_iron", "amount": 90 },
 *   "result": { "id": "minecraft:iron_ingot" }
 * }
 * }</pre>
 *
 * <p>The vanilla item-shaped {@link Recipe} surfaces never match (the input is
 * fluid); {@code CastingMoldBlockEntity} resolves recipes by iterating the
 * type and testing {@link #fluid()} against its buffer.
 */
public class MoldCastingRecipe implements Recipe<SingleRecipeInput> {

    private final SizedFluidIngredient fluid;
    private final ItemStack result;

    public MoldCastingRecipe(SizedFluidIngredient fluid, ItemStack result) {
        this.fluid = fluid;
        this.result = result;
    }

    /** The fluid (tag or id) + mB this cast consumes. */
    public SizedFluidIngredient fluid() {
        return fluid;
    }

    /** The cast item. Callers copy before mutating. */
    public ItemStack result() {
        return result;
    }

    /** True when the buffered fluid is the right type AND amount for this cast. */
    public boolean matchesFluid(FluidStack buffered) {
        return fluid.test(buffered);
    }

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        // Fluid-input recipe: never matched through the item-based manager path.
        return false;
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PFRecipeTypes.MOLD_CASTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return PFRecipeTypes.MOLD_CASTING.get();
    }

    public static class Serializer implements RecipeSerializer<MoldCastingRecipe> {

        public static final MapCodec<MoldCastingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SizedFluidIngredient.FLAT_CODEC.fieldOf("fluid").forGetter(MoldCastingRecipe::fluid),
            ItemStack.CODEC.fieldOf("result").forGetter(MoldCastingRecipe::result)
        ).apply(instance, MoldCastingRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MoldCastingRecipe> STREAM_CODEC = StreamCodec.composite(
            SizedFluidIngredient.STREAM_CODEC, MoldCastingRecipe::fluid,
            ItemStack.STREAM_CODEC, MoldCastingRecipe::result,
            MoldCastingRecipe::new
        );

        @Override
        public MapCodec<MoldCastingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MoldCastingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
