package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.menu.IncubatorMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Status screen for the Incubator: a growth-progress bar and a one-line state
 * (empty / incubating / waiting for space). Procedural panel - no GUI sheet.
 */
public class IncubatorScreen extends PFContainerScreen<IncubatorMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int BAR_BG = 0xFF373737;
    private static final int BAR_FILL = 0xFF8FCB5A; // slime green

    private static final int BAR_X = 18;
    private static final int BAR_Y = 44;
    private static final int BAR_W = 140;
    private static final int BAR_H = 10;

    public IncubatorScreen(IncubatorMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 72;
    }

    @Override
    protected void renderBg(GuiGraphics gui, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        gui.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        gui.fill(x, y, x + this.imageWidth, y + 1, BEVEL_LIGHT);
        gui.fill(x, y, x + 1, y + this.imageHeight, BEVEL_LIGHT);
        gui.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);
        gui.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);

        // Progress bar (only meaningful while growing).
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        gui.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, BAR_BG);
        int state = this.menu.state();
        int total = this.menu.growthTotal();
        if (state == 1 && total > 0) {
            int done = total - this.menu.growthRemaining();
            int filled = Math.max(0, Math.min(BAR_W, done * BAR_W / total));
            gui.fill(bx, by, bx + filled, by + BAR_H, BAR_FILL);
        } else if (state == 2) {
            gui.fill(bx, by, bx + BAR_W, by + BAR_H, BAR_FILL); // full, held
        }
    }

    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        Component status = switch (this.menu.state()) {
            case 1 -> Component.translatable("productivefrogs.gui.incubator.growing");
            case 2 -> Component.translatable("productivefrogs.gui.incubator.waiting")
                .withStyle(ChatFormatting.DARK_RED);
            default -> Component.translatable("productivefrogs.gui.incubator.empty")
                .withStyle(ChatFormatting.DARK_GRAY);
        };
        gui.drawString(this.font, status, 18, 26, 0x404040, false);
    }
}
