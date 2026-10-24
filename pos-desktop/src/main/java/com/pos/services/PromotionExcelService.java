package com.pos.services;

import com.pos.services.PromotionEngine.*;
import com.pos.utils.SpreadsheetFiles;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class PromotionExcelService {
    private static final String[] OFFER={"Promotion ID","Name","Type","Start date","End date","Offer price","Discount %","Discount amount","Buy quantity","Reward quantity","Per-sale limit","Different products","Coupon code"};
    private static final String[] ITEMS={"Promotion ID","Barcode","Group","Required quantity"};
    public record Plan(List<Rule> rules,List<String> errors,List<String> warnings) {
        public Plan{rules=List.copyOf(rules);errors=List.copyOf(errors);warnings=List.copyOf(warnings);}
        public boolean valid(){return errors.isEmpty()&&!rules.isEmpty();}
        public String summary(){return rules.stream().map(PromotionExcelService::describe).collect(java.util.stream.Collectors.joining("\n\n"));}
    }
    public static String describe(Rule r) {
        String offer=switch(r.type()) {
            case PRICE -> "Selected products at R"+r.value()+" each";
            case PERCENT -> r.value()+"% off selected products";
            case AMOUNT -> "R"+r.value()+" off each selected product";
            case MULTIBUY -> "Buy "+r.buy()+(r.distinct()?" different selected products":" selected units")+" for R"+r.value();
            case BUY_GET -> "Buy "+r.buy()+", get "+r.get()+" at "+r.value()+"% off (identical lists discount the cheapest rewards)";
            case BUNDLE -> "Complete the "+r.items().stream().map(Item::group).distinct().count()+" required product groups for R"+r.value();
        };
        return r.id()+" — "+r.name()+"\n"+offer+"\n"+r.from().toLocalDate()+" through "+r.until().minusNanos(1).toLocalDate()
                +" · "+(r.code().isBlank()?"Automatic":"Code: "+r.code())+" · "+(r.limit()==0?"Repeat allowed":r.limit()+" applications per sale");
    }
    public void export(File file,List<Rule> rules,List<PromotionService.Product> catalog)throws IOException {
        for(Rule rule:rules)PromotionService.validate(rule);
        try(Workbook book=new XSSFWorkbook()) {
            Sheet instructions=book.createSheet("Instructions");instructions.setColumnWidth(0,120*256);
            String[] help={"Import creates draft revisions only. Existing approved versions stay live until a supervisor approves the replacement with a single-use key.",
                    "Promotion IDs are stable uppercase letters/digits/_/-. Reimporting an ID updates its draft; it does not create another offer.",
                    "Dates: yyyy-MM-dd. Both dates are included; the offer ends at midnight after End date (South Africa time).",
                    "Types: PRICE = special unit price; PERCENT = percent off a unit; AMOUNT = rand off a unit; MULTIBUY = quantity for a total; BUY_GET = buy X, reward Y; BUNDLE = complete all groups.",
                    "Use Offer price for PRICE/MULTIBUY/BUNDLE; Discount % for PERCENT/BUY_GET (100 = free, 50 = half price); Discount amount for AMOUNT. Leave other value columns blank.",
                    "Groups: ITEM for PRICE/PERCENT/AMOUNT/MULTIBUY; BUY and GET for BUY_GET; named groups such as MAIZE/FLOUR/RICE/OIL/EGGS for BUNDLE. Alternatives in a bundle group share a required quantity.",
                    "BUY_GET lists must be identical or separate. Scan every supplied unit, including free products. Identical lists discount the cheapest reward units.",
                    "Buy quantity: qualifying quantity for MULTIBUY/BUY_GET. Reward quantity: BUY_GET only. Required quantity: per bundle group; use 1 otherwise.",
                    "Different products: YES or NO, MULTIBUY only. Per-sale limit: 0 means repeat without a per-sale cap; otherwise caps complete applications.",
                    "Blank Coupon code means automatic. Enter an uppercase code for a code-triggered offer. Existing basket coupon codes cannot be reused.",
                    "Barcodes MUST be text, including leading zeros. Use Product Lookup, not product names, to identify products. Enter literal values; formulas are not imported.",
                    "Every complete offer is priced only when it saves money. Overlapping offers cannot reuse the same units. Partial returns use saved discounted unit amounts.",
                    "Example MULTIBUY: COLA3 | 3 x 2L cola | MULTIBUY | 2026-10-26 | 2026-11-01 | 60 | (blank %) | (blank amount) | 3 | 0 | 0 | NO | (blank code). Add its selected 2L barcodes under ITEM.",
                    "Example BUNDLE: PANTRY5 | Five-product basket | BUNDLE | same dates | 100. Add one selected 2kg maize under MAIZE, 2kg flour under FLOUR, 2kg rice under RICE, 2L oil under OIL and 30 eggs under EGGS; required quantity 1 each.",
                    "To add alternative brands, add another barcode to the SAME bundle group. Product pack sizes come from the selected barcode; categories alone never qualify."};
            for(int i=0;i<help.length;i++)instructions.createRow(i).createCell(0).setCellValue(help[i]);
            Sheet offers=sheet(book,"Promotions",OFFER),items=sheet(book,"Promotion Items",ITEMS);
            choices(offers,2,Arrays.stream(Type.values()).map(Enum::name).toArray(String[]::new));choices(offers,11,new String[]{"NO","YES"});
            int row=1,itemRow=1;for(Rule rule:rules) {
                Row r=offers.createRow(row++);put(r,0,rule.id());put(r,1,rule.name());put(r,2,rule.type().name());put(r,3,rule.from().toLocalDate().toString());put(r,4,rule.until().minusNanos(1).toLocalDate().toString());
                int valueColumn=switch(rule.type()){case PRICE,MULTIBUY,BUNDLE->5;case PERCENT,BUY_GET->6;case AMOUNT->7;};put(r,valueColumn,rule.value().toPlainString());
                put(r,8,Integer.toString(rule.buy()));put(r,9,Integer.toString(rule.get()));put(r,10,Integer.toString(rule.limit()));put(r,11,rule.distinct()?"YES":"NO");put(r,12,rule.code());
                for(Item item:rule.items()){Row ir=items.createRow(itemRow++);put(ir,0,rule.id());put(ir,1,item.barcode());put(ir,2,item.group());put(ir,3,Integer.toString(item.quantity()));}
            }
            Sheet lookup=sheet(book,"Product Lookup",new String[]{"Barcode","Product","Category","Normal price","Unit cost"});int index=1;
            for(var p:catalog){Row r=lookup.createRow(index++);put(r,0,p.barcode());put(r,1,p.name());put(r,2,p.category());put(r,3,p.price().toPlainString());put(r,4,p.cost()==null?"":p.cost().toPlainString());}
            CellStyle text=book.createCellStyle();text.setDataFormat(book.createDataFormat().getFormat("@"));items.setDefaultColumnStyle(1,text);lookup.setDefaultColumnStyle(0,text);
            try(OutputStream out=new FileOutputStream(file)){book.write(out);}
        }
    }
    private static Sheet sheet(Workbook book,String name,String[] headers){Sheet s=book.createSheet(name);Row row=s.createRow(0);for(int i=0;i<headers.length;i++){put(row,i,headers[i]);s.setColumnWidth(i,Math.min(36,Math.max(18,headers[i].length()+3))*256);}s.createFreezePane(0,1);return s;}
    private static void choices(Sheet sheet,int column,String[] values){var helper=sheet.getDataValidationHelper();var validation=helper.createValidation(helper.createExplicitListConstraint(values),new org.apache.poi.ss.util.CellRangeAddressList(1,200,column,column));validation.setShowErrorBox(true);sheet.addValidationData(validation);}
    private static void put(Row r,int col,String value){r.createCell(col,CellType.STRING).setCellValue(value==null?"":value);}
    public Plan analyze(File file,List<PromotionService.Product> catalog)throws IOException {
        List<String> errors=new ArrayList<>(),warnings=new ArrayList<>();List<Rule> rules=new ArrayList<>();
        Map<String,PromotionService.Product> products=new HashMap<>();catalog.forEach(p->products.put(p.barcode(),p));
        try(Workbook book=SpreadsheetFiles.open(file)) {
            Sheet offers=required(book,"Promotions",OFFER),items=required(book,"Promotion Items",ITEMS);
            if(offers.getLastRowNum()>200 || items.getLastRowNum()>10000)throw new IOException("Import at most 200 promotions and 10,000 product rows.");
            Map<String,List<Item>> selections=new LinkedHashMap<>();
            for(int n=1;n<=items.getLastRowNum();n++){Row row=items.getRow(n);if(blank(row))continue;try {
                String id=text(row,0).toUpperCase(Locale.ROOT);Cell barcodeCell=row.getCell(1);
                if(barcodeCell==null || barcodeCell.getCellType()!=CellType.STRING)throw new IllegalArgumentException("Barcode must be a text cell to preserve leading zeros.");
                String barcode=text(row,1);var product=products.get(barcode);if(product==null)throw new IllegalArgumentException("Unknown or archived barcode: "+barcode);
                String group=text(row,2).toUpperCase(Locale.ROOT);if(group.isBlank())group="ITEM";
                selections.computeIfAbsent(id,k->new ArrayList<>()).add(new Item(product.id(),barcode,group,integer(row,3,1)));
            }catch(RuntimeException e){errors.add("Promotion Items row "+(n+1)+": "+e.getMessage());}}
            Set<String> ids=new HashSet<>();
            for(int n=1;n<=offers.getLastRowNum();n++){Row row=offers.getRow(n);if(blank(row))continue;try {
                String id=text(row,0).toUpperCase(Locale.ROOT);if(!ids.add(id))throw new IllegalArgumentException("Duplicate promotion ID: "+id);
                Type type=Type.valueOf(text(row,2).toUpperCase(Locale.ROOT));int valueColumn=switch(type){case PRICE,MULTIBUY,BUNDLE->5;case PERCENT,BUY_GET->6;case AMOUNT->7;};
                for(int col=5;col<=7;col++)if(col!=valueColumn&&!text(row,col).isBlank())throw new IllegalArgumentException("Fill only the value column for "+type+".");
                String different=text(row,11).toUpperCase(Locale.ROOT);if(!Set.of("","YES","NO").contains(different))throw new IllegalArgumentException("Different products must be YES or NO.");
                Rule rule=new Rule(id,text(row,1),type,date(row,3).atStartOfDay(),date(row,4).plusDays(1).atStartOfDay(),integer(row,10,0),different.equals("YES"),text(row,12).toUpperCase(Locale.ROOT),new BigDecimal(text(row,valueColumn)),integer(row,8,0),integer(row,9,0),selections.getOrDefault(id,List.of()),0);
                PromotionService.validate(rule);rules.add(rule);warnings.addAll(warnings(rule,catalog));
            }catch(RuntimeException e){errors.add("Promotions row "+(n+1)+": "+e.getMessage());}}
            for(String id:selections.keySet())if(!ids.contains(id))errors.add("Product rows refer to an unknown promotion ID: "+id);
        }
        if(rules.isEmpty()&&errors.isEmpty())errors.add("The workbook contains no promotions.");
        return new Plan(rules,errors,warnings);
    }
    public static List<String> warnings(Rule rule,List<PromotionService.Product> catalog) {
        Map<Integer,PromotionService.Product> products=new HashMap<>();catalog.forEach(p->products.put(p.id(),p));
        boolean below=false;BigDecimal worst=BigDecimal.ZERO;
        if(rule.type()==Type.BUY_GET)return List.of(rule.id()+": review buy/get margins, including the cost of reward products.");
        Map<String,BigDecimal> groupCosts=new HashMap<>();
        for(Item item:rule.items()) {
            var p=products.get(item.productId());if(p==null||p.cost()==null)continue;
            BigDecimal net=switch(rule.type()){case PRICE->rule.value();case PERCENT->p.price().multiply(BigDecimal.ONE.subtract(rule.value().movePointLeft(2)));case AMOUNT->p.price().subtract(rule.value()).max(BigDecimal.ZERO);default->p.price();};
            if(Set.of(Type.PRICE,Type.PERCENT,Type.AMOUNT).contains(rule.type())&&net.compareTo(p.cost())<0)below=true;
            worst=worst.max(p.cost());groupCosts.merge(item.group(),p.cost().multiply(BigDecimal.valueOf(item.quantity())),BigDecimal::max);
        }
        if(rule.type()==Type.MULTIBUY&&worst.multiply(BigDecimal.valueOf(rule.buy())).compareTo(rule.value())>0)below=true;
        if(rule.type()==Type.BUNDLE&&groupCosts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add).compareTo(rule.value())>0)below=true;
        return below?List.of(rule.id()+": some eligible choices can sell below cost. Review the margin before activation."):List.of();
    }
    private static Sheet required(Workbook book,String name,String[] headers)throws IOException {Sheet s=book.getSheet(name);if(s==null||s.getRow(0)==null)throw new IOException("Missing sheet: "+name);for(int i=0;i<headers.length;i++)if(!headers[i].equals(text(s.getRow(0),i)))throw new IOException(name+": expected column "+headers[i]);return s;}
    private static boolean blank(Row row){if(row==null)return true;for(Cell c:row)if(c.getCellType()!=CellType.BLANK&&!(c.getCellType()==CellType.STRING&&c.getStringCellValue().isBlank()))return false;return true;}
    private static String text(Row row,int col){Cell c=row.getCell(col);if(c==null||c.getCellType()==CellType.BLANK)return "";if(c.getCellType()==CellType.FORMULA)throw new IllegalArgumentException("Replace formulas with literal values.");return new DataFormatter(Locale.ROOT).formatCellValue(c).trim();}
    private static int integer(Row row,int col,int defaultValue){String value=text(row,col);return value.isBlank()?defaultValue:new BigDecimal(value).intValueExact();}
    private static LocalDate date(Row row,int col){Cell c=row.getCell(col);if(c!=null&&c.getCellType()==CellType.NUMERIC&&DateUtil.isCellDateFormatted(c))return c.getLocalDateTimeCellValue().toLocalDate();return LocalDate.parse(text(row,col));}
}
