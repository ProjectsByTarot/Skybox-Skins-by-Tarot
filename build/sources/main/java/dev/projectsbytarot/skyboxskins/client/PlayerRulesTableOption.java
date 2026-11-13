package dev.projectsbytarot.skyboxskins.client;

import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.gui.elements.Dropdown;
import cc.polyfrost.oneconfig.gui.elements.IFocusable;
import cc.polyfrost.oneconfig.gui.elements.text.TextInputField;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.font.Fonts;
import cc.polyfrost.oneconfig.utils.InputHandler;
import cc.polyfrost.oneconfig.utils.color.ColorPalette;
import org.lwjgl.input.Keyboard;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class PlayerRulesTableOption extends BasicOption implements IFocusable {
    private static final int ROW_HEIGHT = 36;
    private static final int TEXT_COLOR = 0xFFE0E0E0;
    private static final int MUTED_TEXT_COLOR = 0xFF9CA3AF;
    private static final int BUTTON_COLOR = 0xFF343A46;
    private static final int BUTTON_HOVER_COLOR = 0xFF495263;
    private static final int ENABLED_COLOR = 0xFF356D50;
    private static final int DISABLED_COLOR = 0xFF82454B;

    private final SkyboxOneConfig config;
    private final Map<SkyboxOneConfig.PlayerRule, TextInputField> editors =
            new IdentityHashMap<SkyboxOneConfig.PlayerRule, TextInputField>();
    private final Map<SkyboxOneConfig.PlayerRule, DropdownState> skyboxDropdowns =
            new IdentityHashMap<SkyboxOneConfig.PlayerRule, DropdownState>();
    private TextInputField focusedEditor;

    PlayerRulesTableOption(
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
        List<SkyboxOneConfig.PlayerRule> rules = config.getPlayerRules();
        editors.keySet().retainAll(rules);
        skyboxDropdowns.keySet().retainAll(rules);
        focusedEditor = null;

        nanoVG.drawText(vg, name, x, y + 16, nameColor, 14, Fonts.MEDIUM);
        nanoVG.drawText(vg, "NAME", x, y + 42, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "SAVE", x + 112, y + 42, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "WINDOW", x + 158, y + 42, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "SKYBOX OVERRIDE", x + 220, y + 42, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        nanoVG.drawText(vg, "REMOVE", x + 376, y + 42, MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);

        if (rules.isEmpty()) {
            nanoVG.drawText(vg, "No player rules yet. Add any username above, even while offline.",
                    x, y + 64, MUTED_TEXT_COLOR, 12, Fonts.REGULAR);
        }

        List<SkyboxOneConfig.PlayerRule> visibleRules = new ArrayList<SkyboxOneConfig.PlayerRule>();
        List<Integer> visibleRowIndices = new ArrayList<Integer>();
        for (int i = 0; i < rules.size(); i++) {
            SkyboxOneConfig.PlayerRule rule = rules.get(i);
            if (rule == null || rule.name == null) {
                continue;
            }
            visibleRules.add(rule);
            visibleRowIndices.add(i);

            int rowY = y + 48 + i * ROW_HEIGHT;
            TextInputField editor = getEditor(rule);
            editor.draw(vg, x, rowY, inputHandler);
            if (editor.isToggled()) {
                focusedEditor = editor;
            }

            if (drawButton(vg, inputHandler, x + 112, rowY, 40, "Save", BUTTON_COLOR)) {
                saveName(rule, editor);
            }

            if (drawSwitch(vg, inputHandler, x + 158, rowY, rule.windowEnabled)) {
                reportAction(SkyboxOneConfig.togglePlayerRule(rule));
            }

            if (drawButton(vg, inputHandler, x + 376, rowY, 60, "Remove", DISABLED_COLOR)) {
                String message = SkyboxOneConfig.removePlayerRule(rule);
                editors.remove(rule);
                skyboxDropdowns.remove(rule);
                reportAction(message);
                return;
            }
        }

        List<DropdownDraw> dropdownDraws = new ArrayList<DropdownDraw>();
        for (int i = 0; i < visibleRules.size(); i++) {
            SkyboxOneConfig.PlayerRule rule = visibleRules.get(i);
            int rowY = y + 48 + visibleRowIndices.get(i) * ROW_HEIGHT;
            DropdownState state = getSkyboxDropdown(rule);
            dropdownDraws.add(new DropdownDraw(rule, state.dropdown, rowY));
        }

        DropdownDraw activeDropdown = findActiveDropdown(dropdownDraws, x, inputHandler);
        for (DropdownDraw dropdownDraw : dropdownDraws) {
            if (dropdownDraw == activeDropdown) {
                continue;
            }
            if (activeDropdown == null) {
                dropdownDraw.dropdown.draw(vg, x + 220, dropdownDraw.y, inputHandler);
            } else {
                drawSkyboxDropdownButton(vg, inputHandler, x + 220,
                        dropdownDraw.y, dropdownDraw.rule);
            }
        }
        if (activeDropdown != null) {
            activeDropdown.dropdown.draw(vg, x + 220, activeDropdown.y, inputHandler);
        }

        drawDescription(vg, x, y, inputHandler);
    }

    @Override
    public void keyTyped(char key, int keyCode) {
        if (focusedEditor == null) {
            return;
        }
        focusedEditor.keyTyped(key, keyCode);
        if (keyCode == Keyboard.KEY_RETURN) {
            for (Map.Entry<SkyboxOneConfig.PlayerRule, TextInputField> entry : editors.entrySet()) {
                if (entry.getValue() == focusedEditor) {
                    saveName(entry.getKey(), focusedEditor);
                    break;
                }
            }
        }
    }

    @Override
    public boolean hasFocus() {
        return focusedEditor != null && focusedEditor.isToggled();
    }

    @Override
    public int getHeight() {
        int size = config.getPlayerRules().size();
        return size == 0 ? 64 : 52 + ROW_HEIGHT * size;
    }

    private DropdownState getSkyboxDropdown(final SkyboxOneConfig.PlayerRule rule) {
        List<SkyboxOneConfig.SkyboxEntry> skyboxes = config.getSkyboxes();
        final List<String> optionIds = new ArrayList<String>();
        List<String> optionLabels = new ArrayList<String>();
        optionIds.add(null);
        optionLabels.add("Global");
        optionIds.add(SkyboxIds.BUILT_IN);
        optionLabels.add("Built-in");
        for (int i = 0; i < skyboxes.size(); i++) {
            SkyboxOneConfig.SkyboxEntry entry = skyboxes.get(i);
            if (entry == null || entry.id == null || entry.name == null) {
                continue;
            }
            optionIds.add(entry.id);
            optionLabels.add(formatSkyboxOption(i + 1, entry.name));
        }

        int selectedIndex = findOptionIndex(optionIds, rule.skyboxId);
        String stateKey = makeStateKey(optionIds, optionLabels, rule.skyboxId);
        DropdownState state = skyboxDropdowns.get(rule);
        if (state == null || !state.key.equals(stateKey)) {
            final List<String> savedOptionIds = new ArrayList<String>(optionIds);
            String[] labels = optionLabels.toArray(new String[optionLabels.size()]);
            Dropdown dropdown = new Dropdown(150, 32, 16, labels, selectedIndex, ColorPalette.SECONDARY) {
                @Override
                public void onChange(int index) {
                    if (index >= 0 && index < savedOptionIds.size()) {
                        SkyboxOneConfig.reportAction(
                                SkyboxOneConfig.setPlayerSkybox(rule, savedOptionIds.get(index)));
                    }
                }
            };
            state = new DropdownState(stateKey, dropdown);
            skyboxDropdowns.put(rule, state);
        }
        return state;
    }

    private static int findOptionIndex(List<String> optionIds, String currentId) {
        String normalizedCurrentId = currentId == null || currentId.isEmpty() ? null : currentId;
        for (int i = 0; i < optionIds.size(); i++) {
            String optionId = optionIds.get(i);
            if (optionId == null ? normalizedCurrentId == null : optionId.equals(normalizedCurrentId)) {
                return i;
            }
        }
        return 0;
    }

    private static String makeStateKey(List<String> optionIds, List<String> labels, String selectedId) {
        StringBuilder key = new StringBuilder(String.valueOf(selectedId));
        for (int i = 0; i < optionIds.size(); i++) {
            key.append('\u0000').append(optionIds.get(i)).append('\u0001').append(labels.get(i));
        }
        return key.toString();
    }

    private static String formatSkyboxOption(int index, String name) {
        String prefix = "#" + index + " ";
        int maxNameLength = Math.max(8, 27 - prefix.length());
        if (name.length() > maxNameLength) {
            name = name.substring(0, maxNameLength - 3) + "...";
        }
        return prefix + name;
    }

    private static DropdownDraw findActiveDropdown(
            List<DropdownDraw> dropdownDraws,
            int x,
            InputHandler inputHandler
    ) {
        for (DropdownDraw dropdownDraw : dropdownDraws) {
            if (dropdownDraw.dropdown.isToggled()) {
                return dropdownDraw;
            }
        }
        for (DropdownDraw dropdownDraw : dropdownDraws) {
            if (inputHandler.isAreaClicked(x + 220, dropdownDraw.y, 150, 32)) {
                return dropdownDraw;
            }
        }
        return null;
    }

    private void drawSkyboxDropdownButton(
            long vg,
            InputHandler inputHandler,
            int x,
            int y,
            SkyboxOneConfig.PlayerRule rule
    ) {
        NanoVGHelper nanoVG = NanoVGHelper.INSTANCE;
        boolean hovered = inputHandler.isAreaHovered(x, y, 150, 32);
        nanoVG.drawRoundedRect(vg, x, y, 150, 32,
                hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR, 6);
        String label = SkyboxOneConfig.getPlayerSkyboxLabel(rule);
        if (label.length() > 19) {
            label = label.substring(0, 16) + "...";
        }
        nanoVG.drawText(vg, label, x + 8, y + 20, TEXT_COLOR, 11, Fonts.REGULAR);
        nanoVG.drawText(vg, "v", x + 136, y + 20, MUTED_TEXT_COLOR, 11, Fonts.MEDIUM);
    }

    private TextInputField getEditor(SkyboxOneConfig.PlayerRule rule) {
        TextInputField editor = editors.get(rule);
        if (editor == null) {
            editor = new TextInputField(108, 24, rule.name, false, false);
            editors.put(rule, editor);
        }
        return editor;
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
        int buttonColor = hovered ? BUTTON_HOVER_COLOR : color;
        nanoVG.drawRoundedRect(vg, x, y, width, 24, buttonColor, 4);
        nanoVG.drawCenteredText(vg, text, x + width / 2f, y + 16, TEXT_COLOR, 11, Fonts.MEDIUM);
        return inputHandler.isAreaClicked(x, y, width, 24);
    }

    private boolean drawSwitch(long vg, InputHandler inputHandler, int x, int y, boolean enabled) {
        NanoVGHelper nanoVG = NanoVGHelper.INSTANCE;
        boolean hovered = inputHandler.isAreaHovered(x, y, 56, 24);
        int trackColor = enabled ? ENABLED_COLOR : (hovered ? BUTTON_HOVER_COLOR : BUTTON_COLOR);
        nanoVG.drawRoundedRect(vg, x + 2, y + 5, 32, 16, trackColor, 8);
        nanoVG.drawCircle(vg, enabled ? x + 26 : x + 10, y + 13, 6, TEXT_COLOR);
        nanoVG.drawText(vg, enabled ? "On" : "Off", x + 37, y + 16,
                enabled ? TEXT_COLOR : MUTED_TEXT_COLOR, 10, Fonts.MEDIUM);
        return inputHandler.isAreaClicked(x, y, 56, 24);
    }

    private void saveName(SkyboxOneConfig.PlayerRule rule, TextInputField editor) {
        String oldName = rule.name;
        String message = SkyboxOneConfig.renamePlayerRule(rule, editor.getInput());
        if (message.startsWith("Updated player rule")) {
            editor.setInput(rule.name);
        } else {
            editor.setInput(oldName);
        }
        reportAction(message);
    }

    private static void reportAction(String message) {
        SkyboxOneConfig.reportAction(message);
    }

    private static final class DropdownState {
        private final String key;
        private final Dropdown dropdown;

        private DropdownState(String key, Dropdown dropdown) {
            this.key = key;
            this.dropdown = dropdown;
        }
    }

    private static final class DropdownDraw {
        private final SkyboxOneConfig.PlayerRule rule;
        private final Dropdown dropdown;
        private final int y;

        private DropdownDraw(SkyboxOneConfig.PlayerRule rule, Dropdown dropdown, int y) {
            this.rule = rule;
            this.dropdown = dropdown;
            this.y = y;
        }
    }
}
