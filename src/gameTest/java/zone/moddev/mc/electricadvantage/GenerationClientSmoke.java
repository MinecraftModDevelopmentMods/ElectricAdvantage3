package zone.moddev.mc.electricadvantage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Opt-in client bootstrap smoke; never included in the release JAR. */
@EventBusSubscriber(modid = ElectricAdvantage.MOD_ID, value = Dist.CLIENT)
public final class GenerationClientSmoke {
    private static int titleTicks;
    private static int ticks;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("electricadvantage.clientSmoke")) return;
        var client = Minecraft.getInstance();
        if (client.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) onboarding.onClose();
        if (++ticks > 600) {
            ElectricAdvantage.LOGGER.error("EA3_CLIENT_BOOTSTRAP_TIMEOUT");
            client.stop();
        }
        if (client.screen instanceof TitleScreen && ++titleTicks >= 40) {
            ElectricAdvantage.LOGGER.info("EA3_CLIENT_BOOTSTRAP_PASS");
            client.stop();
        }
    }
}
