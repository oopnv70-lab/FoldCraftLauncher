package com.tungsten.fcl.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.LinearLayout;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.tungsten.fcl.R;
import com.tungsten.fcl.util.TaskCancellationAction;
import com.tungsten.fclcore.fakefx.beans.property.StringProperty;
import com.tungsten.fclcore.task.FileDownloadTask;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.TaskExecutor;
import com.tungsten.fclcore.task.TaskListener;
import com.tungsten.fcllibrary.component.dialog.FCLDialog;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLTextView;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

public class TaskDialog extends FCLDialog implements View.OnClickListener {

    private FCLTextView titleView;
    private FCLTextView speedView;
    private FCLButton cancelButton;
    private LinearLayout expandArea;
    private ListView taskListView;

    private TaskExecutor executor;
    private TaskCancellationAction onCancel;
    private final Consumer<FileDownloadTask.SpeedEvent> speedEventHandler;

    private TaskListPane taskListPane;

    private boolean isExpanded = false;
    private float density;
    private Window window;
    private WindowManager.LayoutParams windowParams;

    private static final int COLLAPSED_WIDTH_DP = 200;
    private static final int EXPANDED_WIDTH_DP = 300;
    private static final int COLLAPSED_HEIGHT_DP = 42;
    private static final int ANIM_DURATION = 250;

    @SuppressLint("DefaultLocale")
    public TaskDialog(@NonNull Context context, @NotNull TaskCancellationAction cancel) {
        super(context);
        setContentView(R.layout.dialog_task);
        setCancelable(false);

        density = context.getResources().getDisplayMetrics().density;

        // === 灵动岛浮动弹窗 ===
        window = getWindow();
        if (window != null) {
            windowParams = window.getAttributes();
            windowParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            windowParams.y = (int) (40 * density);
            windowParams.width = (int) (COLLAPSED_WIDTH_DP * density);
            windowParams.height = (int) (COLLAPSED_HEIGHT_DP * density);
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            windowParams.flags |= WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;
            windowParams.dimAmount = 0.0f;
            window.setAttributes(windowParams);
        }

        titleView = findViewById(R.id.title);
        taskListView = findViewById(R.id.list);
        speedView = findViewById(R.id.speed);
        cancelButton = findViewById(R.id.cancel);
        expandArea = findViewById(R.id.expand_area);

        setCancel(cancel);

        // 取消按钮
        cancelButton.setOnClickListener(this);

        // 点击弹窗任意位置 → 展开/收起
        findViewById(R.id.root).setOnClickListener(v -> toggle());

        // 初始状态：收起（隐藏展开区）
        expandArea.setVisibility(View.GONE);

        speedEventHandler = speedEvent -> {
            String unit = "B/s";
            double speed = speedEvent.getSpeed();
            if (speed > 1024) {
                speed /= 1024;
                unit = "KB/s";
            }
            if (speed > 1024) {
                speed /= 1024;
                unit = "MB/s";
            }
            double finalSpeed = speed;
            String finalUnit = unit;
            Schedulers.androidUIThread().execute(() -> {
                speedView.setText(String.format("%.1f %s", finalSpeed, finalUnit));
            });
        };
        FileDownloadTask.speedEvent.channel(FileDownloadTask.SpeedEvent.class).registerWeak(speedEventHandler);
    }

    private void toggle() {
        if (isExpanded) {
            collapse();
        } else {
            expand();
        }
    }

    private void expand() {
        isExpanded = true;
        expandArea.setVisibility(View.VISIBLE);
        animateSize(COLLAPSED_WIDTH_DP, EXPANDED_WIDTH_DP, COLLAPSED_HEIGHT_DP, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private void collapse() {
        isExpanded = false;
        animateSize(EXPANDED_WIDTH_DP, COLLAPSED_WIDTH_DP, WindowManager.LayoutParams.WRAP_CONTENT, COLLAPSED_HEIGHT_DP);
        // 动画结束后隐藏
        expandArea.postDelayed(() -> expandArea.setVisibility(View.GONE), ANIM_DURATION);
    }

    private void animateSize(int fromWdp, int toWdp, int fromHdp, int toHdp) {
        int fromW = (int) (fromWdp * density);
        int toW = (int) (toWdp * density);
        int fromH = (int) (fromHdp * density);
        int toH = toHdp == WindowManager.LayoutParams.WRAP_CONTENT ? fromH * 3 : (int) (toHdp * density);

        Animation anim = new Animation() {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                windowParams.width = fromW + (int) ((toW - fromW) * interpolatedTime);
                if (toHdp == WindowManager.LayoutParams.WRAP_CONTENT) {
                    windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
                } else {
                    windowParams.height = fromH + (int) ((toH - fromH) * interpolatedTime);
                }
                window.setAttributes(windowParams);
            }
        };
        anim.setDuration(ANIM_DURATION);
        anim.setInterpolator(new AccelerateDecelerateInterpolator());
        findViewById(R.id.root).startAnimation(anim);
    }

    public void setExecutor(TaskExecutor executor) {
        setExecutor(executor, true);
    }

    public void setExecutor(TaskExecutor executor, boolean autoClose) {
        this.executor = executor;

        if (executor != null) {
            if (autoClose) {
                executor.addTaskListener(new TaskListener() {
                    @Override
                    public void onStop(boolean success, TaskExecutor executor) {
                        Schedulers.androidUIThread().execute(() -> dismiss());
                    }
                });
            }

            taskListPane = new TaskListPane(getContext(), executor);
            taskListView.setAdapter(taskListPane);
        }
    }

    public StringProperty titleProperty() {
        return titleView.stringProperty();
    }

    public String getTitle() {
        return titleView.getText().toString();
    }

    public void setTitle(String currentState) {
        titleView.setString(currentState);
    }

    public void setCancel(TaskCancellationAction onCancel) {
        this.onCancel = onCancel;
        cancelButton.setEnabled(onCancel != null);
    }

    @Override
    public void onClick(View view) {
        if (view == cancelButton) {
            Optional.ofNullable(executor).ifPresent(TaskExecutor::cancel);
            onCancel.getCancellationAction().accept(this);
            dismiss();
        }
    }
}
