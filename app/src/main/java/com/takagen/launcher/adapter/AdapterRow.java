package com.takagen.launcher.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.widget.PopupWindow;

import com.takagen.launcher.R;
import com.takagen.launcher.bean.AppInfo;
import com.takagen.launcher.databinding.AdapterBannerBinding;
import com.takagen.launcher.databinding.AdapterIconBinding;

import java.util.Collections;
import java.util.List;

public class AdapterRow extends RecyclerView.Adapter<AdapterRow.Holder> {

    public interface Listener {
        void onItemClick(AppInfo item);
        void onReorder(List<AppInfo> newOrder, boolean isTv);
        void onUninstall(AppInfo item);
    }

    private final List<AppInfo> items;
    private final boolean isTv;
    private final Listener listener;

    private int movingFrom = -1;        // start position when move began (for change detection)
    private int selectedPosition = -1;  // current position of the app being moved
    private boolean moved = false;      // true once at least one swap happened

    public AdapterRow(List<AppInfo> items, boolean isTv, Listener listener) {
        this.items = items;
        this.isTv = isTv;
        this.listener = listener;
        setHasStableIds(true);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).getPack().hashCode();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ViewBinding binding = isTv
                ? AdapterBannerBinding.inflate(inflater, parent, false)
                : AdapterIconBinding.inflate(inflater, parent, false);
        return new Holder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(items.get(position));
    }

    private boolean inMoveMode() {
        return selectedPosition != -1;
    }

    boolean isInMoveMode() {
        return selectedPosition != -1;
    }

    private void enterMoveMode(View v, int position) {
        movingFrom = position;
        selectedPosition = position;
        moved = false;
        v.animate().scaleX(1.5f).scaleY(1.5f).alpha(0.4f).setDuration(150).start();
        Toast.makeText(v.getContext(),
                "Move with D-pad, click to drop",
                Toast.LENGTH_SHORT).show();
    }

    void exitMoveMode() {
        int settled = selectedPosition;
        boolean didMove = moved;
        movingFrom = -1;
        selectedPosition = -1;
        moved = false;
        if (didMove && listener != null) {
            listener.onReorder(items, isTv);
        }
        // Rebind the settled holder so bind() resets its visual state cleanly.
        if (settled != -1) notifyItemChanged(settled);
    }

    private void showLongPressMenu(View anchor, int pos, AppInfo item) {
        View content = LayoutInflater.from(anchor.getContext())
                .inflate(R.layout.popup_app_options, null);

        // Measure content so we know its exact dimensions before showing
        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = content.getMeasuredWidth();
        int popupH = content.getMeasuredHeight();
        int gap = Math.round(6 * anchor.getResources().getDisplayMetrics().density);

        PopupWindow popup = new PopupWindow(content, popupW, popupH, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setElevation(16f);
        popup.setOutsideTouchable(true);

        content.findViewById(R.id.optionMove).setOnClickListener(v -> {
            popup.dismiss();
            enterMoveMode(anchor, pos);
        });
        content.findViewById(R.id.optionUninstall).setOnClickListener(v -> {
            popup.dismiss();
            // Post after dismiss so the popup window is fully gone before
            // we start a new Activity — avoids window-focus race on some builds.
            anchor.post(() -> {
                if (listener != null) listener.onUninstall(item);
            });
        });

        // Show to the right of the anchor, top-aligned at the anchor's midpoint
        int xOff = anchor.getWidth() + gap;
        int yOff = -anchor.getHeight() / 2;
        popup.showAsDropDown(anchor, xOff, yOff);
        content.post(() -> content.findViewById(R.id.optionMove).requestFocus());
    }

    class Holder extends RecyclerView.ViewHolder {

        private final ViewBinding binding;

        Holder(ViewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> onFocus(v, hasFocus));
        }

        void bind(AppInfo item) {
            // Reset any lingering move-mode or focus visual from a previous bind.
            View root = binding.getRoot();
            root.clearAnimation();
            root.setScaleX(1.0f);
            root.setScaleY(1.0f);
            root.setAlpha(0.6f);
            root.setElevation(0);
            root.setSelected(false);

            if (binding instanceof AdapterBannerBinding) {
                AdapterBannerBinding b = (AdapterBannerBinding) binding;
                b.name.setText(item.getName());
                b.icon.setImageDrawable(item.getBanner());
            } else {
                AdapterIconBinding b = (AdapterIconBinding) binding;
                b.name.setText(item.getName());
                b.icon.setImageDrawable(item.getIcon());
            }

            root.setOnClickListener(v -> {
                if (inMoveMode()) {
                    exitMoveMode();
                } else if (listener != null) {
                    listener.onItemClick(item);
                }
            });
            root.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;
                if (inMoveMode()) {
                    exitMoveMode();
                } else {
                    showLongPressMenu(v, pos, item);
                }
                return true;
            });
        }

        private void onFocus(View v, boolean hasFocus) {
            int pos = getBindingAdapterPosition();
            if (hasFocus) {
                if (inMoveMode() && pos != RecyclerView.NO_POSITION && pos != selectedPosition) {
                    // Focus landed on a neighbour — swap selected item to this position.
                    Collections.swap(items, selectedPosition, pos);
                    final int newPos = pos;
                    notifyItemMoved(selectedPosition, pos);
                    selectedPosition = pos;
                    moved = true;

                    // v is the NEIGHBOUR's view, not the selected item.
                    // Redirect focus to the selected item (now at newPos) so it stays
                    // "held" throughout. onFocus(true) for newPos applies move visual.
                    RecyclerView rv = (RecyclerView) v.getParent();
                    if (rv != null) {
                        rv.post(() -> {
                            RecyclerView.ViewHolder target =
                                    rv.findViewHolderForAdapterPosition(newPos);
                            if (target != null) target.itemView.requestFocus();
                        });
                    }

                } else if (inMoveMode() && pos == selectedPosition) {
                    // Selected item (re-)gained focus — maintain move visual.
                    applyMoveVisual(v);
                } else {
                    applyFocusVisual(v, true);
                }

            } else {
                if (inMoveMode() && pos != RecyclerView.NO_POSITION && pos == selectedPosition) {
                    // The selected item is temporarily losing focus during a swap transition
                    // (focus is about to return via the post above). Keep move visual so it
                    // does not flicker back to the unfocused/scaled-down appearance.
                    applyMoveVisual(v);
                } else {
                    applyFocusVisual(v, false);
                }
                // If focus left the row entirely (not just moved to a sibling), drop move mode.
                v.post(() -> {
                    if (!inMoveMode()) return;
                    RecyclerView parent = (RecyclerView) v.getParent();
                    if (parent != null && !parent.hasFocus()) exitMoveMode();
                });
            }
        }

        private void applyFocusVisual(View v, boolean focused) {
            if (focused) {
                v.setAlpha(1f);
                v.setElevation(10);
                v.animate().scaleX(1.25f).scaleY(1.25f).setDuration(100).start();
                v.setSelected(true);
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                v.setSelected(false);
                v.setElevation(0);
                v.setAlpha(0.6f);
            }
        }

        private void applyMoveVisual(View v) {
            v.setElevation(20);
            v.setAlpha(0.4f);
            v.animate().scaleX(1.5f).scaleY(1.5f).setDuration(150).start();
        }
    }
}
