package cc.hyperium.launch

import cc.hyperium.Hyperium
import cc.hyperium.internal.addons.AddonBootstrap
import net.minecraft.launchwrapper.LaunchClassLoader
import org.spongepowered.asm.launch.MixinBootstrap
import org.spongepowered.asm.mixin.MixinEnvironment

object HyperiumTweakerInjector {
    @JvmStatic
    fun injectIntoClassLoader(optifine: Boolean) {
        Hyperium.LOGGER.info("Loading Addons...")
        Hyperium.LOGGER.info("Initialising Bootstraps...")
        MixinBootstrap.init()
        AddonBootstrap.init()
        Hyperium.LOGGER.info("Applying transformers...")
        val environment = MixinEnvironment.getDefaultEnvironment()

        if (optifine) {
            environment.obfuscationContext = "notch"
        }

        if (environment.obfuscationContext == null) {
            environment.obfuscationContext = "notch"
        }

        environment.side = MixinEnvironment.Side.CLIENT
    }

    fun callFromClassloader(classLoader: LaunchClassLoader?, hasOptifine: Boolean) {
        try {
            val clazz = Class.forName("cc.hyperium.launch.HyperiumTweakerInjector", false, classLoader)
            val m = clazz.getMethod("injectIntoClassLoader", *arrayOf<Class<*>>(Boolean::class.java))
            m.invoke(null, hasOptifine)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}