package zone.moddev.mc.electricadvantage.generation;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import zone.moddev.mc.lib.machine.neoforge.DefinedMachineEntity;

/** Runs battery arrays' backup discharge at the end of each level tick, after generators and loads have ticked. */
public final class GenerationSystems {
    private static final Map<ServerLevel, Set<BlockPos>> BACKUP = new HashMap<>();
    private GenerationSystems() {}

    static void queueBackup(ServerLevel level, BlockPos pos) {
        BACKUP.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(pos.immutable());
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((LevelTickEvent.Post event) -> {
            if (event.getLevel() instanceof ServerLevel level) {
                var pending = BACKUP.remove(level);
                if (pending != null) for (var pos : pending) {
                    if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof DefinedMachineEntity machine)
                        machine.features.findProcessor("batteries", BatteryArrayProcessor.class)
                                .ifPresent(array -> array.discharge(level));
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload event) -> {
            if (event.getLevel() instanceof ServerLevel level) BACKUP.remove(level);
        });
    }
}
