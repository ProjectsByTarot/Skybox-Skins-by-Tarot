package dev.projectsbytarot.skyboxskins.client;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import java.util.Collections;
import java.util.List;

public class SkyboxSkinsCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "skyboxskins";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/skyboxskins [settings|reload]";
    }

    @Override
    public List<String> getCommandAliases() {
        return Collections.singletonList("skyboxsettings");
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) throws CommandException {
        if (arguments.length == 0
                || (arguments.length == 1 && "settings".equalsIgnoreCase(arguments[0]))) {
            openSettings();
            return;
        }
        if (arguments.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        if (!"reload".equalsIgnoreCase(arguments[0])) {
            throw new WrongUsageException(getCommandUsage(sender));
        }

        boolean customSkyboxLoaded = SkyboxClientHandler.reloadSkyboxTextures();
        boolean customMaskLoaded = SkyboxClientHandler.isCustomMaskLoaded();
        String panoramaStatus = customSkyboxLoaded
                ? "Selected skybox \"" + SkyboxOneConfig.getSkyboxName(
                        SkyboxOneConfig.getActiveSkyboxId()) + "\" loaded."
                : SkyboxOneConfig.getActiveSkyboxId().isEmpty()
                        ? "Using the built-in skybox."
                        : "The selected skybox could not be loaded; using the built-in skybox.";
        String maskStatus = customMaskLoaded
                ? " Custom mask loaded."
                : " Using the full-skin mask; optional mask path: "
                        + SkyboxClientHandler.getCustomMaskFile().getAbsolutePath() + ".";
        sender.addChatMessage(new ChatComponentText(panoramaStatus + maskStatus));
    }

    private void openSettings() {
        SkyboxClientHandler.requestSettingsScreen();
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
