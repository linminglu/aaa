package qs.pay.qsp;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import qs.pay.qsp.utils.Configs;
import qs.pay.qsp.utils.LogUtils;
import qs.pay.qsp.utils.PayUtils;

public class HookMain implements IXposedHookLoadPackage
{
    //被申请要创建二维码的广播
    public static final String WECHAT_CREAT_QR = "com.wechat.qr.create";
    public static final String ALIPAY_CREAT_QR = "com.alipay.qr.create";

    //成功生成二维码的HOOK广播消息
    public static final String RECEIVE_QR_WECHAT = "com.wechat.qr.receive";
    public static final String RECEIVE_QR_ALIPAY = "com.alipay.qr.receive";

    //接收到新订单的HOOK广播消息
    public static final String RECEIVE_BILL_WECHAT = "com.wechat.bill.receive";
    public static final String RECEIVE_BILL_ALIPAY = "com.alipay.bill.receive";
    public static final String RECEIVE_BILL_ALIPAY2 = "com.alipay.bill.receive2";


    private final String WECHAT_PACKAGE = "com.tencent.mm";
    private final String ALIPAY_PACKAGE = "com.eg.android.AlipayGphone";

    //是否已经HOOK过微信或者支付宝了
    private boolean WECHAT_PACKAGE_ISHOOK = false;
    private boolean ALIPAY_PACKAGE_ISHOOK = false;


    //开始hook支付宝和微信
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable
    {
        if (lpparam.appInfo == null || (lpparam.appInfo.flags & (ApplicationInfo.FLAG_SYSTEM |
                ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) {
            return;
        }
        final String packageName = lpparam.packageName;
        final String processName = lpparam.processName;


        if (WECHAT_PACKAGE.equals(packageName)) {
            try {
                XposedHelpers.findAndHookMethod(ContextWrapper.class, "attachBaseContext", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Context context = (Context) param.args[0];
                        ClassLoader appClassLoader = context.getClassLoader();
                        if (WECHAT_PACKAGE.equals(processName) && !WECHAT_PACKAGE_ISHOOK) {
                            WECHAT_PACKAGE_ISHOOK = true;
                            //注册广播
                            ReceivedStartWechat stratWechat = new ReceivedStartWechat();
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(WECHAT_CREAT_QR);
                            context.registerReceiver(stratWechat, intentFilter);
                            LogUtils.show("微信初始化成功");
                            Toast.makeText(context, "微信初始化成功", Toast.LENGTH_LONG).show();
                            new HookWechat().hook(appClassLoader, context);
                        }
                    }
                });
            } catch (Throwable e) {
                LogUtils.show("微信初始化失败："+ e.getMessage());
            }
        }

        if (ALIPAY_PACKAGE.equals(packageName)) {
            try {
                XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        super.afterHookedMethod(param);
                        Context context = (Context) param.args[0];
                        ClassLoader appClassLoader = context.getClassLoader();
                        if(ALIPAY_PACKAGE.equals(processName))
                        {
                            String str = PayUtils.getInstance().getAlipayCookieStr(appClassLoader);
                            LogUtils.show("支付宝Cookie：" + str);
                        }
                        if (ALIPAY_PACKAGE.equals(processName) && !ALIPAY_PACKAGE_ISHOOK) {
                            ALIPAY_PACKAGE_ISHOOK = true;
                            //注册广播
                            ReceivedStartAlipay startAlipay = new ReceivedStartAlipay();
                            IntentFilter intentFilter = new IntentFilter();
                            intentFilter.addAction(ALIPAY_CREAT_QR);
                            context.registerReceiver(startAlipay, intentFilter);
                            LogUtils.show("支付宝初始化成功");
                            Toast.makeText(context, "支付宝初始化成功", Toast.LENGTH_LONG).show();
                            new HookAlipay().hook(appClassLoader, context);
                        }
                    }
                });
            } catch (Throwable e) {
                LogUtils.show("支付宝初始化失败："+ e.getMessage());
                LogUtils.show(e.getMessage());
            }
        }

    }

    /**
     * 此广播用于接收到微信二维码请求后，打开二维码支付页面。
     */
    class ReceivedStartWechat extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            LogUtils.show("获取微信二维码");
            try
            {
                LogUtils.show("开始创建二维码，金额为：" + intent.getStringExtra("money"));
                Intent intent2 = new Intent(context, XposedHelpers.findClass("com.tencent.mm.plugin.collect.ui.CollectCreateQRCodeUI", context.getClassLoader()));
                intent2.putExtra("mark", intent.getStringExtra("mark"));
                intent2.putExtra("money", intent.getStringExtra("money"));
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent2);
            }
            catch (Exception e)
            {
                Configs.MakerQrStat = false;
                MainActivity.delOrder(Configs.MarkCode.getId());
                LogUtils.show("启动微信失败：" + e.getMessage());
            }
        }
    }


    /**
     * 此广播用于接收到支付宝二维码请求后，打开二维码支付页面。
     */
    class ReceivedStartAlipay extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            LogUtils.show("获取支付宝二维码");
            try
            {
                Intent intent2 = new Intent(context, XposedHelpers.findClass("com.alipay.mobile.payee.ui.PayeeQRSetMoneyActivity", context.getClassLoader()));
                intent2.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent2.putExtra("mark", intent.getStringExtra("mark"));
                intent2.putExtra("money", intent.getStringExtra("money"));
                context.startActivity(intent2);
            }
            catch (Exception e)
            {
                Configs.MakerQrStat = false;
                MainActivity.delOrder(Configs.MarkCode.getId());
                LogUtils.show("启动支付宝失败：" + e.getMessage());
            }
        }
    }
}
