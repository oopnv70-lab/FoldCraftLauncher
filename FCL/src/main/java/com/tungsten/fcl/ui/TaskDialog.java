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

    // ==================== 并发控制 ====================
    private static final Object busyLock = new Object();
    private static volatile boolean busyFlag = false;

    /**
     * 尝试获取下载锁。成功返回 true，失败（已有下载进行中）返回 false。
     * 调用方应先调用此方法，若返回 false 则弹提示，不新建 TaskDialog。
     */
    public static boolean tryBusy() {
        synchronized (busyLock) {
            if (busyFlag) return false;
            busyFlag = true;
            return true;
        }
    }

    /**
     * 查询忙碌状态（只读，不抢占锁）。
     * 用于其他调用方的兼容检查。
     */
    public static boolean isBusy() {
        synchronized (busyLock) {
            return busyFlag;
        }
    }

    /**
     * 强制清除忙碌标志（异常兜底）。
     */
    public static void ensureUnbusy() {
        synchronized (busyLock) {
            busyFlag = false;
        }
    }

    private void markBusy() {
        synchronized (busyLock) {
            busyFlag = true;
        }
    }

    private void markUnbusy() {
        synchronized (busyLock) {
            busyFlag = false;
        }
    }

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

    private static final int COLLAPSED_WIDTH_DP = 160;
    private static final int EXPANDED_WIDTH_DP = 280;
    private static final int COLLAPSED_HEIGHT_DP = 38;
    private static final int EXPANDED_MAX_HEIGHT_DP = 200;
    private static final int ANIM_DURATION = 250;

    @SuppressLint("DefaultLocale")
    public TaskDialog(@NonNull Context context, @NotNull TaskCancellationAction cancel) {
        super(context);
        setContentView(R.layout.dialog_task);
        setCancelable(false);
        markBusy();

        density = getContext().getResources().getDisplayMetrics().density;

        window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            windowParams = window.getAttributes();
            windowParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            windowParams.y = (int) (20 * density);
            windowParams.width = (int) (COLLAPSED_WIDTH_DP * density);
            windowParams.height = (int) (COLLAPSED_HEIGHT_DP * density);
            windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            windowParams.dimAmount = 0.0f;
            window.setAttributes(windowParams);
        }

        titleView = findViewById(R.id.title);
        taskListView = findViewById(R.id.list);
        speedView = findViewById(R.id.speed);
        cancelButton = findViewById(R.id.cancel);
        expandArea = findViewById(R.id.expand_area);

        setCancel(cancel);
        cancelButton.setOnClickListener(this);
        findViewById(R.id.root).setOnClickListener(v -> toggle());

        expandArea.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);

        speedEventHandler = speedEvent -> {
            String unit = "B/s";
            double speed = speedEvent.getSpeed();
            if (speed > 1024) { speed /= 1024; unit = "KB/s"; }
            if (speed > 1024) { speed /= 1024; unit = "MB/s"; }
            double finalSpeed = speed;
            String finalUnit = unit;
            Schedulers.androidUIThread().execute(() ->
                speedView.setText(String.format("%.1f %s", finalSpeed, finalUnit)));
        };
        FileDownloadTask.speedEvent.channel(FileDownloadTask.SpeedEvent.class).registerWeak(speedEventHandler);
    }

    private void toggle() {
        if (isExpanded) collapse(); else expand();
    }

    private void expand() {
        isExpanded = true;
        expandArea.setVisibility(View.VISIBLE);
        cancelButton.setVisibility(View.VISIBLE);
        animateSize(COLLAPSED_WIDTH_DP, EXPANDED_WIDTH_DP, COLLAPSED_HEIGHT_DP, EXPANDED_MAX_HEIGHT_DP);
    }

    private void collapse() {
        isExpanded = false;
        cancelButton.setVisibility(View.GONE);
        animateSize(EXPANDED_WIDTH_DP, COLLAPSED_WIDTH_DP, EXPANDED_MAX_HEIGHT_DP, COLLAPSED_HEIGHT_DP);
        expandArea.postDelayed(() -> expandArea.setVisibility(View.GONE), ANIM_DURATION);
    }

    private void animateSize(int fromWdp, int toWdp, int fromHdp, int toHdp) {
        int fromW = (int)(fromWdp * density), toW = (int)(toWdp * density);
        int fromH = (int)(fromHdp * density), toH = (int)(toHdp * density);
        Animation anim = new Animation() {
            @Override
            protected void applyTransformation(float t, Transformation tf) {
                windowParams.width = fromW + (int)((toW - fromW) * t);
                windowParams.height = fromH + (int)((toH - fromH) * t);
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

    public StringProperty titleProperty() { return titleView.stringProperty(); }
    public String getTitle() { return titleView.getText().toString(); }
    public void setTitle(String s) { titleView.setString(s); }

    public void setCancel(TaskCancellationAction onCancel) {
        this.onCancel = onCancel;
        cancelButton.setEnabled(onCancel != null);
    }

    @Override
    public void dismiss() {
        FileDownloadTask.speedEvent.channel(FileDownloadTask.SpeedEvent.class).unregister(speedEventHandler);
        markUnbusy();
        super.dismiss();
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