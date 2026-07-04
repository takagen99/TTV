package com.takagen.launcher.adapter;

import android.content.Context;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.Setting;
import com.takagen.launcher.bean.AppItem;
import com.takagen.launcher.databinding.ItemHeaderBinding;
import com.takagen.launcher.ui.custom.ShakeDpadRecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdapterMain extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER        = 0;
    private static final int TYPE_TV_SECTION    = 1;
    private static final int TYPE_MOBILE_SECTION = 2;

    private final List<AppItem> items = new ArrayList<>();
    private final AdapterRow.Listener listener;

    // One shared AdapterRow per bucket — reused across rebinds so inner RV scroll is preserved.
    private AdapterRow tvAdapter;
    private AdapterRow mobileAdapter;

    public AdapterMain(AdapterRow.Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    /** Call from Activity.onResume to re-apply row heights if changed in Settings. */
    public void refreshRowHeights() {
        notifyDataSetChanged();
    }

    /** Exits move mode on any active row. Returns true if move mode was active. */
    public boolean exitAnyMoveMode() {
        boolean exited = false;
        if (tvAdapter != null && tvAdapter.isInMoveMode()) {
            tvAdapter.exitMoveMode();
            exited = true;
        }
        if (mobileAdapter != null && mobileAdapter.isInMoveMode()) {
            mobileAdapter.exitMoveMode();
            exited = true;
        }
        return exited;
    }

    public void submit(List<AppItem> newItems) {
        items.clear();
        items.addAll(newItems);
        tvAdapter = null;
        mobileAdapter = null;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        AppItem item = items.get(position);
        if (item instanceof AppItem.Header) return TYPE_HEADER;
        return ((AppItem.Section) item).isTv ? TYPE_TV_SECTION : TYPE_MOBILE_SECTION;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        AppItem item = items.get(position);
        if (item instanceof AppItem.Header) {
            return ("h:" + ((AppItem.Header) item).title).hashCode();
        }
        return ("s:" + (((AppItem.Section) item).isTv ? "tv" : "mobile")).hashCode();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(ItemHeaderBinding.inflate(inflater, parent, false));
        }
        boolean isTv = (viewType == TYPE_TV_SECTION);
        return new SectionHolder(createRow(parent.getContext(), isTv), isTv);
    }

    private ShakeDpadRecyclerView createRow(Context context, boolean isTv) {
        ShakeDpadRecyclerView rv = new ShakeDpadRecyclerView(context);
        rv.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        rv.setClipToPadding(false);
        rv.setClipChildren(false);
        rv.setPadding(24, 8, 24, 8);
        rv.setHasFixedSize(true);
        rv.setItemAnimator(null);
        rv.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, rowHeightPx(context, isTv)));
        return rv;
    }

    private int rowHeightPx(Context context, boolean isTv) {
        int dp = isTv ? Setting.getTvRowDp() : Setting.getMobileRowDp();
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }

    private AdapterRow rowAdapterFor(AppItem.Section section) {
        if (section.isTv) {
            if (tvAdapter == null) tvAdapter = new AdapterRow(section.apps, true, listener);
            return tvAdapter;
        }
        if (mobileAdapter == null) mobileAdapter = new AdapterRow(section.apps, false, listener);
        return mobileAdapter;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        AppItem item = items.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind((AppItem.Header) item);
        } else {
            AppItem.Section section = (AppItem.Section) item;
            ((SectionHolder) holder).bind(section, rowAdapterFor(section));
        }
    }

    // ——— HeaderHolder ———

    static class HeaderHolder extends RecyclerView.ViewHolder {
        private final ItemHeaderBinding binding;

        HeaderHolder(ItemHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setFocusable(false);
        }

        void bind(AppItem.Header header) {
            binding.headerTitle.setText(header.title);
        }
    }

    // ——— SectionHolder ———

    class SectionHolder extends RecyclerView.ViewHolder {
        private final ShakeDpadRecyclerView recyclerView;
        private final boolean isTv;

        SectionHolder(ShakeDpadRecyclerView rv, boolean isTv) {
            super(rv);
            this.recyclerView = rv;
            this.isTv = isTv;
        }

        void bind(AppItem.Section section, AdapterRow rowAdapter) {
            // Re-apply height in case user changed it in Settings since this holder was created.
            int heightPx = rowHeightPx(recyclerView.getContext(), isTv);
            ViewGroup.LayoutParams lp = recyclerView.getLayoutParams();
            if (lp != null && lp.height != heightPx) {
                lp.height = heightPx;
                recyclerView.setLayoutParams(lp);
            }
            // Only swap adapter when it actually changes — preserves inner scroll position.
            if (recyclerView.getAdapter() != rowAdapter) {
                recyclerView.setAdapter(rowAdapter);
            }
        }
    }
}
