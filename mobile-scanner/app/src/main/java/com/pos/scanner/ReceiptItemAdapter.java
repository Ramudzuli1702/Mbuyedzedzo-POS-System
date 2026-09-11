package com.pos.scanner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class ReceiptItemAdapter extends RecyclerView.Adapter<ReceiptItemAdapter.ItemViewHolder> {
    
    private List<Receipt.ReceiptItem> items;
    
    public ReceiptItemAdapter(List<Receipt.ReceiptItem> items) {
        this.items = items;
    }
    
    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_receipt_item, parent, false);
        return new ItemViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        Receipt.ReceiptItem item = items.get(position);
        
        holder.itemNameText.setText(item.getName());
        holder.itemQuantityText.setText("Qty: " + item.getQuantity());
        holder.itemPriceText.setText(String.format("R %.2f", item.getPrice()));
        holder.itemSubtotalText.setText(String.format("R %.2f", item.getSubtotal()));
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView itemNameText;
        TextView itemQuantityText;
        TextView itemPriceText;
        TextView itemSubtotalText;
        
        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            itemNameText = itemView.findViewById(R.id.itemNameText);
            itemQuantityText = itemView.findViewById(R.id.itemQuantityText);
            itemPriceText = itemView.findViewById(R.id.itemPriceText);
            itemSubtotalText = itemView.findViewById(R.id.itemSubtotalText);
        }
    }
}