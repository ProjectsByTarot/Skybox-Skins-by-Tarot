package dev.projectsbytarot.skyboxskins.client;

import cc.polyfrost.oneconfig.config.Config;
import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.CustomOption;
import cc.polyfrost.oneconfig.config.annotations.KeyBind;
import cc.polyfrost.oneconfig.config.annotations.Switch;
import cc.polyfrost.oneconfig.config.annotations.Text;
import cc.polyfrost.oneconfig.config.core.ConfigUtils;
import cc.polyfrost.oneconfig.config.core.OneKeyBind;
import cc.polyfrost.oneconfig.config.data.Mod;
import cc.polyfrost.oneconfig.config.data.ModType;
import cc.polyfrost.oneconfig.config.data.OptionSize;
import cc.polyfrost.oneconfig.config.elements.BasicOption;
import cc.polyfrost.oneconfig.config.elements.OptionPage;
import dev.projectsbytarot.skyboxskins.SkyboxSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SkyboxOneConfig extends Config {
    public static SkyboxOneConfig instance;

    @Switch(
            name = "Enable skybox windows",
            description = "Globally show or hide skybox windows on connected players.",
            category = "General"
    )
    public static boolean skyboxEnabled = true;

    @KeyBind(
            name = "Open settings",
            description = "Open this OneConfig page. Unbound by default.",
            category = "General"
    )
    public static OneKeyBind openSettingsKey = new OneKeyBind();

    @Button(
            name = "Add skybox image",
            text = "Add",
            description = "Import a panorama into your saved skybox library and select it.",
            category = "Images"
    )
    public Runnable chooseSkybox = new Runnable() {
        @Override
        public void run() {
            requestImagePicker(false);
        }
    };

    @Button(
            name = "Choose skin mask",
            text = "Choose",
            description = "Select a 64x64 black-and-white mask for the skin window.",
            category = "Images"
    )
    public Runnable chooseMask = new Runnable() {
        @Override
        public void run() {
            requestImagePicker(true);
        }
    };

    @Button(
            name = "Reset skybox",
            text = "Reset",
            description = "Use the built-in skybox without removing saved skybox images.",
            category = "Images"
    )
    public Runnable resetSkybox = new Runnable() {
        @Override
        public void run() {
            reportAction(SkyboxTextures.removeImage(false));
        }
    };

    @Button(
            name = "Reset mask",
            text = "Reset",
            description = "Remove the custom mask and make the full skin a window.",
            category = "Images"
    )
    public Runnable resetMask = new Runnable() {
        @Override
        public void run() {
            reportAction(SkyboxTextures.removeImage(true));
        }
    };

    @Button(
            name = "Reload images",
            text = "Reload",
            description = "Reload the selected skybox and skin mask.",
            category = "Images"
    )
    public Runnable reloadImages = new Runnable() {
        @Override
        public void run() {
            boolean customSkybox = SkyboxClientHandler.reloadSkyboxTextures();
            boolean customMask = SkyboxClientHandler.isCustomMaskLoaded();
            String skyboxStatus = customSkybox
                    ? "Global skybox \"" + getSkyboxName(getActiveSkyboxId()) + "\" loaded."
                    : "Using the built-in skybox.";
            reportAction(skyboxStatus
                    + (customMask ? " Custom mask loaded." : " Using the full-skin mask."));
        }
    };

    @Button(
            name = "Open image folder",
            text = "Open",
            description = "Open the folder containing saved skyboxes and mask.png.",
            category = "Images"
    )
    public Runnable openImageFolder = new Runnable() {
        @Override
        public void run() {
            openCustomImageFolder();
        }
    };

    @Button(
            name = "Scan image folder",
            text = "Scan",
            description = "Add supported images placed in config/skyboxskins or its skyboxes folder.",
            category = "Images"
    )
    public Runnable scanImageFolder = new Runnable() {
        @Override
        public void run() {
            reportAction(indexDroppedImages());
        }
    };

    @CustomOption(id = "skyboxLibraryTable")
    public List<SkyboxEntry> skyboxes = new ArrayList<SkyboxEntry>();

    public String selectedSkyboxId = "";
    public boolean legacyPanoramaMigrated;

    @Text(
            name = "Add player by name",
            placeholder = "Minecraft username",
            description = "Add a connected or offline player's username to the rules table.",
            category = "Players"
    )
    public String newPlayerName = "";

    @Button(
            name = "Add player rule",
            text = "Add",
            description = "Add this username with its skybox window disabled by default.",
            category = "Players"
    )
    public Runnable addPlayerRule = new Runnable() {
        @Override
        public void run() {
            reportAction(addPlayerRule(newPlayerName));
        }
    };

    @CustomOption(id = "playerRulesTable")
    public List<PlayerRule> playerRules = new ArrayList<PlayerRule>();

    public SkyboxOneConfig() {
        super(new Mod("Skybox Skins by Tarot", ModType.UTIL_QOL, "/assets/skyboxskins/icon.png"), "skyboxskins.json");
        instance = this;
        initialize();
        ensureCurrentPlayerRule();
        registerKeyBind(openSettingsKey, new Runnable() {
            @Override
            public void run() {
                SkyboxClientHandler.requestSettingsScreen();
            }
        });
        migrateLegacyPanorama();
        IndexSummary summary = indexSkyboxFiles();
        if (summary.added > 0 || summary.failed > 0) {
            SkyboxSkins.LOGGER.info(summary.toMessage());
        }
    }

    @Override
    protected BasicOption getCustomOption(
            Field field,
            CustomOption annotation,
            OptionPage page,
            Mod mod,
            boolean migrate
    ) {
        if ("playerRulesTable".equals(annotation.id())) {
            BasicOption option = new PlayerRulesTableOption(field, this, "Player rules",
                    "Player rules are saved by username. Your own username is added with its window off by default; enable it to change your own skin. Choose a skybox override per player.",
                    "Players", "Rules",
                    OptionSize.SINGLE);
            ConfigUtils.getSubCategory(page, "Players", "Rules").options.add(option);
            return option;
        }
        if ("skyboxLibraryTable".equals(annotation.id())) {
            BasicOption option = new SkyboxLibraryTableOption(field, this, "Saved skyboxes",
                    "Import multiple panoramas and choose a global skybox.", "Images", "Library",
                    OptionSize.SINGLE);
            ConfigUtils.getSubCategory(page, "Images", "Library").options.add(option);
            return option;
        }
        return super.getCustomOption(field, annotation, page, mod, migrate);
    }

    public static void saveSettings() {
        if (instance != null) {
            instance.save();
        }
    }

    private void ensureCurrentPlayerRule() {
        String username = Minecraft.getMinecraft().getSession().getUsername();
        String normalizedName = validatePlayerName(username);
        if (normalizedName == null || findPlayerRule(normalizedName) != null) {
            return;
        }
        getPlayerRules().add(new PlayerRule(normalizedName, false));
        save();
    }

    public List<PlayerRule> getPlayerRules() {
        if (playerRules == null) {
            playerRules = new ArrayList<PlayerRule>();
        }
        return playerRules;
    }

    public List<SkyboxEntry> getSkyboxes() {
        if (skyboxes == null) {
            skyboxes = new ArrayList<SkyboxEntry>();
        }
        return skyboxes;
    }

    public static String getActiveSkyboxId() {
        if (instance == null) {
            return "";
        }
        SkyboxEntry entry = instance.findSkybox(instance.selectedSkyboxId);
        return entry == null ? "" : entry.id;
    }

    public static String getSkyboxIdForPlayer(String playerName) {
        if (instance == null) {
            return "";
        }
        PlayerRule rule = instance.findPlayerRule(playerName);
        if (rule != null && SkyboxIds.BUILT_IN.equals(rule.skyboxId)) {
            return "";
        }
        SkyboxEntry entry = rule == null ? null : instance.findSkybox(rule.skyboxId);
        return entry == null ? getActiveSkyboxId() : entry.id;
    }

    public static String getSkyboxName(String skyboxId) {
        if (instance == null) {
            return "Built-in";
        }
        SkyboxEntry entry = instance.findSkybox(skyboxId);
        return entry == null ? "Built-in" : entry.name;
    }

    public static String getPlayerSkyboxLabel(PlayerRule rule) {
        if (rule != null && SkyboxIds.BUILT_IN.equals(rule.skyboxId)) {
            return "Built-in";
        }
        if (rule == null || rule.skyboxId == null || rule.skyboxId.isEmpty()) {
            return "Global";
        }
        SkyboxEntry entry = instance == null ? null : instance.findSkybox(rule.skyboxId);
        return entry == null ? "Global" : entry.name;
    }

    public static String cyclePlayerSkybox(PlayerRule rule) {
        if (instance == null || rule == null || !instance.getPlayerRules().contains(rule)) {
            return "That player rule is no longer available.";
        }
        List<SkyboxEntry> entries = instance.getSkyboxes();

        int currentIndex = 0;
        if (SkyboxIds.BUILT_IN.equals(rule.skyboxId)) {
            currentIndex = 1;
        }
        for (int i = 0; i < entries.size(); i++) {
            SkyboxEntry entry = entries.get(i);
            if (entry != null && entry.id != null && entry.id.equals(rule.skyboxId)) {
                currentIndex = i + 2;
                break;
            }
        }
        int nextIndex = (currentIndex + 1) % (entries.size() + 2);
        if (nextIndex == 0) {
            rule.skyboxId = null;
        } else if (nextIndex == 1) {
            rule.skyboxId = SkyboxIds.BUILT_IN;
        } else {
            rule.skyboxId = entries.get(nextIndex - 2).id;
        }
        instance.save();
        return "Skybox for " + rule.name + " set to " + getPlayerSkyboxLabel(rule) + ".";
    }

    public static String setPlayerSkybox(PlayerRule rule, String skyboxId) {
        if (instance == null || rule == null || !instance.getPlayerRules().contains(rule)) {
            return "That player rule is no longer available.";
        }
        if (skyboxId == null || skyboxId.isEmpty()) {
            rule.skyboxId = null;
        } else if (SkyboxIds.BUILT_IN.equals(skyboxId)) {
            rule.skyboxId = SkyboxIds.BUILT_IN;
        } else {
            SkyboxEntry entry = instance.findSkybox(skyboxId);
            if (entry == null) {
                return "That saved skybox is no longer available.";
            }
            rule.skyboxId = entry.id;
        }
        instance.save();
        return "Skybox for " + rule.name + " set to " + getPlayerSkyboxLabel(rule) + ".";
    }

    public static boolean isValidSkyboxId(String skyboxId) {
        return skyboxId != null
                && skyboxId.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
    }

    public static boolean addSkybox(String skyboxId, String name) {
        if (instance == null || !isValidSkyboxId(skyboxId) || name == null || name.trim().isEmpty()) {
            return false;
        }
        if (instance.findSkybox(skyboxId) != null) {
            return false;
        }
        instance.getSkyboxes().add(new SkyboxEntry(skyboxId, instance.makeUniqueSkyboxName(name)));
        instance.selectedSkyboxId = skyboxId;
        instance.save();
        return true;
    }

    static boolean addIndexedSkybox(String skyboxId, String name, String sourcePath) {
        if (instance == null || !isValidSkyboxId(skyboxId)
                || name == null || name.trim().isEmpty() || sourcePath == null) {
            return false;
        }
        if (instance.findSkybox(skyboxId) != null) {
            return false;
        }
        SkyboxEntry entry = new SkyboxEntry(skyboxId, instance.makeUniqueSkyboxName(name));
        entry.sourcePath = sourcePath;
        instance.getSkyboxes().add(entry);
        return true;
    }

    public static String indexDroppedImages() {
        if (instance == null) {
            return "Skybox settings are not initialized.";
        }
        return instance.indexSkyboxFiles().toMessage();
    }

    public static String selectSkybox(String skyboxId) {
        if (instance == null) {
            return "Skybox settings are not initialized.";
        }
        SkyboxEntry entry = instance.findSkybox(skyboxId);
        if (entry == null) {
            return "That saved skybox is no longer available.";
        }
        instance.selectedSkyboxId = entry.id;
        instance.save();
        boolean loaded = SkyboxTextures.reload();
        return loaded
                ? "Selected skybox \"" + entry.name + "\"."
                : "Selected skybox \"" + entry.name + "\", but its image could not be loaded.";
    }

    public static String removeSkybox(String skyboxId) {
        if (instance == null) {
            return "Skybox settings are not initialized.";
        }
        SkyboxEntry entry = instance.findSkybox(skyboxId);
        if (entry == null) {
            return "That saved skybox is no longer available.";
        }

        File image = SkyboxClientHandler.getSkyboxFile(entry.id);
        try {
            if (image != null && image.exists() && !image.delete()) {
                return "Could not remove skybox image: " + image.getAbsolutePath();
            }
        } catch (SecurityException exception) {
            SkyboxSkins.LOGGER.warn("Could not remove saved skybox image {}.", image, exception);
            return "Could not remove skybox image: " + exception.getMessage();
        }

        instance.getSkyboxes().remove(entry);
        if (entry.id.equals(instance.selectedSkyboxId)) {
            instance.selectedSkyboxId = "";
        }
        int resetRules = 0;
        for (PlayerRule rule : instance.getPlayerRules()) {
            if (rule != null && entry.id.equals(rule.skyboxId)) {
                rule.skyboxId = null;
                resetRules++;
            }
        }
        instance.save();
        SkyboxTextures.reload();
        return "Removed skybox \"" + entry.name + "\"; "
                + resetRules + " player assignment(s) now use the global skybox.";
    }

    public static String useBuiltInSkybox() {
        if (instance == null) {
            return "Skybox settings are not initialized.";
        }
        instance.selectedSkyboxId = "";
        instance.save();
        SkyboxTextures.reload();
        return "Using the built-in skybox. Saved skybox images were kept.";
    }

    public static boolean isPlayerWindowEnabled(String playerName) {
        if (instance == null) {
            return true;
        }
        PlayerRule rule = instance.findPlayerRule(playerName);
        return rule == null || rule.windowEnabled;
    }

    public static String togglePlayerWindow(String playerName) {
        if (instance == null) {
            return "Player settings are not initialized.";
        }

        String normalizedName = validatePlayerName(playerName);
        if (normalizedName == null) {
            return "Could not update the player rule for an invalid username.";
        }

        PlayerRule rule = instance.findPlayerRule(normalizedName);
        if (rule == null) {
            rule = new PlayerRule(normalizedName, false);
            instance.getPlayerRules().add(rule);
        } else {
            rule.windowEnabled = !rule.windowEnabled;
        }
        instance.save();
        return "Skybox window " + (rule.windowEnabled ? "enabled" : "disabled")
                + " for " + rule.name + ".";
    }

    public static String renamePlayerRule(PlayerRule rule, String requestedName) {
        if (instance == null || rule == null || !instance.getPlayerRules().contains(rule)) {
            return "That player rule is no longer available.";
        }

        String normalizedName = validatePlayerName(requestedName);
        if (normalizedName == null) {
            return "Enter a valid Minecraft username (1-16 letters, numbers, or underscores).";
        }
        PlayerRule existingRule = instance.findPlayerRule(normalizedName);
        if (existingRule != null && existingRule != rule) {
            return "A rule for " + normalizedName + " already exists.";
        }

        rule.name = normalizedName;
        instance.save();
        return "Updated player rule for " + normalizedName + ".";
    }

    public static String togglePlayerRule(PlayerRule rule) {
        if (instance == null || rule == null || !instance.getPlayerRules().contains(rule)) {
            return "That player rule is no longer available.";
        }
        rule.windowEnabled = !rule.windowEnabled;
        instance.save();
        return "Skybox window " + (rule.windowEnabled ? "enabled" : "disabled")
                + " for " + rule.name + ".";
    }

    public static String removePlayerRule(PlayerRule rule) {
        if (instance == null || rule == null || !instance.getPlayerRules().remove(rule)) {
            return "That player rule is no longer available.";
        }
        instance.save();
        return "Removed the player rule for " + rule.name + ".";
    }

    private static String addPlayerRule(String requestedName) {
        if (instance == null) {
            return "Player settings are not initialized.";
        }

        String normalizedName = validatePlayerName(requestedName);
        if (normalizedName == null) {
            return "Enter a valid Minecraft username (1-16 letters, numbers, or underscores).";
        }
        if (instance.findPlayerRule(normalizedName) != null) {
            return "A rule for " + normalizedName + " already exists.";
        }

        instance.getPlayerRules().add(new PlayerRule(normalizedName, false));
        instance.newPlayerName = "";
        instance.save();
        return "Added a disabled skybox window rule for " + normalizedName + ".";
    }

    private PlayerRule findPlayerRule(String playerName) {
        if (playerName == null) {
            return null;
        }
        for (PlayerRule rule : getPlayerRules()) {
            if (rule != null && rule.name != null && rule.name.equalsIgnoreCase(playerName.trim())) {
                return rule;
            }
        }
        return null;
    }

    private String makeUniqueSkyboxName(String requestedName) {
        String baseName = requestedName.trim();
        if (baseName.length() > 48) {
            baseName = baseName.substring(0, 48);
        }

        String candidate = baseName;
        int suffixNumber = 2;
        while (hasSkyboxName(candidate)) {
            String suffix = " (" + suffixNumber++ + ")";
            int baseLength = Math.max(1, 48 - suffix.length());
            candidate = baseName.substring(0, Math.min(baseName.length(), baseLength)) + suffix;
        }
        return candidate;
    }

    private boolean hasSkyboxName(String name) {
        for (SkyboxEntry entry : getSkyboxes()) {
            if (entry != null && entry.name != null && entry.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private SkyboxEntry findSkybox(String skyboxId) {
        if (!isValidSkyboxId(skyboxId)) {
            return null;
        }
        for (SkyboxEntry entry : getSkyboxes()) {
            if (entry != null && skyboxId.equals(entry.id)) {
                return entry;
            }
        }
        return null;
    }

    private static String validatePlayerName(String playerName) {
        if (playerName == null) {
            return null;
        }
        String normalizedName = playerName.trim();
        return normalizedName.matches("[A-Za-z0-9_]{1,16}") ? normalizedName : null;
    }

    public static void reportAction(String message) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText("[Skybox Skins] " + message));
        } else {
            SkyboxSkins.LOGGER.info(message);
        }
    }

    public static class PlayerRule {
        public String name;
        public boolean windowEnabled;
        public String skyboxId;

        public PlayerRule() {
        }

        public PlayerRule(String name, boolean windowEnabled) {
            this.name = name;
            this.windowEnabled = windowEnabled;
        }
    }

    public static class SkyboxEntry {
        public String id;
        public String name;
        public String sourcePath;

        public SkyboxEntry() {
        }

        public SkyboxEntry(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private IndexSummary indexSkyboxFiles() {
        IndexSummary summary = new IndexSummary();
        Set<String> indexedPaths = new HashSet<String>();
        for (SkyboxEntry entry : getSkyboxes()) {
            if (entry != null && entry.sourcePath != null) {
                indexedPaths.add(entry.sourcePath.toLowerCase(Locale.ROOT));
            }
        }

        File root = SkyboxClientHandler.getCustomDirectory();
        indexDirectory(root, false, indexedPaths, summary);
        indexDirectory(SkyboxClientHandler.getSkyboxLibraryDirectory(), true, indexedPaths, summary);
        if (summary.added > 0) {
            save();
        }
        return summary;
    }

    private void indexDirectory(
            File directory,
            boolean libraryDirectory,
            Set<String> indexedPaths,
            IndexSummary summary
    ) {
        File[] files;
        try {
            if (!directory.exists()) {
                return;
            }
            if (!directory.isDirectory()) {
                SkyboxSkins.LOGGER.warn("Skybox image path is not a directory: {}", directory);
                summary.failed++;
                return;
            }
            files = directory.listFiles();
        } catch (SecurityException exception) {
            SkyboxSkins.LOGGER.warn("Could not list skybox image directory {}.", directory, exception);
            summary.failed++;
            return;
        }
        if (files == null) {
            SkyboxSkins.LOGGER.warn("Could not list skybox image directory {}.", directory);
            summary.failed++;
            return;
        }
        Arrays.sort(files);
        for (File file : files) {
            try {
                if (!file.isFile() || !isSkyboxImageFile(file)) {
                    continue;
                }
                String lowerName = file.getName().toLowerCase(Locale.ROOT);
                if (!libraryDirectory && ("mask.png".equals(lowerName) || "skybox.png".equals(lowerName))) {
                    continue;
                }

                String sourcePath = (libraryDirectory ? "skyboxes/" : "") + lowerName;
                if (indexedPaths.contains(sourcePath)) {
                    summary.alreadyIndexed++;
                    continue;
                }
                String fileId = lowerName.substring(0, lowerName.lastIndexOf('.'));
                if (libraryDirectory && isValidSkyboxId(fileId) && findSkybox(fileId) != null) {
                    indexedPaths.add(sourcePath);
                    summary.alreadyIndexed++;
                    continue;
                }

                SkyboxTextures.ImportResult result = SkyboxTextures.importIndexedImage(file, sourcePath);
                if (result.success) {
                    indexedPaths.add(sourcePath);
                    summary.added++;
                } else {
                    summary.failed++;
                    SkyboxSkins.LOGGER.warn("Could not index skybox image {}: {}", file, result.message);
                }
            } catch (SecurityException exception) {
                summary.failed++;
                SkyboxSkins.LOGGER.warn("Could not access skybox image {}.", file, exception);
            }
        }
    }

    private static boolean isSkyboxImageFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif");
    }

    private static final class IndexSummary {
        private int added;
        private int alreadyIndexed;
        private int failed;

        private String toMessage() {
            return "Skybox folder scan complete: " + added + " added, "
                    + alreadyIndexed + " already indexed, " + failed + " failed.";
        }
    }

    private void migrateLegacyPanorama() {
        File legacyImage = SkyboxClientHandler.getCustomPanoramaFile();
        if (legacyPanoramaMigrated || !legacyImage.isFile()) {
            return;
        }

        String id = UUID.randomUUID().toString();
        File destination = SkyboxClientHandler.getSkyboxFile(id);
        if (destination == null) {
            return;
        }
        try {
            File directory = destination.getParentFile();
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Could not create skybox library folder.");
            }
            Files.copy(legacyImage.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            getSkyboxes().add(new SkyboxEntry(id, makeUniqueSkyboxName("Previous skybox")));
            if (findSkybox(selectedSkyboxId) == null) {
                selectedSkyboxId = id;
            }
            legacyPanoramaMigrated = true;
            save();
        } catch (IOException | SecurityException exception) {
            SkyboxSkins.LOGGER.error("Could not add the previous skybox to the saved library.", exception);
        }
    }

    private static void requestImagePicker(boolean mask) {
        Minecraft minecraft = Minecraft.getMinecraft();
        SkyboxClientHandler.requestImagePicker(mask, minecraft.currentScreen);
    }

    private static void openCustomImageFolder() {
        try {
            File directory = SkyboxClientHandler.getCustomPanoramaFile().getParentFile();
            if (!directory.exists() && !directory.mkdirs()) {
                reportAction("Could not create image folder: " + directory.getAbsolutePath());
                return;
            }
            if (!Desktop.isDesktopSupported()) {
                reportAction("Opening folders is not supported on this system.");
                return;
            }
            Desktop.getDesktop().open(directory);
            reportAction("Opened the custom image folder.");
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            SkyboxSkins.LOGGER.warn("Could not open the custom image folder.", exception);
            reportAction("Could not open the custom image folder: " + exception.getMessage());
        }
    }
}
