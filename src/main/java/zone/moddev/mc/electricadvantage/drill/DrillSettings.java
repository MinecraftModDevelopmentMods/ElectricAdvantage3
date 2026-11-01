package zone.moddev.mc.electricadvantage.drill;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * EA2 values at 4 FE per electricity: 500 (2000 FE) buffer, 24 (96 FE) per tick of mining, 250 (1000 FE)
 * per move. A face takes the sum over its blocks of max(factor x hardness, factor / 2) ticks.
 */
public record DrillSettings(long capacity,int rate,int progressCost,int moveCost,int range,float miningTimeFactor,
        int updateInterval,float beamDamage,int burnSeconds) {
    public static final Codec<DrillSettings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.validate(v->v>0?com.mojang.serialization.DataResult.success(v):com.mojang.serialization.DataResult.error(()->"Capacity must be positive")).optionalFieldOf("capacity",2000L).forGetter(DrillSettings::capacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("rate",4096).forGetter(DrillSettings::rate),
            Codec.intRange(0,1000000).optionalFieldOf("progress_cost",96).forGetter(DrillSettings::progressCost),
            Codec.intRange(0,1000000).optionalFieldOf("move_cost",1000).forGetter(DrillSettings::moveCost),
            Codec.intRange(1,256).optionalFieldOf("range",64).forGetter(DrillSettings::range),
            Codec.floatRange(0,10000).optionalFieldOf("mining_time_factor",32f).forGetter(DrillSettings::miningTimeFactor),
            Codec.intRange(1,200).optionalFieldOf("update_interval",8).forGetter(DrillSettings::updateInterval),
            Codec.floatRange(0,1000).optionalFieldOf("beam_damage",2f).forGetter(DrillSettings::beamDamage),
            Codec.intRange(0,60).optionalFieldOf("burn_seconds",3).forGetter(DrillSettings::burnSeconds)
    ).apply(i,DrillSettings::new));
}
