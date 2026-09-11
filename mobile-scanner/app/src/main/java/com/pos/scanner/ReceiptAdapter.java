package com.pos.scanner;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class ReceiptAdapter extends RecyclerView.Adapter<ReceiptAdapter.ReceiptViewHolder> {

    private List<Receipt> receipts;
    private Context context;

    public ReceiptAdapter(List<Receipt> receipts, Context context) {
        this.receipts = receipts;
        this.context = context;
    }

    @NonNull
    @Override
    public ReceiptViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_receipt, parent, false);
        return new ReceiptViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReceiptViewHolder holder, int position) {
        Receipt receipt = receipts.get(position);

        holder.customerNameText.setText(receipt.getCustomerName());
        holder.dateText.setText(receipt.getDate());
        holder.itemCountText.setText(receipt.getItems().size() + " items");
        holder.totalText.setText(String.format(Locale.getDefault(), "R %.2f", receipt.getTotal()));

        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ReceiptDetailActivity.class);
            intent.putExtra("receipt", receipt);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return receipts.size();
    }

    static class ReceiptViewHolder extends RecyclerView.ViewHolder {
        TextView customerNameText;
        TextView dateText;
        TextView itemCountText;
        TextView totalText;

        public ReceiptViewHolder(@NonNull View itemView) {
            super(itemView);
            customerNameText = itemView.findViewById(R.id.customerNameText);
            dateText = itemView.findViewById(R.id.dateText);
            itemCountText = itemView.findViewById(R.id.itemCountText);
            totalText = itemView.findViewById(R.id.totalText);
        }
    }
}