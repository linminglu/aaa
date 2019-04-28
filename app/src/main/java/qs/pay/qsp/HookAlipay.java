package qs.pay.qsp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;

import com.alibaba.fastjson.JSON;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import qs.pay.qsp.utils.LogUtils;
import qs.pay.qsp.utils.OrderData;
import qs.pay.qsp.utils.PayUtils;

import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY;
import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY2;
import static qs.pay.qsp.HookMain.RECEIVE_QR_ALIPAY;

/**
 * @ Created by Dlg
 * @ <p>TiTle:  HookAlipay</p>
 * @ <p>Description:支付宝的主要HOOK类</p>
 * @ date:  2018/9/28 22:57
 * @ QQ群：524901982
 */
public class HookAlipay {

    /**
     * 主要的hook函数
     *
     * @param appClassLoader
     * @param context
     */
    public void hook(ClassLoader appClassLoader, final Context context)
    {
        try
        {
            hookSafeCheck(appClassLoader);
            hookBill(appClassLoader, context);
            hookQRCreat(appClassLoader, context);
            hookQRWindows(appClassLoader);
        } catch (Error | Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开始Hook二维码创建窗口，目的是为了创建生成二维码
     *
     * @param appClassLoader
     * @throws Exception
     */
    private void hookQRWindows(final ClassLoader appClassLoader) {
        XposedHelpers.findAndHookMethod("com.alipay.mobile.payee.ui.PayeeQRSetMoneyActivity"
                , appClassLoader, "onCreate", Bundle.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        LogUtils.show("Hook到支付宝窗口");
                        ((Activity) param.thisObject).getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);

                        Intent intent = ((Activity) param.thisObject).getIntent();
                        String mark = intent.getStringExtra("mark");
                        String money = intent.getStringExtra("money");

                        //下面两行其实可以不要的
                        Field markField = XposedHelpers.findField(param.thisObject.getClass(),
                                "g");
                        markField.set(param.thisObject, money);


                        Field jinErField = XposedHelpers.findField(param.thisObject.getClass(), "b");
                        final Object jinErView = jinErField.get(param.thisObject);
                        Field beiZhuField = XposedHelpers.findField(param.thisObject.getClass(), "c");
                        final Object beiZhuView = beiZhuField.get(param.thisObject);
                        //设置支付宝金额和备注
                        XposedHelpers.callMethod(jinErView, "setText", money);
                        XposedHelpers.callMethod(beiZhuView, "setText", mark);

                        //点击确认，这个模拟方案本来淘汰了，觉得直接用Call更稳定，但其实performClick也是相当于call，并不是传统模拟
                        Field quRenField = XposedHelpers.findField(param.thisObject.getClass(), "e");
                        final Button quRenButton = (Button) quRenField.get(param.thisObject);
                        quRenButton.performClick();

                        //方式二是直接调用函数，直接可以替换上面模拟方案，各有各好吧。
                        //XposedHelpers.callMethod(param.thisObject, "a");
                    }
                });
    }


    /**
     * hook二维码生成后操作，目的是为了得到创建二维码的url链接
     *
     * @param appClassLoader
     * @param context
     */
    private void hookQRCreat(final ClassLoader appClassLoader, final Context context) {
        XposedHelpers.findAndHookMethod("com.alipay.mobile.payee.ui.PayeeQRSetMoneyActivity", appClassLoader, "a",
                XposedHelpers.findClass("com.alipay.transferprod.rpc.result.ConsultSetAmountRes", appClassLoader), new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                        Field moneyField = XposedHelpers.findField(param.thisObject.getClass(), "g");
                        String money = (String) moneyField.get(param.thisObject);

                        Field markField = XposedHelpers.findField(param.thisObject.getClass(), "c");
                        Object markObject = markField.get(param.thisObject);
                        String mark = (String) XposedHelpers.callMethod(markObject, "getUbbStr");

                        Object consultSetAmountRes = param.args[0];
                        Field consultField = XposedHelpers.findField(consultSetAmountRes.getClass(), "qrCodeUrl");
                        String payurl = (String) consultField.get(consultSetAmountRes);

                        Field consultField2 = XposedHelpers.findField(consultSetAmountRes
                                .getClass(), "printQrCodeUrl");
                        String payurloffline = (String) consultField2.get(consultSetAmountRes);

                        LogUtils.show("支付宝生成二维码：" + money + "|"
                                + mark + "|" + payurl + "|" + payurloffline);

                        OrderData MsgData = new OrderData();
                        MsgData.setChannel(OrderData.ALIPAY);
                        MsgData.setMark_sell(mark);
                        MsgData.setUrl(payurl);
                        MsgData.setId(UUID.randomUUID().toString());
                        MsgData.setOrder_type(OrderData.MARKQR);
                        MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                        MsgData.setOrder_status(2);

                        String SendData = new Gson().toJson(MsgData);

                        Intent broadCastIntent = new Intent()
                                .putExtra("data", SendData)
                                .setAction(RECEIVE_QR_ALIPAY);
                        context.sendBroadcast(broadCastIntent);
                    }
                });
    }

    /**
     * 当支付宝收到订单时，Hook订单信息
     *
     * @param appClassLoader
     * @param context
     */
    private void hookBill(final ClassLoader appClassLoader, final Context context) {
        XposedHelpers.findAndHookMethod(
                "com.alipay.android.phone.messageboxstatic.biz.dao.ServiceDao", appClassLoader,
                "insertMessageInfo",
                XposedHelpers.findClass(
                        "com.alipay.android.phone.messageboxstatic.biz.db.ServiceInfo",
                        appClassLoader), String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            LogUtils.show("支付宝收到支付订单1");

                            String str = PayUtils.getMidText((String) XposedHelpers.callMethod(param.args[0],
                                    "toString", new Object[0]), "extraInfo='", "'").replace("\\", "");
                            LogUtils.show("支付宝收款到账1：" + str);
                            if ((str.contains("收钱到账")) || (str.contains("收款到账"))) {
                                str = PayUtils.getInstance().getAlipayCookieStr(appClassLoader);
                                LogUtils.show("支付宝Cookie：" + str);
                                Intent broadCastIntent = new Intent()
                                        .putExtra("data", str)
                                        .setAction(RECEIVE_BILL_ALIPAY);
                                context.sendBroadcast(broadCastIntent);
                            }
                        } catch (Exception i) {
                            i.printStackTrace();
                            LogUtils.show("支付宝收到支付订单出错" + i.getMessage());
                        }
                    }
                }
        );
        Class<?> insertTradeMessageInfo = XposedHelpers.findClass("com.alipay.android.phone.messageboxstatic.biz.dao.TradeDao", appClassLoader);
        XposedBridge.hookAllMethods(insertTradeMessageInfo, "insertMessageInfo", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    LogUtils.show("支付宝收到支付订单2");
                    Object object = param.args[0];
                    String MessageInfo = (String) XposedHelpers.callMethod(object, "toString");
                    LogUtils.show(MessageInfo);
                    String content = PayUtils.getMidText(MessageInfo, "content='", "'");
                    if (content.contains("二维码收款") || content.contains("收到一笔转账")
                            || content.contains("成功收款")) {

                        String str = PayUtils.getInstance().getAlipayCookieStr(appClassLoader);
                        LogUtils.show("收款2Cookie：" + str);
                        JSONObject jsonObject = new JSONObject(content);
                        String money = jsonObject.getString("content");
                        money = money.replace("￥", "").replace(" ", "");
                        Double money2 = Double.valueOf(money)*100;
                        String mark = jsonObject.getString("assistMsg2");
                        mark = mark.replace("￥", "").replace(" ", "");
                        String tradeNo = PayUtils.getMidText(MessageInfo, "tradeNO=", "&");
                        LogUtils.show("收到支付宝支付订单：" + tradeNo + "|" + money + "|" + mark);

                        LogUtils.show("开始数据组合");
                        OrderData MsgData = new OrderData();
                        MsgData.setOrder_id(tradeNo);
                        MsgData.setMoney(money2.intValue());
                        MsgData.setMark_sell(mark);
                        MsgData.setChannel(OrderData.ALIPAY);
                        MsgData.setOrder_status(1);
                        MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                        MsgData.setId(UUID.randomUUID().toString());
                        MsgData.setOrder_type(OrderData.PAYMENT);

                        String SendData = new Gson().toJson(MsgData);

                        LogUtils.show("数据组合完毕");
                        LogUtils.show(SendData);

                        Intent broadCastIntent = new Intent()
                                .putExtra("data", SendData)
                                .setAction(RECEIVE_BILL_ALIPAY2);
                        context.sendBroadcast(broadCastIntent);
                    }
                } catch (Exception e) {
                    LogUtils.show("支付宝订单获取错误：" + e.getMessage());
                }
                super.beforeHookedMethod(param);
            }
        });
    }


    /**
     * Hook掉支付宝的多项安全检查
     *
     * @param classLoader
     */
    private void hookSafeCheck(ClassLoader classLoader) {
        try {
            Class<?> securityCheckClazz = XposedHelpers.findClass("com.alipay.mobile.base.security.CI", classLoader);
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", String.class, String.class, String.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object object = param.getResult();
                    XposedHelpers.setBooleanField(object, "a", false);
                    param.setResult(object);
                    super.afterHookedMethod(param);
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", Class.class, String.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return (byte) 1;
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", ClassLoader.class, String.class, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return (byte) 1;
                }
            });
            XposedHelpers.findAndHookMethod(securityCheckClazz, "a", new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return false;
                }
            });
        } catch (Error | Exception e) {
            e.printStackTrace();
        }
    }

}
