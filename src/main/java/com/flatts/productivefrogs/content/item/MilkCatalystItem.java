package com.flatts.productivefrogs.content.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A Slime Milk catalyst item. Behaviourally a plain {@link Item} - the upgrade
 * logic lives in {@code SlimeMilkSourceBlock}/{@code SlimeMilkSourceBlockEntity},
 * keyed off {@link MilkCatalyst} - but it carries two hover tooltip lines so the
 * drop-into-the-pool interaction is discoverable without JEI:
 *
 * <ul>
 *   <li>a per-catalyst effect line (e.g. "More slimes before it runs dry")</li>
 *   <li>a shared "Drop into a Slime Milk source to apply" line</li>
 * </ul>
 *
 * Both read from lang keys {@code tooltip.productivefrogs.catalyst.<type>} and
 * {@code tooltip.productivefrogs.catalyst.apply}. See docs/slime_milk_catalysts.md.
 */
public class MilkCatalystItem extends Item {

    private final MilkCatalyst catalyst;

    public MilkCatalystItem(MilkCatalyst catalyst, Properties properties) {
        super(properties);
        this.catalyst = catalyst;
    }

    public MilkCatalyst getCatalyst() {
        return catalyst;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(
            "tooltip.productivefrogs.catalyst." + catalyst.name().toLowerCase(java.util.Locale.ROOT))
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.productivefrogs.catalyst.apply")
            .withStyle(ChatFormatting.DARK_GRAY));
    }
}
