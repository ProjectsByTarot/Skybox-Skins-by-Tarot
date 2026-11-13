package dev.projectsbytarot.skyboxskins.client;

public final class SkyboxConfig {
    private SkyboxConfig() {
    }

    public static boolean isEnabled() {
        return SkyboxOneConfig.skyboxEnabled;
    }

    public static void setEnabled(boolean value) {
        SkyboxOneConfig.skyboxEnabled = value;
        SkyboxOneConfig.saveSettings();
    }
}
