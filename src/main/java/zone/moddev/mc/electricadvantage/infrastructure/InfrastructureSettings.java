package zone.moddev.mc.electricadvantage.infrastructure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record InfrastructureSettings(String kind, int capacity, int rate, int cycleTicks, int cycleCost) {
    public static final Codec<InfrastructureSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.validate(value -> java.util.Set.of("cable", "scaffold", "switch", "led").contains(value)
                    ? com.mojang.serialization.DataResult.success(value)
                    : com.mojang.serialization.DataResult.error(() -> "Unknown infrastructure kind: " + value))
                    .fieldOf("kind").forGetter(InfrastructureSettings::kind),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("capacity", 4096).forGetter(InfrastructureSettings::capacity),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rate", 4096).forGetter(InfrastructureSettings::rate),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("cycle_ticks", 8).forGetter(InfrastructureSettings::cycleTicks),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("cycle_cost", 1).forGetter(InfrastructureSettings::cycleCost)
    ).apply(instance, InfrastructureSettings::new));
}
