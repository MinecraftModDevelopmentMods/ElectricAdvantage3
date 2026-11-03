package zone.moddev.mc.electricadvantage;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LootGameTests {
    private static final List<String> TARGETS=List.of("abandoned_mineshaft","simple_dungeon","village/village_weaponsmith",
            "stronghold_corridor","stronghold_crossing","end_city_treasure");

    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="empty")
    public static void componentTableMatchesEa2(GameTestHelper helper){
        int rolls=4000,hits=0;
        for(int n=0;n<rolls;n++){
            var loot=roll(helper,ResourceLocation.fromNamespaceAndPath(ElectricAdvantage.MOD_ID,"chests/components"));
            if(!loot.isEmpty())hits++;
            for(var stack:loot){
                helper.assertTrue(isEa(stack),"Unexpected item "+stack);
                helper.assertTrue(stack.getCount()<=stack.getMaxStackSize(),"Oversized stack "+stack);
            }
        }
        // 48 of 192 weight: 25%, so 1000 expected; allow a wide margin.
        helper.assertTrue(hits>800&&hits<1200,"Expected about 25% of rolls to give an item, got "+hits+"/"+rolls);
        helper.succeed();
    }

    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="empty")
    public static void componentsAddedOnlyToEa2Chests(GameTestHelper helper){
        for(var target:TARGETS){
            boolean found=false;
            for(int n=0;n<200&&!found;n++)found=roll(helper,ResourceLocation.withDefaultNamespace("chests/"+target)).stream().anyMatch(LootGameTests::isEa);
            helper.assertTrue(found,"No Electric Advantage loot in 200 "+target+" chests");
        }
        for(int n=0;n<200;n++)
            helper.assertFalse(roll(helper,ResourceLocation.withDefaultNamespace("chests/desert_pyramid")).stream().anyMatch(LootGameTests::isEa),
                    "Desert pyramids must not get Electric Advantage loot");
        helper.succeed();
    }

    private static boolean isEa(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(ElectricAdvantage.MOD_ID);}
    private static List<ItemStack> roll(GameTestHelper helper,ResourceLocation id){
        var level=helper.getLevel();
        LootTable table=level.getServer().reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE,id));
        var params=new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN,Vec3.atCenterOf(helper.absolutePos(net.minecraft.core.BlockPos.ZERO))).create(LootContextParamSets.CHEST);
        return new ArrayList<>(table.getRandomItems(params));
    }
}
