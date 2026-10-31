package zone.moddev.mc.electricadvantage;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import zone.moddev.mc.electricadvantage.init.ModItems;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(ElectricAdvantage.MOD_ID)
public final class ElectricAdvantage {
    public static final String MOD_ID = "electricadvantage";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ElectricAdvantage(IEventBus modBus) {
        new ModItems(modBus);
        LOGGER.info("Initializing Electric Advantage");
    }
}
