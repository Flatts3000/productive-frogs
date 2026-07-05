package com.flatts.productivefrogs.content.item;

import com.flatts.productivefrogs.content.block.entity.MimicMilkSourceBlockEntity;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

/**
 * Bucket for Mimic Milk (#253). A standard fluid {@link BucketItem} placing the
 * single Mimic Milk source block; it carries the synthesized item id as a
 * top-level {@link PFDataComponents#SYNTHESIZED_ITEM} component (runtime tint +
 * name), and on placement writes that id onto the placed
 * {@link MimicMilkSourceBlockEntity} via {@link #checkExtraContent} - mirroring
 * how {@code SlimeMilkBucketItem} restores upgrades. Produced by the Milker
 * (extended in a later slice) and by re-bucketing a placed source.
 */
public final class MimicMilkBucketItem extends BucketItem {

    public MimicMilkBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    /** Mint a Mimic Milk bucket carrying {@code itemId} (the Milker / re-bucket writer). */
    public static ItemStack forItem(Identifier itemId) {
        ItemStack stack = new ItemStack(PFItems.MIMIC_MILK_BUCKET.get());
        stack.set(PFDataComponents.SYNTHESIZED_ITEM.get(), itemId);
        return stack;
    }

    @Override
    public void checkExtraContent(@Nullable LivingEntity user, Level level, ItemStack stack, BlockPos pos) {
        super.checkExtraContent(user, level, stack, pos);
        Identifier itemId = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
        if (itemId != null && level.getBlockEntity(pos) instanceof MimicMilkSourceBlockEntity be) {
            be.setSynthesizedItem(itemId); // seeds the default budget on first placement
            // Restore any stamped budget/catalyst upgrades over those defaults so a
            // re-placed buffed source keeps its state (mirrors SlimeMilkBucketItem).
            Integer remaining = stack.get(PFDataComponents.SPAWNS_REMAINING.get());
            Integer capacity = stack.get(PFDataComponents.MILK_CAPACITY.get());
            Integer speed = stack.get(PFDataComponents.MILK_SPEED.get());
            Integer quantity = stack.get(PFDataComponents.MILK_QUANTITY.get());
            Boolean infinite = stack.get(PFDataComponents.MILK_INFINITE.get());
            if (remaining != null || capacity != null || speed != null || quantity != null
                    || Boolean.TRUE.equals(infinite)) {
                be.restoreUpgrades(
                    remaining != null ? remaining : be.getSpawnsRemaining(),
                    capacity != null ? capacity : be.getSpawnsCapacity(),
                    speed != null ? speed : 0,
                    quantity != null ? quantity : 0,
                    Boolean.TRUE.equals(infinite));
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        Identifier itemId = stack.get(PFDataComponents.SYNTHESIZED_ITEM.get());
        if (itemId != null) {
            net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            Component itemName = item != null
                ? Component.translatable(item.getDescriptionId())
                : Component.literal(itemId.toString());
            return Component.translatable("item.productivefrogs.mimic_slime_milk_bucket.item", itemName);
        }
        return Component.translatable(getDescriptionId());
    }
}
