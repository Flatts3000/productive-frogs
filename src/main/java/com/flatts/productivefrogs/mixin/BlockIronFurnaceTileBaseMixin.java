package com.flatts.productivefrogs.mixin;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.compat.ironfurnaces.FactoryAutoSplit;
import com.flatts.productivefrogs.compat.ironfurnaces.RecipeCacheFix;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Iron Furnaces' component-blind factory auto-split with the
 * component-aware {@link FactoryAutoSplit}.
 *
 * <p>Why a mixin and not a pack fix: the corruption happens inside
 * {@code BlockIronFurnaceTileBase.split}, which no datapack, recipe or config
 * can reach. Iron Furnaces exposes no toggle for auto-split, and the only
 * pack-side lever is removing the Factory augment outright, which costs the
 * feature rather than fixing it. See {@link FactoryAutoSplit} for the defect
 * itself and the upstream references.
 *
 * <p>{@code split} is the sole entry point into that code (its two helpers,
 * {@code fillEmptySlots} and {@code getSplitCounts}, have no other callers),
 * and it is called from exactly two places, both inside the
 * {@code isFactory()} branch of the block entity tick. Cancelling at HEAD
 * therefore substitutes the whole behaviour cleanly rather than leaving half
 * of the original running.
 *
 * <p>The target is named by string so the mod needs no compile-time
 * dependency on Iron Furnaces, and it is gated twice: {@code PFMixinPlugin}
 * declines to apply the mixin at all when {@code ironfurnaces} is not loaded,
 * and {@code compat.ironFurnacesAutoSplitFix} lets an operator hand the
 * behaviour back to Iron Furnaces if it ever ships its own fix before we drop
 * this. Neither the injector ({@code require = 0}) nor the mixin config
 * ({@code "required": false}) is fatal on failure, and both matter:
 * {@code require} covers only the {@code @Inject}, while the {@code @Shadow}
 * below is resolved at apply time and would crash a required config. Should a
 * future Iron Furnaces reshape {@code split} or rename {@code FACTORY_INPUT},
 * the patch quietly stops applying instead of taking the game down, which is
 * the right failure direction for a courtesy patch on someone else's code.
 * That trade means a version bump on their side must be re-verified here
 * rather than assumed.
 */
@Mixin(targets = "ironfurnaces.tileentity.furnaces.BlockIronFurnaceTileBase", remap = false)
public abstract class BlockIronFurnaceTileBaseMixin {

    /** {@code {7, 8, 9, 10, 11, 12}} - shadowed rather than copied so a slot-layout change cannot silently corrupt. */
    @Shadow
    @Final
    public static int[] FACTORY_INPUT;

    /** The furnace's current mode. Vanilla type, public field, so shadowing costs no Iron Furnaces surface. */
    @Shadow
    public RecipeType<? extends AbstractCookingRecipe> recipeType;

    @Inject(method = "split(ZII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void productivefrogs$componentAwareAutoSplit(boolean fullCheck, int start, int size, CallbackInfo ci) {
        if (!PFConfig.IRON_FURNACES_AUTOSPLIT_FIX.get()) {
            return;
        }
        FactoryAutoSplit.split((Container) (Object) this, FACTORY_INPUT, fullCheck, start, size);
        ci.cancel();
    }

    /**
     * Answers "can this be smelted?" without consulting Iron Furnaces'
     * {@code Map<Item, Boolean>} cache, for stacks whose smeltability depends on
     * their components rather than their id.
     *
     * <p>Only component-carrying stacks are intercepted. Everything else falls
     * through to the original cached path, so the optimisation the cache exists
     * for is preserved and plain items are untouched.
     *
     * <p>Without this, one variant-less Froglight tested in a furnace caches
     * {@code false} against the Froglight item and locks every variant out of
     * every Iron Furnace for the rest of the session. See
     * {@link RecipeCacheFix} for the full account.
     */
    @Inject(method = "hasRecipe(Lnet/minecraft/world/item/ItemStack;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void productivefrogs$componentAwareHasRecipe(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!PFConfig.IRON_FURNACES_RECIPE_CACHE_FIX.get() || !RecipeCacheFix.needsComponentAwareCheck(stack)) {
            return;
        }
        cir.setReturnValue(RecipeCacheFix.hasRecipe(((BlockEntity) (Object) this).getLevel(), recipeType, stack));
    }
}
