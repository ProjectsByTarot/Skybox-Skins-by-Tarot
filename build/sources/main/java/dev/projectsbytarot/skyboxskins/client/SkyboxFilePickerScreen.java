package dev.projectsbytarot.skyboxskins.client;

import dev.projectsbytarot.skyboxskins.SkyboxSkins;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SkyboxFilePickerScreen extends GuiScreen {
    private static final int FILE_LIST_TOP = 140;
    private static final String[] IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};

    private final GuiScreen parentScreen;
    private final boolean mask;
    private final List<File> entries = new ArrayList<File>();

    private GuiTextField pathField;
    private GuiTextField searchField;
    private File currentDirectory;
    private int page;
    private int pageSize;
    private String status = "";

    public SkyboxFilePickerScreen(GuiScreen parentScreen, boolean mask, File initialDirectory) {
        this.parentScreen = parentScreen;
        this.mask = mask;
        this.currentDirectory = initialDirectory;
    }

    @Override
    public void initGui() {
        pageSize = Math.max(1, Math.min(13, (height - 234) / 22));
        pathField = new GuiTextField(0, fontRendererObj, 12, 40, width - 106, 20);
        pathField.setMaxStringLength(512);
        pathField.setText(currentDirectory == null ? "" : currentDirectory.getAbsolutePath());
        searchField = new GuiTextField(1, fontRendererObj, 12, 92, width - 24, 20);
        searchField.setMaxStringLength(64);
        refreshEntries();
        rebuildButtons();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (!button.enabled) {
            return;
        }

        if (button.id == 1) {
            navigateUp();
        } else if (button.id == 2) {
            navigateTo(new File(System.getProperty("user.home", ".")));
        } else if (button.id == 3) {
            navigateToPictures();
        } else if (button.id == 4) {
            refreshEntries();
            rebuildButtons();
        } else if (button.id == 5) {
            mc.displayGuiScreen(parentScreen);
        } else if (button.id == 6) {
            page--;
            rebuildButtons();
        } else if (button.id == 7) {
            navigateToPath();
        } else if (button.id == 8) {
            page++;
            rebuildButtons();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (pathField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                navigateToPath();
                return;
            }
            if (pathField.textboxKeyTyped(typedChar, keyCode)) {
                return;
            }
        }
        if (searchField.textboxKeyTyped(typedChar, keyCode)) {
            page = 0;
            refreshEntries();
            rebuildButtons();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        pathField.mouseClicked(mouseX, mouseY, mouseButton);
        searchField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && mouseX >= 12 && mouseX < width - 12
                && mouseY >= FILE_LIST_TOP) {
            int row = (mouseY - FILE_LIST_TOP) / 22;
            int rowTop = FILE_LIST_TOP + row * 22;
            int entryIndex = page * pageSize + row;
            if (row < pageSize && mouseY < rowTop + 20 && entryIndex < entries.size()) {
                selectEntry(entries.get(entryIndex));
                return;
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, mask ? "Choose a 64x64 skin mask" : "Choose a skybox image",
                width / 2, 18, 0xFFFFFF);
        pathField.drawTextBox();
        if (pathField.getText().isEmpty()) {
            drawString(fontRendererObj, "Type or paste a folder or image path", 17, 46, 0x777777);
        }
        searchField.drawTextBox();
        if (searchField.getText().isEmpty()) {
            drawString(fontRendererObj, "Search this folder", 17, 86, 0x777777);
        }
        int pageCount = Math.max(1, (entries.size() + pageSize - 1) / pageSize);
        String pageText = entries.isEmpty()
                ? "No matching images or folders."
                : "Page " + (page + 1) + " of " + pageCount + " (" + entries.size()
                        + " items). Use Next for more folders.";
        drawString(fontRendererObj, fontRendererObj.trimStringToWidth(pageText, width - 190),
                12, 118, 0xAAAAAA);
        drawFileRows(mouseX, mouseY);
        String statusText = status.isEmpty()
                ? "Click a folder to open it or an image to import. PNG, JPG, BMP and GIF are supported."
                : status;
        drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(statusText, width - 24),
                width / 2, height - 66, 0xFFFFFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private void refreshEntries() {
        entries.clear();
        status = "";

        try {
            if (currentDirectory == null) {
                File[] roots = File.listRoots();
                if (roots != null) {
                    for (File root : roots) {
                        if (matchesSearch(root.getPath())) {
                            entries.add(root);
                        }
                    }
                }
            } else {
                File[] children = currentDirectory.listFiles();
                if (children == null) {
                    status = "Cannot read this folder.";
                    SkyboxSkins.LOGGER.warn("Cannot list image-picker folder {}.", currentDirectory);
                } else {
                    for (File child : children) {
                        if ((child.isDirectory() && matchesSearch(child.getName()))
                                || (child.isFile() && isImageFile(child)
                                && matchesSearch(child.getName()))) {
                            entries.add(child);
                        }
                    }
                    Collections.sort(entries, new Comparator<File>() {
                        @Override
                        public int compare(File first, File second) {
                            if (first.isDirectory() != second.isDirectory()) {
                                return first.isDirectory() ? -1 : 1;
                            }
                            return first.getName().compareToIgnoreCase(second.getName());
                        }
                    });
                }
            }
        } catch (SecurityException exception) {
            status = "Access to this folder was denied.";
            SkyboxSkins.LOGGER.warn("Access denied while listing image-picker folder {}.", currentDirectory,
                    exception);
        }

        page = Math.max(0, Math.min(page, Math.max(0, (entries.size() - 1) / pageSize)));
    }

    private void rebuildButtons() {
        buttonList.clear();
        int parentButtonY = 66;
        int leftButtonWidth = Math.min(80, Math.max(30, (width - 118) / 4));
        GuiButton upButton = new GuiButton(1, 12, parentButtonY, leftButtonWidth, 20,
                currentDirectory != null && currentDirectory.getParentFile() == null ? "Drives" : "Up");
        upButton.enabled = currentDirectory != null;
        buttonList.add(upButton);
        buttonList.add(new GuiButton(2, 18 + leftButtonWidth, parentButtonY, leftButtonWidth, 20, "Home"));
        buttonList.add(new GuiButton(3, 24 + leftButtonWidth * 2, parentButtonY, leftButtonWidth, 20, "Pics"));
        buttonList.add(new GuiButton(4, 30 + leftButtonWidth * 3, parentButtonY, leftButtonWidth, 20, "Scan"));
        buttonList.add(new GuiButton(5, width - 82, parentButtonY, 70, 20, "Cancel"));
        GuiButton goButton = new GuiButton(7, width - 82, 40, 70, 20, "Go");
        buttonList.add(goButton);

        int firstEntry = page * pageSize;
        int lastEntry = Math.min(entries.size(), firstEntry + pageSize);

        int navigationY = 112;
        GuiButton previous = new GuiButton(6, width - 172, navigationY, 70, 20, "Previous");
        GuiButton next = new GuiButton(8, width - 94, navigationY, 82, 20, "Next");
        previous.enabled = page > 0;
        next.enabled = lastEntry < entries.size();
        buttonList.add(previous);
        buttonList.add(next);
    }

    private void drawFileRows(int mouseX, int mouseY) {
        int firstEntry = page * pageSize;
        int lastEntry = Math.min(entries.size(), firstEntry + pageSize);
        for (int i = firstEntry; i < lastEntry; i++) {
            int row = i - firstEntry;
            int top = FILE_LIST_TOP + row * 22;
            boolean hovered = mouseX >= 12 && mouseX < width - 12
                    && mouseY >= top && mouseY < top + 20;
            int background = hovered ? 0xCC555555 : 0xAA222222;
            Gui.drawRect(12, top, width - 12, top + 20, background);
            Gui.drawRect(12, top, width - 12, top + 1, 0xFF666666);

            File entry = entries.get(i);
            String label = entry.isDirectory() ? "[Folder] " + getDisplayName(entry) : entry.getName();
            int color = entry.isDirectory() ? 0xFFFFFF : 0xDDDDDD;
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(label, width - 40),
                    20, top + 6, color);
        }
    }

    private void navigateUp() {
        if (currentDirectory == null) {
            return;
        }

        currentDirectory = currentDirectory.getParentFile();
        page = 0;
        searchField.setText("");
        updatePathField();
        refreshEntries();
        rebuildButtons();
    }

    private void navigateToPath() {
        String path = pathField.getText().trim();
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            path = path.substring(1, path.length() - 1);
        }
        if (path.isEmpty()) {
            currentDirectory = null;
            page = 0;
            searchField.setText("");
            refreshEntries();
            rebuildButtons();
            return;
        }

        File target = new File(path);
        if (!target.isAbsolute()) {
            File base = currentDirectory == null
                    ? new File(System.getProperty("user.home", "."))
                    : currentDirectory;
            target = new File(base, path);
        }

        try {
            if (target.isFile() && isImageFile(target)) {
                selectEntry(target);
                return;
            }
            navigateTo(target);
        } catch (SecurityException exception) {
            status = "Access to the requested path was denied.";
            SkyboxSkins.LOGGER.warn("Access denied for image-picker path {}.", target, exception);
        }
    }

    private void navigateTo(File directory) {
        try {
            if (!directory.isDirectory()) {
                status = "Folder not found: " + directory.getAbsolutePath();
                return;
            }

            currentDirectory = directory.getCanonicalFile();
            page = 0;
            searchField.setText("");
            updatePathField();
            refreshEntries();
            rebuildButtons();
        } catch (IOException | SecurityException exception) {
            status = "Could not open folder: " + exception.getMessage();
            SkyboxSkins.LOGGER.warn("Could not open image-picker folder {}.", directory, exception);
        }
    }

    private void navigateToPictures() {
        File home = new File(System.getProperty("user.home", "."));
        File pictures = new File(home, "Pictures");
        if (!pictures.isDirectory()) {
            pictures = new File(new File(home, "OneDrive"), "Pictures");
        }
        navigateTo(pictures);
    }

    private void updatePathField() {
        pathField.setText(currentDirectory == null ? "" : currentDirectory.getAbsolutePath());
    }

    private void selectEntry(File entry) {
        try {
            if (entry.isDirectory()) {
                currentDirectory = entry;
                page = 0;
                searchField.setText("");
                refreshEntries();
                rebuildButtons();
                return;
            }

            SkyboxTextures.ImportResult result = SkyboxTextures.importImage(entry, mask);
            status = result.message;
            if (result.success) {
                SkyboxOneConfig.reportAction(result.message);
                mc.displayGuiScreen(parentScreen);
            }
        } catch (SecurityException exception) {
            status = "Access to the selected file was denied.";
            SkyboxSkins.LOGGER.warn("Access denied for image-picker entry {}.", entry, exception);
        }
    }

    private boolean matchesSearch(String name) {
        String query = searchField.getText().trim().toLowerCase(Locale.ROOT);
        return query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean isImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        for (String extension : IMAGE_EXTENSIONS) {
            if (name.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String getDisplayName(File file) {
        String name = file.getName();
        return name.isEmpty() ? file.getAbsolutePath() : name;
    }
}
