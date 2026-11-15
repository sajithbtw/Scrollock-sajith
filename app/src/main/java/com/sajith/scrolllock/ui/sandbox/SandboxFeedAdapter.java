package com.sajith.scrolllock.ui.sandbox;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.databinding.ItemSandboxCardBinding;
import com.sajith.scrolllock.data.model.DemoFeedItem;

import java.util.List;

/**
 * Adapter rendering calm demonstration cards inside the Scroll-Lock Sandbox Feed.
 */
public class SandboxFeedAdapter extends RecyclerView.Adapter<SandboxFeedAdapter.CardViewHolder> {

    private final List<DemoFeedItem> items;

    public SandboxFeedAdapter(List<DemoFeedItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSandboxCardBinding binding = ItemSandboxCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new CardViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        DemoFeedItem item = items.get(position);
        holder.binding.tvSandboxCardTag.setText(item.getTag());
        holder.binding.tvSandboxCardTitle.setText(item.getTitle());
        holder.binding.tvSandboxCardBody.setText(item.getBody());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        final ItemSandboxCardBinding binding;

        CardViewHolder(ItemSandboxCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
