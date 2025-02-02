package com.takagen.launcher.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.media.tv.TvContract;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.takagen.launcher.App;
import com.takagen.launcher.R;
import com.takagen.launcher.databinding.DialogInputBinding;

public class InputDialog {

    private final DialogInputBinding binding;
    String id_0 = "com.google.android.videos/.tv.usecase.tvinput.playback.TvInputService"; // Tuner
    String id_1 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW4";     // HDMI 1
    String id_2 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5";     // HDMI 2
    String id_3 = "com.mediatek.tvinput/.hdmi.HDMIInputService/HW6";     // HDMI 3
    String id_av = "com.mediatek.tvinput/.composite.CompositeInputService/HW1";     // AV Composite
    private AlertDialog dialog;
    private Uri mUri;

    public InputDialog(Activity activity) {
        this.binding = DialogInputBinding.inflate(LayoutInflater.from(activity));
    }

    public static InputDialog create(Activity activity) {
        return new InputDialog(activity);
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setView(binding.getRoot()).create();
        dialog.getWindow().setDimAmount(0.1f);
        dialog.getWindow().setBackgroundDrawableResource(R.drawable.shape_dialog_bg);
        dialog.getWindow().getAttributes().windowAnimations = R.style.InputDialogAnimation;
        dialog.show();
    }

    private void initView() {
        binding.tvHDMI1.requestFocus();
    }

    private void initEvent() {
        binding.tvHDMI1.setOnClickListener(this::onInputClick);
        binding.tvHDMI2.setOnClickListener(this::onInputClick);
        binding.tvHDMI3.setOnClickListener(this::onInputClick);
        binding.tvAV.setOnClickListener(this::onInputClick);
    }

    private void onInputClick(View v) {
        if (v == binding.tvHDMI1) {
            callInput(id_1);
        } else if (v == binding.tvHDMI2) {
            callInput(id_2);
        } else if (v == binding.tvHDMI3) {
            callInput(id_3);
        } else if (v == binding.tvAV) {
            callInput(id_av);
        }
        dialog.dismiss();
    }

    private void callInput(String id) {
        mUri = TvContract.buildChannelUriForPassthroughInput(id);
//        App.get().startActivity(new Intent(Intent.ACTION_VIEW, mUri), mUri);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setData(mUri);
        App.get().startActivity(intent);
    }

}