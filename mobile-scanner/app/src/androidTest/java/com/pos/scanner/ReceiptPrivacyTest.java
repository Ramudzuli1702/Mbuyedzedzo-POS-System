package com.pos.scanner;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReceiptPrivacyTest {
    private Context context;private ReceiptStorage storage;
    private final String desktop=new String(new char[64]).replace('\0','f');
    @Before public void setup(){context=InstrumentationRegistry.getInstrumentation().getTargetContext();storage=ReceiptStorage.getInstance(context);storage.setScope(desktop,2147483000);storage.clearAllReceipts();}
    @After public void cleanup(){storage.setScope(desktop,2147483000);storage.clearAllReceipts();storage.clearScope();}
    @Test public void encryptedReceiptsBelongToTheirDesktopAndStaffAccount() {
        Receipt receipt=Receipt.fromString("Customer: PrivacyFixtureCustomer-October2026\nEmail: privacy-fixture@example.test\nTOTAL R2.50\n");
        assertTrue(storage.saveReceipt(receipt));assertEquals(1,storage.getAllReceipts().size());
        String stored=context.getSharedPreferences("receipts_prefs",Context.MODE_PRIVATE).getString("v2_"+desktop+"_2147483000","");
        assertFalse(stored.contains("PrivacyFixtureCustomer"));assertFalse(stored.contains("privacy-fixture@example.test"));
        storage.setScope(desktop,2147483001);assertTrue(storage.getAllReceipts().isEmpty());
        storage.setScope(new String(new char[64]).replace('\0','e'),2147483000);assertTrue(storage.getAllReceipts().isEmpty());
        storage.setScope(desktop,2147483000);assertEquals(receipt.getReceiptId(),storage.getAllReceipts().get(0).getReceiptId());
        storage.clearScope();assertTrue(storage.getAllReceipts().isEmpty());assertFalse(storage.saveReceipt(receipt));
    }
    @Test public void alteredCiphertextIsNeverShownAsAReceipt() {
        Receipt receipt=Receipt.fromString("Customer: Test\nTOTAL R1.00\n");assertTrue(storage.saveReceipt(receipt));
        context.getSharedPreferences("receipts_prefs",Context.MODE_PRIVATE).edit().putString("v2_"+desktop+"_2147483000","tampered:AAAA").commit();
        assertTrue(storage.getAllReceipts().isEmpty());
    }
}
