package com.pos.scanner;

import java.util.*;

/** Nullable counts distinguish an uncounted product from a physical count of zero. */
public final class StocktakeDraft {
    public String id,staffName,status="Draft",notes="",reviewNotes="";
    public int staffId;
    public List<Item> items=new ArrayList<>();
    public static final class Item {
        public int productId,expected;
        public long version;
        public String name,barcode,category;
        public Integer counted;
    }
    public int counted(){int n=0;for(Item item:items)if(item.counted!=null)n++;return n;}
    public void restoreLocalCounts(StocktakeDraft local) {
        if(local==null||!"Draft".equals(status)||!id.equals(local.id)||staffId!=local.staffId)return;
        Map<Integer,Integer> counts=new HashMap<>();for(Item item:local.items)if(item.counted!=null)counts.put(item.productId,item.counted);
        for(Item item:items)item.counted=counts.get(item.productId);notes=local.notes;
    }
    public static Integer parseCount(String value) {
        if(value==null||!value.trim().matches("\\d{1,7}"))return null;
        try{int n=Integer.parseInt(value.trim());return n<=1000000?n:null;}catch(NumberFormatException e){return null;}
    }
}
