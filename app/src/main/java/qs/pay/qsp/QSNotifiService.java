package qs.pay.qsp;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.google.gson.Gson;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import qs.pay.qsp.utils.Configs;
import qs.pay.qsp.utils.LogUtils;
import qs.pay.qsp.utils.OrderData;

import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY2;

public class QSNotifiService extends NotificationListenerService {

    //通知消息内容
    private String data;

    //支付宝规则
    private Pattern pAlipay;
    private Pattern pAlipay2;
    private Pattern pAlipayDianyuan;

    //微信规则
    private Pattern pWeixin;
    private Pattern pWeixin2;

    //保持黑屏运行
    private PowerManager.WakeLock wakeLock;

    public void onCreate()
    {
        super.onCreate();

        //支付宝消息规则
        String pattern = "(\\S*)通过扫码向你付款([\\d\\.]+)元";
        pAlipay = Pattern.compile(pattern);
        pattern = "成功收款([\\d\\.]+)元。享免费提现等更多专属服务，点击查看";
        pAlipay2 = Pattern.compile(pattern);
        pAlipayDianyuan = Pattern.compile("支付宝成功收款([\\d\\.]+)元。收钱码收钱提现免费，赶紧推荐顾客使用");

        //微信消息规则
        pWeixin = Pattern.compile("微信支付收款([\\d\\.]+)元");

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        //保持cpu一直运行，不管屏幕是否黑屏
        if (pm != null && wakeLock == null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getCanonicalName());
            wakeLock.acquire();
        }

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.show("Service is started");
        data = intent.getStringExtra("data");

        return super.onStartCommand(intent, flags, startId);
    }

    public void onNotificationPosted(StatusBarNotification sbn)
    {
        try
        {

            if(Configs.NotifyStart.equals("true"))
            {
                //获取通知内容
                Bundle bundle = sbn.getNotification().extras;

                //获取通知对象的包名
                String pkgName = sbn.getPackageName();

                //获取通知标题
                String title = bundle.getString("android.title");
                //获取通知详情
                String text = bundle.getString("android.text");

                LogUtils.show("获取到通知消息：" + title + "," + text + "," + pkgName);

                //如果是支付宝
                if (pkgName.equals("com.eg.android.AlipayGphone") && text != null)
                {
                    // 现在创建 matcher 对象
                    do {
                        Matcher m = pAlipay.matcher(text);
                        if (m.find()) {
                            LogUtils.show("支付宝匹配");
                            String uname = m.group(1);
                            String money = m.group(2);

                            Double money2 = Double.valueOf(money)*100;
                            //postMethod(AliPay, money, uname, false);

                            OrderData MsgData = new OrderData();
                            MsgData.setOrder_id("");
                            MsgData.setMoney(money2.intValue());
                            MsgData.setMark_sell("");
                            MsgData.setChannel(OrderData.ALIPAY);
                            MsgData.setOrder_status(1);
                            MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                            MsgData.setId(UUID.randomUUID().toString());
                            MsgData.setOrder_type(OrderData.PAYMENT);

                            String SendData = new Gson().toJson(MsgData);

                            MainActivity.addrealm(SendData);

                            break;
                        }
                        m = pAlipay2.matcher(text);
                        if (m.find()) {
                            LogUtils.show("支付宝匹配2");
                            String money = m.group(1);

                            Double money2 = Double.valueOf(money)*100;

                            //postMethod(AliPay, money, "支付宝用户", false);
                            OrderData MsgData = new OrderData();
                            MsgData.setOrder_id("");
                            MsgData.setMoney(money2.intValue());
                            MsgData.setMark_sell("");
                            MsgData.setChannel(OrderData.ALIPAY);
                            MsgData.setOrder_status(1);
                            MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                            MsgData.setId(UUID.randomUUID().toString());
                            MsgData.setOrder_type(OrderData.PAYMENT);

                            String SendData = new Gson().toJson(MsgData);

                            MainActivity.addrealm(SendData);
                            break;
                        }
                        m = pAlipayDianyuan.matcher(text);
                        if (m.find()) {
                            LogUtils.show("支付宝匹配3");
                            String money = m.group(1);

                            Double money2 = Double.valueOf(money)*100;

                            //postMethod(AliPay, money, "支付宝-店员", true);
                            OrderData MsgData = new OrderData();
                            MsgData.setOrder_id("");
                            MsgData.setMoney(money2.intValue());
                            MsgData.setMark_sell("");
                            MsgData.setChannel(OrderData.ALIPAY);
                            MsgData.setOrder_status(1);
                            MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                            MsgData.setId(UUID.randomUUID().toString());
                            MsgData.setOrder_type(OrderData.PAYMENT);

                            String SendData = new Gson().toJson(MsgData);

                            MainActivity.addrealm(SendData);
                            break;
                        }
                    } while (false);
                }
                else if(pkgName.equals("com.tencent.mm") && text != null)
                {
                    Matcher m = pWeixin.matcher(text);
                    if (m.find()) {
                        LogUtils.show("微信匹配");
                        String uname = "微信用户";
                        String money = m.group(1);

                        Double money2 = Double.valueOf(money)*100;

                        //postMethod(WeixinPay, money, uname, false);
                        OrderData MsgData = new OrderData();
                        MsgData.setMoney(money2.intValue());
                        MsgData.setChannel(OrderData.WECHAT);
                        MsgData.setOrder_status(1);
                        MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                        MsgData.setId(UUID.randomUUID().toString());
                        MsgData.setOrder_type(OrderData.PAYMENT);

                        String SendData = new Gson().toJson(MsgData);

                        MainActivity.addrealm(SendData);
                    }
                }
            }
        }
        catch (Exception e)
        {
            LogUtils.show("通知栏消息解析出错" + e.getMessage());
        }

    }

    public void onDestroy() {
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }
}
