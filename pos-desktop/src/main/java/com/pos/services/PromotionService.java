package com.pos.services;

import com.google.gson.*;
import com.google.gson.stream.*;
import com.pos.database.DatabaseConnection;
import com.pos.services.PromotionEngine.*;
import com.pos.views.SalesView.CartItem;
import java.io.IOException;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Draft revisions, authenticated publishing and checkout revalidation. */
public final class PromotionService {
    public interface Connections {Connection open() throws SQLException;}
    private final Connections connections;
    static final Gson JSON=new GsonBuilder().registerTypeAdapter(LocalDateTime.class,new TypeAdapter<LocalDateTime>() {
        public void write(JsonWriter out,LocalDateTime value)throws IOException{out.value(value.toString());}
        public LocalDateTime read(JsonReader in)throws IOException{return LocalDateTime.parse(in.nextString());}
    }.nullSafe()).create();
    public PromotionService(){this(DatabaseConnection::getConnection);}
    public PromotionService(Connections connections){this.connections=connections;}
    public record Product(int id,String barcode,String name,String category,BigDecimal price,BigDecimal cost) {}
    public record Campaign(Rule draft,Rule published,String state,long applications,BigDecimal savings) {
        public String status(){LocalDateTime now=LocalDateTime.now(ZoneId.of("Africa/Johannesburg"));String label=state;
            if(state.equals("APPROVED")&&published!=null)label=now.isBefore(published.from())?"SCHEDULED":!now.isBefore(published.until())?"EXPIRED":"ACTIVE";
            return label+(published!=null&&published.revision()!=draft.revision()?" · draft changes":"");}
    }
    public List<Product> catalog() throws SQLException {try(Connection c=connections.open()){return catalog(c);}}
    private List<Product> catalog(Connection c)throws SQLException {
        List<Product> result=new ArrayList<>();
        try(Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT p.ProductID,p.BarCode,p.ProductName,c.CategoryName,p.Price,p.CostPrice FROM Product p LEFT JOIN Category c ON c.CategoryID=p.CategoryID WHERE p.IsArchived=FALSE ORDER BY p.ProductID")) {
            while(r.next())result.add(new Product(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getBigDecimal(5),r.getBigDecimal(6)));
        }return List.copyOf(result);
    }
    public List<Campaign> list()throws SQLException {
        try(Connection c=connections.open();Statement p=c.createStatement();ResultSet r=p.executeQuery("SELECT p.*,COALESCE((SELECT SUM(Applications) FROM PromotionRedemption x WHERE x.PromotionID=p.PromotionID),0) AS Uses,COALESCE((SELECT SUM(Discount) FROM PromotionRedemption x WHERE x.PromotionID=p.PromotionID),0) AS Savings FROM PromotionCampaign p ORDER BY p.UpdatedAt DESC,p.PromotionID")) {
            List<Campaign> result=new ArrayList<>();while(r.next())result.add(new Campaign(decode(r.getString("Definition")),r.getString("PublishedDefinition")==null?null:decode(r.getString("PublishedDefinition")),r.getString("State"),r.getLong("Uses"),r.getBigDecimal("Savings")));return List.copyOf(result);
        }
    }
    static Rule decode(String json){return JSON.fromJson(json,Rule.class);}
    static Rule revision(Rule rule,int revision){return new Rule(rule.id(),rule.name().trim(),rule.type(),rule.from(),rule.until(),rule.limit(),rule.distinct(),rule.code(),rule.value().setScale(2),rule.buy(),rule.get(),rule.items().stream().sorted(Comparator.comparing(Item::group).thenComparing(Item::barcode)).toList(),revision);}
    public static void validate(Rule rule) {
        if(rule==null || rule.id()==null || !rule.id().matches("[A-Z0-9_-]{1,40}") || rule.id().startsWith("COUPON_")
                || rule.name()==null || rule.name().isBlank() || rule.name().length()>120 || rule.name().chars().anyMatch(Character::isISOControl) || rule.type()==null)
            throw new IllegalArgumentException("Use a unique uppercase promotion ID (letters, digits, _ or -) and a name up to 120 characters.");
        if(rule.from()==null || rule.until()==null || !rule.until().isAfter(rule.from()) || rule.limit()<0 || rule.limit()>1000000)
            throw new IllegalArgumentException(rule.id()+": check the dates and per-sale application limit (0 means unlimited).");
        if(!rule.from().toLocalTime().equals(LocalTime.MIDNIGHT)||!rule.until().toLocalTime().equals(LocalTime.MIDNIGHT))
            throw new IllegalArgumentException(rule.id()+": offers use whole calendar days. Start at midnight and end at midnight after the inclusive end date.");
        if(rule.code()==null || !rule.code().matches("[A-Z0-9_-]{0,30}"))throw new IllegalArgumentException(rule.id()+": coupon codes use uppercase letters, digits, _ or -.");
        PromotionEngine.cents(rule.value());
        if((rule.type()==Type.PERCENT || rule.type()==Type.BUY_GET) && (rule.value().signum()<=0 || rule.value().compareTo(new BigDecimal("100"))>0))
            throw new IllegalArgumentException(rule.id()+": the percentage must be greater than 0 and at most 100.");
        if(rule.items().isEmpty() || rule.items().size()>500)throw new IllegalArgumentException(rule.id()+": select between 1 and 500 product entries.");
        Set<String> entries=new HashSet<>();Map<String,Integer> quantities=new HashMap<>();Map<Integer,String> membership=new HashMap<>();
        for(Item item:rule.items()) {
            if(item.productId()<=0 || item.barcode()==null || item.barcode().isBlank() || item.group()==null || !item.group().matches("[A-Za-z0-9 _-]{1,40}") || item.quantity()<1 || item.quantity()>1000
                    || !entries.add(item.group()+":"+item.productId()))throw new IllegalArgumentException(rule.id()+": invalid or duplicate product/group entry.");
            Integer old=quantities.putIfAbsent(item.group(),item.quantity());if(old!=null&&old!=item.quantity())throw new IllegalArgumentException(rule.id()+": alternatives in a bundle group must have the same required quantity.");
            if(rule.type()!=Type.BUNDLE&&item.quantity()!=1)throw new IllegalArgumentException(rule.id()+": use required group quantity 1; configure the offer's Buy and Reward quantities separately.");
            String prior=membership.putIfAbsent(item.productId(),item.group());if(rule.type()==Type.BUNDLE&&prior!=null)throw new IllegalArgumentException(rule.id()+": one product cannot fill different bundle groups.");
        }
        if(rule.distinct()&&rule.type()!=Type.MULTIBUY)throw new IllegalArgumentException(rule.id()+": Different products applies to multibuys only.");
        if((rule.type()==Type.MULTIBUY || rule.type()==Type.BUY_GET) && (rule.buy()<1 || rule.buy()>1000))throw new IllegalArgumentException(rule.id()+": buy quantity must be 1 to 1,000.");
        if(rule.type()==Type.BUY_GET) {
            if(rule.get()<1 || rule.get()>1000 || !quantities.keySet().equals(Set.of("BUY","GET")))throw new IllegalArgumentException(rule.id()+": buy/get offers need BUY and GET product groups and a reward quantity.");
            Set<Integer> buy=new HashSet<>(),get=new HashSet<>();rule.items().forEach(i->{if(i.group().equals("BUY"))buy.add(i.productId());else get.add(i.productId());});
            if(!Collections.disjoint(buy,get)&&!buy.equals(get))throw new IllegalArgumentException(rule.id()+": BUY and GET lists must be identical or completely separate. Identical lists discount the cheapest reward units.");
        } else if(rule.type()!=Type.BUNDLE && !quantities.keySet().equals(Set.of("ITEM")))throw new IllegalArgumentException(rule.id()+": use ITEM as the product group for this offer type.");
    }
    private void validateCatalog(Connection c,Rule rule)throws SQLException {
        validate(rule);Map<Integer,Product> products=new HashMap<>();catalog(c).forEach(p->products.put(p.id(),p));
        for(Item item:rule.items()){Product p=products.get(item.productId());if(p==null || !Objects.equals(p.barcode(),item.barcode()))throw new IllegalArgumentException(rule.id()+": unknown or archived barcode "+item.barcode());}
        if(!rule.code().isBlank())try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM Promo WHERE PromoCode=?")){p.setString(1,rule.code());try(ResultSet r=p.executeQuery()){if(r.next())throw new IllegalArgumentException("This code already belongs to a basket coupon. Choose a different code.");}}
    }
    static void guard(Connection c)throws SQLException {try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT Revision FROM PromotionGuard WHERE GuardID=1 FOR UPDATE")){if(!r.next())throw new SQLException("Promotion setup is missing. Restart database setup.");}}
    private static void manager(Connection c,int staff)throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT StaffID FROM Staff WHERE StaffID=? AND Status='Active' AND UserType IN ('Admin','Manager') FOR UPDATE")){p.setInt(1,staff);try(ResultSet r=p.executeQuery()){if(!r.next())throw new SQLException("An active Admin or Manager is required.");}}
    }
    /**
     * Validates and saves an entire draft batch in one transaction. Stable IDs upsert
     * drafts; identical definitions are no-ops and never overwrite the live version.
     * A positive incoming revision requires an exact match (interactive edit); zero
     * is an explicit import/new-offer upsert without optimistic revision checking.
     */
    public void saveDrafts(int staff,List<Rule> rules)throws SQLException {
        if(rules.isEmpty() || rules.size()>200)throw new IllegalArgumentException("Import 1 to 200 promotions at a time.");
        try(Connection c=connections.open()){c.setAutoCommit(false);try {
            manager(c,staff);guard(c);Set<String> ids=new HashSet<>();
            for(Rule rule:rules){if(!ids.add(rule.id()))throw new IllegalArgumentException("Duplicate promotion ID: "+rule.id());validateCatalog(c,rule);}
            for(Rule rule:rules) {
                int rev=1;boolean unchanged=false;
                try(PreparedStatement p=c.prepareStatement("SELECT Revision,Definition FROM PromotionCampaign WHERE PromotionID=? FOR UPDATE")){p.setString(1,rule.id());try(ResultSet r=p.executeQuery()){if(r.next()) {
                    if(rule.revision()>0&&rule.revision()!=r.getInt(1))throw new SQLException("This draft changed. Refresh before saving your edits.");
                    rev=r.getInt(1)+1;unchanged=JSON.toJson(revision(decode(r.getString(2)),0)).equals(JSON.toJson(revision(rule,0)));
                }}}
                if(unchanged)continue;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO PromotionCampaign (PromotionID,Name,Definition,Revision,CreatedBy) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE Name=VALUES(Name),Definition=VALUES(Definition),Revision=VALUES(Revision),UpdatedAt=CURRENT_TIMESTAMP")) {
                    p.setString(1,rule.id());p.setString(2,rule.name());p.setString(3,JSON.toJson(revision(rule,rev)));p.setInt(4,rev);p.setInt(5,staff);p.executeUpdate();
                }
            }c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    /**
     * Publishes the reviewed draft or disables its live version. Reviewer verification,
     * revision check, review snapshot, guard update and key consumption commit together.
     * Failed/stale reviews roll back the key consumption as well as the offer changes.
     */
    public void review(int staff,String key,String id,int expectedRevision,boolean activate)throws SQLException {
        try(Connection c=connections.open()){c.setAutoCommit(false);try {
            int authorization=SupervisorAuthorization.verify(c,staff,key);guard(c);
            Rule reviewed;
            try(PreparedStatement p=c.prepareStatement("SELECT Definition,Revision,State,PublishedDefinition FROM PromotionCampaign WHERE PromotionID=? FOR UPDATE")) {
                p.setString(1,id);try(ResultSet r=p.executeQuery()) {
                    if(!r.next() || r.getInt(2)!=expectedRevision)throw new SQLException("This draft changed. Refresh and review it again.");
                    reviewed=decode(r.getString(1));Rule published=r.getString(4)==null?null:decode(r.getString(4));
                    if(activate) {
                        if("APPROVED".equals(r.getString(3))&&published!=null&&published.revision()==reviewed.revision())throw new SQLException("This revision has already been approved.");
                        validateCatalog(c,reviewed);
                    } else {if(!"APPROVED".equals(r.getString(3))||published==null)throw new SQLException("This offer is not enabled.");reviewed=published;}
                }
            }
            try(PreparedStatement p=c.prepareStatement("INSERT INTO PromotionReview (PromotionID,Revision,ReviewerID,Action,DefinitionSnapshot) VALUES (?,?,?,?,?)")) {
                p.setString(1,id);p.setInt(2,reviewed.revision());p.setInt(3,staff);p.setString(4,activate?"APPROVE":"DISABLE");p.setString(5,JSON.toJson(reviewed));p.executeUpdate();
            }
            try(PreparedStatement p=c.prepareStatement("UPDATE PromotionCampaign SET State=?,PublishedDefinition=CASE WHEN ? THEN Definition ELSE PublishedDefinition END,ReviewedBy=?,ReviewedAt=CURRENT_TIMESTAMP WHERE PromotionID=?")) {
                p.setString(1,activate?"APPROVED":"DISABLED");p.setBoolean(2,activate);p.setInt(3,staff);p.setString(4,id);p.executeUpdate();
            }
            try(Statement s=c.createStatement()){s.executeUpdate("UPDATE PromotionGuard SET Revision=Revision+1 WHERE GuardID=1");}
            SupervisorAuthorization.consume(c,authorization);c.commit();
        }catch(SQLException|RuntimeException e){c.rollback();throw e;}}
    }
    public Quote quote(List<CartItem> cart,String code)throws SQLException {try(Connection c=connections.open()){return quote(c,cart,code,false);}}
    /** Uses the caller's transaction; lock=true serializes checkout with publication. */
    Quote quote(Connection c,List<CartItem> cart,String code,boolean lock)throws SQLException {
        if(lock)guard(c);List<Rule> rules=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT PublishedDefinition FROM PromotionCampaign WHERE State='APPROVED' AND PublishedDefinition IS NOT NULL")){while(r.next())rules.add(decode(r.getString(1)));}
        LocalDateTime now=LocalDateTime.now(ZoneId.of("Africa/Johannesburg"));String entered=code==null?"":code.trim().toUpperCase(Locale.ROOT);
        List<Cart> input=cart.stream().map(i->new Cart(i.getProductId(),i.getPrice(),i.getQuantity())).toList();
        BigDecimal subtotal=cart.stream().map(CartItem::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(!entered.isEmpty())try(PreparedStatement p=c.prepareStatement("SELECT *,CURRENT_TIMESTAMP AS CheckoutNow FROM Promo WHERE PromoCode=?"+(lock?" FOR UPDATE":""))) {
            p.setString(1,entered);try(ResultSet r=p.executeQuery()) {
                if(r.next()) {
                    Timestamp from=r.getTimestamp("ValidFrom"),until=r.getTimestamp("ValidTill");LocalDateTime dbNow=r.getTimestamp("CheckoutNow").toLocalDateTime();int limit=r.getInt("UsageLimit");boolean limited=!r.wasNull();
                    if(!r.getBoolean("IsActive") || from!=null&&dbNow.isBefore(from.toLocalDateTime()) || until!=null&&dbNow.isAfter(until.toLocalDateTime())
                            || limited&&r.getInt("UsageCount")>=limit || r.getBigDecimal("MinimumPurchase")!=null&&subtotal.compareTo(r.getBigDecimal("MinimumPurchase"))<0)
                        throw new IllegalArgumentException("This coupon is inactive, expired, exhausted or below its minimum spend.");
                    BigDecimal discount="PERCENT".equals(r.getString("DiscountType"))?subtotal.multiply(r.getBigDecimal("DiscountValue")).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP).min(subtotal):r.getBigDecimal("DiscountValue").min(subtotal);
                    String label=r.getString("PromoName");if(label==null||label.isBlank())label=entered;
                    return PromotionEngine.withCoupon(input,rules,now,entered,r.getInt("PromoID"),label,discount);
                }
            }
            if(rules.stream().noneMatch(rule->rule.code().equalsIgnoreCase(entered)&&!now.isBefore(rule.from())&&now.isBefore(rule.until())))throw new IllegalArgumentException("No active promotion or coupon matches this code.");
        }
        return PromotionEngine.price(input,rules,now,entered);
    }
    void record(Connection c,int sale,Quote quote)throws SQLException {
        for(Applied offer:quote.offers())if(!offer.id().startsWith("COUPON_"))try(PreparedStatement p=c.prepareStatement("INSERT INTO PromotionRedemption (SaleID,PromotionID,Revision,NameSnapshot,Applications,Discount) VALUES (?,?,?,?,?,?)")) {
            p.setInt(1,sale);p.setString(2,offer.id());p.setInt(3,offer.revision());p.setString(4,offer.name());p.setInt(5,offer.applications());p.setBigDecimal(6,offer.saving());p.executeUpdate();
        }
    }
    public static String signature(Quote quote){return JSON.toJson(quote);}
}
