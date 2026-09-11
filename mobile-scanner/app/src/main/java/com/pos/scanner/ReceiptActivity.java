package com.pos.scanner;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ReceiptActivity extends AppCompatActivity {
    
    private RecyclerView receiptsRecyclerView;
    private TextView emptyStateText;
    private ProgressBar progressBar;
    private FloatingActionButton refreshFab;
    
    private ReceiptAdapter receiptAdapter;
    private List<Receipt> receipts;
    
    private Handler handler;
    
    // Broadcast receiver for new receipts from Bluetooth
    private final BroadcastReceiver receiptReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("RECEIPT_RECEIVED".equals(intent.getAction())) {
                Receipt receipt = (Receipt) intent.getSerializableExtra("receipt");
                if (receipt != null) {
                    // Add to list and update UI
                    receipts.add(0, receipt);
                    receiptAdapter.notifyItemInserted(0);
                    receiptsRecyclerView.scrollToPosition(0);
                    updateEmptyState();
                    
                    Toast.makeText(ReceiptActivity.this, 
                        "New receipt received!", 
                        Toast.LENGTH_SHORT).show();
                }
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receipt);
        
        initializeViews();
        setupRecyclerView();
        loadReceipts();
        
        refreshFab.setOnClickListener(v -> refreshReceipts());
        
        // Register broadcast receiver for new receipts
        LocalBroadcastManager.getInstance(this).registerReceiver(
                receiptReceiver, new IntentFilter("RECEIPT_RECEIVED"));
    }
    
    private void initializeViews() {
        receiptsRecyclerView = findViewById(R.id.receiptsRecyclerView);
        emptyStateText = findViewById(R.id.emptyStateText);
        progressBar = findViewById(R.id.progressBar);
        refreshFab = findViewById(R.id.refreshFab);
        
        handler = new Handler(Looper.getMainLooper());
    }
    
    private void setupRecyclerView() {
        receipts = new ArrayList<>();
        receiptAdapter = new ReceiptAdapter(receipts, this);
        
        receiptsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        receiptsRecyclerView.setAdapter(receiptAdapter);
    }
    
    private void loadReceipts() {
        progressBar.setVisibility(View.VISIBLE);
        
        new Thread(() -> {
            List<Receipt> loadedReceipts = ReceiptStorage.getInstance(this).getAllReceipts();
            
            handler.post(() -> {
                receipts.clear();
                receipts.addAll(loadedReceipts);
                receiptAdapter.notifyDataSetChanged();
                
                progressBar.setVisibility(View.GONE);
                updateEmptyState();
            });
        }).start();
    }
    
    private void refreshReceipts() {
        loadReceipts();
        Toast.makeText(this, "Receipts refreshed", Toast.LENGTH_SHORT).show();
    }
    
    private void updateEmptyState() {
        if (receipts.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            receiptsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            receiptsRecyclerView.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unregister broadcast receiver
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiptReceiver);
    }
}