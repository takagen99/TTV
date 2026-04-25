package com.takagen.launcher.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.R;
import com.takagen.launcher.bean.AppHideItem;
import com.takagen.launcher.databinding.ItemAppHideBinding;

import java.util.List;

public class AdapterAppsHide extends RecyclerView.Adapter<AdapterAppsHide.Holder> {

    public interface Listener {
        void onToggle(int position, AppHideItem item);
    }

    private final List<AppHideItem> items;
    private final Listener listener;

    public AdapterAppsHide(List<AppHideItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemAppHideBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class Holder extends RecyclerView.ViewHolder {

        private final ItemAppHideBinding binding;

        Holder(ItemAppHideBinding binding) {
            super(binding.getRoot());
            this.binding = binding;

            binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> {
                v.animate()
                        .scaleX(hasFocus ? 1.03f : 1.0f)
                        .scaleY(hasFocus ? 1.03f : 1.0f)
                        .setDuration(100)
                        .start();
            });

            binding.getRoot().setOnClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onToggle(pos, items.get(pos));
                }
            });
        }

        void bind(AppHideItem item) {
            binding.icon.setImageDrawable(item.app.getIcon());
            binding.name.setText(item.app.getName());
            applyVisibility(item);
        }

        void applyVisibility(AppHideItem item) {
            android.content.Context ctx = binding.getRoot().getContext();
            if (item.hidden) {
                binding.status.setText(ctx.getString(R.string.apps_hide_status_hidden));
                binding.status.setTextColor(0xFFAAAAAA);
                binding.icon.setAlpha(0.35f);
                binding.name.setAlpha(0.5f);
            } else {
                binding.status.setText(ctx.getString(R.string.apps_hide_status_visible));
                binding.status.setTextColor(0xFFFFFFFF);
                binding.icon.setAlpha(1.0f);
                binding.name.setAlpha(1.0f);
            }
        }
    }
}
