package com.flatts.productivefrogs.registry;

import com.flatts.productivefrogs.items.FrogNetItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class ItemRegistry {
    public static final FrogNetItem FROG_NET = new FrogNetItem(new Item.Settings().group(ItemGroup.REDSTONE));

    public static void registerItems() {
        Registry.register(Registry.ITEM, new Identifier("productivefrogs", "frog_net"), FROG_NET);
    }
}