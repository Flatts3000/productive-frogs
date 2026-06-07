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
}
