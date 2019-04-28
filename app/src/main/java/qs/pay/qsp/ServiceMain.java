package qs.pay.qsp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;

import qs.pay.qsp.utils.LogUtils;

import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;

public class ServiceMain extends Service {
    //防止被休眠，你们根据情况可以开关，我是一直打开的，有点费电是必然的，哈哈
    private PowerManager.WakeLock mWakeLock;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP, "com.sjk.tpay:waketag");
        mWakeLock.acquire();

        LogUtils.show("服务启动");
        ReceiverMain.startReceive();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        try {
            if (mWakeLock != null)
                mWakeLock.release();
            mWakeLock = null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        LogUtils.show("服务被杀死");
        Intent intent = new Intent(this.getApplicationContext(), ServiceMain.class);
        this.startService(intent);
    }
}
