package qs.pay.qsp.utils;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.URLEncoder;
import java.security.MessageDigest;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import de.robv.android.xposed.XposedHelpers;
import qs.pay.qsp.HookMain;
import qs.pay.qsp.MainActivity;
import qs.pay.qsp.QSApplication;
import qs.pay.qsp.request.SingletonRequest;
import qs.pay.qsp.request.StringRequestGet;

import static qs.pay.qsp.utils.Configs.LastTradeNo;
import static qs.pay.qsp.utils.LogUtils.show;

public class PayUtils {
    private static PayUtils MePayUtils;

    public synchronized static PayUtils getInstance()
    {
        if (MePayUtils == null) {
            MePayUtils = new PayUtils();
        }
        return MePayUtils;
    }

    /**
     * 字符串MD5加密
     *
     * @param s    待加密的字符串值
     */

    public final String MD5(String s) {
        char hexDigits[] = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f' };
        try {
            byte[] btInput = s.getBytes();
            // 获得MD5摘要算法的 MessageDigest 对象
            MessageDigest mdInst = MessageDigest.getInstance("MD5");
            // 使用指定的字节更新摘要
            mdInst.update(btInput);
            // 获得密文
            byte[] md = mdInst.digest();
            // 把密文转换成十六进制的字符串形式
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取指定文本的两指定文本之间的文本
     *
     * @param text
     * @param begin
     * @param end
     * @return
     */
    public static String getMidText(String text, String begin, String end) {
        try {
            int b = text.indexOf(begin) + begin.length();
            int e = text.indexOf(end, b);
            return text.substring(b, e);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String toURLEncoded(String paramString) {
        if (paramString == null || paramString.equals("")) {
            return "";
        }

        try
        {
            String str = new String(paramString.getBytes(), "UTF-8");
            str = URLEncoder.encode(str, "UTF-8");
            return str;
        }
        catch (Exception localException)
        {
            LogUtils.show("urlencoded失败：" + localException.getMessage());
        }

        return "";
    }


    //根据传入参数加入必要校验参数生成token
    public String makeParams(String JsonString)
    {
        try
        {
            String timeStamp = System.currentTimeMillis()+ "";

            if(Configs.Appid.equals("") || Configs.Security.equals(""))
            {
                return "";
            }
            else
            {
                JSONObject jsonSendData = new JSONObject();

                JSONObject jsonObject = JSON.parseObject(JsonString);
                jsonObject.put("appid", Configs.Appid);
                jsonObject.put("timestamp",timeStamp);

                String ParamsUrl = "";


                TreeMap<String ,Object> mapData = JSONObject.parseObject(jsonObject.toJSONString(), new TypeReference<TreeMap<String, Object>>(){});

                Iterator iterator = mapData.keySet().iterator();
                while (iterator.hasNext())
                {
                    String key = (String) iterator.next();
                    String value = mapData.get(key).toString();
                    if(!value.equals("sign") && !value.equals(""))
                    {
                        jsonSendData.put(key,value);
                        ParamsUrl = ParamsUrl + key + "=" + toURLEncoded(value) + "&";
                    }
                }


                ParamsUrl = ParamsUrl + "key=" + Configs.Security;
                show("签名字符串：" + ParamsUrl.toLowerCase());

                jsonSendData.put("sign",TextUtils.isEmpty(ParamsUrl)?"":this.MD5(ParamsUrl.toLowerCase()).toUpperCase());

                LogUtils.show(jsonObject.toJSONString());
                return jsonSendData.toString();
            }

        }
        catch (JSONException ex)
        {
            LogUtils.show("生成签名函数出错拉：" + ex.getMessage());
            return "";
        }

    }

    /**
     * 创建微信收款二维码
     * @param context
     * @param money   金额，单位为分，范围1-30000000
     * @param mark    收款备注，最长30个字符，不能为空
     */
    public void creatWechatQr(Context context, Integer money, String mark)
    {
        LogUtils.show("收到创建微信二维码请求，金额为：" + money + "备注：" + mark);
        if (money == null || TextUtils.isEmpty(mark)) {
            Configs.MakerQrStat = false;
            Configs.MakerQrTime = 0;
            MainActivity.delOrder(Configs.MarkCode.getId());
            show("创建二维码金额空的，删除");
            return;
        }
        if (mark.length() > 30 || money > 30000000 || money < 1) {
            Configs.MakerQrStat = false;
            Configs.MakerQrTime = 0;
            MainActivity.delOrder(Configs.MarkCode.getId());
            show("创建二维码金额或者备注错误，删除");
            return;
        }
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(HookMain.WECHAT_CREAT_QR);
        broadCastIntent.putExtra("mark", mark); //备注
        broadCastIntent.putExtra("money", String.valueOf(money / 100.0f));//金额
        context.sendBroadcast(broadCastIntent);
    }

    /**
     * 创建支付宝收款二维码
     *
     * @param context
     * @param money   金额，单位为分，范围1-30000000
     * @param mark    收款备注，最长30个字符，不能为空
     */
    public void creatAlipayQr(Context context, Integer money, String mark)
    {
        if (money == null || TextUtils.isEmpty(mark)) {
            Configs.MakerQrStat = false;
            Configs.MakerQrTime = 0;
            MainActivity.delOrder(Configs.MarkCode.getId());
            show("创建二维码金额空的，删除");
            return;
        }
        if (mark.length() > 30 || money > 30000000 || money < 1) {
            Configs.MakerQrStat = false;
            Configs.MakerQrTime = 0;
            MainActivity.delOrder(Configs.MarkCode.getId());
            show("创建二维码金额或者备注错误，删除");
            return;
        }
        Intent broadCastIntent = new Intent();
        broadCastIntent.setAction(HookMain.ALIPAY_CREAT_QR);
        broadCastIntent.putExtra("mark", mark);
        broadCastIntent.putExtra("money", String.valueOf(money / 100.0f));
        context.sendBroadcast(broadCastIntent);
    }

    /**
     * 通过支付宝APP得到web访问的Cookies数据
     * 因为全是static方法，还是很方便获取的
     *
     * @param paramClassLoader
     * @return 成功返回cookies，失败返回空文本，非null
     */
    public String getAlipayCookieStr(ClassLoader paramClassLoader)
    {
        XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.alipay.mobile.common.transportext.biz.appevent.AmnetUserInfo", paramClassLoader), "getSessionid", new Object[0]);
        Context localContext = (Context) XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.alipay.mobile.common.transportext.biz.shared.ExtTransportEnv", paramClassLoader), "getAppContext", new Object[0]);
        if (localContext != null) {
            if (XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.alipay.mobile.common.helper.ReadSettingServerUrl", paramClassLoader), "getInstance", new Object[0]) != null) {
                return (String) XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.alipay.mobile.common.transport.http.GwCookieCacheHelper", paramClassLoader), "getCookie", new Object[]{".alipay.com"});
            }
            LogUtils.show("支付宝订单Cookies获取异常");
            return "";
        }
        LogUtils.show("支付宝Context获取异常");
        return "";
    }

    public void httpToServer(final String type,final String SendData)
    {
        LogUtils.show("http通道收到消息请求");
        String url = "";
        switch (type)
        {
            case "SendQrUrl":
                url= Configs.DomainUrl + Configs.SendQrUrl;
                break;
            case "SendPayUrl":
                url= Configs.DomainUrl + Configs.SendPayUrl;
                break;
            case "GetQrUrl":
                url= Configs.DomainUrl + Configs.GetQrUrl;
                break;
            case "InitUrl":
                url= Configs.DomainUrl + Configs.InitUrl;
                break;
        }
        LogUtils.show(url);
        LogUtils.show(SendData);
        //RequestQueue queue=Volley.newRequestQueue(QSApplication.app);
        StringRequest request=new StringRequest(Request.Method.POST,url, new Response.Listener<String>()
        {
            @Override
            public void onResponse(String result)
            {
                try
                {
                    LogUtils.show("http接口返回结果：" + result);
                    JSONObject JsonData = JSON.parseObject(result);
                    switch (JsonData.getString("type"))
                    {
                        case "SendQrUrl":
                            LogUtils.show("http SendQrUrl");
                            MainActivity.delOrder(JsonData.getString("data"));
                            break;
                        case "SendPayUrl":
                            LogUtils.show("http SendPayUrl");
                            MainActivity.delOrder(JsonData.getString("data"));
                            break;
                        case "GetQrUrl":
                            LogUtils.show("http GetQrUrl");
                            if(JsonData.getString("code").equals("0"))
                            {
                                JSONArray OrderList = (JSONArray) JsonData.get("data");
                                for (Iterator iterator = OrderList.iterator(); iterator.hasNext();) {
                                    JSONObject Order = (JSONObject) iterator.next();
                                    OrderData MsgData = new OrderData();
                                    MsgData.setId(UUID.randomUUID().toString());
                                    MsgData.setMark_sell(Order.getString("mark_sell"));
                                    MsgData.setOrder_type(OrderData.MARKQR);
                                    MsgData.setChannel(Order.getString("channel"));
                                    Double Money = Double.valueOf(Order.getString("money"))*100;
                                    MsgData.setMoney(Money.intValue());
                                    MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                                    MsgData.setOrder_status(1);
                                    MsgData.setOrder_id(Order.getString("Id"));

                                    String SendData = new Gson().toJson(MsgData);
                                    MainActivity.addrealm(SendData);
                                }
                            }

                            break;
                        case "InitUrl":
                            if(JsonData.getString("code").equals("0"))
                            {
                                Configs.HttpStart = true;
                                Configs.Did = JsonData.getString("data");
                                MainActivity.addlog("Http启动成功->" + JsonData.getString("msg"));
                                MainActivity.mHandler.obtainMessage(3, null).sendToTarget();
                            }
                            else
                            {
                                MainActivity.addlog("Http启动失败->" + JsonData.getString("msg"));
                            }
                            break;
                    }

                }
                catch (Exception e)
                {
                    MainActivity.addlog("http接口访问失败：" + e.getMessage());
                    LogUtils.show("http接口访问失败：" + e.getMessage());
                }

            }
        }, new Response.ErrorListener()
        {
            @Override
            public void onErrorResponse(VolleyError volleyError)
            {
                LogUtils.show("http接口访问出错：" + volleyError.getMessage());
                MainActivity.addlog("http服务错误：" + volleyError.getMessage());
            }
        })
        {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError
            {
                JSONObject TempData = JSON.parseObject(SendData);

                LogUtils.show("原始发送数据");
                LogUtils.show(SendData);

                Map<String,String> map=new HashMap<>();

                for (Map.Entry<String, Object> entry : TempData.entrySet()) {
                    System.out.println(entry.getKey() + ":" + entry.getValue());
                    map.put(entry.getKey(),entry.getValue().toString());
                }
                LogUtils.show("post数据");
                JSONObject itemJSONObj = JSONObject.parseObject(JSON.toJSONString(map));
                LogUtils.show(itemJSONObj.toJSONString());

                return map;
            }
        };

        SingletonRequest.getInstance(QSApplication.app).addToRequestQueue(request);
        //queue.add(request);
    }

    /**
     * 通过网络请求获取最近的5个订单号
     * 把最近xx分钟内的订单传号传给getAlipayTradeDetail函数处理
     *
     * @param context
     * @param cookies
     */
    public static void dealAlipayWebTrade(final Context context, final String cookies)
    {
        show("获取订单的cookie：" + cookies);
        long l = System.currentTimeMillis() + 200000;//怕手机的时间比支付宝慢了点，刚产生的订单就无法获取到
        String getUrl = "https://mbillexprod.alipay.com/enterprise/simpleTradeOrderQuery.json?beginTime=" + (l - 864000000L)
                + "&limitTime=" + l + "&pageSize=5&pageNum=1&channelType=ALL";
        StringRequestGet request = new StringRequestGet(getUrl, new Response.Listener<String>()
        {
            @Override
            public void onResponse(String response)
            {
                try
                {
                    LogUtils.show("获取到了订单数据,下面需要对数据进行梳理");

                    JSONObject jsonObject = JSON.parseObject(response);

                    LogUtils.show(jsonObject.toJSONString());

                    JSONArray OrderList = (JSONArray) ((JSONObject) jsonObject.get("result")).get("list");


                    for (Iterator iterator = OrderList.iterator(); iterator.hasNext();)
                    {
                        JSONObject Orders = (JSONObject) iterator.next();
                        if (System.currentTimeMillis() - Long.valueOf(Orders.getString("gmtCreateStamp"))  > Configs.ALIPAY_BILL_TIME) {
                            LogUtils.show("时间太久的订单，跳过");
                            break;
                        }

                        if(!TextUtils.isEmpty(LastTradeNo))
                        {
                            LogUtils.show("检测是否是重复订单:" + LastTradeNo + "-" + Orders.getString("tradeNo"));
                            if(LastTradeNo.equals(Orders.getString("tradeNo")))
                            {
                                LogUtils.show("最后一个订单已经查找过，跳过");
                                continue;//最新的订单都已经处理过，那就直接返回
                            }
                        }

                        getAlipayTradeDetail(context, Orders.getString("tradeNo")
                                , Integer.valueOf(new DecimalFormat("#").format(Float.valueOf(Orders.getString("totalAmount")) * 100))
                                , cookies);
                    }
                } catch (Exception e) {
                    LogUtils.show("支付宝订单获取网络错误" + e.getMessage());
                }
            }
        }
                , new Response.ErrorListener()
        {
            @Override
            public void onErrorResponse(VolleyError error)
            {
                LogUtils.show("支付宝订单获取网络错误，请不要设置代理");
            }
        });

        String dataNow = new SimpleDateFormat("yyyy-MM-dd").format(new Date(l));
        String dataLastDay = new SimpleDateFormat("yyyy-MM-dd").format(new Date(l - 864000000L));

        request.addHeaders("Cookies", cookies).addHeaders("Referer", "https://render.alipay.com/p/z/merchant-mgnt/simple-order.html?beginTime=" + dataLastDay + "&endTime=" + dataNow + "&fromBill=true&channelType=ALL");

        SingletonRequest.getInstance(QSApplication.app).addToRequestQueue(request);

        //RequestQueue queue = Volley.newRequestQueue(context);
        //queue.add(request);
        //queue.start();
    }

    /**
     * 获取指定订单号的订单信息，如果是已收款状态，则发送给服务器，
     * 失败的会自动加数据库以后补发送。
     *
     * @param context
     * @param tradeNo
     * @param money   单位为分
     * @param cookies
     */
    private static void getAlipayTradeDetail(Context context, final String tradeNo, final int money, String cookies)
    {
        String getUrl = "https://tradeeportlet.alipay.com/wireless/tradeDetail.htm?tradeNo=" + tradeNo + "&source=channel&_from_url=https%3A%2F%2Frender.alipay.com%2Fp%2Fz%2Fmerchant-mgnt%2Fsimple-order._h_t_m_l_%3Fsource%3Dmdb_card";
        StringRequestGet request = new StringRequestGet(getUrl, new Response.Listener<String>()
        {
            @Override
            public void onResponse(String response)
            {
                try
                {
                    LogUtils.show("支付宝订单详情");
                    String html = response.toLowerCase();

                    LogUtils.show(html);
                    html = html.replace(" ", "")
                            .replace("\r", "")
                            .replace("\n", "")
                            .replace("\t", "");
                    html = getMidText(html, "\"id=\"j_logourl\"/>", "j_maskcode\"class=\"maskcodemain\"");

                    String tmp;
                    OrderData MsgData = new OrderData();
                    MsgData.setChannel(OrderData.ALIPAY);
                    MsgData.setOrder_id(tradeNo);
                    MsgData.setId(UUID.randomUUID().toString());
                    MsgData.setOrder_type(OrderData.PAYMENT);
                    MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));

                    tmp = getMidText(html, "<divclass=\"am-flexbox\">当前状态</div>", "<divclass=\"am-list-itemtrade-info-item\">");
                    MsgData.setMark_buy(getMidText(tmp, "<divclass=\"trade-info-value\">", "</div>"));

                    tmp = getMidText(html, "<divclass=\"am-flexbox-item\">说</div><divclass=\"am-flexbox-item\">明", "<divclass=\"am-list-itemtrade-info-item\">");
                    MsgData.setMark_sell(getMidText(tmp, "<divclass=\"trade-info-value\">", "</div"));

                    tmp = getMidText(html, "am-flexbox-item\">金</div><divclass=\"am-flexbox-item\">额", "<divclass=\"am-list-itemtrade-info-item\">");
                    Float money = Float.valueOf(getMidText(tmp, "<divclass=\"trade-info-value\">", "</div")) * 100;
                    MsgData.setMoney(money.intValue());

                    if (TextUtils.isEmpty(MsgData.getMark_sell()) || !MsgData.getMark_buy().contentEquals("已收款"))
                    {
                        return;
                    }
                    LastTradeNo = tradeNo;
                    String SendData = new Gson().toJson(MsgData);
                    MainActivity.addrealm(SendData);
                    LogUtils.show("支付宝发送支付成功任务：" + tradeNo + "|" + MsgData.getMark_sell() + "|" + MsgData.getMoney());
                }
                catch (Exception ignore)
                {
                    LogUtils.show("支付宝获取指定订单详情失败");
                }
            }
        }
                , new Response.ErrorListener()
        {
            @Override
            public void onErrorResponse(VolleyError error) {
                LogUtils.show("支付宝订单详情获取错误：" + tradeNo + "-->" + error.getMessage());
            }
        });

        request.addHeaders("Cookies", cookies);

        SingletonRequest.getInstance(QSApplication.app).addToRequestQueue(request);

        //RequestQueue queue = Volley.newRequestQueue(context);
        //queue.add(request);
        //queue.start();
    }
}
