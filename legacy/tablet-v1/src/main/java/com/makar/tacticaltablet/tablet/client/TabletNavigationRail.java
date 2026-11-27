package com.makar.tacticaltablet.tablet.client;

import com.makar.tacticaltablet.tablet.client.ui.TacticalTheme;
import com.makar.tacticaltablet.tablet.client.ui.TacticalUi;
import com.makar.tacticaltablet.tablet.client.ui.widget.FocusKeyProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Focusable, narrated page navigation that preserves the tablet resource-pack textures. */
public final class TabletNavigationRail {
    public static final int WIDTH = 72;
    public static final int HEIGHT = 192;
    public static final int BUTTON_HEIGHT = 28;
    public static final int BUTTON_GAP = 5;
    private static final int VISIBLE_BUTTONS = 5;

    private final List<Item> items;
    private final IntConsumer onSelect;
    private final Runnable onHover;
    private final List<NavigationButton> buttons = new ArrayList<>();
    private int x;
    private int y;
    private int selectedIndex;
    private int scroll;

    public TabletNavigationRail(List<Item> items, IntConsumer onSelect, Runnable onHover) {
        this.items = List.copyOf(Objects.requireNonNull(items, "items"));
        this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
        this.onHover = Objects.requireNonNull(onHover, "onHover");
    }

    public void initialize(int x, int y, Consumer<Button> widgetRegistrar) {
        this.x = x;
        this.y = y;
        buttons.clear();
        for (int index = 0; index < items.size(); index++) {
            NavigationButton button = new NavigationButton(index, items.get(index));
            buttons.add(button);
            widgetRegistrar.accept(button);
        }
        layoutButtons();
    }

    public void setSelectedIndex(int selectedIndex) {
        if (items.isEmpty()) {
            this.selectedIndex = 0;
            return;
        }
        this.selectedIndex = Math.max(0, Math.min(items.size() - 1, selectedIndex));
        reveal(this.selectedIndex);
    }

    public Button selectedButton() {
        return buttons.isEmpty() ? null : buttons.get(selectedIndex);
    }

    public Button moveFocus(int keyCode, GuiEventListener focused) {
        if (!(focused instanceof NavigationButton current)) return null;
        int delta = switch (keyCode) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_RIGHT -> 1;
            default -> 0;
        };
        if (delta == 0 || buttons.isEmpty()) return null;
        int next = Math.floorMod(current.index + delta, buttons.size());
        reveal(next);
        return buttons.get(next);
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        for (NavigationButton button : buttons) {
            if (button.visible) button.render(graphics, mouseX, mouseY, partialTick);
        }
        if (scroll > 0) {
            graphics.drawCenteredString(Minecraft.getInstance().font, "\u25b2",
                    x + WIDTH / 2, y - 8, TacticalTheme.TEXT_SECONDARY);
        }
        if (scroll + VISIBLE_BUTTONS < items.size()) {
            graphics.drawCenteredString(Minecraft.getInstance().font, "\u25bc",
                    x + WIDTH / 2, y + HEIGHT - 8, TacticalTheme.TEXT_SECONDARY);
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!inside(mouseX, mouseY, x, y, WIDTH, HEIGHT) || items.size() <= VISIBLE_BUTTONS || delta == 0.0D) {
            return false;
        }
        int before = scroll;
        scroll += delta > 0.0D ? -1 : 1;
        clampScroll();
        layoutButtons();
        return before != scroll;
    }

    private void select(int index) {
        if (index != selectedIndex) onSelect.accept(index);
    }

    private void reveal(int index) {
        if (index < scroll) scroll = index;
        if (index >= scroll + VISIBLE_BUTTONS) scroll = index - VISIBLE_BUTTONS + 1;
        clampScroll();
        layoutButtons();
    }

    private void layoutButtons() {
        for (NavigationButton button : buttons) {
            int visibleRow = button.index - scroll;
            button.setX(x);
            button.setY(y + visibleRow * (BUTTON_HEIGHT + BUTTON_GAP));
            button.visible = visibleRow >= 0 && visibleRow < VISIBLE_BUTTONS;
        }
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(Math.max(0, items.size() - VISIBLE_BUTTONS), scroll));
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public record Item(String label, ButtonTextureSet textures) {
        public Item {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(textures, "textures");
        }
    }

    private final class NavigationButton extends Button implements FocusKeyProvider {
        private final int index;
        private final Item item;
        private boolean wasHovered;

        private NavigationButton(int index, Item item) {
            super(Button.builder(Component.literal(item.label()), ignored -> select(index))
                    .bounds(0, 0, WIDTH, BUTTON_HEIGHT));
            this.index = index;
            this.item = item;
        }

        @Override
        public String focusKey() {
            return "tablet.navigation." + index;
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isMouseOver(mouseX, mouseY);
            if (hovered && !wasHovered && index != selectedIndex) onHover.run();
            wasHovered = hovered;
            boolean selected = selectedIndex == index;
            ButtonTextureSpec texture = item.textures().select(active, selected, hovered || isFocused());
            GuiTextureRenderer.blitWithAlpha(graphics, texture, getX(), getY(), width, height);
            int color = selected || hovered || isFocused() ? TacticalTheme.TEXT_PRIMARY : TacticalTheme.TEXT_SECONDARY;
            graphics.drawCenteredString(Minecraft.getInstance().font, item.label(),
                    getX() + width / 2, getY() + 10, color);
            if (isFocused()) {
                TacticalUi.drawFocusRing(graphics, getX(), getY(), width, height, TacticalTheme.ACCENT);
            }
        }
    }
}
