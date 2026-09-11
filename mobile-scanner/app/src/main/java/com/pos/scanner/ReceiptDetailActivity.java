package com.pos.scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.ParcelFileDescriptor;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ReceiptDetailActivity extends AppCompatActivity {

    private TextView customerNameText;
    private TextView customerEmailText;
    private TextView cashierNameText;
    private TextView dateText;
    private RecyclerView itemsRecyclerView;
    private TextView totalText;
    private TextView fullReceiptText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt_detail);

        customerNameText = findViewById(R.id.customerNameText);
        customerEmailText = findViewById(R.id.customerEmailText);
        cashierNameText = findViewById(R.id.cashierNameText);
        dateText = findViewById(R.id.dateText);
        itemsRecyclerView = findViewById(R.id.itemsRecyclerView);
        totalText = findViewById(R.id.totalText);
        fullReceiptText = findViewById(R.id.fullReceiptText);

        Receipt receipt = (Receipt) getIntent().getSerializableExtra("receipt");

        if (receipt != null) {
            customerNameText.setText(receipt.getCustomerName());
            customerEmailText.setText(receipt.getCustomerEmail());
            cashierNameText.setText("Cashier: " + receipt.getCashierName());
            dateText.setText(receipt.getDate());

            itemsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
            ReceiptItemAdapter itemAdapter = new ReceiptItemAdapter(receipt.getItems());
            itemsRecyclerView.setAdapter(itemAdapter);

            totalText.setText(String.format(Locale.getDefault(), "R %.2f", receipt.getTotal()));

            fullReceiptText.setText(receipt.getFullReceiptText());
        }
    }
}