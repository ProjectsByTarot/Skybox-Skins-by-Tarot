package dev.projectsbytarot.skyboxskins.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Mouse;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;

public class SkyboxClientHandler {
    private static volatile boolean settingsScreenRequested;
    private static volatile boolean imagePickerRequested;
    private static boolean imagePickerMask;
    private static GuiScreen imagePickerParent;

    private boolean wasMiddleButtonDown;
    private RenderManager installedRenderManager;

    public static boolean isSkyboxEnabled(String playerName) {
        return SkyboxConfig.isEnabled() && SkyboxOneConfig.isPlayerWindowEnabled(playerName);
    }

    public static void requestSettingsScreen() {
        settingsScreenRequested = true;
    }

    public static void requestImagePicker(boolean mask, GuiScreen parent) {
        imagePickerMask = mask;
        imagePickerParent = parent;
        imagePickerRequested = true;
    }

    public static boolean isOnlinePlayer(EntityPlayer player) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (player == null) {
            return false;
        }
        if (player == minecraft.thePlayer) {
            return true;
        }

        NetHandlerPlayClient netHandler = minecraft.getNetHandler();
        return netHandler != null && netHandler.getPlayerInfo(player.getUniqueID()) != null;
    }

    public static File getCustomPanoramaFile() {
        return new File(getCustomDirectory(), "skybox.png");
    }

    public static File getCustomMaskFile() {
        return new File(getCustomDirectory(), "mask.png");
    }

    public static File getSkyboxLibraryDirectory() {
        return new File(getCustomDirectory(), "skyboxes");
    }

    public static File getSkyboxFile(String skyboxId) {
        if (!SkyboxOneConfig.isValidSkyboxId(skyboxId)) {
            return null;
        }
        return new File(getSkyboxLibraryDirectory(), skyboxId + ".png");
    }

    public static boolean reloadSkyboxTextures() {
        return SkyboxTextures.reload();
    }

    public static boolean isCustomMaskLoaded() {
        return SkyboxTextures.isCustomMaskLoaded();
    }

    static File getCustomDirectory() {
        return new File(new File(Minecraft.getMinecraft().mcDataDir, "config"), "skyboxskins");
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        openRequestedScreens(minecraft);

        if (minecraft.theWorld == null) {
            installedRenderManager = null;
            wasMiddleButtonDown = false;
            return;
        }

        installRenderers(minecraft.getRenderManager());

        boolean middleButtonDown = minecraft.currentScreen == null
                && minecraft.inGameHasFocus
                && Mouse.isButtonDown(2);
        if (middleButtonDown && !wasMiddleButtonDown) {
            toggleTargetedPlayer(minecraft);
        }
        wasMiddleButtonDown = middleButtonDown;
    }

    private void openRequestedScreens(Minecraft minecraft) {
        if (imagePickerRequested) {
            GuiScreen parent = imagePickerParent;
            if (parent == null && minecraft.currentScreen != null) {
                return;
            }
            if (parent != null && minecraft.currentScreen != parent) {
                imagePickerRequested = false;
                imagePickerParent = null;
                return;
            }

            boolean mask = imagePickerMask;
            imagePickerRequested = false;
            imagePickerParent = null;
            minecraft.displayGuiScreen(new SkyboxFilePickerScreen(parent, mask,
                    new File(System.getProperty("user.home", "."))));
            return;
        }

        if (settingsScreenRequested && minecraft.currentScreen == null) {
            settingsScreenRequested = false;
            if (SkyboxOneConfig.instance != null) {
                SkyboxOneConfig.instance.openGui();
            }
        }
    }

    private void toggleTargetedPlayer(Minecraft minecraft) {
        MovingObjectPosition target = minecraft.objectMouseOver;
        if (target == null
                || target.typeOfHit != MovingObjectPosition.MovingObjectType.ENTITY
                || !(target.entityHit instanceof EntityPlayer)) {
            return;
        }

        EntityPlayer player = (EntityPlayer) target.entityHit;
        if (!isOnlinePlayer(player)) {
            return;
        }

        SkyboxOneConfig.reportAction(SkyboxOneConfig.togglePlayerWindow(player.getName()));
    }

    @SuppressWarnings("unchecked")
    private void installRenderers(RenderManager renderManager) {
        if (renderManager == installedRenderManager) {
            return;
        }

        try {
            Field skinMapField = ReflectionHelper.findField(
                    RenderManager.class, "skinMap", "field_178636_l"
            );
            Map<String, RenderPlayer> skinMap =
                    (Map<String, RenderPlayer>) skinMapField.get(renderManager);
            if (skinMap == null || !skinMap.containsKey("default") || !skinMap.containsKey("slim")) {
                return;
            }

            skinMap.put("default", new SkyboxRenderPlayer(renderManager, false));
            skinMap.put("slim", new SkyboxRenderPlayer(renderManager, true));
            installedRenderManager = renderManager;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to install Skybox Skins by Tarot player renderers.", exception);
        }
    }

    private static class SkyboxRenderPlayer extends RenderPlayer {
        private SkyboxRenderPlayer(RenderManager renderManager, boolean useSmallArms) {
            super(renderManager, useSmallArms);
            this.mainModel = new SkyboxPlayerModel(useSmallArms);
        }
    }
}
