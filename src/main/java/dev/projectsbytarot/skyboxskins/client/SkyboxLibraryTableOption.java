package dev.projectsbytarot.skyboxskins.client;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.elements.Dropdown;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

final class SkyboxLibraryTableOption extends BasicOption {
    private static final int ROW_HEIGHT = 28;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int MUTED_TEXT_COLOR = 0xFF9CA3AF;
    private static final int BUTTON_HOVER_COLOR = 0xFF495263;
    private static final int ACTIVE_COLOR = 0xFF356D50;
    private static final int REMOVE_COLOR = 0xFF82454B;

    private final SkyboxOneConfig config;
    private Dropdown globalDropdown;
    private String globalDropdownStateKey;

    SkyboxLibraryTableOption(
            Field field,
            SkyboxOneConfig config,
            String name,
            String description,
            String category,
            String subcategory,
            int size
    ) {
        super(field, config, name, description, category, subcategory, size);
        this.config = config;
    }

    @Override
    public void draw(long vg, int x, int y, InputHandler inputHandler) {
        NanoVGHelper nanoVG = NanoVGHelper.INSTANCE;
        List<SkyboxOneConfig.SkyboxEntry> skyboxes = config.getSkyboxes();
        Dropdown dropdown = getGlobalDropdown();
        boolean dropdownOpen = dropdown.isToggled()
                || inputHandler.isAreaClicked(x + 132, y + 24, 300, 32);

        nanoVG.drawText(vg, name, x, y + 16, nameColor, 14, Fonts.MEDIUM);
        nanoVG.drawText(vg, "GLOBAL SKYBOX", x, y + 44, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "SAVED IMAGES", x, y + 70, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "GLOBAL", x + 270, y + 70, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "REMOVE", x + 368, y + 70, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);

        if (skyboxes.isEmpty()) {
            nanoVG.drawText(vg, "No saved skyboxes. Use Add skybox image above to import one.",
                    x, y + 94, MUTED_TEXT_COLOR, 12, Fonts.REGULAR);
        }

        for (int i = 0; i < skyboxes.size(); i++) {
            SkyboxOneConfig.SkyboxEntry entry = skyboxes.get(i);
            if (entry == null || entry.id == null || entry.name == null) {
                continue;
            }

            int rowY = y + 74 + i * ROW_HEIGHT;
            String displayName = entry.name.length() > 31
                    ? entry.name.substring(0, 28) + "..."
                    : entry.name;
            nanoVG.drawText(vg, displayName, x, rowY + 16, TEXT_COLOR, 12, Fonts.REGULAR);

            boolean active = entry.id.equals(config.selectedSkyboxId);
            String status = active ? "ACTIVE" : "SAVED";
            nanoVG.drawCenteredText(vg, status, x + 305, rowY + 16,
                    active ? ACTIVE_COLOR : MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
            if (dropdownOpen) {
                drawButtonVisual(vg, inputHandler, x + 368, rowY, 64, "Remove", REMOVE_COLOR);
            } else if (drawButton(vg, inputHandler, x + 368, rowY, 64, "Remove", REMOVE_COLOR)) {
                SkyboxOneConfig.reportAction(SkyboxOneConfig.removeSkybox(entry.id));
                return;
            }
        }

        dropdown.draw(vg, x + 132, y + 24, inputHandler);
        drawDescription(vg, x, y, inputHandler);
    }

    @Override
    public int getHeight() {
        int size = config.getSkyboxes().size();
        return size == 0 ? 96 : 76 + ROW_HEIGHT * size;
    }

    private Dropdown getGlobalDropdown() {
        List<String> optionIds = new ArrayList<String>();
        List<String> optionLabels = new ArrayList<String>();
        optionIds.add("");
        optionLabels.add("Built-in");
        List<SkyboxOneConfig.SkyboxEntry> skyboxes = config.getSkyboxes();
        for (int i = 0; i < skyboxes.size(); i++) {
            SkyboxOneConfig.SkyboxEntry entry = skyboxes.get(i);
            if (entry == null || entry.id == null || entry.name == null) {
                continue;
            }
            optionIds.add(entry.id);
            optionLabels.add(formatSkyboxOption(i + 1, entry.name));
        }

        String activeId = SkyboxOneConfig.getActiveSkyboxId();
        int selectedIndex = optionIds.indexOf(activeId);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
        String stateKey = makeStateKey(optionIds, optionLabels, activeId);
        if (globalDropdown == null || !stateKey.equals(globalDropdownStateKey)) {
            final List<String> savedOptionIds = new ArrayList<String>(optionIds);
            String[] labels = optionLabels.toArray(new String[optionLabels.size()]);
            globalDropdown = new Dropdown(300, 32, 16, labels, selectedIndex, ColorPalette.SECONDARY) {
                @Override
                public void onChange(int index) {
                    if (index >= 0 && index < savedOptionIds.size()) {
                        String skyboxId = savedOptionIds.get(index);
                        String message = skyboxId.isEmpty()
                                ? SkyboxOneConfig.useBuiltInSkybox()
                                : SkyboxOneConfig.selectSkybox(skyboxId);
                        SkyboxOneConfig.reportAction(message);
                    }
                }
            };
            globalDropdownStateKey = stateKey;
        }
        return globalDropdown;
    }

    private static String formatSkyboxOption(int index, String name) {
        String prefix = "#" + index + " ";
        int maxNameLength = Math.max(8, 29 - prefix.length());
        if (name.length() > maxNameLength) {
            name = name.substring(0, maxNameLength - 3) + "...";
        }
        return prefix + name;
    }

    private static String makeStateKey(List<String> optionIds, List<String> labels, String selectedId) {
        StringBuilder key = new StringBuilder(String.valueOf(selectedId));
        for (int i = 0; i < optionIds.size(); i++) {
            key.append('\u0000').append(optionIds.get(i)).append('\u0001').append(labels.get(i));
        }
        return key.toString();
    }

    private boolean drawButton(
            long vg,
            InputHandler inputHandler,
            int x,
            int y,
            int width,
            String text,
            int color
    ) {
        NanoVGHelper nanoVG = NanoVGHelper.INSTANCE;
        boolean hovered = inputHandler.isAreaHovered(x, y, width, 24);
        nanoVG.drawRoundedRect(vg, x, y, width, 24,
                hovered ? BUTTON_HOVER_COLOR : color, 4);
        nanoVG.drawCenteredText(vg, text, x + width / 2f, y + 16, TEXT_COLOR, 11, Fonts.MEDIUM);
        return inputHandler.isAreaClicked(x, y, width, 24);
    }

    private void drawButtonVisual(
            long vg,
            InputHandler inputHandler,
            int x,
            int y,
            int width,
            String text,
            int color
    ) {
        NanoVGHelper nanoVG = NanoVGHelper.INSTANCE;
        boolean hovered = inputHandler.isAreaHovered(x, y, width, 24);
        nanoVG.drawRoundedRect(vg, x, y, width, 24,
                hovered ? BUTTON_HOVER_COLOR : color, 4);
        nanoVG.drawCenteredText(vg, text, x + width / 2f, y + 16, TEXT_COLOR, 11, Fonts.MEDIUM);
    }

}
