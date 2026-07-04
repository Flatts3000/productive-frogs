package com.flatts.productivefrogs.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Shared base for the mod's container screens.
 *
 * <p>On 26.1 the GUI draws through the two-phase {@code GuiGraphicsExtractor}
 * pipeline: screens contribute their background in {@code extractBackground} and
 * the base {@link AbstractContainerScreen} renders slots, labels, and item
 * tooltips itself. The 1.21.1 {@code renderTooltip} workaround this base used to
 * carry is therefore gone - tooltips render via the base's {@code extractTooltip}
 * pass with no override needed.
 *
 * <p>{@code imageWidth}/{@code imageHeight} are now {@code final} on the base, so
 * a screen with a non-default panel size passes them through the 5-arg
 * constructor instead of assigning the fields.
 */
public abstract class PFContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected PFContainerScreen(T menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    protected PFContainerScreen(T menu, Inventory playerInv, Component title, int imageWidth, int imageHeight) {
        super(menu, playerInv, title, imageWidth, imageHeight);
    }

    /**
     * Draw a vanilla-style recessed slot frame so a slot reads as a slot even
     * when the (placeholder) background texture has no frame baked at that
     * position. {@code (x, y)} is the slot's item top-left (the 16x16 cell); the
     * 18x18 bevel is drawn one pixel out. Beveled like a vanilla container slot:
     * dark top/left, light bottom/right, mid-gray inner. Called from a screen's
     * {@code extractBackground}.
     */
    protected static void drawSlotFrame(GuiGraphicsExtractor gui, int x, int y) {
        int x0 = x - 1;
        int y0 = y - 1;
        gui.fill(x0, y0, x0 + 18, y0 + 18, 0xFF373737);
        gui.fill(x0 + 1, y0 + 1, x0 + 18, y0 + 18, 0xFFFFFFFF);
        gui.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, 0xFF8B8B8B);
    }
}
