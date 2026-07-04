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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
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

    // The result is stored as an item Holder + count and the ItemStack is built lazily in
    // result(), NOT eagerly at codec-decode time. The ItemStack constructor throws
    // "Components not bound yet" when the item's default data components are not bound,
    // which is the case while the minimal GameTestServer loads datapacks (recipes parse
    // before component binding completes). Building at use time sidesteps that without
    // changing the JSON shape or the runtime result.
    private static final Codec<Pair<Holder<Item>, Integer>> RESULT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(Pair::getFirst),
        ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Pair::getSecond)
    ).apply(instance, Pair::of));

    public static final MapCodec<MoldCastingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        SizedFluidIngredient.CODEC.fieldOf("fluid").forGetter(MoldCastingRecipe::fluid),
        RESULT_CODEC.fieldOf("result").forGetter(r -> Pair.of(r.resultItem, r.resultCount))
    ).apply(instance, (fluid, result) -> new MoldCastingRecipe(fluid, result.getFirst(), result.getSecond())));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoldCastingRecipe> STREAM_CODEC = StreamCodec.composite(
        SizedFluidIngredient.STREAM_CODEC, MoldCastingRecipe::fluid,
        ByteBufCodecs.holderRegistry(Registries.ITEM), r -> r.resultItem,
        ByteBufCodecs.VAR_INT, r -> r.resultCount,
        MoldCastingRecipe::new
    );

    private final SizedFluidIngredient fluid;
    private final Holder<Item> resultItem;
    private final int resultCount;

    public MoldCastingRecipe(SizedFluidIngredient fluid, Holder<Item> resultItem, int resultCount) {
        this.fluid = fluid;
        this.resultItem = resultItem;
        this.resultCount = resultCount;
    }

    /** The fluid (tag or id) + mB this cast consumes. */
    public SizedFluidIngredient fluid() {
        return fluid;
    }

    /** The cast item (built fresh each call). Callers copy before mutating. */
    public ItemStack result() {
        return new ItemStack(resultItem, resultCount);
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
    public ItemStack assemble(SingleRecipeInput input) {
        return result();
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
    public RecipeSerializer<MoldCastingRecipe> getSerializer() {
        return PFRecipeTypes.MOLD_CASTING_SERIALIZER.get();
    }

    @Override
    public RecipeType<MoldCastingRecipe> getType() {
        return PFRecipeTypes.MOLD_CASTING.get();
    }
}
