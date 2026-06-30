package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.content.menu.IncubatorMenu;
import com.flatts.productivefrogs.data.Category;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;

/**
 * Status screen for the Incubator. A category-tinted growth bar with the percent
 * overlaid, the incubating species (with a colour swatch), the time remaining, and
 * a hint when empty. Procedural panel - no GUI sheet.
 */
public class IncubatorScreen extends PFContainerScreen<IncubatorMenu> {

    private static final int PANEL = 0xFFC6C6C6;
    private static final int BEVEL_LIGHT = 0xFFFFFFFF;
    private static final int BEVEL_DARK = 0xFF555555;
    private static final int BAR_BORDER = 0xFF373737;
    private static final int BAR_TRACK = 0xFF8B8B8B;
    private static final int BAR_WAITING = 0xFFD9BE73; // soft honey: matured, held at cap
    private static final int SWATCH_BORDER = 0xFF2B2B2B;
    // 26.1 GuiGraphicsExtractor.text() skips drawing when the alpha channel is 0
    // (the old GuiGraphics.drawString auto-promoted alpha); carry full alpha here.
    private static final int TEXT = 0xFF404040;
    private static final int MATURED_TEXT = 0xFF6E5210; // dark goldenrod, readable on the grey panel
    private static final int EMPTY_TEXT = 0xFF707070;

    private static final int SWATCH_X = 9;
    private static final int SWATCH_Y = 19;
    private static final int SWATCH = 9;
    private static final int BAR_X = 9;
    private static final int BAR_Y = 38;
    private static final int BAR_W = 158;
    private static final int BAR_H = 14;

    public IncubatorScreen(IncubatorMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, 176, 92);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = this.leftPos;
        int y = this.topPos;
        gui.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL);
        gui.fill(x, y, x + this.imageWidth, y + 1, BEVEL_LIGHT);
        gui.fill(x, y, x + 1, y + this.imageHeight, BEVEL_LIGHT);
        gui.fill(x, y + this.imageHeight - 1, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);
        gui.fill(x + this.imageWidth - 1, y, x + this.imageWidth, y + this.imageHeight, BEVEL_DARK);

        int state = this.menu.state();
        Category cat = this.menu.incubatingCategory();

        // Species colour swatch (only while it holds a seed).
        if (cat != null) {
            int sx = x + SWATCH_X;
            int sy = y + SWATCH_Y;
            gui.fill(sx - 1, sy - 1, sx + SWATCH + 1, sy + SWATCH + 1, SWATCH_BORDER);
            gui.fill(sx, sy, sx + SWATCH, sy + SWATCH, 0xFF000000 | cat.tintRgb());
        }

        // Growth bar: recessed track + category-tinted fill (amber when held at cap).
        int bx = x + BAR_X;
        int by = y + BAR_Y;
        gui.fill(bx - 1, by - 1, bx + BAR_W + 1, by + BAR_H + 1, BAR_BORDER);
        gui.fill(bx, by, bx + BAR_W, by + BAR_H, BAR_TRACK);
        int total = this.menu.growthTotal();
        if (state == 1 && total > 0) {
            int done = total - this.menu.growthRemaining();
            int filled = Math.max(0, Math.min(BAR_W, done * BAR_W / total));
            int fill = cat != null ? (0xFF000000 | cat.tintRgb()) : BAR_WAITING;
            gui.fill(bx, by, bx + filled, by + BAR_H, fill);
        } else if (state == 2) {
            gui.fill(bx, by, bx + BAR_W, by + BAR_H, BAR_WAITING);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        gui.text(this.font, this.title, this.titleLabelX, this.titleLabelY, TEXT, false);
        int state = this.menu.state();

        // Status line (swatch is drawn in extractBackground; text sits beside it when seeded).
        int statusX = this.menu.incubatingCategory() != null ? SWATCH_X + SWATCH + 4 : SWATCH_X;
        Component status = switch (state) {
            case 1 -> Component.translatable("productivefrogs.gui.incubator.growing", speciesName());
            case 2 -> Component.translatable("productivefrogs.gui.incubator.waiting");
            default -> Component.translatable("productivefrogs.gui.incubator.empty");
        };
        int statusColor = switch (state) {
            case 1 -> TEXT;
            case 2 -> MATURED_TEXT;
            default -> EMPTY_TEXT;
        };
        gui.text(this.font, status, statusX, SWATCH_Y + 1, statusColor, false);

        int belowBar = BAR_Y + BAR_H + 4;
        if (state == 1 && this.menu.growthTotal() > 0) {
            // Percent overlaid centered on the bar.
            int done = this.menu.growthTotal() - this.menu.growthRemaining();
            int pct = Math.max(0, Math.min(100, done * 100 / this.menu.growthTotal()));
            gui.centeredText(this.font, pct + "%", BAR_X + BAR_W / 2, BAR_Y + 3, 0xFFFFFFFF);
            gui.text(this.font,
                Component.translatable("productivefrogs.gui.incubator.time", formatTime(this.menu.growthRemaining())),
                BAR_X, belowBar, TEXT, false);
            // Hint: feed a Sweetslime to hurry it along.
            drawWrapped(gui, Component.translatable("productivefrogs.gui.incubator.sweetslime_hint")
                .withStyle(ChatFormatting.DARK_GRAY), BAR_X, belowBar + 12);
        } else if (state == 2) {
            // Waiting at the cap: show the population so the hold is self-explanatory.
            gui.text(this.font,
                Component.translatable("productivefrogs.gui.incubator.population",
                    this.menu.frogCount(), this.menu.frogCap()),
                BAR_X, belowBar, TEXT, false);
        } else {
            // Empty: tell the player how to seed it (wrapped so it never overflows).
            drawWrapped(gui, Component.translatable("productivefrogs.gui.incubator.empty_hint")
                .withStyle(ChatFormatting.DARK_GRAY), BAR_X, belowBar);
        }
    }

    /** Draw a hint wrapped to the panel width so long / localized strings never overflow. */
    private void drawWrapped(GuiGraphicsExtractor gui, Component text, int x, int y) {
        int maxWidth = this.imageWidth - x - 8;
        for (FormattedCharSequence line : this.font.split(text, maxWidth)) {
            gui.text(this.font, line, x, y, TEXT, false);
            y += this.font.lineHeight;
        }
    }

    /** Display name of the species being incubated (e.g. "Cave Frog"); blank when empty. */
    private Component speciesName() {
        Category cat = this.menu.incubatingCategory();
        return cat == null
            ? Component.empty()
            : Component.translatable("entity.productivefrogs.resource_frog." + cat.name().toLowerCase(Locale.ROOT));
    }

    /** Ticks -> "m:ss" (20 ticks = 1 second). */
    private static String formatTime(int ticks) {
        int seconds = Math.max(0, ticks) / 20;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }
}
