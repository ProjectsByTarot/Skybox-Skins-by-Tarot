package dev.projectsbytarot.skyboxskins.client;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

public class SkyboxPlayerModel extends ModelPlayer {
    public SkyboxPlayerModel(boolean useSmallArms) {
        super(0.0F, useSmallArms);
    }

    @Override
    public void render(
            Entity entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scale
    ) {
        if (!(entity instanceof AbstractClientPlayer)) {
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            return;
        }

        AbstractClientPlayer player = (AbstractClientPlayer) entity;
        if (player.isInvisible()
                || !SkyboxClientHandler.isOnlinePlayer(player)
                || !SkyboxClientHandler.isSkyboxEnabled(player.getName())) {
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            return;
        }

        float partialTicks = ageInTicks - player.ticksExisted;
        SkyboxShader.RenderState shaderState = SkyboxShader.begin(partialTicks, player.getName());
        if (shaderState == null) {
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
            return;
        }

        ModelRenderer[] outerLayer = {
                bipedHeadwear,
                bipedBodyWear,
                bipedLeftArmwear,
                bipedRightArmwear,
                bipedLeftLegwear,
                bipedRightLegwear
        };
        boolean[] wasVisible = new boolean[outerLayer.length];
        for (int i = 0; i < outerLayer.length; i++) {
            wasVisible[i] = outerLayer[i].showModel;
            outerLayer[i].showModel = false;
        }

        try {
            super.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            for (int i = 0; i < outerLayer.length; i++) {
                outerLayer[i].showModel = wasVisible[i];
            }
            shaderState.restore();
        }
    }
}
