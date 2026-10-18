package com.pos.utils;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DocumentNamesTest {
    @Test void namesArePathSafeAndRetainTheDocumentReference() {
        String name = DocumentNames.name("Stocktake", "../branch:west/007", "XLSX");
        assertTrue(name.matches("Mbuyedzedzo_Stocktake_branch-west-007_\\d{4}-\\d{2}-\\d{2}_\\d{6}_\\d{3}\\.xlsx"), name);
        assertFalse(name.contains(".."));
    }
    @Test void rapidExportsHaveDistinctNames() {
        var names = new HashSet<String>();
        for(int i=0;i<1000;i++) assertTrue(names.add(DocumentNames.name("Inventory",null,"xlsx")));
    }
}
