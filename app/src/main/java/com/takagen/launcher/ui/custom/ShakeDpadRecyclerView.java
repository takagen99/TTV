package com.takagen.launcher.ui.custom;

import android.content.Context;
import android.util.AttributeSet;
import android.view.FocusFinder;
import android.view.View;
import android.view.animation.Animation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.takagen.launcher.R;
import com.takagen.launcher.utils.Utils;

/**
 * Horizontal RecyclerView that shakes the focused view when DPAD_LEFT / DPAD_RIGHT
 * cannot find a next focus target within this row.
 *
 * Extends plain RecyclerView (not DpadRecyclerView) so the layout manager performs
 * standard start-edge scrolling. DpadRecyclerView's PivotLayoutManager centers the
 * focused item, which causes the row to visually shift left/right and snaps the first
 * item back into view when it should be off-screen.
 */
public class ShakeDpadRecyclerView extends RecyclerView {

    private Animation shake;

    public ShakeDpadRecyclerView(@NonNull Context context) {
        super(context);
    }

    public ShakeDpadRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public ShakeDpadRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    private Animation shake() {
        if (shake == null && !isInEditMode()) shake = Utils.getAnim(R.anim.shake);
        return shake;
    }

    @Override
    public View focusSearch(View focused, int direction) {
        if (focused != null && (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT)) {
            View next = FocusFinder.getInstance().findNextFocus(this, focused, direction);
            if (next == null) {
                // Always block focus from leaving at the edge.
                // Shake only when idle — fast hold-scroll should not trigger the animation.
                if (getScrollState() == SCROLL_STATE_IDLE) {
                    Animation anim = shake();
                    if (anim != null) {
                        focused.clearAnimation();
                        focused.startAnimation(anim);
                    }
                }
                return focused;
            }
        }
        return super.focusSearch(focused, direction);
    }
}
