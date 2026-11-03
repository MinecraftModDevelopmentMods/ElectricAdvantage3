package zone.moddev.mc.electricadvantage;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import zone.moddev.mc.electricadvantage.trades.TradeSystems;
import java.util.List;
import java.util.Objects;

@GameTestHolder(ElectricAdvantage.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TradeGameTests {
    @GameTest(templateNamespace=ElectricAdvantage.MOD_ID,template="empty")
    public static void toolsmithAndWandererSellEa2Trades(GameTestHelper helper){
        var villager=helper.spawn(EntityType.VILLAGER,new BlockPos(1,2,1));
        var toolsmith=VillagerTrades.TRADES.get(VillagerProfession.TOOLSMITH);
        for(var trade:TradeSystems.TRADES){
            check(helper,trade,offers(villager,toolsmith.get(trade.level())),"toolsmith level "+trade.level());
            check(helper,trade,offers(villager,VillagerTrades.WANDERING_TRADER_TRADES.get(1)),"wandering trader");
        }
        // EA2 used the librarian; its pools must stay vanilla.
        var librarian=VillagerTrades.TRADES.get(VillagerProfession.LIBRARIAN);
        for(int level=1;level<=5;level++)
            helper.assertFalse(offers(villager,librarian.get(level)).stream().anyMatch(o->isEa(o)),"Librarian level "+level+" has an Electric Advantage trade");
        helper.succeed();
    }

    private static void check(GameTestHelper helper,TradeSystems.Trade trade,List<MerchantOffer> offers,String where){
        var item=trade.result(1).getItem();
        var matching=offers.stream().filter(o->o.getResult().is(item)).toList();
        helper.assertTrue(matching.size()==1,"Expected one "+trade.item()+" trade for "+where+", found "+matching.size());
        var offer=matching.getFirst();
        helper.assertTrue(offer.getBaseCostA().is(Items.EMERALD)&&offer.getCostB().isEmpty(),trade.item()+" must cost only emeralds");
        int price=offer.getBaseCostA().getCount(),count=offer.getResult().getCount();
        helper.assertTrue(price>=trade.minPrice()&&price<=trade.maxPrice(),trade.item()+" price "+price+" out of range");
        helper.assertTrue(count>=trade.minCount()&&count<=trade.maxCount(),trade.item()+" count "+count+" out of range");
    }
    private static boolean isEa(MerchantOffer offer){
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getNamespace().equals(ElectricAdvantage.MOD_ID);
    }
    private static List<MerchantOffer> offers(Entity trader,VillagerTrades.ItemListing[] listings){
        var random=RandomSource.create(1);
        return java.util.Arrays.stream(listings).map(l->l.getOffer(trader,random)).filter(Objects::nonNull).toList();
    }
}
