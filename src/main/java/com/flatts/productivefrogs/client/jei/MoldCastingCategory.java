package com.flatts.productivefrogs.client.jei;

import com.flatts.productivefrogs.content.block.entity.CastingMoldBlockEntity;
import com.flatts.productivefrogs.content.recipe.MoldCastingRecipe;
import com.flatts.productivefrogs.registry.PFBlocks;
import com.flatts.productivefrogs.registry.PFRecipeTypes;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * JEI category for the Casting Mold's solidify recipes: a sized molten fluid
 * (90 mB for the ingot roster) -> the cast item. The input slot cycles every
 * fluid in the recipe's {@code c:molten_<metal>} tag, so on an AllTheOres
 * pack the slot shows ATO's molten and on a lean pack it shows PF's own -
 * and {@code U} on an ingot surfaces the Mold as a source. Recipes ride the
 * {@code productivefrogs:mold_casting} recipe type via the RecipeManager, so
 * datapack additions (nugget/block casts, pack tweaks) display with no code
 * change - the inverse direction of {@link CrucibleMeltCategory}.
 */
public final class MoldCastingCategory implements IRecipeCategory<RecipeHolder<MoldCastingRecipe>> {

    public static final RecipeType<RecipeHolder<MoldCastingRecipe>> TYPE =
        RecipeType.createFromVanilla(PFRecipeTypes.MOLD_CASTING.get());

    private static final int WIDTH = 96;
    private static final int HEIGHT = 26;
    private static final int INPUT_X = 1;
    private static final int OUTPUT_X = 75;
    private static final int SLOT_Y = 4;
    private static final int ARROW_X = 30;
    private static final int ARROW_Y = 4;

    private final IDrawable icon;
    private final IDrawableAnimated arrow;

    public MoldCastingCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(PFBlocks.CASTING_MOLD.get().asItem()));
        this.arrow = guiHelper.createAnimatedRecipeArrow(CastingMoldBlockEntity.CAST_TIME);
    }

    @Override
    public RecipeType<RecipeHolder<MoldCastingRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("productivefrogs.jei.category.mold_casting");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<MoldCastingRecipe> holder, IFocusGroup focuses) {
        MoldCastingRecipe recipe = holder.value();
        // Resolve the fluid ingredient's tag to concrete stacks carrying the
        // recipe's mB, so the slot cycles exactly what this environment can
        // cast. A tag with no members renders a blank slot (JEI tolerates an
        // empty ingredient list) - unreachable for the shipped roster, whose
        // gated recipes only load alongside their providers.
        int amount = recipe.fluid().amount();
        List<FluidStack> fluids = recipe.fluid().ingredient().fluids().stream()
            .map(fluid -> new FluidStack(fluid, amount))
            .toList();
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X, SLOT_Y)
            .setStandardSlotBackground()
            .addIngredients(NeoForgeTypes.FLUID_STACK, fluids)
            .setFluidRenderer(amount, false, 16, 16);
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X, SLOT_Y)
            .setOutputSlotBackground()
            .addItemStack(recipe.result().copy());
    }

    @Override
    public void draw(RecipeHolder<MoldCastingRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor gui, double mouseX, double mouseY) {
        arrow.draw(gui, ARROW_X, ARROW_Y);
    }

    @Override
    public Identifier getRegistryName(RecipeHolder<MoldCastingRecipe> holder) {
        return holder.id().identifier();
    }
}
