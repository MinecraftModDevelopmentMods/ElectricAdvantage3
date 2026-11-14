package zone.moddev.mc.electricadvantage.processing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Set;

/**
 * Block-entity data for the Java processing machines: {@code smelting} (Electric Arc Furnace) or {@code crushing}
 * (Electric Rock Crusher). The oven, still and refinery are MMDLib XML machines in {@code mmdlib/machine/}.
 */
public record ProcessingSettings(String kind, int inputs, int outputs, int capacity, int rate,
        int ticks, int cost, int itemExport) {
    public static final Codec<ProcessingSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.validate(s -> Set.of("smelting", "crushing").contains(s)
                    ? DataResult.success(s) : DataResult.error(() -> "Unknown processing kind: " + s))
                    .fieldOf("kind").forGetter(ProcessingSettings::kind),
            Codec.intRange(1, 16).optionalFieldOf("inputs", 1).forGetter(ProcessingSettings::inputs),
            Codec.intRange(1, 16).optionalFieldOf("outputs", 1).forGetter(ProcessingSettings::outputs),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("capacity", 500).forGetter(ProcessingSettings::capacity),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rate", 4096).forGetter(ProcessingSettings::rate),
            Codec.intRange(1, 1000000).optionalFieldOf("ticks", 200).forGetter(ProcessingSettings::ticks),
            Codec.intRange(1, 1000000).optionalFieldOf("cost", 8).forGetter(ProcessingSettings::cost),
            Codec.intRange(0, 1000000).optionalFieldOf("item_export", 16).forGetter(ProcessingSettings::itemExport)
    ).apply(i, ProcessingSettings::new));
}
