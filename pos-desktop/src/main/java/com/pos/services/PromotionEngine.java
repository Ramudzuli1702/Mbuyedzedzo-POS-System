package com.pos.services;

import java.math.*;
import java.time.LocalDateTime;
import java.util.*;

/** Exact, quantity-aware offer selection. A unit can belong to at most one offer. */
public final class PromotionEngine {
    public enum Type { PRICE, PERCENT, AMOUNT, MULTIBUY, BUY_GET, BUNDLE }
    public record Item(int productId,String barcode,String group,int quantity) {}
    /**
     * Versioned offer definition. {@code from} is inclusive and {@code until} exclusive;
     * publication validation restricts both to midnight. A zero limit allows repeats.
     * Items in one bundle group are alternatives for that group's required quantity.
     */
    public record Rule(String id,String name,Type type,LocalDateTime from,LocalDateTime until,
                       int limit,boolean distinct,String code,BigDecimal value,int buy,int get,List<Item> items,int revision) {
        public Rule { items=List.copyOf(items); }
    }
    public record Cart(int productId,BigDecimal price,int quantity) {}
    /** Paid-price allocation; one product may have multiple charges after cent splitting. */
    public record Charge(int productId,int quantity,BigDecimal regularPrice,BigDecimal price,String offerId,String name,int revision) {}
    public record Applied(String id,String name,int revision,int applications,BigDecimal saving) {}
    public record Quote(List<Charge> lines,List<Applied> offers,BigDecimal subtotal,BigDecimal total) {
        public Quote {lines=List.copyOf(lines);offers=List.copyOf(offers);}
        public BigDecimal discount(){return subtotal.subtract(total);}
        public String description(){return offers.stream().map(a->a.name()+" ×"+a.applications()+" (-R"+a.saving().toPlainString()+")").collect(java.util.stream.Collectors.joining("; "));}
    }
    private record Pattern(Rule rule,int ruleIndex,int[] quantities,long gross,long saving) {}
    private record Pick(int pattern,int count) {}
    private record Solution(long saving,List<Pick> picks) {}
    private final List<Cart> cart;
    private final List<Rule> rules;
    private final List<Pattern> patterns=new ArrayList<>();
    private final Map<String,Solution> memo=new HashMap<>();
    private final int[] available;
    private boolean[][] futureProducts,futureRules;
    private int steps;
    private PromotionEngine(List<Cart> cart,List<Rule> rules) {
        this.cart=cart;this.rules=rules;available=cart.stream().mapToInt(Cart::quantity).toArray();
    }
    /**
     * Finds the greatest complete saving without reusing basket units.
     *
     * <p>This is a pure quote, not stock reservation or payment authorization. The
     * caller supplies the pricing clock and rules; checkout must revalidate them.
     * Search limits deliberately reject overly broad combinations rather than
     * silently returning a greedy or partially calculated price.
     *
     * @param input unique product IDs, non-negative cent prices and positive quantities
     * @param offers candidate definitions already validated by the publishing service
     * @param now wall-clock time in the same zone used by the offer definitions
     * @param code optional entered code; blank-coded offers apply automatically
     * @return exact-cent allocations and applied-offer evidence, without changing inputs
     * @throws IllegalArgumentException for an invalid basket or exceeded pricing limits
     */
    public static Quote price(List<Cart> input,List<Rule> offers,LocalDateTime now,String code) {
        if(input==null || input.size()>500)throw new IllegalArgumentException("A basket can contain at most 500 different products.");
        Set<Integer> ids=new HashSet<>();
        for(Cart item:input) {
            if(item.productId()<=0 || !ids.add(item.productId()) || item.quantity()<=0 || item.quantity()>1000000)
                throw new IllegalArgumentException("Use unique products and positive whole quantities.");
            cents(item.price());
        }
        String entered=code==null?"":code.trim().toUpperCase(Locale.ROOT);
        List<Rule> active=offers.stream().filter(r->!now.isBefore(r.from())&&now.isBefore(r.until())
                && (r.code().isBlank()||r.code().equalsIgnoreCase(entered))&&r.items().stream().anyMatch(i->ids.contains(i.productId())))
                .sorted(Comparator.comparing(Rule::id)).toList();
        if(active.size()>200)throw new IllegalArgumentException("Too many simultaneous offers. Narrow the promotion dates or selected products.");
        PromotionEngine engine=new PromotionEngine(input.stream().sorted(Comparator.comparingInt(Cart::productId)).toList(),active);
        for(int i=0;i<active.size();i++)engine.generate(active.get(i),i);
        engine.patterns.sort(Comparator.comparingLong(Pattern::saving).reversed().thenComparing(p->p.rule().id()));
        engine.indexFuture();
        Solution solution=engine.solve(0,engine.available.clone(),new int[active.size()]);
        return engine.quote(solution);
    }
    /** Add an existing basket coupon as a competing whole-basket offer. */
    public static Quote withCoupon(List<Cart> cart,List<Rule> rules,LocalDateTime now,String code,
                                   int couponId,String couponName,BigDecimal discount) {
        Quote automatic=price(cart,rules,now,code);
        if(discount==null || discount.compareTo(automatic.discount())<=0)return automatic;
        long gross=cart.stream().mapToLong(c->Math.multiplyExact(cents(c.price()),c.quantity())).reduce(0,Math::addExact);
        long saving=cents(discount);if(saving>gross)throw new IllegalArgumentException("Coupon discount exceeds the basket total.");
        List<Charge> lines=allocate(cart,"COUPON_"+couponId,couponName,0,gross-saving);
        return new Quote(lines,List.of(new Applied("COUPON_"+couponId,couponName,0,1,discount)),money(gross),money(gross-saving));
    }
    private void generate(Rule rule,int index) {
        Map<String,List<Item>> groups=new TreeMap<>();
        for(Item item:rule.items())if(cart.stream().anyMatch(c->c.productId()==item.productId()))
            groups.computeIfAbsent(item.group(),k->new ArrayList<>()).add(item);
        switch(rule.type()) {
            case PRICE,PERCENT,AMOUNT -> {
                for(int i=0;i<cart.size();i++) {
                    int position=i;if(rule.items().stream().noneMatch(item->item.productId()==cart.get(position).productId()))continue;
                    int[] counts=new int[cart.size()];counts[i]=1;add(rule,index,counts);
                }
            }
            case MULTIBUY -> distribute(rule,index,rule.items(),rule.buy(),new int[cart.size()],counts->add(rule,index,counts));
            case BUNDLE -> {
                Map<String,List<Item>> all=new TreeMap<>();for(Item item:rule.items())all.computeIfAbsent(item.group(),k->new ArrayList<>()).add(item);
                bundle(rule,index,new ArrayList<>(all.values()),0,new int[cart.size()]);
            }
            case BUY_GET -> {
                List<Item> buy=rule.items().stream().filter(i->i.group().equals("BUY")).toList();
                List<Item> get=rule.items().stream().filter(i->i.group().equals("GET")).toList();
                Set<Integer> buyIds=new HashSet<>(),getIds=new HashSet<>();buy.forEach(i->buyIds.add(i.productId()));get.forEach(i->getIds.add(i.productId()));
                if(buyIds.equals(getIds))distribute(rule,index,buy,Math.addExact(rule.buy(),rule.get()),new int[cart.size()],counts->add(rule,index,counts));
                else distribute(rule,index,buy,rule.buy(),new int[cart.size()],counts->
                        distribute(rule,index,get,rule.get(),counts,combined->add(rule,index,combined)));
            }
        }
    }
    private void bundle(Rule rule,int index,List<List<Item>> groups,int at,int[] counts) {
        if(at==groups.size()){add(rule,index,counts);return;}
        List<Item> group=groups.get(at);
        distribute(rule,index,group,group.get(0).quantity(),counts,next->bundle(rule,index,groups,at+1,next));
    }
    private void distribute(Rule rule,int index,List<Item> choices,int need,int[] counts,java.util.function.Consumer<int[]> consumer) {
        Set<Integer> ids=new HashSet<>();choices.forEach(i->ids.add(i.productId()));
        int[] positions=java.util.stream.IntStream.range(0,cart.size()).filter(i->ids.contains(cart.get(i).productId())).toArray();
        distributeAt(rule,positions,0,need,counts.clone(),consumer);
    }
    private void distributeAt(Rule rule,int[] positions,int at,int need,int[] counts,java.util.function.Consumer<int[]> consumer) {
        budget();
        if(need==0){consumer.accept(counts.clone());return;}
        if(at==positions.length)return;
        int capacity=0;for(int j=at;j<positions.length;j++)capacity+=rule.distinct()?Math.min(1,available[positions[j]]-counts[positions[j]]):Math.min(need,available[positions[j]]-counts[positions[j]]);
        if(capacity<need)return;
        int p=positions[at],original=counts[p];int max=Math.min(need,available[p]-original);if(rule.distinct())max=Math.min(max,1-original);
        for(int n=max;n>=0;n--){counts[p]=original+n;distributeAt(rule,positions,at+1,need-n,counts,consumer);}counts[p]=original;
    }
    private void add(Rule rule,int index,int[] counts) {
        long gross=0;for(int i=0;i<counts.length;i++)gross=Math.addExact(gross,Math.multiplyExact(cents(cart.get(i).price()),counts[i]));
        long saving=switch(rule.type()) {
            case PRICE,MULTIBUY,BUNDLE -> gross-cents(rule.value());
            case PERCENT -> percent(gross,rule.value());
            case AMOUNT -> Math.min(gross,cents(rule.value()));
            case BUY_GET -> {
                Set<Integer> rewards=new HashSet<>();rule.items().stream().filter(i->i.group().equals("GET")).forEach(i->rewards.add(i.productId()));
                int remaining=rule.get();long reward=0;
                for(int p:java.util.stream.IntStream.range(0,counts.length).filter(i->rewards.contains(cart.get(i).productId())).boxed()
                        .sorted(Comparator.comparing(i->cart.get(i).price())).toList()) {
                    int n=Math.min(remaining,counts[p]);reward=Math.addExact(reward,Math.multiplyExact(cents(cart.get(p).price()),n));remaining-=n;
                }
                yield percent(reward,rule.value());
            }
        };
        if(saving>0){if(patterns.size()>=512)throw complexity();patterns.add(new Pattern(rule,index,counts.clone(),gross,Math.min(saving,gross)));}
    }
    private void indexFuture() {
        futureProducts=new boolean[patterns.size()+1][cart.size()];futureRules=new boolean[patterns.size()+1][rules.size()];
        for(int i=patterns.size()-1;i>=0;i--){
            futureProducts[i]=futureProducts[i+1].clone();futureRules[i]=futureRules[i+1].clone();Pattern p=patterns.get(i);
            for(int j=0;j<cart.size();j++)if(p.quantities()[j]>0)futureProducts[i][j]=true;
            if(p.rule().limit()>0)futureRules[i][p.ruleIndex()]=true;
        }
    }
    private Solution solve(int at,int[] remaining,int[] used) {
        budget();if(at==patterns.size())return new Solution(0,List.of());
        StringBuilder state=new StringBuilder().append(at).append(':');
        for(int i=0;i<remaining.length;i++)if(futureProducts[at][i])state.append(remaining[i]).append(',');
        state.append(':');for(int i=0;i<used.length;i++)if(futureRules[at][i])state.append(used[i]).append(',');
        String key=state.toString();Solution cached=memo.get(key);if(cached!=null)return cached;
        Pattern pattern=patterns.get(at);int max=Integer.MAX_VALUE;
        for(int i=0;i<remaining.length;i++)if(pattern.quantities()[i]>0)max=Math.min(max,remaining[i]/pattern.quantities()[i]);
        if(pattern.rule().limit()>0)max=Math.min(max,pattern.rule().limit()-used[pattern.ruleIndex()]);
        Solution best=new Solution(-1,List.of());
        boolean conflicts=pattern.rule().limit()>0&&futureRules[at+1][pattern.ruleIndex()];
        for(int i=0;i<remaining.length;i++)if(pattern.quantities()[i]>0&&futureProducts[at+1][i])conflicts=true;
        for(int n=Math.max(0,max);n>=(conflicts?0:Math.max(0,max));n--) {
            int[] next=remaining.clone();for(int i=0;i<next.length;i++)next[i]-=n*pattern.quantities()[i];
            used[pattern.ruleIndex()]+=n;Solution tail=solve(at+1,next,used);used[pattern.ruleIndex()]-=n;
            long saving=Math.addExact(tail.saving(),Math.multiplyExact(pattern.saving(),n));
            if(saving>best.saving()) {
                List<Pick> picks=new ArrayList<>();if(n>0)picks.add(new Pick(at,n));picks.addAll(tail.picks());best=new Solution(saving,List.copyOf(picks));
            }
        }
        if(memo.size()>=50000)throw complexity();memo.put(key,best);return best;
    }
    private Quote quote(Solution solution) {
        int[] left=available.clone();List<Charge> charges=new ArrayList<>();Map<String,Applied> applied=new TreeMap<>();
        long subtotal=0;for(Cart item:cart)subtotal=Math.addExact(subtotal,Math.multiplyExact(cents(item.price()),item.quantity()));
        for(Pick pick:solution.picks()) {
            Pattern pattern=patterns.get(pick.pattern());Rule rule=pattern.rule();List<Cart> group=new ArrayList<>();
            for(int i=0;i<cart.size();i++)if(pattern.quantities()[i]>0) {
                int quantity=Math.multiplyExact(pattern.quantities()[i],pick.count());left[i]-=quantity;group.add(new Cart(cart.get(i).productId(),cart.get(i).price(),quantity));
            }
            charges.addAll(allocate(group,rule.id(),rule.name(),rule.revision(),Math.multiplyExact(pattern.gross()-pattern.saving(),pick.count())));
            Applied old=applied.get(rule.id());applied.put(rule.id(),new Applied(rule.id(),rule.name(),rule.revision(),pick.count()+(old==null?0:old.applications()),money(Math.multiplyExact(pattern.saving(),pick.count())).add(old==null?BigDecimal.ZERO:old.saving())));
        }
        for(int i=0;i<left.length;i++)if(left[i]>0){Cart item=cart.get(i);charges.add(new Charge(item.productId(),left[i],item.price(),item.price(),"","",0));}
        charges.sort(Comparator.comparingInt(Charge::productId).thenComparing(Charge::offerId).thenComparing(Charge::price));
        return new Quote(charges,new ArrayList<>(applied.values()),money(subtotal),money(subtotal-solution.saving()));
    }
    /** Proportional allocation in cents, including all bundle items, for accurate partial refunds. */
    private static List<Charge> allocate(List<Cart> cart,String id,String name,int revision,long target) {
        long gross=cart.stream().mapToLong(c->Math.multiplyExact(cents(c.price()),c.quantity())).reduce(0,Math::addExact);
        long allocated=0,cumulative=0;List<Charge> result=new ArrayList<>();
        for(Cart item:cart.stream().sorted(Comparator.comparingInt(Cart::productId)).toList()) {
            cumulative=Math.addExact(cumulative,Math.multiplyExact(cents(item.price()),item.quantity()));
            long cumulativeNet=gross==0?0:BigDecimal.valueOf(cumulative).multiply(BigDecimal.valueOf(target)).divide(BigDecimal.valueOf(gross),0,RoundingMode.HALF_UP).longValueExact();
            long net=cumulativeNet-allocated;allocated=cumulativeNet;long unit=net/item.quantity();int extra=(int)(net%item.quantity());
            if(item.quantity()>extra)result.add(new Charge(item.productId(),item.quantity()-extra,item.price(),money(unit),id,name,revision));
            if(extra>0)result.add(new Charge(item.productId(),extra,item.price(),money(unit+1),id,name,revision));
        }
        return result;
    }
    private void budget(){if(++steps>250000)throw complexity();}
    private static IllegalArgumentException complexity(){return new IllegalArgumentException("These overlapping offers have too many combinations to price safely. Narrow their product selections or limits; no sale was charged.");}
    private static long percent(long amount,BigDecimal percent){return BigDecimal.valueOf(amount).multiply(percent).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).longValueExact();}
    static long cents(BigDecimal value){if(value==null || value.signum()<0 || value.stripTrailingZeros().scale()>2)throw new IllegalArgumentException("Prices must be non-negative amounts with at most two decimal places.");return value.movePointRight(2).longValueExact();}
    static BigDecimal money(long cents){return BigDecimal.valueOf(cents,2);}
}
