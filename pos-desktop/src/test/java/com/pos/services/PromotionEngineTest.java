package com.pos.services;

import com.pos.services.PromotionEngine.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PromotionEngineTest {
    static final LocalDateTime NOW=LocalDateTime.of(2026,10,26,12,0);
    static BigDecimal money(String v){return new BigDecimal(v);}
    static Item item(int id,String group){return new Item(id,"000"+id,group,1);}
    static Cart cart(int id,String price,int q){return new Cart(id,money(price),q);}
    static Rule rule(String id,Type type,String value,int buy,int get,int limit,boolean distinct,String code,Item... items){return new Rule(id,id,type,NOW.minusDays(1).toLocalDate().atStartOfDay(),NOW.plusDays(7).toLocalDate().atStartOfDay(),limit,distinct,code,money(value),buy,get,List.of(items),1);}
    static Quote price(List<Cart> cart,Rule... rules){Quote q=PromotionEngine.price(cart,List.of(rules),NOW,"");assertReconciles(q);return q;}
    static void assertReconciles(Quote q){assertEquals(0,q.total().compareTo(q.lines().stream().map(l->l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add)));assertEquals(0,q.discount().compareTo(q.offers().stream().map(Applied::saving).reduce(BigDecimal.ZERO,BigDecimal::add)));}
    @Test void colaMultibuyTriggersAtThreeAndRepeatsWithoutDiscountingRemainders() {
        Rule r=rule("COLA",Type.MULTIBUY,"60",3,0,0,false,"",item(1,"ITEM"));
        for(int q=1;q<=8;q++)assertEquals(0,money("60").multiply(BigDecimal.valueOf(q/3)).add(money("25").multiply(BigDecimal.valueOf(q%3))).compareTo(price(List.of(cart(1,"25",q)),r).total()));
    }
    @Test void perSaleLimitCapsCompleteApplications(){var r=rule("COLA",Type.MULTIBUY,"60",3,0,2,false,"",item(1,"ITEM"));assertEquals(money("195.00"),price(List.of(cart(1,"25",9)),r).total());}
    @Test void fiveProductBundleRequiresEveryExactProductAndQuantity() {
        Rule bundle=rule("PANTRY",Type.BUNDLE,"100",0,0,0,false,"",item(1,"MAIZE"),item(2,"FLOUR"),item(3,"RICE"),item(4,"OIL"),item(5,"EGGS"));
        var basket=new ArrayList<Cart>();for(int i=1;i<=5;i++)basket.add(cart(i,"30",1));basket.add(cart(6,"10",1));
        assertEquals(money("110.00"),price(basket,bundle).total());basket.remove(2);assertEquals(money("130.00"),price(basket,bundle).total());
    }
    @Test void alternativeBrandsCanFillOneBundleGroupButCannotReplaceAnotherGroup() {
        Rule bundle=rule("SET",Type.BUNDLE,"40",0,0,0,false,"",item(1,"RICE"),item(2,"RICE"),item(3,"OIL"));
        assertEquals(money("40.00"),price(List.of(cart(2,"25",1),cart(3,"30",1)),bundle).total());
        assertEquals(money("50.00"),price(List.of(cart(1,"25",1),cart(2,"25",1)),bundle).total());
    }
    @Test void choosesGlobalBestCombinationInsteadOfTheLargestSingleDiscount() {
        var ab=rule("AB",Type.BUNDLE,"50",0,0,0,false,"",item(1,"A"),item(2,"B"));
        var ac=rule("AC",Type.BUNDLE,"100",0,0,0,false,"",item(1,"A"),item(3,"C"));
        var bd=rule("BD",Type.BUNDLE,"100",0,0,0,false,"",item(2,"B"),item(4,"D"));
        var q=price(List.of(cart(1,"100",1),cart(2,"100",1),cart(3,"100",1),cart(4,"100",1)),ab,ac,bd);
        assertEquals(money("200.00"),q.total());assertEquals(Set.of("AC","BD"),new HashSet<>(q.offers().stream().map(Applied::id).toList()));
    }
    @Test void bogoDiscountsCheapestRewardAndAllocatesRefundValueAcrossBothItems() {
        Rule r=rule("BOGO",Type.BUY_GET,"100",1,1,0,false,"",item(1,"BUY"),item(2,"BUY"),item(1,"GET"),item(2,"GET"));
        Quote q=price(List.of(cart(1,"30",1),cart(2,"20",1)),r);assertEquals(money("30.00"),q.total());
        assertEquals(money("18.00"),q.lines().stream().filter(l->l.productId()==1).findFirst().orElseThrow().price());assertEquals(money("12.00"),q.lines().stream().filter(l->l.productId()==2).findFirst().orElseThrow().price());
    }
    @Test void differentRewardProductsAndHalfPriceRewardsWork(){var r=rule("REWARD",Type.BUY_GET,"50",2,1,0,false,"",item(1,"BUY"),item(2,"GET"));assertEquals(money("50.00"),price(List.of(cart(1,"20",2),cart(2,"20",1)),r).total());}
    @Test void distinctMultibuyCountsBarcodesRatherThanRepeatedUnits(){var r=rule("DISTINCT",Type.MULTIBUY,"30",3,0,0,true,"",item(1,"ITEM"),item(2,"ITEM"),item(3,"ITEM"));assertEquals(money("60.00"),price(List.of(cart(1,"20",3)),r).total());assertEquals(money("30.00"),price(List.of(cart(1,"20",1),cart(2,"20",1),cart(3,"20",1)),r).total());}
    @Test void discountsNeverAffectUnselectedProductsAndNeverIncreasePrices(){var r=rule("PRICE",Type.PRICE,"10",0,0,0,false,"",item(1,"ITEM"));Quote q=price(List.of(cart(1,"20",1),cart(2,"50",1)),r);assertEquals(money("60.00"),q.total());assertEquals(money("50"),q.lines().stream().filter(l->l.productId()==2).findFirst().orElseThrow().price());assertEquals(money("5.00"),price(List.of(cart(1,"5",1)),r).total());}
    @Test void datesAndCodesAreEnforced(){var r=rule("CODE",Type.PERCENT,"10",0,0,0,false,"SAVE",item(1,"ITEM"));var c=List.of(cart(1,"20",1));assertEquals(money("20.00"),price(c,r).total());assertEquals(money("18.00"),PromotionEngine.price(c,List.of(r),NOW,"save").total());assertEquals(money("20.00"),PromotionEngine.price(c,List.of(r),r.until(),"SAVE").total());assertEquals(money("20.00"),PromotionEngine.price(c,List.of(r),r.from().minusNanos(1),"SAVE").total());}
    @Test void basketCouponsCompeteWithAutomaticOffersWithoutStacking(){var r=rule("PRICE",Type.PRICE,"10",0,0,0,false,"",item(1,"ITEM"));var c=List.of(cart(1,"20",1));assertEquals(money("10.00"),PromotionEngine.withCoupon(c,List.of(r),NOW,"X",1,"Coupon",money("5")).total());assertEquals(money("8.00"),PromotionEngine.withCoupon(c,List.of(r),NOW,"X",1,"Coupon",money("12")).total());}
    @Test void centAllocationAndLargeQuantitiesReconcileExactly(){var r=rule("PRICE",Type.PRICE,"0.01",0,0,0,false,"",item(1,"ITEM"));Quote q=price(List.of(cart(1,"0.03",1000000)),r);assertEquals(money("10000.00"),q.total());assertEquals(1000000,q.offers().get(0).applications());}
    @Test void fractionalBundleAllocationPreservesEveryCentAndEveryUnit() {
        Rule r=rule("CENTS",Type.MULTIBUY,"0.02",3,0,0,false,"",item(1,"ITEM"));
        Quote q=price(List.of(cart(1,"0.01",7)),r);
        assertEquals(money("0.05"),q.total());assertEquals(7,q.lines().stream().mapToInt(Charge::quantity).sum());
        assertEquals(2,q.offers().get(0).applications());assertTrue(q.lines().stream().allMatch(l->l.price().signum()>=0&&l.price().compareTo(l.regularPrice())<=0));
    }
    @Test void manyIndependentOffersDoNotCreateExponentialSearch(){List<Item> items=new ArrayList<>();List<Cart> cart=new ArrayList<>();for(int i=1;i<=100;i++){items.add(item(i,"ITEM"));cart.add(cart(i,"10",1));}var r=rule("PERCENT",Type.PERCENT,"10",0,0,0,false,"",items.toArray(Item[]::new));assertTimeoutPreemptively(Duration.ofSeconds(3),()->assertEquals(money("900.00"),price(cart,r).total()));}
    @Test void invalidPartiallyOverlappingBuyGetGroupsAreRejected(){var r=rule("BAD",Type.BUY_GET,"100",1,1,0,false,"",item(1,"BUY"),item(2,"BUY"),item(2,"GET"),item(3,"GET"));assertThrows(IllegalArgumentException.class,()->PromotionService.validate(r));}
    @Test void overlappingDealsMatchAnIndependentUnitSubsetOracle() {
        Random random=new Random(51237);
        for(int trial=0;trial<100;trial++) {
            List<Cart> basket=new ArrayList<>();List<Integer> units=new ArrayList<>();List<Long> prices=new ArrayList<>();
            for(int id=1;id<=3;id++) {
                int quantity=1+random.nextInt(3);long cents=1000+100*random.nextInt(21);
                basket.add(new Cart(id,PromotionEngine.money(cents),quantity));
                for(int n=0;n<quantity;n++){units.add(id);prices.add(cents);}
            }
            long multiPrice=1500+100*random.nextInt(26),bundlePrice=1500+100*random.nextInt(26);
            Rule multi=rule("MULTI",Type.MULTIBUY,PromotionEngine.money(multiPrice).toPlainString(),2,0,0,false,"",item(1,"ITEM"),item(2,"ITEM"));
            Rule bundle=rule("BUNDLE",Type.BUNDLE,PromotionEngine.money(bundlePrice).toPlainString(),0,0,0,false,"",item(1,"A"),item(3,"B"));
            Rule unit=rule("UNIT",Type.PERCENT,"10",0,0,0,false,"",item(2,"ITEM"));
            Rule bogo=rule("BOGO",Type.BUY_GET,"100",1,1,0,false,"",item(1,"BUY"),item(2,"BUY"),item(3,"BUY"),item(1,"GET"),item(2,"GET"),item(3,"GET"));
            // Enumerate actual supplied unit subsets, independent of the engine's quantity patterns.
            int size=1<<units.size();long[] saving=new long[size],best=new long[size];
            for(int mask=1;mask<size;mask++) {
                int count=Integer.bitCount(mask);long gross=0,cheapest=Long.MAX_VALUE;Set<Integer> ids=new HashSet<>();
                for(int n=0;n<units.size();n++)if((mask&(1<<n))!=0){gross+=prices.get(n);cheapest=Math.min(cheapest,prices.get(n));ids.add(units.get(n));}
                if(count==2) {
                    saving[mask]=cheapest;
                    if(!ids.contains(3))saving[mask]=Math.max(saving[mask],gross-multiPrice);
                    if(ids.equals(Set.of(1,3)))saving[mask]=Math.max(saving[mask],gross-bundlePrice);
                }
                if(count==1&&ids.contains(2))saving[mask]=gross/10;
                int first=Integer.lowestOneBit(mask);best[mask]=best[mask^first];
                for(int subset=mask;subset>0;subset=(subset-1)&mask)
                    if((subset&first)!=0&&saving[subset]>0)best[mask]=Math.max(best[mask],saving[subset]+best[mask^subset]);
            }
            Quote quote=price(basket,multi,bundle,unit,bogo);
            assertEquals(PromotionEngine.money(best[size-1]),quote.discount(),"Trial "+trial);
            for(Cart line:basket)assertEquals(line.quantity(),quote.lines().stream().filter(l->l.productId()==line.productId()).mapToInt(Charge::quantity).sum());
        }
    }
}
