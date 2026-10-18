package com.pos.scanner;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Locale;

public class ReceiptDetailActivity extends AuthenticatedActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_detail);
        findViewById(R.id.receiptBack).setOnClickListener(v -> finish());
        String id = getIntent().getStringExtra("receiptId");
        // Only the ID crosses the Activity boundary; long receipts can exceed
        // Android's Binder transaction limit when passed as a serialized object.
        new Thread(() -> {
            Receipt receipt = id == null ? null : ReceiptStorage.getInstance(getApplicationContext()).getReceiptById(id);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (receipt == null) {
                    Toast.makeText(this, "This receipt is no longer available.", Toast.LENGTH_LONG).show();
                    finish(); return;
                }
                showReceipt(receipt);
            });
        }, "receipt-detail").start();
    }

    private void text(int id, String value) { ((TextView)findViewById(id)).setText(value); }
    private void showReceipt(Receipt receipt) {
        text(R.id.customerNameText, "Customer: " + receipt.getCustomerName());
        String email = receipt.getCustomerEmail();
        text(R.id.customerEmailText, "Email: " + (email == null || email.isEmpty() ? "Not supplied" : email));
        text(R.id.cashierNameText, "Cashier: " + receipt.getCashierName());
        text(R.id.dateText, "Date: " + receipt.getDate());
        RecyclerView items = findViewById(R.id.itemsRecyclerView);
        items.setLayoutManager(new LinearLayoutManager(this));
        items.setAdapter(new ReceiptItemAdapter(receipt.getItems()));
        text(R.id.totalText, String.format(Locale.getDefault(), "R %.2f", receipt.getTotal()));
        text(R.id.fullReceiptText, receipt.getFullReceiptText());
    }
}
