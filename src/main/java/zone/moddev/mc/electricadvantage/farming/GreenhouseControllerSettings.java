package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Costs are per greenhouse unit; EA2 used 8 electricity (32 FE) and 2 mB water per unit. */
public record GreenhouseControllerSettings(long energyCapacity,int rate,int waterCapacity,int fertilizerCapacity,
        int buffer,int production,int fePerUnit,int waterPerUnit,int fertilizerPerUnit,int maxChambers,int scanInterval) {
    public static final Codec<GreenhouseControllerSettings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.validate(v->v>0?com.mojang.serialization.DataResult.success(v):com.mojang.serialization.DataResult.error(()->"Capacity must be positive")).optionalFieldOf("energy_capacity",10000L).forGetter(GreenhouseControllerSettings::energyCapacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("rate",4096).forGetter(GreenhouseControllerSettings::rate),
            Codec.intRange(1,1000000).optionalFieldOf("water_capacity",4000).forGetter(GreenhouseControllerSettings::waterCapacity),
            Codec.intRange(1,1000000).optionalFieldOf("fertilizer_capacity",1000).forGetter(GreenhouseControllerSettings::fertilizerCapacity),
            Codec.intRange(1,1000000).optionalFieldOf("buffer",1000).forGetter(GreenhouseControllerSettings::buffer),
            Codec.intRange(1,1000000).optionalFieldOf("production",16).forGetter(GreenhouseControllerSettings::production),
            Codec.intRange(0,1000000).optionalFieldOf("fe_per_unit",32).forGetter(GreenhouseControllerSettings::fePerUnit),
            Codec.intRange(0,1000000).optionalFieldOf("water_per_unit",2).forGetter(GreenhouseControllerSettings::waterPerUnit),
            Codec.intRange(0,1000000).optionalFieldOf("fertilizer_per_unit",1).forGetter(GreenhouseControllerSettings::fertilizerPerUnit),
            Codec.intRange(1,4096).optionalFieldOf("max_chambers",16).forGetter(GreenhouseControllerSettings::maxChambers),
            Codec.intRange(1,1200).optionalFieldOf("scan_interval",20).forGetter(GreenhouseControllerSettings::scanInterval)
    ).apply(i,GreenhouseControllerSettings::new));
}
