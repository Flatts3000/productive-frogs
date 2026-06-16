package com.flatts.productivefrogs.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Shared base for the mod's container screens, extracted (per the standing
 * backlog note) when the Casting Mold became the third GUI: on 1.21.1
 * NeoForge, {@link AbstractContainerScreen#render} draws the background,
 * slots, and labels but does NOT render item tooltips - a renderBg-only
 * screen silently shows none on slot hover. This base adds the tooltip pass
 * once; {@code super.render} does not draw it, so there is no double-draw.
 */
public abstract class PFContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected PFContainerScreen(T menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        this.renderTooltip(gui, mouseX, mouseY);
    }

    /**
     * Draw a vanilla-style recessed slot frame so a slot reads as a slot even
     * when the (placeholder) background texture has no frame baked at that
     * position. {@code (x, y)} is the slot's item top-left (the 16x16 cell); the
     * 18x18 bevel is drawn one pixel out. Beveled like a vanilla container slot:
     * dark top/left, light bottom/right, mid-gray inner.
     */
    protected static void drawSlotFrame(GuiGraphics gui, int x, int y) {
        int x0 = x - 1;
        int y0 = y - 1;
        gui.fill(x0, y0, x0 + 18, y0 + 18, 0xFF373737);
        gui.fill(x0 + 1, y0 + 1, x0 + 18, y0 + 18, 0xFFFFFFFF);
        gui.fill(x0 + 1, y0 + 1, x0 + 17, y0 + 17, 0xFF8B8B8B);
    }
}
