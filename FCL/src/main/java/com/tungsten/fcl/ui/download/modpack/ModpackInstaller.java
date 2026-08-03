package com.tungsten.fcl.ui.download.modpack;

import android.content.Context;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDialog;

import com.tungsten.fcl.R;
import com.tungsten.fcl.game.ManuallyCreatedModpackException;
import com.tungsten.fcl.game.ModpackHelper;
import com.tungsten.fcl.setting.Profile;
import com.tungsten.fcl.ui.TaskDialog;
import com.tungsten.fcl.ui.download.DownloadPageManager;
import com.tungsten.fcl.ui.download.version.VersionInstallInfoPage;
import com.tungsten.fcl.ui.manage.ManagePageManager;
import com.tungsten.fcl.util.TaskCancellationAction;
import com.tungsten.fclcore.mod.MismatchedModpackTypeException;
import com.tungsten.fclcore.mod.Modpack;
import com.tungsten.fclcore.mod.ModpackCompletionException;
import com.tungsten.fclcore.mod.UnsupportedModpackException;
import com.tungsten.fclcore.mod.server.ServerModpackManifest;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.task.TaskExecutor;
import com.tungsten.fclcore.task.TaskListener;
import com.tungsten.fcllibrary.component.dialog.FCLAlertDialog;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;

public class ModpackInstaller {

    public static void installModpack(Context context, Task<?> task, boolean update) {
        // === 单下载保护：已有下载任务时弹出 Toast 提示并阻止 ===
        if (TaskDialog.isShowingAny()) {
            Toast.makeText(context, "已有下载任务进行中，请等待完成", Toast.LENGTH_SHORT).show();
            return;
        }

        String title = context.getString(R.string.install_modpack);
        TaskCancellationAction cancelAction = new TaskCancellationAction(AppCompatDialog::dismiss);

        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Schedulers.androidUIThread().execute(() -> {
                    if (success) {
                        FCLAlertDialog.Builder builder1 = new FCLAlertDialog.Builder(context);
                        builder1.setAlertLevel(FCLAlertDialog.AlertLevel.INFO);
                        builder1.setCancelable(false);
                        builder1.setMessage(context.getString(R.string.install_success));
                        builder1.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), () -> {
                            if (update) {
                                ManagePageManager.getInstance().dismissCurrentTempPage();
                            } else {
                                DownloadPageManager.getInstance().dismissCurrentTempPage();
                            }
                        });
                        builder1.create().show();
                    } else {
                        if (executor.getException() == null)
                            return;
                        if (executor.getException() instanceof ModpackCompletionException) {
                            if (executor.getException().getCause() instanceof FileNotFoundException) {
                                FCLAlertDialog.Builder builder1 = new FCLAlertDialog.Builder(context);
                                builder1.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                                builder1.setCancelable(false);
                                builder1.setTitle(context.getString(R.string.install_failed));
                                builder1.setMessage(context.getString(R.string.modpack_type_curse_not_found));
                                builder1.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), () -> {
                                    if (update) {
                                        ManagePageManager.getInstance().dismissCurrentTempPage();
                                    } else {
                                        DownloadPageManager.getInstance().dismissCurrentTempPage();
                                    }
                                });
                                builder1.create().show();
                            } else {
                                FCLAlertDialog.Builder builder1 = new FCLAlertDialog.Builder(context);
                                builder1.setAlertLevel(FCLAlertDialog.AlertLevel.INFO);
                                builder1.setCancelable(false);
                                builder1.setMessage(context.getString(R.string.install_success));
                                builder1.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), () -> {
                                    if (update) {
                                        ManagePageManager.getInstance().dismissCurrentTempPage();
                                    } else {
                                        DownloadPageManager.getInstance().dismissCurrentTempPage();
                                    }
                                });
                                builder1.create().show();
                            }
                        } else {
                            VersionInstallInfoPage.alertFailureMessage(context, executor.getException(), () -> {});
                        }
                    }
                });
            }
        });

        // 正常创建下载弹窗
        TaskDialog pane = new TaskDialog(context, cancelAction);
        pane.setTitle(title);
        pane.setExecutor(executor);
        if (!pane.showAndStart(executor)) {
            return;
        }
    }

    public static Task<?> getModpackInstallTask(Profile profile, File selected, String name, Charset charset) {
        return getModpackInstallTaskAsync(null, profile, null, selected, null, null, name, charset, true);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, ServerModpackManifest serverModpackManifest, Modpack modpack, String name) {
        return getModpackInstallTask(context, profile, null, null, serverModpackManifest, modpack, name);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, File selected, Modpack modpack, String name) {
        return getModpackInstallTask(context, profile, null, selected, null, modpack, name);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, String updateVersion, File selected, Modpack modpack, String name) {
        return getModpackInstallTask(context, profile, updateVersion, selected, null, modpack, name);
    }

    public static Task<?> getModpackInstallTask(Context context, Profile profile, String updateVersion, File selected, ServerModpackManifest serverModpackManifest, Modpack modpack, String name) {
        return getModpackInstallTaskAsync(context, profile, updateVersion, selected, serverModpackManifest, modpack, name, null, false);
    }

    private static Task<?> getModpackInstallTaskAsync(Context context, Profile profile, String updateVersion, File selected, ServerModpackManifest serverModpackManifest, Modpack modpack, String name, Charset charset, boolean isManuallyCreated) {
        if (isManuallyCreated) {
            return ModpackHelper.getInstallManuallyCreatedModpackTask(profile, selected, name, charset);
        }

        if ((selected == null && serverModpackManifest == null) || modpack == null || name == null) return null;

        if (updateVersion != null) {
            if (selected == null) {
                FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(context);
                builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_unsupported));
                builder.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                builder.create().show();
                return null;
            }
            try {
                if (serverModpackManifest != null) {
                    return ModpackHelper.getUpdateTask(profile, serverModpackManifest, modpack.getEncoding(), name, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(name)));
                } else {
                    return ModpackHelper.getUpdateTask(profile, selected, modpack.getEncoding(), name, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(name)));
                }
            } catch (UnsupportedModpackException | ManuallyCreatedModpackException e) {
                FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(context);
                builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_unsupported));
                builder.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                builder.create().show();
            } catch (MismatchedModpackTypeException e) {
                FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(context);
                builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_mismatched_type));
                builder.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                builder.create().show();
            } catch (IOException e) {
                FCLAlertDialog.Builder builder = new FCLAlertDialog.Builder(context);
                builder.setAlertLevel(FCLAlertDialog.AlertLevel.ALERT);
                builder.setCancelable(false);
                builder.setTitle(context.getString(R.string.message_error));
                builder.setMessage(context.getString(R.string.modpack_invalid));
                builder.setNegativeButton(context.getString(com.tungsten.fcllibrary.R.string.dialog_positive), null);
                builder.create().show();
            }
            return null;
        } else {
            if (serverModpackManifest != null) {
                return ModpackHelper.getInstallTask(profile, serverModpackManifest, name, modpack)
                        .thenRunAsync(Schedulers.androidUIThread(), () -> profile.setSelectedVersion(name));
            } else {
                return ModpackHelper.getInstallTask(profile, selected, name, modpack)
                        .thenRunAsync(Schedulers.androidUIThread(), () -> profile.setSelectedVersion(name));
            }
        }
    }

}
