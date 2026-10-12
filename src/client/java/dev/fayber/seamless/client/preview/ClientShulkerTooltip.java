package dev.fayber.seamless.client.preview;

import dev.fayber.seamless.preview.PreviewLayout;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Renders a {@link ShulkerTooltip} below the text lines of an ordinary item tooltip. */
public final class ClientShulkerTooltip implements ClientTooltipComponent {
    private static final int GAP = 2;
    private final ShulkerTooltip tooltip;
    private final List<PreviewLayout.Merged<ItemStack>> merged;

    public ClientShulkerTooltip(ShulkerTooltip tooltip) {
        this.tooltip = tooltip;
        this.merged = tooltip.full() ? List.of() : PanelRenderer.merge(tooltip.contents());
    }

    private int columns() {
        return tooltip.full() ? PreviewLayout.FULL_COLUMNS : PreviewLayout.compactColumns(merged.size());
    }

    private int rows() {
        return tooltip.full() ? PreviewLayout.FULL_ROWS : PreviewLayout.compactRows(merged.size());
    }

    @Override
    public int getHeight(Font font) {
        return PreviewLayout.panelHeight(rows()) + GAP * 2;
    }

    @Override
    public int getWidth(Font font) {
        return PreviewLayout.panelWidth(columns());
    }

    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        if (tooltip.full()) {
            PanelRenderer.fullGrid(graphics, font, tooltip.contents(), x, y + GAP, tooltip.tint(), -1);
        } else {
            PanelRenderer.compactGrid(graphics, font, merged, x, y + GAP, tooltip.tint(), -1);
        }
    }
}
