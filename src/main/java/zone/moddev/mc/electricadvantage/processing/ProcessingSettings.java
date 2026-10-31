package zone.moddev.mc.electricadvantage.processing;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public record ProcessingSettings(String kind, int inputs, int outputs, int capacity, int rate,
        int ticks, int cost, int fluidCapacity, int fluidPerItem, ResourceLocation resultItem,
        List<ResourceLocation> acceptedFluids, int itemExport, int fluidExport) {
    public static final Codec<ProcessingSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.validate(s -> java.util.Set.of("smelting","crushing","oven","distilling","refining").contains(s)
                    ? com.mojang.serialization.DataResult.success(s) : com.mojang.serialization.DataResult.error(() -> "Unknown processing kind: " + s))
                    .fieldOf("kind").forGetter(ProcessingSettings::kind),
            Codec.intRange(0, 16).optionalFieldOf("inputs",0).forGetter(ProcessingSettings::inputs),
            Codec.intRange(0, 16).optionalFieldOf("outputs",0).forGetter(ProcessingSettings::outputs),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("capacity",500).forGetter(ProcessingSettings::capacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("rate",4096).forGetter(ProcessingSettings::rate),
            Codec.intRange(1,1000000).optionalFieldOf("ticks",200).forGetter(ProcessingSettings::ticks),
            Codec.intRange(1,1000000).optionalFieldOf("cost",8).forGetter(ProcessingSettings::cost),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("fluid_capacity",2000).forGetter(ProcessingSettings::fluidCapacity),
            Codec.intRange(1,Integer.MAX_VALUE).optionalFieldOf("fluid_per_item",100).forGetter(ProcessingSettings::fluidPerItem),
            ResourceLocation.CODEC.optionalFieldOf("result_item",ResourceLocation.parse("minecraft:air")).forGetter(ProcessingSettings::resultItem),
            ResourceLocation.CODEC.listOf().optionalFieldOf("accepted_fluids",List.of()).forGetter(ProcessingSettings::acceptedFluids),
            Codec.intRange(0,1000000).optionalFieldOf("item_export",16).forGetter(ProcessingSettings::itemExport),
            Codec.intRange(0,1000000).optionalFieldOf("fluid_export",1000).forGetter(ProcessingSettings::fluidExport)
    ).apply(i, ProcessingSettings::new));
}
