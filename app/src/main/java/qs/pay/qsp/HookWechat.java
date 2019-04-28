package qs.pay.qsp;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.WindowManager;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.UUID;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import qs.pay.qsp.utils.LogUtils;
import qs.pay.qsp.utils.OrderData;
import qs.pay.qsp.utils.XmlToJson;

/**
 * @ Created by Dlg
 * @ <p>TiTle:  HookWechat</p>
 * @ <p>Description:微信的主要HOOK类</p>
 * @ date:  2018/09/22
 * @ QQ群：524901982
 */
public class HookWechat {

    protected void hook(final ClassLoader appClassLoader, final Context context)
    {
        try {
            hookBill(appClassLoader, context);
            hookQRCreat(appClassLoader, context);
            hookQRWindows(appClassLoader);
        } catch (Exception e) {
        }
    }


    /**
     * Hook微信收到订单消息后的处理(用户付款完成后微信官方下发给用户微信的交易订单记录消息)
     *
     * @param appClassLoader
     * @param context
     */
    private void hookBill(final ClassLoader appClassLoader, final Context context)
    {
        XposedHelpers.findAndHookMethod("com.tencent.wcdb.database.SQLiteDatabase", appClassLoader, "insert", String.class, String.class, ContentValues.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param)
                            throws Throwable {
                        try {
                            LogUtils.show("Hook到收款通知");

                            ContentValues contentValues = (ContentValues) param.args[2];
                            String tableName = (String) param.args[0];
                            LogUtils.show("tableName:" + tableName);
                            if (TextUtils.isEmpty(tableName) || !tableName.equals("message")) {
                                return;
                            }
                            Integer type = contentValues.getAsInteger("type");
                            LogUtils.show("Type:" + type);
                            if (null == type) {
                                return;
                            }
                            if (type == 318767153)
                            {
                                String jsonStr = XmlToJson.documentToJSONObject(contentValues.getAsString("content")).toJSONString();
                                JSONObject msg = new JSONObject(jsonStr).getJSONObject("appmsg");
                                LogUtils.show("msg:" + msg);
                                LogUtils.show("type:" + msg.getString("type"));
                                if (!msg.getString("type").equals("5"))
                                {
                                    //收款类型type为5
                                    return;
                                }
                                LogUtils.show("type:" + msg.getString("type"));

                                OrderData MsgData = new OrderData();
                                MsgData.setChannel(OrderData.WECHAT);
                                MsgData.setMoney((int) (Float.valueOf(msg.getJSONObject("mmreader")
                                        .getJSONObject("template_detail")
                                        .getJSONObject("line_content")
                                        .getJSONObject("topline")
                                        .getJSONObject("value")
                                        .getString("word")
                                        .replace("￥", "")) * 100));
                                MsgData.setOrder_id(msg.getJSONObject("ext_pay_info").getString("pay_outtradeno"));

                                MsgData.setId(UUID.randomUUID().toString());
                                MsgData.setOrder_type(OrderData.PAYMENT);
                                MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                                MsgData.setOrder_status(1);

                                JSONArray lines = msg.getJSONObject("mmreader").getJSONObject("template_detail").getJSONObject("line_content").getJSONObject("lines").getJSONArray("line");

                                for (int i = 0; i < 2; i++)
                                {
                                    if (lines.length() < i + 1 && lines.getJSONObject(i) == null) {
                                        break;
                                    }
                                    if (lines.getJSONObject(i).getJSONObject("key").getString("word").contains("付款方"))
                                    {
                                        MsgData.setMark_buy(lines.getJSONObject(i).getJSONObject("value").getString("word"));//付款方备注
                                    }
                                    else if (lines.getJSONObject(i).getJSONObject("key").getString("word").contains("收款方"))
                                    {
                                        MsgData.setMark_sell(lines.getJSONObject(i).getJSONObject("value").getString("word"));//收款方备注
                                    }
                                }

                                String SendData = new Gson().toJson(MsgData);

                                LogUtils.show("微信收到支付订单：" + SendData);

                                Intent broadCastIntent = new Intent();
                                broadCastIntent.putExtra("data", SendData);
                                broadCastIntent.setAction(HookMain.RECEIVE_BILL_WECHAT);
                                context.sendBroadcast(broadCastIntent);
                            }
                        } catch (Exception e) {
                            LogUtils.show("错误" + e.getMessage());
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param)
                            throws Throwable {
                        LogUtils.show("Hook到收款通知-after");
                    }
                });
    }

    /**
     * hook二维码生成后操作，目的是为了得到创建二维码的url链接
     *
     * @param appClassLoader
     * @param context
     */
    private void hookQRCreat(final ClassLoader appClassLoader, final Context context)
    {
        Class<?> clazz = XposedHelpers.findClass("com.tencent.mm.plugin.collect.b.s", appClassLoader);
        XposedBridge.hookAllMethods(clazz, "a", new XC_MethodHook() {

            @Override
            protected void beforeHookedMethod(MethodHookParam param)
                    throws Throwable {
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable
            {
                Field moneyField = XposedHelpers.findField(param.thisObject.getClass(), "iqu");
                Double money = (Double) moneyField.get(param.thisObject);

                Field markField = XposedHelpers.findField(param.thisObject.getClass(), "desc");
                String mark = (String) markField.get(param.thisObject);

                Field payurlField = XposedHelpers.findField(param.thisObject.getClass(), "iqt");
                String payurl = (String) payurlField.get(param.thisObject);

                OrderData MsgData = new OrderData();
                MsgData.setChannel(OrderData.WECHAT);
                MsgData.setMark_sell(mark);
                MsgData.setUrl(payurl);

                MsgData.setId(UUID.randomUUID().toString());
                MsgData.setOrder_type(OrderData.MARKQR);
                MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                MsgData.setOrder_status(2);

                LogUtils.show("微信成功生成二维码：" + money.floatValue() + "  " + mark + "  " + payurl);

                String SendData = new Gson().toJson(MsgData);

                Intent broadCastIntent = new Intent();
                broadCastIntent.putExtra("data", SendData);
                broadCastIntent.setAction(HookMain.RECEIVE_QR_WECHAT);
                context.sendBroadcast(broadCastIntent);
            }
        });
    }

    /**
     * 开始Hook二维码创建窗口，目的是为了创建生成二维码
     *
     * @param appClassLoader
     * @throws Exception
     */
    private void hookQRWindows(final ClassLoader appClassLoader)
    {
        Class<?> clazz = XposedHelpers.findClass("com.tencent.mm.plugin.collect.ui.CollectCreateQRCodeUI", appClassLoader);
        XposedBridge.hookAllMethods(clazz, "onCreate", new XC_MethodHook()
        {

            @Override
            protected void beforeHookedMethod(MethodHookParam param) {

            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                LogUtils.show("Hook到微信窗口");
                ((Activity) param.thisObject).getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            }
        });

        XposedHelpers.findAndHookMethod("com.tencent.mm.plugin.collect.ui.CollectCreateQRCodeUI", appClassLoader, "initView", new XC_MethodHook()
        {

                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param)
                            throws Throwable {
                        LogUtils.show("Hook微信开始......");
                        Intent intent = ((Activity) param.thisObject).getIntent();
                        String mark = intent.getStringExtra("mark");
                        String money = intent.getStringExtra("money");

                        Class<?> bs = XposedHelpers.findClass("com.tencent.mm.plugin.collect.b.s", appClassLoader);
                        Object obj = XposedHelpers.newInstance(bs, Double.valueOf(money), "1", mark);
                        XposedHelpers.callMethod(param.thisObject, "a", obj, true, true);
                    }
                });
    }

}
