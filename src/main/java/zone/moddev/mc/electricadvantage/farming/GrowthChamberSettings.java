package zone.moddev.mc.electricadvantage.farming;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Greenhouse power buffer and output export rate; one greenhouse unit drives one growth tick of one lane. */
public record GrowthChamberSettings(int capacity,int itemExport) {
    public static final Codec<GrowthChamberSettings> CODEC=RecordCodecBuilder.create(i->i.group(
            Codec.intRange(1,1000000).optionalFieldOf("capacity",200).forGetter(GrowthChamberSettings::capacity),
            Codec.intRange(0,1000000).optionalFieldOf("item_export",16).forGetter(GrowthChamberSettings::itemExport)
    ).apply(i,GrowthChamberSettings::new));
}
