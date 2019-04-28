package qs.pay.qsp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.alibaba.fastjson.JSON;

import qs.pay.qsp.utils.Configs;
import qs.pay.qsp.utils.LogUtils;
import qs.pay.qsp.utils.OrderData;
import qs.pay.qsp.utils.PayUtils;

import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY;
import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY2;
import static qs.pay.qsp.HookMain.RECEIVE_BILL_WECHAT;
import static qs.pay.qsp.HookMain.RECEIVE_QR_ALIPAY;
import static qs.pay.qsp.HookMain.RECEIVE_QR_WECHAT;

/**
 * @ Created by Dlg
 * @ <p>TiTle:  ReceiverMain</p>
 * @ <p>Description: 当HOOK之后的处理结果，只能用此广播来接受，不然很多数据不方便共享的</p>
 * @ date:  2018/09/22
 * @ QQ群：524901982
 */
public class ReceiverMain extends BroadcastReceiver
{
    public static boolean mIsInit = false;
    private static String lastMsg = "";//防止重启接收广播，一定要用static
    private static long mLastSucc = 0;
    private static String cook = "";


    public ReceiverMain()
    {
        super();
        mIsInit = true;
        LogUtils.show("Receiver创建成功！");
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String data = intent.getStringExtra("data");
            if (lastMsg.contentEquals(data)) //如果收到重复广播就不管他
            {
                return;
            }
            lastMsg = intent.getStringExtra("data");

            switch (intent.getAction())
            {
                case RECEIVE_QR_WECHAT:
                    //微信收到二维码
                    LogUtils.show("RECEIVE_QR_WECHAT");
                    MainActivity.addrealm(data);
                    Configs.MakerQrStat = false;
                    Configs.MakerQrTime = 0;
                    //MainActivity.delOrder(Configs.MarkCode.getId());
                    break;
                case RECEIVE_BILL_WECHAT:
                    //微信收到支付订单消息
                    LogUtils.show("RECEIVE_BILL_WECHAT");
                    MainActivity.addrealm(data);
                case RECEIVE_QR_ALIPAY:
                    //支付宝收到二维码
                    LogUtils.show("RECEIVE_QR_ALIPAY");
                    MainActivity.addrealm(data);
                    Configs.MakerQrStat = false;
                    Configs.MakerQrTime = 0;
                    //MainActivity.delOrder(Configs.MarkCode.getId()); //这里不要删除订单，应该在收到发送结果后删除
                    break;
                case RECEIVE_BILL_ALIPAY2:
                    LogUtils.show("RECEIVE_BILL_ALIPAY2");
                    //支付宝收到支付订单消息2
                    LogUtils.show(data);
                    MainActivity.addrealm(data);
                    break;
                case RECEIVE_BILL_ALIPAY:
                    LogUtils.show("RECEIVE_BILL_ALIPAY");
                    cook = data;
                    mLastSucc = System.currentTimeMillis();
                    PayUtils.dealAlipayWebTrade(context, data);
                    break;
            }

        } catch (Exception e) {
            LogUtils.show(e.getMessage());
        }
    }

    public static void startReceive()
    {
        if (!ReceiverMain.mIsInit) {
            IntentFilter filter = new IntentFilter(RECEIVE_QR_WECHAT);
            filter.addAction(RECEIVE_QR_ALIPAY);
            filter.addAction(RECEIVE_BILL_WECHAT);
            filter.addAction(RECEIVE_BILL_ALIPAY);
            filter.addAction(RECEIVE_BILL_ALIPAY2);
            QSApplication.app.registerReceiver(new ReceiverMain(), filter);
        }
    }
}
