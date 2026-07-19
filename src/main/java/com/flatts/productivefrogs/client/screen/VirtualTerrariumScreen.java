package com.flatts.productivefrogs.client.screen;

import com.flatts.productivefrogs.PFConfig;
import com.flatts.productivefrogs.ProductiveFrogs;
import com.flatts.productivefrogs.client.color.Tints;
import com.flatts.productivefrogs.content.block.entity.VirtualTerrariumBlockEntity;
import com.flatts.productivefrogs.content.menu.VirtualTerrariumMenu;
import com.flatts.productivefrogs.content.multiblock.MilkCharge;
import com.flatts.productivefrogs.registry.PFDataComponents;
import com.flatts.productivefrogs.registry.PFFluids;
import com.flatts.productivefrogs.util.VariantNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Client screen for the Virtual Terrarium Processor: a bespoke void-tier panel
 * (176x180) laying out the RF meter, the feedstock fluid slot, the frog slot, the
 * 3x2 output grid, the output product tank, and the vertical upgrade column.
 *
 * <p>Live amounts (feedstock / product / energy / progress) ride the synced
 * {@code ContainerData}; fluid TYPES come off the client BlockEntity (the same
 * 26.1 split the Casting Mold uses). Left-clicking the feedstock slot with a
 * cursor-held bucket fills the tank via {@link VirtualTerrariumMenu#clickMenuButton}.
 */
public class VirtualTerrariumScreen extends PFContainerScreen<VirtualTerrariumMenu> {

    private static final Identifier BACKGROUND = Identifier.fromNamespaceAndPath(
        ProductiveFrogs.MOD_ID, "textures/gui/container/virtual_terrarium.png");

    private static final int PANEL_WIDTH = 176;
    private static final int PANEL_HEIGHT = 180;
    private static final int BG_TEX_WIDTH = 256;
    private static final int BG_TEX_HEIGHT = 256;

    private static final int RF_X = 8, RF_Y = 18, RF_W = 14, RF_H = 54;
    private static final int FEED_X = 26, FEED_Y = 18, FEED_SIZE = 16;
    private static final int TANK_X = 130, TANK_Y = 18, TANK_W = 16, TANK_H = 54;

    private static final int ARROW_X = 46, ARROW_Y = 31;
    private static final int ARROW_SRC_X = 176, ARROW_SRC_Y = 14;
    private static final int ARROW_WIDTH = 24, ARROW_HEIGHT = 16;

    public VirtualTerrariumScreen(VirtualTerrariumMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title, PANEL_WIDTH, PANEL_HEIGHT);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(gui, mouseX, mouseY, partialTick);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;
        gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, this.imageWidth, this.imageHeight,
                 BG_TEX_WIDTH, BG_TEX_HEIGHT);

        // RF meter.
        fillMeter(gui, x + RF_X, y + RF_Y, RF_W, RF_H,
            this.menu.getEnergyStored(), VirtualTerrariumBlockEntity.ENERGY_CAPACITY, 0xFFC0402A);
        // Feedstock fluid slot.
        fillMeter(gui, x + FEED_X, y + FEED_Y, FEED_SIZE, FEED_SIZE,
            this.menu.getFeedstockAmount(), VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY, feedstockColor());
        // Output product tank.
        fillMeter(gui, x + TANK_X, y + TANK_Y, TANK_W, TANK_H,
            this.menu.getProductAmount(), productCapacity(), productColor());

        int interval = this.menu.getInterval();
        int filled = interval > 0 ? Math.min(ARROW_WIDTH, (this.menu.getProgress() * ARROW_WIDTH) / interval) : 0;
        if (filled > 0) {
            gui.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND,
                     x + ARROW_X, y + ARROW_Y, (float) ARROW_SRC_X, (float) ARROW_SRC_Y,
                     filled, ARROW_HEIGHT, BG_TEX_WIDTH, BG_TEX_HEIGHT);
        }
    }

    /** Bottom-up flat-colour fill of a meter frame. */
    private static void fillMeter(GuiGraphicsExtractor gui, int mx, int my, int w, int h, int amount, int cap, int argb) {
        if (amount <= 0 || cap <= 0) {
            return;
        }
        int filled = Math.min(h, (int) ((long) amount * h / cap));
        if (filled > 0) {
            gui.fill(mx, my + h - filled, mx + w, my + h, argb);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int fx = (this.width - this.imageWidth) / 2 + FEED_X;
            int fy = (this.height - this.imageHeight) / 2 + FEED_Y;
            if (event.x() >= fx && event.x() < fx + FEED_SIZE && event.y() >= fy && event.y() < fy + FEED_SIZE
                    && VirtualTerrariumBlockEntity.isFeedstockBucket(this.menu.getCarried())
                    && this.minecraft != null && this.minecraft.gameMode != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, VirtualTerrariumMenu.FILL_FEEDSTOCK);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        super.extractLabels(gui, mouseX, mouseY);
        VirtualTerrariumBlockEntity.Status status = this.menu.getStatus();
        if (status == VirtualTerrariumBlockEntity.Status.PRODUCING) {
            return;
        }
        Component text = Component.translatable(
            "productivefrogs.gui.vt.status." + status.name().toLowerCase(Locale.ROOT));
        int color = switch (status) {
            case MISMATCH -> 0xFFB0322A;      // red: the frog can't eat this feedstock
            case NEEDS_POWER -> 0xFFB07A20;   // amber
            default -> 0xFF707070;            // gray: just not set up yet
        };
        gui.text(this.font, text.getString(), 8, 74, color);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        super.extractTooltip(gui, mouseX, mouseY);
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        if (inside(mouseX, mouseY, x + FEED_X, y + FEED_Y, FEED_SIZE, FEED_SIZE)) {
            gui.setComponentTooltipForNextFrame(this.font, feedstockTooltip(), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + RF_X, y + RF_Y, RF_W, RF_H)) {
            gui.setComponentTooltipForNextFrame(this.font, List.of(
                Component.translatable("productivefrogs.gui.energy_amount",
                    this.menu.getEnergyStored(), VirtualTerrariumBlockEntity.ENERGY_CAPACITY)), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, x + TANK_X, y + TANK_Y, TANK_W, TANK_H)) {
            gui.setComponentTooltipForNextFrame(this.font, productTooltip(), mouseX, mouseY);
        }
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // -- fluid type / colour / tooltips (types off the client BE) --

    private FluidStack feedstock() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be == null ? FluidStack.EMPTY : be.getFeedstock().getFluid();
    }

    private int feedstockColor() {
        FluidStack fluid = feedstock();
        if (fluid.is(PFFluids.SLIME_MILK.get())) {
            Identifier variant = fluid.get(PFDataComponents.SLIME_VARIANT.get());
            if (variant != null && this.minecraft != null) {
                int argb = Tints.variantColor(this.minecraft.level, variant);
                if (argb != -1) {
                    return argb;   // the actual per-variant Slime Milk tint
                }
            }
            return 0xFFB8E0C0;
        }
        if (fluid.is(PFFluids.MIMIC_MILK.get())) {
            return 0xFFE0A8E0;
        }
        if (fluid.is(PFFluids.MOB_SLURRY.get())) {
            return 0xFF6A5A70;
        }
        return 0xFF8B8B8B;
    }

    private int productCapacity() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be == null ? VirtualTerrariumBlockEntity.XP_CAPACITY : be.productCapacity();
    }

    private int productColor() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        return be != null && be.productIsMolten() ? 0xFFE0742A : 0xFF66D060;
    }

    private List<Component> feedstockTooltip() {
        List<Component> lines = new ArrayList<>();
        FluidStack fluid = feedstock();
        if (fluid.isEmpty()) {
            lines.add(Component.translatable("productivefrogs.gui.fluid_empty"));
            return lines;
        }
        lines.add(feedstockName(fluid));   // variant-aware ("Iron Slime Milk")
        lines.add(Component.translatable("productivefrogs.gui.fluid_amount",
            this.menu.getFeedstockAmount(), VirtualTerrariumBlockEntity.FEEDSTOCK_CAPACITY)
            .withStyle(ChatFormatting.GRAY));
        // The milk's stamped stats (matches the bucket / milk-source readout).
        MilkCharge charge = MilkCharge.fromFluid(fluid);
        if (charge.infinite()) {
            lines.add(Component.translatable("productivefrogs.jade.spawns_unlimited").withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("productivefrogs.jade.spawns_left",
                charge.spawnsRemaining(), charge.capacity()).withStyle(ChatFormatting.GRAY));
        }
        if (charge.speed() > 0) {
            lines.add(Component.translatable("productivefrogs.jade.catalyst_speed",
                charge.speed(), PFConfig.catalystMaxSpeedLevel()).withStyle(ChatFormatting.GRAY));
        }
        if (charge.quantity() > 0) {
            lines.add(Component.translatable("productivefrogs.jade.catalyst_quantity",
                charge.quantity(), PFConfig.catalystMaxQuantityLevel()).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    /** Variant-aware feedstock name ("Iron Slime Milk"); falls back to the fluid's hover name. */
    private static Component feedstockName(FluidStack fluid) {
        if (fluid.is(PFFluids.SLIME_MILK.get())) {
            Identifier variant = fluid.get(PFDataComponents.SLIME_VARIANT.get());
            if (variant != null) {
                return Component.translatable("item.productivefrogs.slime_milk_bucket.item",
                    VariantNames.titleCase(variant));
            }
        }
        return fluid.getHoverName();
    }

    private List<Component> productTooltip() {
        VirtualTerrariumBlockEntity be = this.menu.blockEntity();
        FluidStack fluid = be == null ? FluidStack.EMPTY : be.activeProductTank().getFluid();
        List<Component> lines = new ArrayList<>();
        if (fluid.isEmpty()) {
            lines.add(Component.translatable("productivefrogs.gui.fluid_empty"));
        } else {
            lines.add(fluid.getHoverName());
            lines.add(Component.translatable("productivefrogs.gui.fluid_amount",
                this.menu.getProductAmount(), productCapacity()).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
