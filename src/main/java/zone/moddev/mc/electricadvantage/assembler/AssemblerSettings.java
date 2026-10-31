package zone.moddev.mc.electricadvantage.assembler;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record AssemblerSettings(long capacity,int rate,int defaultTime,int defaultPower,int fePerElectricity,int exportRate,int planningSteps) {
    public static final Codec<AssemblerSettings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.validate(value->value>0?com.mojang.serialization.DataResult.success(value):com.mojang.serialization.DataResult.error(()->"Capacity must be positive")).optionalFieldOf("capacity",5000L).forGetter(AssemblerSettings::capacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("rate",4096).forGetter(AssemblerSettings::rate),
            Codec.intRange(1,1000000).optionalFieldOf("default_time",10).forGetter(AssemblerSettings::defaultTime),
            Codec.intRange(0,Integer.MAX_VALUE).optionalFieldOf("default_power_cost",25).forGetter(AssemblerSettings::defaultPower),
            Codec.intRange(1,1000000).optionalFieldOf("fe_per_electricity",4).forGetter(AssemblerSettings::fePerElectricity),
            Codec.intRange(0,1000000).optionalFieldOf("item_export",16).forGetter(AssemblerSettings::exportRate),
            Codec.intRange(1,65536).optionalFieldOf("planning_steps",2048).forGetter(AssemblerSettings::planningSteps)
    ).apply(i,AssemblerSettings::new));
}
