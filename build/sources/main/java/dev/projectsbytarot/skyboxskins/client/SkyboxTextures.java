package dev.projectsbytarot.skyboxskins.client;

import dev.projectsbytarot.skyboxskins.SkyboxSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

public final class SkyboxTextures {
    private static final int MASK_SIZE = 64;

    private static final Map<String, ResourceLocation> skyboxLocations =
            new HashMap<String, ResourceLocation>();
    private static final Map<String, Boolean> customSkyboxStatuses = new HashMap<String, Boolean>();
    private static ResourceLocation maskLocation;
    private static boolean customMaskLoaded;

    private SkyboxTextures() {
    }

    public static ResourceLocation getSkyboxLocation() {
        return getSkyboxLocationById(SkyboxOneConfig.getActiveSkyboxId());
    }

    public static ResourceLocation getSkyboxLocation(String playerName) {
        String playerSkyboxId = SkyboxOneConfig.getSkyboxIdForPlayer(playerName);
        ResourceLocation playerSkybox = getSkyboxLocationById(playerSkyboxId);
        if (playerSkyboxId == null || playerSkyboxId.isEmpty()
                || Boolean.TRUE.equals(customSkyboxStatuses.get(playerSkyboxId))) {
            return playerSkybox;
        }
        return getSkyboxLocation();
    }

    public static ResourceLocation getMaskLocation() {
        loadMaskIfNeeded();
        return maskLocation;
    }

    public static boolean reload() {
        for (ResourceLocation location : skyboxLocations.values()) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        }
        if (maskLocation != null) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(maskLocation);
        }
        skyboxLocations.clear();
        customSkyboxStatuses.clear();
        maskLocation = null;
        customMaskLoaded = false;
        getSkyboxLocation();
        loadMaskIfNeeded();
        return isCustomSkyboxLoaded();
    }

    public static boolean isCustomSkyboxLoaded() {
        String skyboxId = SkyboxOneConfig.getActiveSkyboxId();
        getSkyboxLocationById(skyboxId);
        return Boolean.TRUE.equals(customSkyboxStatuses.get(skyboxId == null ? "" : skyboxId));
    }

    public static boolean isCustomMaskLoaded() {
        loadMaskIfNeeded();
        return customMaskLoaded;
    }

    public static ImportResult importImage(File source, boolean mask) {
        return importImage(source, mask, true, null);
    }

    static ImportResult importIndexedImage(File source, String sourcePath) {
        return importImage(source, false, false, sourcePath);
    }

    private static ImportResult importImage(
            File source,
            boolean mask,
            boolean selectSkybox,
            String sourcePath
    ) {
        BufferedImage image;
        try {
            if (source == null || !source.isFile()) {
                return ImportResult.failure("Selected file does not exist.");
            }
            image = readImage(source, mask);
        } catch (IOException | SecurityException exception) {
            SkyboxSkins.LOGGER.error("Could not read selected skybox image {}.", source, exception);
            return ImportResult.failure("Could not read image: " + exception.getMessage());
        }

        String skyboxId = mask ? null : UUID.randomUUID().toString();
        String skyboxName = mask ? null : getSkyboxName(source);
        File destination = mask
                ? SkyboxClientHandler.getCustomMaskFile()
                : SkyboxClientHandler.getSkyboxFile(skyboxId);
        if (destination == null) {
            return ImportResult.failure("Could not create a saved skybox entry.");
        }
        File directory = destination.getParentFile();
        File temporary = null;
        try {
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Could not create image folder: " + directory.getAbsolutePath());
            }
            temporary = Files.createTempFile(directory.toPath(), "skyboxskins-", ".png").toFile();
            if (!ImageIO.write(image, "PNG", temporary)) {
                throw new IOException("PNG encoder is not available.");
            }
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        } catch (IOException | SecurityException exception) {
            SkyboxSkins.LOGGER.error("Could not import skybox image from {} to {}.", source, destination, exception);
            return ImportResult.failure("Could not save image: " + exception.getMessage());
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary.toPath());
                } catch (IOException | SecurityException exception) {
                    SkyboxSkins.LOGGER.warn("Could not remove temporary image file {}.", temporary, exception);
                }
            }
        }

        boolean skyboxAdded = !mask && (selectSkybox
                ? SkyboxOneConfig.addSkybox(skyboxId, skyboxName)
                : SkyboxOneConfig.addIndexedSkybox(skyboxId, skyboxName, sourcePath));
        if (!mask && !skyboxAdded) {
            try {
                Files.deleteIfExists(destination.toPath());
            } catch (IOException | SecurityException exception) {
                SkyboxSkins.LOGGER.warn("Could not remove unregistered skybox image {}.", destination,
                        exception);
            }
            return ImportResult.failure("Could not add the imported image to the skybox library.");
        }
        if (!mask) {
            skyboxName = SkyboxOneConfig.getSkyboxName(skyboxId);
        }

        if (!mask && !selectSkybox) {
            return ImportResult.success("Added \"" + skyboxName + "\" to the skybox library.");
        }

        reload();
        if (mask && !customMaskLoaded) {
            return ImportResult.failure("The selected mask could not be loaded.");
        }
        if (!mask && !isCustomSkyboxLoaded()) {
            return ImportResult.failure("The selected skybox could not be loaded.");
        }
        return ImportResult.success(mask ? "Custom mask loaded."
                : "Added and selected skybox \"" + skyboxName + "\".");
    }

    private static boolean isCubemap3x2(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        return w > 0 && h > 0 && w % 3 == 0 && h % 2 == 0 && w / 3 == h / 2;
    }

    private static BufferedImage cubemapToEquirectangular(BufferedImage src) {
        int faceSize = src.getWidth() / 3;
        int outW = faceSize * 4;
        int outH = faceSize * 2;

        // Slice the 3x2 grid:
        // Top row: Bottom (-Y), Top (+Y), South (+Z)
        // Bottom row: West (-X), North (-Z), East (+X)
        BufferedImage bottom = src.getSubimage(0, 0, faceSize, faceSize);
        BufferedImage top    = src.getSubimage(faceSize, 0, faceSize, faceSize);
        BufferedImage south  = src.getSubimage(faceSize * 2, 0, faceSize, faceSize);
        BufferedImage west   = src.getSubimage(0, faceSize, faceSize, faceSize);
        BufferedImage north  = src.getSubimage(faceSize, faceSize, faceSize, faceSize);
        BufferedImage east   = src.getSubimage(faceSize * 2, faceSize, faceSize, faceSize);

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);

        for (int py = 0; py < outH; py++) {
            double v = 1.0 - (py + 0.5) / outH;
            double phi = (v - 0.5) * Math.PI; // Elevation: -pi/2 (bottom) to +pi/2 (top)
            double cosPhi = Math.cos(phi);
            double sinPhi = Math.sin(phi);

            for (int px = 0; px < outW; px++) {
                double u = (px + 0.5) / outW;
                double theta = (u - 0.5) * 2.0 * Math.PI; // Azimuth: -pi to +pi

                // Invert the shader's atan(direction.z, direction.x) mapping.
                double dx = cosPhi * Math.cos(theta);
                double dy = sinPhi;
                double dz = cosPhi * Math.sin(theta);

                double absX = Math.abs(dx);
                double absY = Math.abs(dy);
                double absZ = Math.abs(dz);

                BufferedImage face;
                double sc, tc, ma;

                if (absX >= absY && absX >= absZ) {
                    ma = absX;
                    if (dx > 0) { // East (+X), viewed from inside the cube
                        face = east;
                        sc = dz;
                        tc = -dy;
                    } else {      // West (-X)
                        face = west;
                        sc = -dz;
                        tc = -dy;
                    }
                } else if (absY >= absX && absY >= absZ) {
                    ma = absY;
                    if (dy > 0) { // Top (+Y) - rotated to match OptiFine horizon
                        face = top;
                        sc = dx;
                        tc = -dz;
                    } else {      // Bottom (-Y) - rotated to match OptiFine horizon
                        face = bottom;
                        sc = dx;
                        tc = dz;
                    }
                } else {
                    ma = absZ;
                    if (dz > 0) { // South (+Z)
                        face = south;
                        sc = -dx;
                        tc = -dy;
                    } else {      // North (-Z)
                        face = north;
                        sc = dx;
                        tc = -dy;
                    }
                }

                double fu = 0.5 * (sc / ma + 1.0);
                double fv = 0.5 * (tc / ma + 1.0);

                int sx = Math.max(0, Math.min(faceSize - 1, (int) (fu * faceSize)));
                int sy = Math.max(0, Math.min(faceSize - 1, (int) (fv * faceSize)));

                out.setRGB(px, py, face.getRGB(sx, sy));
            }
        }
        return out;
    }


    public static String removeImage(boolean mask) {
        if (!mask) {
            return SkyboxOneConfig.useBuiltInSkybox();
        }

        File image = mask
                ? SkyboxClientHandler.getCustomMaskFile()
                : SkyboxClientHandler.getCustomPanoramaFile();
        if (image.exists() && !image.delete()) {
            return "Could not remove image: " + image.getAbsolutePath();
        }

        reload();
        return "Using the default full-skin mask.";
    }

    private static ResourceLocation getSkyboxLocationById(String skyboxId) {
        String key = skyboxId == null ? "" : skyboxId;
        if (!key.isEmpty() && !SkyboxOneConfig.isValidSkyboxId(key)) {
            key = "";
        }
        ResourceLocation location = skyboxLocations.get(key);
        if (location != null) {
            return location;
        }

        BufferedImage panorama = null;
        if (!key.isEmpty()) {
            File file = SkyboxClientHandler.getSkyboxFile(key);
            if (file == null || !file.isFile()) {
                SkyboxSkins.LOGGER.warn("Saved skybox {} is missing; using the built-in skybox.", key);
            } else {
                panorama = loadImage(file,
                        "Expected an image at least 16x16, with dimensions no larger than 4096x4096 and 8 megapixels.",
                        true);
            }
        }

        boolean customLoaded = panorama != null;
        if (panorama == null) {
            panorama = createBuiltInSkybox();
        } else if (isCubemap3x2(panorama)) {
            panorama = cubemapToEquirectangular(panorama);
        }
        String textureName = key.isEmpty() ? "skybox_skins_panorama_default"
                : "skybox_skins_panorama_" + key;
        location = registerTexture(textureName, panorama, true);
        skyboxLocations.put(key, location);
        customSkyboxStatuses.put(key, customLoaded);
        return location;
    }

    private static void loadMaskIfNeeded() {
        if (maskLocation != null) {
            return;
        }

        File directory = SkyboxClientHandler.getCustomPanoramaFile().getParentFile();
        if (!directory.exists() && !directory.mkdirs()) {
            SkyboxSkins.LOGGER.warn("Could not create custom skybox directory: {}", directory);
        }

        BufferedImage mask = loadImage(
                SkyboxClientHandler.getCustomMaskFile(),
                "Expected a 64x64 PNG mask; white pixels show the skybox and black pixels keep the normal skin.",
                false
        );
        customMaskLoaded = mask != null;
        if (mask == null) {
            mask = createDefaultMask();
        }

        maskLocation = registerTexture("skybox_skins_mask", mask, false);
    }

    private static String getSkyboxName(File source) {
        String name = source.getName();
        int extension = name.lastIndexOf('.');
        if (extension > 0) {
            name = name.substring(0, extension);
        }
        name = name.trim();
        if (name.isEmpty()) {
            return "Skybox";
        }
        return name.length() > 48 ? name.substring(0, 48) : name;
    }

    private static BufferedImage loadImage(File file, String expectedFormat, boolean panorama) {
        if (!file.isFile()) {
            return null;
        }

        try {
            return readImage(file, !panorama);
        } catch (IOException exception) {
            SkyboxSkins.LOGGER.error(
                    "Could not load image at {}. {} Using the default texture.",
                    file, expectedFormat, exception
            );
            return null;
        }
    }

    private static BufferedImage readImage(File file, boolean mask) throws IOException {
        String expectedFormat = mask
                ? "Expected a 64x64 image."
                : "Expected an image at least 16x16, at most 4096x4096 and no larger than 8 megapixels.";
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) {
                throw new IOException("Selected file is not a supported image.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Selected file is not a supported image.");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (mask) {
                    if (width != MASK_SIZE || height != MASK_SIZE) {
                        throw new IOException(expectedFormat);
                    }
                } else {
                    long pixelCount = (long) width * height;
                    if (width < 16 || height < 16 || width > 4096 || height > 4096
                            || pixelCount > 8388608L) {
                        throw new IOException(expectedFormat);
                    }
                    boolean exactCubemap = width % 3 == 0 && height % 2 == 0
                            && width / 3 == height / 2;
                    boolean nearCubemap = Math.abs((width / 3.0) - (height / 2.0)) <= 2.0;
                    if (nearCubemap && !exactCubemap) {
                        throw new IOException("A cubemap atlas must be an exact 3x2 grid of square faces.");
                    }
                }

                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw new IOException("Selected file is not a supported image.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    public static final class ImportResult {
        public final boolean success;
        public final String message;

        private ImportResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        private static ImportResult success(String message) {
            return new ImportResult(true, message);
        }

        private static ImportResult failure(String message) {
            return new ImportResult(false, message);
        }
    }

    private static ResourceLocation registerTexture(String name, BufferedImage image, boolean repeatHorizontally) {
        DynamicTexture texture = new DynamicTexture(image.getWidth(), image.getHeight());
        int[] textureData = texture.getTextureData();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                textureData[y * image.getWidth() + x] = 0xFF000000 | image.getRGB(x, y) & 0x00FFFFFF;
            }
        }
        texture.updateDynamicTexture();
        ResourceLocation location = Minecraft.getMinecraft().getTextureManager()
                .getDynamicTextureLocation(name, texture);
        if (repeatHorizontally) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(location);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        }
        return location;
    }

    private static BufferedImage createDefaultMask() {
        BufferedImage image = new BufferedImage(MASK_SIZE, MASK_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < MASK_SIZE; y++) {
            for (int x = 0; x < MASK_SIZE; x++) {
                image.setRGB(x, y, 0xFFFFFFFF);
            }
        }
        return image;
    }

    private static BufferedImage createBuiltInSkybox() {
        int width = 512;
        int height = 256;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            double latitude = (0.5 - (y + 0.5) / height) * Math.PI;
            double horizontal = Math.cos(latitude);
            for (int x = 0; x < width; x++) {
                double longitude = ((x + 0.5) / width * 2.0 - 1.0) * Math.PI;
                double dx = horizontal * Math.cos(longitude);
                double dy = Math.sin(latitude);
                double dz = horizontal * Math.sin(longitude);
                image.setRGB(x, y, getGeneratedSkyPixel(dx, dy, dz));
            }
        }
        return image;
    }

    private static int getGeneratedSkyPixel(double dx, double dy, double dz) {
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;

        double zenith = smoothStep(-0.15, 0.85, dy);
        int red = 118 - (int) (78 * zenith);
        int green = 190 - (int) (91 * zenith);
        int blue = 238 - (int) (42 * zenith);

        double cloud = 0.52 * valueNoise(dx, dy, dz, 5)
                + 0.30 * valueNoise(dx, dy, dz, 11)
                + 0.18 * valueNoise(dx, dy, dz, 23);
        double cloudBand = Math.exp(-Math.pow((dy - 0.12) / 0.55, 2.0));
        double cloudShadow = smoothStep(0.48, 0.58, cloud) * cloudBand;
        double cloudLight = smoothStep(0.55, 0.68, cloud) * cloudBand;
        red -= (int) (26 * cloudShadow * (1.0 - cloudLight));
        green -= (int) (24 * cloudShadow * (1.0 - cloudLight));
        blue -= (int) (20 * cloudShadow * (1.0 - cloudLight));
        red += (int) ((250 - red) * cloudLight);
        green += (int) ((250 - green) * cloudLight);
        blue += (int) ((255 - blue) * cloudLight);

        double sunDot = dx * 0.22 + dy * 0.48 - dz * 0.85;
        double sunGlow = smoothStep(Math.cos(0.55), Math.cos(0.12), sunDot) * 0.35;
        double sunDisc = smoothStep(Math.cos(0.12), Math.cos(0.07), sunDot);
        red += (int) ((255 - red) * Math.max(sunGlow, sunDisc));
        green += (int) ((244 - green) * Math.max(sunGlow, sunDisc));
        blue += (int) ((210 - blue) * Math.max(sunGlow, sunDisc));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static double valueNoise(double x, double y, double z, int frequency) {
        double gridX = x * frequency;
        double gridY = y * frequency;
        double gridZ = z * frequency;
        int x0 = (int) Math.floor(gridX);
        int y0 = (int) Math.floor(gridY);
        int z0 = (int) Math.floor(gridZ);
        double tx = smoothStep(0.0, 1.0, gridX - x0);
        double ty = smoothStep(0.0, 1.0, gridY - y0);
        double tz = smoothStep(0.0, 1.0, gridZ - z0);

        double zLow = interpolate(
                interpolate(randomValue(x0, y0, z0), randomValue(x0 + 1, y0, z0), tx),
                interpolate(randomValue(x0, y0 + 1, z0), randomValue(x0 + 1, y0 + 1, z0), tx),
                ty
        );
        double zHigh = interpolate(
                interpolate(randomValue(x0, y0, z0 + 1), randomValue(x0 + 1, y0, z0 + 1), tx),
                interpolate(randomValue(x0, y0 + 1, z0 + 1), randomValue(x0 + 1, y0 + 1, z0 + 1), tx),
                ty
        );
        return interpolate(zLow, zHigh, tz);
    }

    private static double randomValue(int x, int y, int z) {
        int value = x * 374761393 + y * 668265263 + z * 1442695041 + 0x9E3779B9;
        value = (value ^ value >>> 13) * 1274126177;
        value ^= value >>> 16;
        return (value & 0x7FFFFFFF) / (double) Integer.MAX_VALUE;
    }

    private static double smoothStep(double edge0, double edge1, double value) {
        double t = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    private static double interpolate(double from, double to, double amount) {
        return from + (to - from) * amount;
    }
}
