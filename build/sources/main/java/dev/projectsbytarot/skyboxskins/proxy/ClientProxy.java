package dev.projectsbytarot.skyboxskins.proxy;

import dev.projectsbytarot.skyboxskins.client.SkyboxClientHandler;
import dev.projectsbytarot.skyboxskins.client.SkyboxOneConfig;
import dev.projectsbytarot.skyboxskins.client.SkyboxSkinsCommand;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;

public class ClientProxy extends CommonProxy {
    @Override
    public void preInit() {
        FMLCommonHandler.instance().bus().register(new SkyboxClientHandler());
    }

    @Override
    public void init() {
        new SkyboxOneConfig();
        ClientCommandHandler.instance.registerCommand(new SkyboxSkinsCommand());
    }
}
