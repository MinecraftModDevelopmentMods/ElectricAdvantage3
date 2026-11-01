package zone.moddev.mc.electricadvantage.pump;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** FE costs are EA2's electricity costs at 4 FE per electricity (pipe 100, pump 1000 + 32 per block of depth). */
public record PumpSettings(long capacity,int rate,int tankCapacity,int pumpInterval,int pipeInterval,int pipeCost,
        int pumpCost,int depthCost,int fluidExport,int infiniteRecheck) {
    public static final Codec<PumpSettings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.LONG.validate(v->v>0?com.mojang.serialization.DataResult.success(v):com.mojang.serialization.DataResult.error(()->"Capacity must be positive")).optionalFieldOf("capacity",40000L).forGetter(PumpSettings::capacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("rate",4096).forGetter(PumpSettings::rate),
            Codec.intRange(1000,1000000).optionalFieldOf("tank_capacity",4000).forGetter(PumpSettings::tankCapacity),
            Codec.intRange(1,1200).optionalFieldOf("pump_interval",20).forGetter(PumpSettings::pumpInterval),
            Codec.intRange(1,1200).optionalFieldOf("pipe_interval",11).forGetter(PumpSettings::pipeInterval),
            Codec.intRange(0,1000000).optionalFieldOf("pipe_cost",400).forGetter(PumpSettings::pipeCost),
            Codec.intRange(0,1000000).optionalFieldOf("pump_cost",4000).forGetter(PumpSettings::pumpCost),
            Codec.intRange(0,1000000).optionalFieldOf("depth_cost",128).forGetter(PumpSettings::depthCost),
            Codec.intRange(0,1000000).optionalFieldOf("fluid_export",1000).forGetter(PumpSettings::fluidExport),
            Codec.intRange(1,72000).optionalFieldOf("infinite_recheck",1200).forGetter(PumpSettings::infiniteRecheck)
    ).apply(i,PumpSettings::new));
}
