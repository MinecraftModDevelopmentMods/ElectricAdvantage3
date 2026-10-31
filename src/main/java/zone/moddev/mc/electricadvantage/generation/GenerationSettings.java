package zone.moddev.mc.electricadvantage.generation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GenerationSettings(String kind, int capacity, int rate, double generation,
        int steamCapacity, int steamPerElectricity, double electricityPerFe, double blockLightFactor,
        double rainFactor, double thunderFactor, float propellerDamage) {
    public static final Codec<GenerationSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.validate(s -> java.util.Set.of("solar", "hydro", "steam", "array").contains(s)
                    ? com.mojang.serialization.DataResult.success(s)
                    : com.mojang.serialization.DataResult.error(() -> "Unknown generator kind: " + s)).fieldOf("kind").forGetter(GenerationSettings::kind),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("capacity", 1000).forGetter(GenerationSettings::capacity),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("rate", 4096).forGetter(GenerationSettings::rate),
            Codec.doubleRange(0, 1000000).optionalFieldOf("generation_per_tick", 4.0).forGetter(GenerationSettings::generation),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("steam_capacity", 32000).forGetter(GenerationSettings::steamCapacity),
            Codec.intRange(1, 1000000).optionalFieldOf("steam_per_electricity", 32).forGetter(GenerationSettings::steamPerElectricity),
            Codec.doubleRange(0.000001, 1000000).optionalFieldOf("electricity_per_fe", 0.25).forGetter(GenerationSettings::electricityPerFe),
            Codec.doubleRange(0, 1).optionalFieldOf("block_light_factor", 0.1).forGetter(GenerationSettings::blockLightFactor),
            Codec.doubleRange(0, 1).optionalFieldOf("rain_factor", 0.35).forGetter(GenerationSettings::rainFactor),
            Codec.doubleRange(0, 1).optionalFieldOf("thunder_factor", 0.2).forGetter(GenerationSettings::thunderFactor),
            Codec.floatRange(0, 1000).optionalFieldOf("propeller_damage", 5f).forGetter(GenerationSettings::propellerDamage)
    ).apply(i, GenerationSettings::new));
    public int steamPerFe() {
        double amount = steamPerElectricity * electricityPerFe;
        if (!Double.isFinite(amount) || amount < 1 || amount > Integer.MAX_VALUE || amount != Math.rint(amount))
            throw new IllegalArgumentException("Steam conversion must be a positive whole number of mB per FE");
        return (int)amount;
    }
}
