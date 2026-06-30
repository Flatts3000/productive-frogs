package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.recipe.CrucibleMeltRecipe;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom recipe types. The first is {@code crucible_melting} - the Froglight
 * Crucible's datapack-driven Froglight -> fluid mapping (see
 * {@link CrucibleMeltRecipe}). Until v1.12 every PF recipe rode a vanilla type
 * (smelting / crafting) so this register didn't exist; keep it the home for
 * any future PF recipe type (the Casting Mold's solidify mapping lands here
 * in wave 2).
 */
public final class PFRecipeTypes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
        DeferredRegister.create(Registries.RECIPE_TYPE, ProductiveFrogs.MOD_ID);

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, ProductiveFrogs.MOD_ID);

    public static final Supplier<RecipeType<CrucibleMeltRecipe>> CRUCIBLE_MELTING =
        RECIPE_TYPES.register("crucible_melting", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "crucible_melting")));

    public static final Supplier<RecipeSerializer<CrucibleMeltRecipe>> CRUCIBLE_MELTING_SERIALIZER =
        RECIPE_SERIALIZERS.register("crucible_melting",
            () -> new RecipeSerializer<>(CrucibleMeltRecipe.CODEC, CrucibleMeltRecipe.STREAM_CODEC));

    public static final Supplier<RecipeType<com.flatts.productivefrogs.content.recipe.MoldCastingRecipe>> MOLD_CASTING =
        RECIPE_TYPES.register("mold_casting", () -> RecipeType.simple(
            Identifier.fromNamespaceAndPath(ProductiveFrogs.MOD_ID, "mold_casting")));

    public static final Supplier<RecipeSerializer<com.flatts.productivefrogs.content.recipe.MoldCastingRecipe>> MOLD_CASTING_SERIALIZER =
        RECIPE_SERIALIZERS.register("mold_casting",
            () -> new RecipeSerializer<>(
                com.flatts.productivefrogs.content.recipe.MoldCastingRecipe.CODEC,
                com.flatts.productivefrogs.content.recipe.MoldCastingRecipe.STREAM_CODEC));

    private PFRecipeTypes() {
        // utility class
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
