package dev.projectsbytarot.skyboxskins;

import dev.projectsbytarot.skyboxskins.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = SkyboxSkins.MODID,
        name = SkyboxSkins.NAME,
        version = SkyboxSkins.VERSION,
        clientSideOnly = true
)
public class SkyboxSkins {
    public static final String MODID = "skyboxskins";
    public static final String NAME = "Skybox Skins by Tarot";
    public static final String VERSION = "1.0.13";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @SidedProxy(
            clientSide = "dev.projectsbytarot.skyboxskins.proxy.ClientProxy",
            serverSide = "dev.projectsbytarot.skyboxskins.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init();
    }
}
