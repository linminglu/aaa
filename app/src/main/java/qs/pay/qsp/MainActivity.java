package qs.pay.qsp;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.google.gson.Gson;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import io.realm.Realm;
import io.realm.RealmResults;
import me.leefeng.promptlibrary.PromptButton;
import me.leefeng.promptlibrary.PromptButtonListener;
import me.leefeng.promptlibrary.PromptDialog;
import qs.pay.qsp.utils.Configs;
import qs.pay.qsp.utils.LogUtils;
import qs.pay.qsp.utils.OrderData;
import qs.pay.qsp.utils.PayUtils;
import qs.pay.qsp.utils.SetData;

import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY;
import static qs.pay.qsp.HookMain.RECEIVE_BILL_ALIPAY2;
import static qs.pay.qsp.HookMain.RECEIVE_BILL_WECHAT;
import static qs.pay.qsp.HookMain.RECEIVE_QR_ALIPAY;
import static qs.pay.qsp.HookMain.RECEIVE_QR_WECHAT;
import static qs.pay.qsp.QSApplication.app;
import static qs.pay.qsp.utils.Configs.Did;
import static qs.pay.qsp.utils.Configs.HEART_BEAT_RATE;
import static qs.pay.qsp.utils.Configs.HttpStart;
import static qs.pay.qsp.utils.Configs.SocketHost;
import static qs.pay.qsp.utils.Configs.SocketStart;
import static qs.pay.qsp.utils.Configs.sendTime;
import static qs.pay.qsp.utils.LogUtils.show;

public class MainActivity extends AppCompatActivity {

    private static WebSocketClient mSocketClient; //websocket通信对象
    private static Realm realm;

    private static TextView logTextView;
    public static ScrollView mScrollView;

    private static PromptDialog promptDialog;

    private static Handler sHandler = new Handler();

    private static Context mContext;

    private static Boolean SocketConnect = false; //socket状态是否是连接中

    private static boolean flag = true; //定时任务是否继续
    private Handler qHandler = new Handler(); //队列的定时对象

    private static FloatingActionButton fab; //控制按钮

    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mContext = this;

        getPermissions();
        promptDialog = new PromptDialog(this);

        initWebSocket(); //初始化socket 服务

        realm = Realm.getDefaultInstance();

        SetData Config = realm.where(SetData.class).equalTo("id", 1).findFirst();
        show("获取配置:");
        if(Config==null)
        {
            show("没有数据:");

            String deviceid = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID) + "";
            deviceid = PayUtils.getInstance().MD5(deviceid + Build.SERIAL);
            try
            {
                realm.beginTransaction();
                SetData setdata = realm.createObject(SetData.class,1); // Create a new object  
                setdata.setDevice_id(deviceid);
                setdata.setAppid("");
                setdata.setSecurity("");
                setdata.setNotify("false");
                realm.commitTransaction();
                Configs.DeviceId = deviceid;
                Config = realm.where(SetData.class).equalTo("id", 1).findFirst();
            }
            catch (Exception e)
            {
                show("写入失败：" + e.getMessage());

                PromptDialog promptDialog = new PromptDialog(this);
                promptDialog.showWarnAlert("数据库初始化失败，请退出重试！", new PromptButton("确定", new PromptButtonListener() {
                    @Override
                    public void onClick(PromptButton button) {
                        System.exit(0);
                    }
                }));
            }
        }
        else
        {
            show("取到数据:" + Config.getId() + "-" + Config.getDevice_id());
        }

        Configs.Appid = Config.getAppid();
        Configs.Security = Config.getSecurity();
        Configs.DeviceId = Config.getDevice_id();
        Configs.NotifyStart = Config.getNotify();

        if(!Configs.Appid.equals(""))
        {
            TextView TxtSecurity = findViewById(R.id.appid);
            TxtSecurity.setText(Configs.Appid);
        }

        if(!Configs.Security.equals(""))
        {
            TextView TxtSecurity = findViewById(R.id.security);
            TxtSecurity.setText(Configs.Security);
        }

        fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
                if(SocketStart || HttpStart)
                {
                    stopService();
                    //停止服务
                }
                else
                {
                    TextView TxtSecurity = findViewById(R.id.security);
                    final String security = TxtSecurity.getText().toString() + "";

                    TextView TxtAppid = findViewById(R.id.appid);
                    final String appid = TxtAppid.getText().toString() + "";

                    if(appid.equals("") || security.equals(""))
                    {
                        PromptDialog promptDialog = new PromptDialog(MainActivity.this);
                        promptDialog.showWarnAlert("项目编码或密钥不能为空！", new PromptButton("确定", new PromptButtonListener() {
                            @Override
                            public void onClick(PromptButton button) {

                            }
                        }));
                    }
                    else
                    {
                        realm.executeTransaction(new Realm.Transaction() {
                            @Override
                            public void execute(Realm realm) {
                                //先查找后得到User对象
                                SetData Config = realm.where(SetData.class).equalTo("id",1).findFirst();
                                Config.setAppid(appid);
                                Config.setSecurity(security);
                            }
                        });
                        Configs.Appid = appid;
                        Configs.Security = security;
                        startService(); //调用启动服务方法
                    }
                }

            }
        });

        logTextView = findViewById(R.id.text_log);
        mScrollView = findViewById(R.id.scrollView);

        WindowManager wm = (WindowManager) this
                .getSystemService(Context.WINDOW_SERVICE);
        int width = wm.getDefaultDisplay().getWidth();

        mScrollView.getLayoutParams().height = width;

        sHandler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);//开启定时轮询任务及心跳检测

        IntentFilter filter = new IntentFilter(RECEIVE_QR_WECHAT);
        filter.addAction(RECEIVE_QR_ALIPAY);
        filter.addAction(RECEIVE_BILL_WECHAT);
        filter.addAction(RECEIVE_BILL_ALIPAY);
        filter.addAction(RECEIVE_BILL_ALIPAY2);
        registerReceiver(new ReceiverMain(), filter);

        //有的手机就算已经静态注册服务还是不行启动，我再手动启动一下吧。
        startService(new Intent(this, ServiceMain.class));

        //广播也再次注册一下。。。机型兼容。。。
        ReceiverMain.startReceive();

        toggleNotificationListenerService();

        Intent intent =new Intent(MainActivity.this, QSNotifiService.class);//启动服务
        intent.putExtra("data", "");
        startService(intent);//启动服务

        addlog("系统初始化完成");

        logTextView.setMovementMethod(ScrollingMovementMethod.getInstance());

        startService(); //启动服务

        qHandler.postDelayed(QueueTask, 1000); //启动二维码生成队列任务

        mHandler.obtainMessage(3, null).sendToTarget();
    }


    public static Handler mHandler = new Handler(new Handler.Callback()
    {
        @Override
        public boolean handleMessage(final Message msg)
        {
            try
            {
                final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd日 HH:mm:ss");// HH:mm:ss
                //获取当前时间
                final Date date = new Date(System.currentTimeMillis());
                //message.what 0=操作日志 1=服务端消息达到
                if(msg.what==0)
                {
                    logTextView.post(new Runnable() {
                        @Override
                        public void run() {
                            if(logTextView.getLineCount()>=50)
                            {
                                logTextView.setText(simpleDateFormat.format(date) + "：" + "清理日志\n");
                            }
                        }
                    });
                    final Object logMsg = msg.obj;
                    logTextView.append(simpleDateFormat.format(date) + "：" + logMsg.toString() + "\n");
                    scrollToBottom(mScrollView,logTextView);
                }
                else if(msg.what ==1)
                {
                    promptDialog.dismiss();
                    String JsonData = msg.obj.toString();
                    show(JsonData);
                    JSONObject jsonObject = JSON.parseObject(JsonData);
                    String method = jsonObject.getString("method");
                    switch (method)
                    {
                        case "init":
                            if(jsonObject.getString("code").equals("1"))
                            {
                                SocketStart = true; //服务启动成功，Socket通道状态设置为启动
                                addlog("Socket启动成功->" + jsonObject.getString("result"));
                                Did = jsonObject.getString("did");
                                mHandler.obtainMessage(3, null).sendToTarget();
                                if(HttpStart==true)
                                {
                                    addlog("Socket启动成功->备用http服务已停止");
                                    HttpStart = false;
                                }
                            }
                            else
                            {
                                SocketStart = false; //服务启动失败，Socket通道状态设置为停止
                                //显示
                                addlog("Socket启动失败->" + jsonObject.getString("result"));
                            }
                            break;
                        case "stop":
                            if(jsonObject.getString("code").equals("1"))
                            {
                                SocketStart = false; //服务停止成功，Socket通道状态设置为停止
                                addlog("Socket停止成功->" + jsonObject.getString("result"));
                                mHandler.obtainMessage(4, null).sendToTarget();
                            }
                            else
                            {
                                addlog("Socket停止失败->" + jsonObject.getString("result"));
                            }
                            break;
                        case "addorder":
                            OrderData MsgData = new OrderData();
                            MsgData.setId(UUID.randomUUID().toString());
                            MsgData.setMark_sell(JSON.parseObject(jsonObject.get("order").toString()).get("mark_sell").toString());
                            MsgData.setOrder_type(OrderData.MARKQR);
                            MsgData.setChannel(JSON.parseObject(jsonObject.get("order").toString()).get("channel").toString());
                            Double Money = Double.valueOf(JSON.parseObject(jsonObject.get("order").toString()).get("money").toString())*100;
                            MsgData.setMoney(Money.intValue());
                            MsgData.setTimestamp(String.valueOf(System.currentTimeMillis()));
                            MsgData.setOrder_status(1);
                            MsgData.setOrder_id(JSON.parseObject(jsonObject.get("order").toString()).get("Id").toString());

                            String SendData = new Gson().toJson(MsgData);
                            addrealm(SendData);
                            break;
                        case "qrmake":
                            delOrder(jsonObject.getString("result"));
                            break;
                        case "orderpay":
                            delOrder(jsonObject.getString("result"));
                            break;
                    }
                }
                else if(msg.what==2)
                {
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("method", "init");
                    jsonObject.put("deviceid", Configs.DeviceId);
                    jsonObject.put("devicename", Build.MODEL);
                    jsonObject.put("wechat_hook",1);
                    jsonObject.put("alipay_hook",1);
                    Log.d("picher_log","init:" + jsonObject.toString());
                    mSocketClient.send(PayUtils.getInstance().makeParams(jsonObject.toString()));
                    show("socket启动");
                    addlog("Socket启动指令->已发送");
                }
                else if(msg.what ==3)
                {
                    show(HttpStart.toString());
                    show(SocketConnect.toString());
                    show(SocketStart.toString());

                    //如果是3 代表有设备启动了
                    if(HttpStart || SocketStart)
                    {
                        fab.setImageDrawable(ContextCompat.getDrawable(MainActivity.mContext,R.drawable.stop));
                    }

                    String txtmsg = "";
                    txtmsg = txtmsg + "HTTP：" + (HttpStart?"已启动":"未启动");
                    if(mSocketClient.getReadyState().equals(WebSocket.READYSTATE.OPEN))
                    {
                        txtmsg = txtmsg + " SOCKET：" + (SocketStart?"已启动":"未启动");
                    }
                    else
                    {
                        txtmsg = txtmsg + " SOCKET：未连接";
                    }

                    Snackbar snackbar = Snackbar.make(fab, txtmsg, Snackbar.LENGTH_INDEFINITE);
                    String SButtonTxt = Configs.NotifyStart.equals("true")? "关闭(通知栏)" : "开启(通知栏)";
                    snackbar.setAction(SButtonTxt, new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v) {
                            LogUtils.show("按钮被点击了");
                            if(Configs.NotifyStart.equals("true"))
                            {
                                Configs.NotifyStart = "false";
                            }
                            else
                            {
                                Configs.NotifyStart = "true";
                            }

                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(Realm realm) {
                                    //先查找后得到User对象
                                    SetData Config = realm.where(SetData.class).equalTo("id",1).findFirst();
                                    Config.setNotify(Configs.NotifyStart);
                                }
                            });
                            mHandler.obtainMessage(3, null).sendToTarget();
                        }
                    });
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    {
                        TextView message = (TextView) snackbar.getView().findViewById(R.id.snackbar_text);
                        message.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                        message.setGravity(Gravity.CENTER);
                    }
                    snackbar.show();
                }
                else if(msg.what ==4)
                {
                    show(HttpStart.toString());
                    show(SocketConnect.toString());
                    show(SocketStart.toString());

                    //如果是4 代表有设备关闭了
                    if(!HttpStart || !SocketStart)
                    {
                        fab.setImageDrawable(ContextCompat.getDrawable(MainActivity.mContext,R.drawable.start));
                    }

                    String txtmsg = "";
                    txtmsg = txtmsg + "HTTP：" + (HttpStart?"已启动":"未启动");
                    if(SocketConnect)
                    {
                        txtmsg = txtmsg + " SOCKET：" + (SocketStart?"已启动":"未启动");
                    }
                    else
                    {
                        txtmsg = txtmsg + " SOCKET：未连接";
                    }

                    Snackbar snackbar = Snackbar.make(fab, txtmsg, Snackbar.LENGTH_INDEFINITE);
                    String SButtonTxt = Configs.NotifyStart.equals("true")? "关闭(通知栏)" : "开启(通知栏)";
                    snackbar.setAction(SButtonTxt, new View.OnClickListener()
                    {
                        @Override
                        public void onClick(View v) {
                            LogUtils.show("按钮被点击了");
                            if(Configs.NotifyStart.equals("true"))
                            {
                                Configs.NotifyStart = "false";
                            }
                            else
                            {
                                Configs.NotifyStart = "true";
                            }
                            realm.executeTransaction(new Realm.Transaction() {
                                @Override
                                public void execute(Realm realm) {
                                    //先查找后得到User对象
                                    SetData Config = realm.where(SetData.class).equalTo("id",1).findFirst();
                                    Config.setNotify(Configs.NotifyStart);
                                }
                            });
                            mHandler.obtainMessage(4, null).sendToTarget();
                        }
                    });
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    {
                        TextView message = (TextView) snackbar.getView().findViewById(R.id.snackbar_text);
                        message.setTextAlignment(View.TEXT_ALIGNMENT_GRAVITY);
                        message.setGravity(Gravity.CENTER);
                    }
                    snackbar.show();


                }
            }
            catch (Exception e)
            {
                addlog("系统错误->" + e.getMessage());
            }
            return false;
        }
    });

    private void initWebSocket() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mSocketClient = new WebSocketClient(new URI(SocketHost), new Draft_10()) {
                        @Override
                        public void onOpen(ServerHandshake handshakedata)
                        {
                            SocketConnect = false;
                            addlog("Socket链接成功");
                            if(HttpStart==true)
                            {
                                addlog("开始唤醒socket");
                                mHandler.obtainMessage(2, null).sendToTarget();
                            }
                            mHandler.obtainMessage(3, null).sendToTarget();
                        }

                        @Override
                        public void onMessage(String message)
                        {
                            show(message);
                            mHandler.obtainMessage(1, message).sendToTarget();
                        }

                        @Override
                        public void onClose(int code, String reason, boolean remote)
                        {
                            SocketConnect = false;
                            if(SocketStart==true)
                            {
                                SocketStart = false;

                                addlog("Socket链接关闭->备用Http通道准备启动");

                                addlog("Http启动指令->已发送");
                                OrderData SendData = new OrderData();
                                SendData.setId(UUID.randomUUID().toString());
                                sendToServer(SendData);

                                mHandler.obtainMessage(4, null).sendToTarget();
                            }
                            else
                            {
                                if(!Configs.Appid.equals("") && !Configs.Security.equals(""))
                                {
                                    addlog("Socket链接关闭");
                                }

                            }

                        }

                        @Override
                        public void onError(Exception ex)
                        {
                            SocketConnect = false;
                            promptDialog.dismiss();
                        }
                    };
                    SocketConnect = true;
                    mSocketClient.connect();

                } catch (URISyntaxException e) {
                    SocketConnect = false;
                    e.printStackTrace();
                }
            }

        }).start();
    }

    private Runnable heartBeatRunnable = new Runnable()
    {
        @Override
        public void run()
        {
            if (System.currentTimeMillis() - sendTime >= HEART_BEAT_RATE)
            {
                try
                {
                    Log.d("picher_log","链接状态："  + mSocketClient.getReadyState().toString());
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("method", "heart");
                    WebSocket.READYSTATE SocketStatus = mSocketClient.getReadyState();
                    if(SocketStatus.equals(WebSocket.READYSTATE.OPEN))
                    {
                        mSocketClient.send(jsonObject.toString());
                        if(HttpStart==true && SocketStart==false)
                        {
                            mHandler.obtainMessage(2, null).sendToTarget();
                        }
                    }
                    else
                    {
                        //判断是否正在连接中
                        if(SocketConnect ==false)
                        {
                            if(SocketStatus.equals(WebSocket.READYSTATE.CLOSED) || SocketStatus.equals(WebSocket.READYSTATE.NOT_YET_CONNECTED))
                            {
                                Log.d("picher_log","发起重连:" + SocketStatus.toString());
                                if(!Configs.Appid.equals("") && !Configs.Security.equals(""))
                                {
                                    addlog("Socket发起重连...");
                                }
                                try
                                {
                                    mSocketClient.close();
                                    //mSocketClient.closeBlocking();
                                    mSocketClient = null;
                                    initWebSocket();
                                }
                                catch (Exception e)
                                {
                                    show("重试连接出错：" + e.getMessage());
                                }

                            }
                            else
                            {
                                Log.d("picher_log","正在连接或关闭");
                            }
                        }

                    }
                }
                catch (JSONException e)
                {
                    Log.d("picher_log","初始化失败，失败原因" + e.getMessage());
                }

                sendTime = System.currentTimeMillis();
            }
            sHandler.postDelayed(this, HEART_BEAT_RATE);//每隔一定的时间，对长连接进行一次心跳检测
        }
    };

    /*
    *启动服务，将设备设置为在线和启动状态
     */
    private void startService()
    {
        show("请求启动服务");
        try
        {
            //如果配置了appid和security
            if(!Configs.Appid.equals("") && !Configs.Security.equals(""))
            {
                //如果websocket链接成功
                if(mSocketClient.getReadyState().equals(WebSocket.READYSTATE.OPEN))
                {
                    promptDialog.showLoading("启动中");
                    mHandler.obtainMessage(2, null).sendToTarget();
                    show("socket启动");
                    addlog("Socket启动指令->已发送");
                }
                else
                {
                    addlog("Socket服务未链接，已启动备用Http通道");
                    addlog("Http启动指令->已发送");
                    OrderData SendData = new OrderData();
                    SendData.setId(UUID.randomUUID().toString());
                    sendToServer(SendData);
                }
            }
            else
            {
                addlog("请配置项目编码（appid）和项目秘钥（secyruty）");
            }
        }
        catch (Exception e)
        {
            show("服务启动失败，失败原因：" + e.getMessage());

            addlog("服务启动失败，失败原因：" + e.getMessage());
            showDialog("服务启动失败，失败原因：" + e.getMessage());
        }

    }


    private void stopService()
    {
        try
        {
            //如果websocket链接成功
            if(mSocketClient.getReadyState().equals(WebSocket.READYSTATE.OPEN) && SocketStart==true)
            {
                show("socket状态是已链接，而且状态是已启动，因此要通知服务器停止服务");
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("method", "stop");
                jsonObject.put("did",Did);
                jsonObject.put("wechat_hook",1);
                jsonObject.put("alipay_hook",1);
                mSocketClient.send(PayUtils.getInstance().makeParams(jsonObject.toString()));
                addlog("Socket停止指令->已发送");
            }
            else
            {
                SocketStart = false; //如果当前Http通道未链接，设置Socket通道状态为停止
                addlog("Sockt通道->已停止");
            }

            addlog("Http通道->已停止");
            HttpStart = false; //停止HTTP备用通道检测服务

            mHandler.obtainMessage(4, null).sendToTarget();

        }
        catch (Exception e)
        {
            addlog("服务停止失败，失败原因：" + e.getMessage());
            showDialog("服务停止失败，失败原因：" + e.getMessage());
        }
    }

    /**
     * 获取权限。。有些手机很坑，明明是READ_PHONE_STATE权限，却问用户是否允许拨打电话，汗。
     */
    private void getPermissions()
    {
        boolean isNotificationManagerEnabled = false;
        //通知栏权限
        String pkgName = getApplicationContext().getPackageName();
        final String flat = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        isNotificationManagerEnabled = true;
                    }
                }
            }
        }

        if(isNotificationManagerEnabled==false)
        {
            startActivity(new Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS));
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        List<String> sa = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            //申请READ_PHONE_STATE权限。。。。
            sa.add(Manifest.permission.READ_PHONE_STATE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            sa.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            sa.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        if (sa.size() < 1) {
            return;
        }
        ActivityCompat.requestPermissions(this, sa.toArray(new String[]{}), 1);
    }

    /**
     * 获取到权限后的回调
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //获取到了权限之后才可以启动xxxx操作。
        for (int i = 0; i < grantResults.length; i++) {
            if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                promptDialog.showWarn("权限设置异常");
                //如果被永久拒绝。。。那只有引导跳权限设置页
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                {
                    if (!shouldShowRequestPermissionRationale(permissions[i])) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName())); // 根据包名打开对应的设置界面
                        startActivity(intent);
                        return;
                    }
                }
                break;
            }
        }
    }

    public static void addrealm(String JsonString)
    {
        try
        {
            LogUtils.show("收到数据插入请求");
            LogUtils.show(JsonString);
            final OrderData MsgData = new Gson().fromJson(JsonString,OrderData.class);
            switch (MsgData.getOrder_type())
            {
                case OrderData.MARKQR:

                    //找到 mark_sell 并更新二维码 更新状态 尝试发送
                    LogUtils.show("收到数据插入请求->MARKQR");

                    if(MsgData.getOrder_status()==1)
                    {
                        //开始检测是否是重复数据
                        //realm.refresh();
                        OrderData CheckIn = realm.where(OrderData.class).equalTo("order_id",MsgData.getOrder_id()).findFirst();
                        if(CheckIn==null)
                        {
                            show("空订单开始插入");
                            try
                            {
                                realm.beginTransaction();
                                OrderData PayOrder = realm.createObject(OrderData.class,MsgData.getId()); // Create a new object  

                                PayOrder.setOrder_status(MsgData.getOrder_status());
                                PayOrder.setUrl(MsgData.getUrl());
                                PayOrder.setMark_sell(MsgData.getMark_sell());
                                PayOrder.setMoney(MsgData.getMoney());
                                PayOrder.setMark_buy(MsgData.getMark_buy());
                                PayOrder.setOrder_type(MsgData.getOrder_type());
                                PayOrder.setTimestamp(MsgData.getTimestamp());
                                PayOrder.setChannel(MsgData.getChannel());
                                PayOrder.setOrder_id(MsgData.getOrder_id());
                                PayOrder.setUptimestamp(String.valueOf(System.currentTimeMillis()));

                                realm.commitTransaction();

                                show("二维码生成任务已插入数据库");

                                markQrCode(MsgData);
                                addlog("生成二维码启动：" + PayOrder.getId());
                            }
                            catch (Exception e)
                            {
                                addlog("生成二维码出错：" + e.getMessage());
                            }
                        }
                        else
                        {
                            show("重复订单，跳过插入");
                        }
                    }
                    else if(MsgData.getOrder_status()==2)
                    {
                        show("收到二维码");
                        final OrderData Order = realm.where(OrderData.class).equalTo("mark_sell", MsgData.getMark_sell()).findFirst();
                        if(Order!=null)
                        {
                            show("找到二维码");
                            try
                            {
                                realm.executeTransaction(new Realm.Transaction() {
                                    @Override
                                    public void execute(Realm realm) {
                                        //先查找后得到User对象
                                        OrderData OrderUpdate = realm.where(OrderData.class).equalTo("id", Order.getId()).findFirst();
                                        if(OrderUpdate!=null)
                                        {
                                            OrderUpdate.setUrl(MsgData.getUrl());
                                            OrderUpdate.setOrder_status(3);
                                            OrderUpdate.setUptimestamp(String.valueOf(System.currentTimeMillis()));

                                            sendToServer(OrderUpdate);
                                        }

                                    }
                                });
                            }
                            catch (Exception e)
                            {
                                addlog("上报二维码出错：" + e.getMessage());
                            }

                        }
                    }

                    break;
                case OrderData.PAYMENT:
                    LogUtils.show("收到数据插入请求->PAYMENT");
                    MsgData.setOrder_status(2); //设置状态为2，因为订单到达后会立即进行一次发送

                    try
                    {
                        OrderData CheckIn = realm.where(OrderData.class).equalTo("id",MsgData.getId()).findFirst();
                        if(CheckIn==null)
                        {
                            realm.beginTransaction();
                            OrderData PayOrder = realm.createObject(OrderData.class,MsgData.getId()); // Create a new object  

                            PayOrder.setOrder_status(MsgData.getOrder_status());
                            PayOrder.setUrl(MsgData.getUrl());
                            PayOrder.setMark_sell(MsgData.getMark_sell());
                            PayOrder.setMoney(MsgData.getMoney());
                            PayOrder.setMark_buy(MsgData.getMark_buy());
                            PayOrder.setOrder_type(MsgData.getOrder_type());
                            PayOrder.setTimestamp(MsgData.getTimestamp());
                            PayOrder.setChannel(MsgData.getChannel());
                            PayOrder.setOrder_id(MsgData.getOrder_id());
                            PayOrder.setUptimestamp(String.valueOf(System.currentTimeMillis()));

                            realm.commitTransaction();
                        }


                        sendToServer(MsgData);
                        addlog("上报支付数据启动：" + MsgData.getId());
                    }
                    catch (Exception e)
                    {
                        addlog("上报支付数据出错：" + e.getMessage());
                    }
                    break;
            }

            OrderData CheckIn = realm.where(OrderData.class).equalTo("id",MsgData.getId()).findFirst();
            if(CheckIn==null)
            {
                show("数据检测发现插入失败");
            }
            else
            {
                show("数据检测发现插入成功：" + CheckIn.getId());
            }
        }
        catch (Exception e)
        {
            addlog("入库错误：" + e.getMessage());
        }

    }

    //发送数据到服务器
    public static void sendToServer(OrderData MsgData)
    {
        LogUtils.show("收到数据发送请求");
        JSONObject jsonObject = new JSONObject();

        if(MsgData.getOrder_type().equals(OrderData.PAYMENT))
        {
            jsonObject.put("method", "orderpay");
            jsonObject.put("id",MsgData.getId());
            jsonObject.put("mark_sell",MsgData.getMark_sell());
            jsonObject.put("mark_buy",MsgData.getMark_buy());
            jsonObject.put("money",MsgData.getMoney());
            jsonObject.put("url",MsgData.getUrl());
            jsonObject.put("channel",MsgData.getChannel());
            jsonObject.put("order_id",MsgData.getOrder_id());
        }
        else if(MsgData.getOrder_type().equals(OrderData.MARKQR))
        {
            jsonObject.put("method", "qrmake");
            jsonObject.put("id",MsgData.getId());
            jsonObject.put("mark_sell",MsgData.getMark_sell());
            jsonObject.put("mark_buy",MsgData.getMark_buy());
            jsonObject.put("money",MsgData.getMoney());
            jsonObject.put("url",MsgData.getUrl());
            jsonObject.put("order_id",MsgData.getOrder_id());
            jsonObject.put("channel",MsgData.getChannel());
        }
        else
        {
            jsonObject.put("deviceid", Configs.DeviceId);
        }
        jsonObject.put("did",Configs.Did);

        String SendJson = PayUtils.getInstance().makeParams(jsonObject.toString());

        if(mSocketClient.getReadyState().equals(WebSocket.READYSTATE.OPEN) && SocketStart==true)
        {
            LogUtils.show("收到数据发送请求->走socket通道");
            mSocketClient.send(SendJson);
        }
        else
        {
            LogUtils.show("收到数据发送请求->走http通道");

            if(MsgData.getOrder_type().equals(OrderData.PAYMENT))
            {
                PayUtils.getInstance().httpToServer("SendPayUrl",SendJson);
                addlog("上报支付数据启动：" + MsgData.getId());
            }
            else if(MsgData.getOrder_type().equals(OrderData.MARKQR))
            {
                PayUtils.getInstance().httpToServer("SendQrUrl",SendJson);
                addlog("上报二维码启动：" + MsgData.getId());
            }
            else
            {
                PayUtils.getInstance().httpToServer("InitUrl",SendJson);
            }
        }
    }

    /**
     * 发送二维码
     * @param MsgData
     */
    public static void markQrCode(OrderData MsgData)
    {
        show("当前二维码生成状态：" + Configs.MakerQrStat);
        show("当前时间：" + System.currentTimeMillis());
        show("上次时间：" + Configs.MakerQrTime);
        show("当前二维码超时状态：" + (System.currentTimeMillis()-Configs.MakerQrTime)/1000);
        if(Configs.MakerQrStat==false || ((System.currentTimeMillis()-Configs.MakerQrTime)/1000>10))
        {
            show("开始生成二维码");
            if(MsgData.getOrder_type().equals(OrderData.MARKQR))
            {
                if(MsgData.getOrder_status()==1)
                {
                    addlog("生成二维码启动：" + MsgData.getId());
                    Configs.MarkCode = MsgData;
                    Configs.MakerQrStat = true;
                    Configs.MakerQrTime = System.currentTimeMillis();
                    if(MsgData.getChannel().equals(OrderData.ALIPAY))
                    {
                        PayUtils.getInstance().creatAlipayQr(mContext,MsgData.getMoney(),MsgData.getMark_sell());
                    }
                    else if(MsgData.getChannel().equals(OrderData.WECHAT))
                    {
                        PayUtils.getInstance().creatWechatQr(mContext,MsgData.getMoney(),MsgData.getMark_sell());
                    }
                }
            }
        }
        else {
            show("有任务在执行，请稍等");
        }

    }

    /**
     * 删除订单
     * @param id
     */
    public static void delOrder(String id)
    {
        show("开始主动删除订单");
        try
        {
            realm.refresh();
            final RealmResults<OrderData> delData = realm.where(OrderData.class).equalTo("id",id).findAll();
            realm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    delData.deleteAllFromRealm();
                }
            });
        }
        catch (Exception e)
        {
            show("订单主动删除失败：" + e.getMessage());
        }
    }

    //定时检测任务
    Runnable QueueTask  = new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                show("我在执行检测任务");
                show(HttpStart.toString());
                show(SocketStart.toString());
                if(SocketStart==true || HttpStart==true)
                {
                    show("我在执行检测任务");
                    //任意一种模式开启了之后就开始定时任务

                    //任务1 检测是否需要有需要处理的数据库队列
                    //从数据库读取所有需要处理的任务
                    Realm ThreadRealm = Realm.getDefaultInstance();
                    ThreadRealm.refresh();

                    ThreadRealm.beginTransaction();

                    final RealmResults<OrderData> Orders = ThreadRealm.where(OrderData.class).findAll();

                    show("发现待处理任务->总量：" + Orders.size());

                    for(OrderData Order:Orders)
                    {
                        show("执行任务检测：");

                        if(((System.currentTimeMillis() - Long.parseLong(Order.getTimestamp()))/60000)>=5)
                        {
                            //超时订单删除
                            try
                            {
                                show("订单超时了，删掉:" + Order.getId());

                                Order.deleteFromRealm();

                                show("订单超时了，删掉-成功:" + Order.getId());
                                addlog("超时订单被删除：" + Order.getId());
                            }
                            catch (Exception e)
                            {
                                show("订单超时了，删掉-失败:" + e.getMessage());
                                e.printStackTrace();
                                //ThreadRealm.cancelTransaction();
                            }
                        }
                        else
                        {
                            //如果订单没有超时就开始处理订单
                            if(Order.getOrder_type().equals(OrderData.PAYMENT))
                            {
                                //开始处理支付订单
                                if(Order.getOrder_status()==1)
                                {
                                    try
                                    {
                                        Order.setOrder_status(2);
                                        Order.setUptimestamp(String.valueOf(System.currentTimeMillis()));
                                        sendToServer(Order);
                                        addlog("上报支付数据启动（补单）：" + Order.getId());
                                    }
                                    catch (Exception e)
                                    {
                                        addlog("上报支付数据出错（补单）：" + Order.getId());
                                    }

                                }
                                else if(Order.getOrder_status()==2)
                                {
                                    //如果订单状态是2，但是已经超过了5妙没有删除那么可能服务端没有反馈，因此重新发送
                                    if(((System.currentTimeMillis() - Long.parseLong(Order.getTimestamp()))/1000)>=5)
                                    {
                                        try
                                        {
                                            Order.setUptimestamp(String.valueOf(System.currentTimeMillis()));
                                            sendToServer(Order);
                                            //addlog("上报支付数据启动（补单）：" + Order.getId());
                                        }
                                        catch (Exception e)
                                        {
                                            addlog("上报数据出错（补单）：" + Order.getId());
                                        }

                                    }
                                }
                            }
                            else if(Order.getOrder_type().equals(OrderData.MARKQR))
                            {
                                //开始处理二维码订单
                                if(Order.getOrder_status()==1)
                                {
                                    try
                                    {
                                        if(((System.currentTimeMillis() - Long.parseLong(Order.getTimestamp()))/1000)>=5)
                                        {
                                            Order.setUptimestamp(String.valueOf(System.currentTimeMillis()));
                                            markQrCode(Order);
                                            addlog("生成二维码启动（补单）：" + Order.getId());
                                        }

                                    }
                                    catch (Exception e)
                                    {
                                        addlog("生成二维码出错（补单）：" + Order.getId());
                                    }

                                }
                                else if(Order.getOrder_status()==5 ||Order.getOrder_status()==3)
                                {
                                    //如果订单状态是2，但是已经超过了3妙没有删除那么可能服务端没有反馈，因此重新发送
                                    if(((System.currentTimeMillis() - Long.parseLong(Order.getTimestamp()))/1000)>=5)
                                    {
                                        try
                                        {
                                            Order.setUptimestamp(String.valueOf(System.currentTimeMillis()));
                                            sendToServer(Order);
                                            addlog("上报二维码启动（补单）：" + Order.getId());
                                        }
                                        catch (Exception e)
                                        {
                                            addlog("上报二维码出错（补单）：" + Order.getId());
                                        }

                                    }
                                }
                            }
                        }
                    }
                    ThreadRealm.commitTransaction();
                    //任务2 从服务器获取需要处理的任务队列
                    if(HttpStart==true && SocketStart==false)
                    {
                        JSONObject MsgData = new JSONObject();
                        MsgData.put("did",Configs.Did);
                        String SendData = MsgData.toJSONString();
                        SendData = PayUtils.getInstance().makeParams(SendData);
                        PayUtils.getInstance().httpToServer("GetQrUrl",SendData);
                    }
                }
                else
                {
                    show("服务没有开启，暂停执行");
                }
                if (flag) {
                    qHandler.postDelayed(this, 1000);
                }
            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
                LogUtils.show("队列任务错误：" + e.getMessage());
            }
        }
    };


    private void toggleNotificationListenerService()
    {
        PackageManager localPackageManager = getPackageManager();
        localPackageManager.setComponentEnabledSetting(new ComponentName(this, QSNotifiService.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
        localPackageManager.setComponentEnabledSetting(new ComponentName(this, QSNotifiService.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
    }

    /**
     * 日志写入方法
     * @param log
     */
    public static void addlog(final String log)
    {
        try{
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mHandler.obtainMessage(0, log).sendToTarget();
                }
            }).start();
        }
        catch (Exception e)
        {
            show("日志写入失败：" + e.getMessage());
        }
    }


    /**
     * 根据scrolview 和子view去测量滑动的位置
     *
     * @param scrollView
     * @param view
     */
    private static void scrollToBottom(final ScrollView scrollView, final View view) {

        sHandler.post(new Runnable() {

            @Override
            public void run() {
                if (scrollView == null || view == null) {
                    return;
                }
                // offset偏移量。是指当textview中内容超出 scrollview的高度，那么超出部分就是偏移量
                int offset = view.getMeasuredHeight()
                        - scrollView.getMeasuredHeight();
                if (offset < 0) {
                    offset = 0;
                }
                //scrollview开始滚动
                scrollView.scrollTo(0, offset);
            }
        });
    }

    /**
     * 通用提示消息框
     * @param text 弹窗提示内容
     */
    private void showDialog(String text)
    {
        promptDialog.showWarnAlert(text, new PromptButton("确定", new PromptButtonListener() {
            @Override
            public void onClick(PromptButton button) {
            }
        }));
    }


    //监听按键 如果是返回键盘app转后台
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        flag = false;
        realm.close(); // Remember to close Realm when done.
    }
}
