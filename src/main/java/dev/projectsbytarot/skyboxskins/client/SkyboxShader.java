package dev.projectsbytarot.skyboxskins.client;

import dev.projectsbytarot.skyboxskins.SkyboxSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBFragmentShader;
import org.lwjgl.opengl.ARBShaderObjects;
import org.lwjgl.opengl.ARBVertexShader;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;
import org.lwjgl.util.vector.Matrix4f;
import org.lwjgl.util.vector.Vector3f;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;

public final class SkyboxShader {
    private static int programId;
    private static boolean initialized;

    private SkyboxShader() {
    }

    public static boolean initialize() {
        if (initialized) {
            return programId != 0;
        }
        initialized = true;

        if (!GLContext.getCapabilities().GL_ARB_shader_objects
                || !GLContext.getCapabilities().GL_ARB_vertex_shader
                || !GLContext.getCapabilities().GL_ARB_fragment_shader) {
            SkyboxSkins.LOGGER.error("This graphics device does not support the ARB shaders required for skybox windows.");
            return false;
        }

        int vertexShader = compileShader(
                new ResourceLocation(SkyboxSkins.MODID, "shaders/sky_window.vsh"),
                ARBVertexShader.GL_VERTEX_SHADER_ARB
        );
        int fragmentShader = compileShader(
                new ResourceLocation(SkyboxSkins.MODID, "shaders/sky_window.fsh"),
                ARBFragmentShader.GL_FRAGMENT_SHADER_ARB
        );
        if (vertexShader == 0 || fragmentShader == 0) {
            if (vertexShader != 0) {
                ARBShaderObjects.glDeleteObjectARB(vertexShader);
            }
            if (fragmentShader != 0) {
                ARBShaderObjects.glDeleteObjectARB(fragmentShader);
            }
            return false;
        }

        int program = ARBShaderObjects.glCreateProgramObjectARB();
        ARBShaderObjects.glAttachObjectARB(program, vertexShader);
        ARBShaderObjects.glAttachObjectARB(program, fragmentShader);
        ARBShaderObjects.glLinkProgramARB(program);
        ARBShaderObjects.glDeleteObjectARB(vertexShader);
        ARBShaderObjects.glDeleteObjectARB(fragmentShader);

        if (ARBShaderObjects.glGetObjectParameteriARB(
                program, ARBShaderObjects.GL_OBJECT_LINK_STATUS_ARB
        ) == GL11.GL_FALSE) {
            SkyboxSkins.LOGGER.error("Could not link skybox window shader: {}",
                    ARBShaderObjects.glGetInfoLogARB(program, 4096));
            ARBShaderObjects.glDeleteObjectARB(program);
            return false;
        }

        programId = program;
        return true;
    }

    public static RenderState begin(float partialTicks, String playerName) {
        if (!initialize()) {
            return null;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Entity viewEntity = minecraft.getRenderViewEntity();
        if (viewEntity == null) {
            return null;
        }

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture0 = getTextureBinding(GL13.GL_TEXTURE0);
        int previousTexture1 = getTextureBinding(GL13.GL_TEXTURE1);
        int previousTexture2 = getTextureBinding(GL13.GL_TEXTURE2);

        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        ResourceLocation maskLocation = SkyboxTextures.getMaskLocation();
        ResourceLocation skyboxLocation = SkyboxTextures.getSkyboxLocation(playerName);
        GlStateManager.bindTexture(previousTexture0);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
        minecraft.getTextureManager().bindTexture(maskLocation);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE2);
        minecraft.getTextureManager().bindTexture(skyboxLocation);
        GlStateManager.setActiveTexture(previousActiveTexture);

        ARBShaderObjects.glUseProgramObjectARB(programId);
        setUniformInt("skinTex", 0);
        setUniformInt("maskTex", 1);
        setUniformInt("skyboxTex", 2);
        setUniformMatrix("viewRotationInverse", getInverseCameraRotation(viewEntity, partialTicks));

        return new RenderState(previousProgram, previousActiveTexture, previousTexture0, previousTexture1,
                previousTexture2);
    }

    public static void setUniformInt(String name, int value) {
        int location = ARBShaderObjects.glGetUniformLocationARB(programId, name);
        if (location >= 0) {
            ARBShaderObjects.glUniform1iARB(location, value);
        }
    }

    private static void setUniformMatrix(String name, Matrix4f matrix) {
        int location = ARBShaderObjects.glGetUniformLocationARB(programId, name);
        if (location >= 0) {
            FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
            matrix.store(buffer);
            buffer.flip();
            ARBShaderObjects.glUniformMatrix4ARB(location, false, buffer);
        }
    }

    private static int getTextureBinding(int textureUnit) {
        GlStateManager.setActiveTexture(textureUnit);
        return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
    }

    private static Matrix4f getInverseCameraRotation(Entity viewEntity, float partialTicks) {
        float yaw = viewEntity.prevRotationYaw
                + (viewEntity.rotationYaw - viewEntity.prevRotationYaw) * partialTicks;
        float pitch = viewEntity.prevRotationPitch
                + (viewEntity.rotationPitch - viewEntity.prevRotationPitch) * partialTicks;

        Matrix4f inverseRotation = new Matrix4f();
        inverseRotation.setIdentity();
        Matrix4f.rotate((float) Math.toRadians(yaw + 180.0F), new Vector3f(0.0F, 1.0F, 0.0F),
                inverseRotation, inverseRotation);
        Matrix4f.rotate((float) Math.toRadians(-pitch), new Vector3f(1.0F, 0.0F, 0.0F),
                inverseRotation, inverseRotation);
        return inverseRotation;
    }

    private static int compileShader(ResourceLocation location, int shaderType) {
        String source;
        try {
            source = readShader(location);
        } catch (IOException exception) {
            SkyboxSkins.LOGGER.error("Could not read skybox shader {}", location, exception);
            return 0;
        }

        int shader = ARBShaderObjects.glCreateShaderObjectARB(shaderType);
        ARBShaderObjects.glShaderSourceARB(shader, source);
        ARBShaderObjects.glCompileShaderARB(shader);
        if (ARBShaderObjects.glGetObjectParameteriARB(
                shader, ARBShaderObjects.GL_OBJECT_COMPILE_STATUS_ARB
        ) == GL11.GL_FALSE) {
            SkyboxSkins.LOGGER.error("Could not compile skybox shader {}: {}",
                    location, ARBShaderObjects.glGetInfoLogARB(shader, 4096));
            ARBShaderObjects.glDeleteObjectARB(shader);
            return 0;
        }
        return shader;
    }

    private static String readShader(ResourceLocation location) throws IOException {
        InputStream input = Minecraft.getMinecraft().getResourceManager().getResource(location).getInputStream();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            StringBuilder source = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                source.append(line).append('\n');
            }
            return source.toString();
        }
    }

    public static final class RenderState {
        private final int previousProgram;
        private final int previousActiveTexture;
        private final int previousTexture0;
        private final int previousTexture1;
        private final int previousTexture2;

        private RenderState(
                int previousProgram,
                int previousActiveTexture,
                int previousTexture0,
                int previousTexture1,
                int previousTexture2
        ) {
            this.previousProgram = previousProgram;
            this.previousActiveTexture = previousActiveTexture;
            this.previousTexture0 = previousTexture0;
            this.previousTexture1 = previousTexture1;
            this.previousTexture2 = previousTexture2;
        }

        public void restore() {
            ARBShaderObjects.glUseProgramObjectARB(previousProgram);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.bindTexture(previousTexture0);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE1);
            GlStateManager.bindTexture(previousTexture1);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE2);
            GlStateManager.bindTexture(previousTexture2);
            GlStateManager.setActiveTexture(previousActiveTexture);
        }
    }
}
