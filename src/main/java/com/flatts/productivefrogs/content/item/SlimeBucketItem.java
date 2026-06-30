package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.data.Category;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.DispensibleContainerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Bucket variant for {@link com.flatts.productivefrogs.content.entity.ResourceSlime}.
 * Inherits everything functional from {@link MobBucketItem} (capture/release
 * sound, fluid placement, NBT round-trip via {@code ResourceSlime#saveToBucketTag});
 * the only addition is a per-variant / per-category display name pulled from
 * the bucket's {@code BUCKET_ENTITY_DATA} payload.
 *
 * <p>Name resolution mirrors the tint pipeline in
 * {@code BucketedCategoryTint}:
 *
 * <ol>
 *   <li><b>Variant</b> — read the {@code Variant} id from the bucket NBT via
 *       {@link ResourceTadpoleBucketItem#readVariant} (works on slime buckets
 *       too — same NBT layout). Falls through to the
 *       {@code item.productivefrogs.slime_bucket.<variant_path>} translation
 *       key (e.g. {@code .iron}, {@code .copper}). Most specific match — a
 *       captured iron Resource Slime reads "Bucket of Iron Slime".</li>
 *   <li><b>Category</b> — fall back to the broader category via
 *       {@link ResourceTadpoleBucketItem#readCategory}. Catches the rare
 *       category-only slime (no variant assigned) plus the
 *       {@code BUCKET_ENTITY_DATA}-without-variant edge cases. Translation
 *       key {@code .<category_id>}.</li>
 *   <li><b>Default</b> — empty bucket / pre-component save data. Falls
 *       through to the base translation
 *       {@code item.productivefrogs.slime_bucket}.</li>
 * </ol>
 *
 * <p>The lang file ({@code assets/productivefrogs/lang/en_us.json}) ships a
 * translation for the base key + every category + every shipped variant; a
 * variant that lacks a translation will surface as the raw key (Minecraft's
 * standard fallback), which is the visible failure mode that prompts a
 * lang-entry addition.
 *
 * <p>Why a custom class: vanilla {@link MobBucketItem#getName(ItemStack)} just
 * returns the base description id. To distinguish the 12 stamped subtypes in
 * JEI search and tooltips, we need the per-stack lookup. Mirrors what
 * {@link ResourceTadpoleBucketItem} already does for tadpole buckets.
 */
public final class SlimeBucketItem extends MobBucketItem {

    public SlimeBucketItem(EntityType<? extends Mob> type, Fluid fluid,
                           SoundEvent emptySound, Properties properties) {
        super(type, fluid, emptySound, properties);
    }

    /**
     * Release the slime <b>without</b> placing a fluid. A captured Resource Slime is a
     * land mob, but {@link MobBucketItem} inherits the fish-bucket behaviour of dumping
     * its {@code content} fluid (water) when emptied. We keep {@code content = WATER}
     * only so {@code BucketItem#use} routes into the place branch; this override then
     * skips the fluid placement and just plays the empty sound, so {@code use} still
     * proceeds to {@link #checkExtraContent} (which spawns the slime via
     * {@code MobBucketItem#spawn}). Net effect: slime out, no water.
     *
     * <p><b>Why the 5-arg signature on 1.21.1 NeoForge:</b> NeoForge patches
     * {@code BucketItem} to add an extra {@code ItemStack} parameter on
     * {@code emptyContents}; {@code BucketItem#use} invokes that 5-arg overload
     * directly (the player right-click path), and the inherited 4-arg method
     * just delegates to it with a {@code null} stack. Overriding the 4-arg
     * alone leaves the player path on vanilla's 5-arg implementation, which
     * places water - so we override the 5-arg here. The 4-arg call sites
     * (notably {@link DispensibleContainerItem} dispatched from our dispenser
     * behaviour) still flow through this override via vanilla's delegation.
     */
    @Override
    public boolean emptyContents(@Nullable LivingEntity user, Level level, BlockPos pos,
                                 @Nullable BlockHitResult result, @Nullable ItemStack container) {
        this.playEmptySound(user, level, pos);
        return true;
    }

    /**
     * Make a dispenser release the captured slime (no water), mirroring vanilla's
     * fish-bucket dispense behaviour minus the fluid. Registered on common setup
     * via {@code enqueueWork} (the dispenser registry isn't thread-safe). When the
     * block in front can't hold a slime, falls back to ejecting the bucket so we
     * don't shove a slime into a wall - matching how vanilla fish buckets behave
     * when their target isn't placeable.
     */
    public static void registerDispenseBehavior() {
        DispenserBlock.registerBehavior(PFItems.SLIME_BUCKET.get(), new DefaultDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack stack) {
                ServerLevel level = source.level();
                BlockPos target = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
                if (!level.getBlockState(target).canBeReplaced()) {
                    return super.execute(source, stack);
                }
                DispensibleContainerItem bucket = (DispensibleContainerItem) stack.getItem();
                if (bucket.emptyContents(null, level, target, null)) {
                    bucket.checkExtraContent(null, level, stack, target);
                    return new ItemStack(Items.BUCKET);
                }
                return super.execute(source, stack);
            }
        });
    }

    /**
     * Mint a captured-slime bucket for a variant <b>without</b> a live entity -
     * the Slime Churn produces these. Delegates to
     * {@link PFItems#variantSlimeBucket} (the single writer of the minimal
     * {@code Category}+{@code Variant} bucket NBT shape, shared with the
     * creative tab and JEI) so churned buckets are component-identical to
     * every other display/capture stack. Release runs the normal
     * {@code loadFromBucketTag} path (size forced to 1, marked
     * bucket-originated), so a churned bucket and a hand-captured bucket are
     * interchangeable; the standard mob-bucket keys (Health, NoAI, ...) are
     * deliberately absent - {@code Bucketable.loadDefaultDataFromBucketTag}
     * treats each as optional.
     */
    public static ItemStack forVariant(Category category, Identifier variantId) {
        return PFItems.variantSlimeBucket(variantId, category);
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier variantId = ResourceTadpoleBucketItem.readVariant(stack);
        if (variantId != null) {
            // Built-in variants ship an explicit key; a datapack-added variant
            // (no lang) falls back to a title-cased name, so it reads cleanly
            // without a resource pack - matching the milk bucket / froglight.
            return Component.translatableWithFallback(
                "item.productivefrogs.slime_bucket." + variantId.getPath(),
                "Bucket of " + com.flatts.productivefrogs.util.VariantNames.titleCase(variantId) + " Slime");
        }
        Category category = ResourceTadpoleBucketItem.readCategory(stack);
        if (category != null) {
            return Component.translatable(
                "item.productivefrogs.slime_bucket." + category.id());
        }
        return Component.translatable(getDescriptionId());
    }
}
