package com.everythingplayer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.session.MediaLibraryService;
import com.facebook.infer.annotation.Assertions;
import com.facebook.react.ReactHost;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint;
import com.facebook.react.jstasks.HeadlessJsTaskConfig;
import com.facebook.react.jstasks.HeadlessJsTaskContext;
import com.facebook.react.jstasks.HeadlessJsTaskEventListener;
import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactInstanceEventListener;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactApplication;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class HeadlessJsMediaService extends MediaLibraryService implements HeadlessJsTaskEventListener {

    private final Set<Integer> mActiveTasks = new CopyOnWriteArraySet<>();
    public static @Nullable PowerManager.WakeLock sWakeLock;

    private boolean initialized = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        HeadlessJsTaskConfig taskConfig = getTaskConfig(intent);
        if (!initialized && taskConfig != null) {
            initialized = true;
            startTask(taskConfig);
            return START_REDELIVER_INTENT;
        }
        return START_NOT_STICKY;
    }

    protected @Nullable HeadlessJsTaskConfig getTaskConfig(Intent intent) {
        return null;
    }

    @SuppressLint("WakelockTimeout")
    public static void acquireWakeLockNow(Context context) {
        if (sWakeLock == null || !sWakeLock.isHeld()) {
            PowerManager powerManager =
                    Assertions.assertNotNull((PowerManager) context.getSystemService(POWER_SERVICE));
            sWakeLock =
                    powerManager.newWakeLock(
                            PowerManager.PARTIAL_WAKE_LOCK, HeadlessJsMediaService.class.getCanonicalName());
            sWakeLock.setReferenceCounted(false);
            sWakeLock.acquire();
        }
    }

    @Override
    public @Nullable IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    protected void startTask(final HeadlessJsTaskConfig taskConfig) {
        UiThreadUtil.assertOnUiThread();
        ReactContext reactContext = getReactContext();
        if (reactContext == null) {
            createReactContextAndScheduleTask(taskConfig);
        } else {
            invokeStartTask(reactContext, taskConfig);
        }
    }

    private void invokeStartTask(ReactContext reactContext, final HeadlessJsTaskConfig taskConfig) {
        final HeadlessJsTaskContext headlessJsTaskContext =
                HeadlessJsTaskContext.getInstance(reactContext);
        headlessJsTaskContext.addTaskEventListener(this);

        UiThreadUtil.runOnUiThread(
                () -> {
                    int taskId = headlessJsTaskContext.startTask(taskConfig);
                    mActiveTasks.add(taskId);
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ReactContext reactContext = getReactContext();
        if (reactContext != null) {
            HeadlessJsTaskContext headlessJsTaskContext =
                    HeadlessJsTaskContext.getInstance(reactContext);
            headlessJsTaskContext.removeTaskEventListener(this);
        }
        if (sWakeLock != null) {
            sWakeLock.release();
        }
    }

    @Override
    public void onHeadlessJsTaskStart(int taskId) {}

    @Override
    public void onHeadlessJsTaskFinish(int taskId) {
        mActiveTasks.remove(taskId);
        if (mActiveTasks.isEmpty()) {
            stopSelf();
        }
    }

    protected ReactNativeHost getReactNativeHost() {
        return ((ReactApplication) getApplication()).getReactNativeHost();
    }

    protected ReactHost getReactHost() {
        return ((ReactApplication) getApplication()).getReactHost();
    }

    @SuppressLint("VisibleForTests")
    protected ReactContext getReactContext() {
        if (DefaultNewArchitectureEntryPoint.getBridgelessEnabled()) {
            ReactHost reactHost = getReactHost();
            Assertions.assertNotNull(reactHost, "React host is null in newArchitecture");
            return reactHost.getCurrentReactContext();
        }
        final ReactInstanceManager reactInstanceManager =
                getReactNativeHost().getReactInstanceManager();
        return reactInstanceManager.getCurrentReactContext();
    }

    private void createReactContextAndScheduleTask(final HeadlessJsTaskConfig taskConfig) {
        if (DefaultNewArchitectureEntryPoint.getBridgelessEnabled()) {
            final ReactHost reactHost = getReactHost();
            reactHost.addReactInstanceEventListener(
                    new ReactInstanceEventListener() {
                        @Override
                        public void onReactContextInitialized(@NonNull ReactContext reactContext) {
                            invokeStartTask(reactContext, taskConfig);
                            reactHost.removeReactInstanceEventListener(this);
                        }
                    }
            );
            reactHost.start();
        } else {
            final ReactInstanceManager reactInstanceManager =
                    getReactNativeHost().getReactInstanceManager();
            reactInstanceManager.addReactInstanceEventListener(
                    new ReactInstanceEventListener() {
                        @Override
                        public void onReactContextInitialized(@NonNull ReactContext reactContext) {
                            invokeStartTask(reactContext, taskConfig);
                            reactInstanceManager.removeReactInstanceEventListener(this);
                        }
                    });
            reactInstanceManager.createReactContextInBackground();
        }
    }
}
