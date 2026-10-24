package com.pos.services;

import com.pos.services.PromotionEngine.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.pos.services.PromotionEngineTest.*;

class PromotionExcelServiceTest {
    @TempDir Path directory;
    List<PromotionService.Product> products=List.of(new PromotionService.Product(1,"0001","2L Cola","Drinks",money("25"),money("12")),new PromotionService.Product(2,"0002","2kg Rice","Food",money("30"),money("20")));
    @Test void workbookRoundTripsAllSixTypesAndPreservesBarcodeZeros()throws Exception {
        List<Rule> rules=List.of(rule("PRICE",Type.PRICE,"20",0,0,0,false,"",item(1,"ITEM")),rule("PERCENT",Type.PERCENT,"10",0,0,0,false,"",item(1,"ITEM")),rule("AMOUNT",Type.AMOUNT,"3",0,0,0,false,"",item(1,"ITEM")),rule("MULTI",Type.MULTIBUY,"60",3,0,2,false,"",item(1,"ITEM")),rule("BOGO",Type.BUY_GET,"100",1,1,0,false,"",item(1,"BUY"),item(1,"GET")),rule("BUNDLE",Type.BUNDLE,"40",0,0,0,false,"",item(1,"DRINK"),item(2,"RICE")));
        var file=directory.resolve("offers.xlsx").toFile();var excel=new PromotionExcelService();excel.export(file,rules,products);var plan=excel.analyze(file,products);
        assertTrue(plan.valid(),plan.errors().toString());assertEquals(6,plan.rules().size());assertEquals("0001",plan.rules().get(0).items().get(0).barcode());assertEquals(rules.get(0).until().toLocalDate(),plan.rules().get(0).until().toLocalDate());
        assertTrue(plan.summary().contains("60"));
    }
    @Test void numericAndUnknownBarcodesAreRejectedBeforeImport()throws Exception {
        var file=directory.resolve("invalid.xlsx").toFile();var excel=new PromotionExcelService();excel.export(file,List.of(rule("PRICE",Type.PRICE,"20",0,0,0,false,"",item(1,"ITEM"))),products);
        try(var input=Files.newInputStream(file.toPath());var book=new XSSFWorkbook(input)){book.getSheet("Promotion Items").getRow(1).getCell(1).setCellValue(1d);try(var out=Files.newOutputStream(file.toPath())){book.write(out);}}
        var plan=excel.analyze(file,products);assertFalse(plan.valid());assertTrue(plan.errors().stream().anyMatch(s->s.contains("text cell")));
    }
    @Test void formulasAndDuplicatePromotionIdsAreReportedAsWorkbookErrors()throws Exception {
        var file=directory.resolve("formulas.xlsx").toFile();var excel=new PromotionExcelService();var offer=rule("PRICE",Type.PRICE,"20",0,0,0,false,"",item(1,"ITEM"));excel.export(file,List.of(offer,offer),products);
        try(var input=Files.newInputStream(file.toPath());var book=new XSSFWorkbook(input)){book.getSheet("Promotions").getRow(1).getCell(5).setCellFormula("10+10");try(var out=Files.newOutputStream(file.toPath())){book.write(out);}}
        var plan=excel.analyze(file,products);assertFalse(plan.valid());assertTrue(plan.errors().stream().anyMatch(s->s.contains("formulas")));assertTrue(plan.errors().stream().anyMatch(s->s.contains("Duplicate promotion")));
    }
    @Test void belowCostWarningsDoNotSilentlyActivateOrAlterOffers()throws Exception {
        var file=directory.resolve("margin.xlsx").toFile();var excel=new PromotionExcelService();excel.export(file,List.of(rule("CHEAP",Type.PRICE,"1",0,0,0,false,"",item(1,"ITEM"))),products);var plan=excel.analyze(file,products);assertTrue(plan.valid());assertFalse(plan.warnings().isEmpty());assertEquals(money("1"),plan.rules().get(0).value());
    }
    @Test void workedExamplesValidateAndRoundTripThePantryAndColaOffers()throws Exception {
        var catalog=new ArrayList<>(products);
        catalog.addAll(List.of(new PromotionService.Product(3,"DEMO-MAIZE-2KG","2kg maize","Staples",money("25"),money("15")),new PromotionService.Product(4,"DEMO-FLOUR-2KG","2kg flour","Baking",money("20"),money("10")),new PromotionService.Product(5,"DEMO-OIL-2L","2L cooking oil","Cooking",money("35"),money("15")),new PromotionService.Product(6,"DEMO-EGGS-30","30 eggs","Eggs",money("50"),money("25"))));
        List<Rule> rules=List.of(rule("COLA3",Type.MULTIBUY,"60",3,0,0,false,"",item(1,"ITEM")),
                rule("PANTRY5",Type.BUNDLE,"100",0,0,0,false,"",new Item(3,"DEMO-MAIZE-2KG","MAIZE",1),new Item(4,"DEMO-FLOUR-2KG","FLOUR",1),item(2,"RICE"),new Item(5,"DEMO-OIL-2L","OIL",1),new Item(6,"DEMO-EGGS-30","EGGS",1)),
                rule("COLA_PRICE",Type.PRICE,"22",0,0,0,false,"",item(1,"ITEM")),rule("RICE_PERCENT",Type.PERCENT,"10",0,0,0,false,"",item(2,"ITEM")),
                rule("FLOUR_AMOUNT",Type.AMOUNT,"3",0,0,0,false,"",new Item(4,"DEMO-FLOUR-2KG","ITEM",1)),
                rule("COLA_BOGO",Type.BUY_GET,"100",1,1,0,false,"BOGO",item(1,"BUY"),item(1,"GET")),
                rule("MIX3",Type.MULTIBUY,"65",3,0,1,true,"MIX",item(2,"ITEM"),new Item(3,"DEMO-MAIZE-2KG","ITEM",1),new Item(4,"DEMO-FLOUR-2KG","ITEM",1)));
        var excel=new PromotionExcelService();var file=directory.resolve("Sample_Promotions.xlsx").toFile();excel.export(file,rules,catalog);
        var plan=excel.analyze(file,catalog);assertTrue(plan.valid(),plan.errors().toString());assertEquals(7,plan.rules().size());
        assertEquals(5,plan.rules().stream().filter(r->r.id().equals("PANTRY5")).findFirst().orElseThrow().items().size());
        String output=System.getProperty("pos.promotions.examples");
        if(output!=null){Path target=Path.of(output);Files.createDirectories(target.toAbsolutePath().getParent());Files.copy(file.toPath(),target,StandardCopyOption.REPLACE_EXISTING);}
    }
}
