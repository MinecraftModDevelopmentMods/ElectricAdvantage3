package zone.moddev.mc.electricadvantage.turret;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** EA2 values: 1000 electricity (4000 FE) per shot, 1 (4 FE) per active tick, 31-tick charge, hit at 7, beam for 10. */
public record TurretSettings(boolean evil,long capacity,int rate,int shotCost,int idleCost,float damage,int burnSeconds,
        int targetRange,int attackRange,int chargeTime,int hitTime,int blastTime,int scanInterval,boolean igniteGround) {
    public static final Codec<TurretSettings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.BOOL.optionalFieldOf("evil",false).forGetter(TurretSettings::evil),
            Codec.LONG.validate(v->v>0?com.mojang.serialization.DataResult.success(v):com.mojang.serialization.DataResult.error(()->"Capacity must be positive")).optionalFieldOf("capacity",8000L).forGetter(TurretSettings::capacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("rate",4096).forGetter(TurretSettings::rate),
            Codec.intRange(0,1000000).optionalFieldOf("shot_cost",4000).forGetter(TurretSettings::shotCost),
            Codec.intRange(0,1000000).optionalFieldOf("idle_cost",4).forGetter(TurretSettings::idleCost),
            Codec.floatRange(0,1000).optionalFieldOf("damage",7f).forGetter(TurretSettings::damage),
            Codec.intRange(0,60).optionalFieldOf("burn_seconds",3).forGetter(TurretSettings::burnSeconds),
            Codec.intRange(1,64).optionalFieldOf("target_range",16).forGetter(TurretSettings::targetRange),
            Codec.intRange(1,128).optionalFieldOf("attack_range",32).forGetter(TurretSettings::attackRange),
            Codec.intRange(2,1200).optionalFieldOf("charge_time",31).forGetter(TurretSettings::chargeTime),
            Codec.intRange(1,1200).optionalFieldOf("hit_time",7).forGetter(TurretSettings::hitTime),
            Codec.intRange(1,1200).optionalFieldOf("blast_time",10).forGetter(TurretSettings::blastTime),
            Codec.intRange(1,200).optionalFieldOf("scan_interval",4).forGetter(TurretSettings::scanInterval),
            Codec.BOOL.optionalFieldOf("ignite_ground",true).forGetter(TurretSettings::igniteGround)
    ).apply(i,TurretSettings::new));
}
