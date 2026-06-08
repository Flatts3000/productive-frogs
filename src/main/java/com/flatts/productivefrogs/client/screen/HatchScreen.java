package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.menu.HatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Hatch's 18-slot froglight output. Rendered as a vanilla
 * 2-row chest: the shared {@code generic_54.png} container texture, blitted as
 * the top (2 slot rows) + the player-inventory bottom, so the Hatch reads as an
 * ordinary chest the player takes Froglights out of (it is output-only - the
 * Terrarium fills it).
 */
public class HatchScreen extends PFContainerScreen<HatchMenu> {

    private static final ResourceLocation CONTAINER =
        ResourceLocation.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 2;

    public HatchScreen(HatchMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 114 + ROWS * 18; // vanilla generic-container height for 2 rows
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // Top: the title bar + ROWS slot rows.
        gui.blit(CONTAINER, x, y, 0, 0, this.imageWidth, ROWS * 18 + 17);
        // Bottom: the player-inventory section (source v=126, height 96).
        gui.blit(CONTAINER, x, y + ROWS * 18 + 17, 0, 126, this.imageWidth, 96);
    }
}
