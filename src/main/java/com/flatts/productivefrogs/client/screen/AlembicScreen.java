package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.content.menu.AlembicMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * Client screen for the Alembic (#253): an RF energy bar, the synthesis-progress
 * arrow (item -> output), the bucket/item/output slots, and the player grid.
 * Reuses the Casting Mold GUI sheet as placeholder art (shared with the Distiller).
 */
public class AlembicScreen extends PFContainerScreen<AlembicMenu> {

    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/casting_mold.png");

    // Align with the baked arrow outline in the placeholder background (x=79);
    // a mismatched X drew the progress fill offset from the outline.
    private static final int ARROW_BG_X = 79;
    private static final int ARROW_BG_Y = 34;
    private static final int ARROW_SRC_X = 176;
    private static final int ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 16;

    private static final int BAR_X = 26;
    private static final int BAR_Y = 17;
    private static final int BAR_WIDTH = 16;
    private static final int BAR_HEIGHT = 52;

    private static final int BAR_FILL = 0xFFFF6A00;
    private static final int BAR_EMPTY = 0xFF20100A;

    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    public AlembicScreen(AlembicMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        super.render(gui, mouseX, mouseY, partialTick);
        renderEnergyTooltip(gui, mouseX, mouseY);
    }

    private void renderEnergyTooltip(GuiGraphics gui, int mouseX, int mouseY) {
        int bx = (this.width - this.imageWidth) / 2 + BAR_X;
        int by = (this.height - this.imageHeight) / 2 + BAR_Y;
        if (mouseX < bx || mouseX >= bx + BAR_WIDTH || mouseY < by || mouseY >= by + BAR_HEIGHT) {
            return;
        }
        gui.renderComponentTooltip(this.font, java.util.List.of(
            Component.translatable("productivefrogs.gui.energy_amount",
                this.menu.getEnergy(), this.menu.getEnergyCapacity()).withStyle(ChatFormatting.GRAY)
        ), mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        int cap = this.menu.getEnergyCapacity();
        int filled = cap > 0 ? Math.min(BAR_HEIGHT, this.menu.getEnergy() * BAR_HEIGHT / cap) : 0;
        gui.fill(x + BAR_X, y + BAR_Y, x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, BAR_EMPTY);
        if (filled > 0) {
            gui.fill(x + BAR_X, y + BAR_Y + (BAR_HEIGHT - filled),
                     x + BAR_X + BAR_WIDTH, y + BAR_Y + BAR_HEIGHT, BAR_FILL);
        }

        // Slot frames for the two inputs only - the placeholder bg has no frame at
        // these vertical positions. The OUTPUT keeps the bg's own baked frame
        // (drawing one there too double-framed it).
        drawSlotFrame(gui, x + com.flatts.productivefrogs.content.menu.AlembicMenu.BUCKET_SLOT_X,
                           y + com.flatts.productivefrogs.content.menu.AlembicMenu.BUCKET_SLOT_Y);
        drawSlotFrame(gui, x + com.flatts.productivefrogs.content.menu.AlembicMenu.ITEM_SLOT_X,
                           y + com.flatts.productivefrogs.content.menu.AlembicMenu.ITEM_SLOT_Y);

        int progress = this.menu.getProgress();
        int total = this.menu.getProgressTotal();
        if (progress > 0 && total > 0) {
            int arrow = Math.min(ARROW_WIDTH, (progress * ARROW_WIDTH) / total);
            gui.blit(BACKGROUND,
                     x + ARROW_BG_X, y + ARROW_BG_Y,
                     ARROW_SRC_X, ARROW_SRC_Y,
                     arrow, ARROW_HEIGHT,
                     BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }
    }
}
