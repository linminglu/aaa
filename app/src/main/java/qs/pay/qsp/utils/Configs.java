package qs.pay.qsp.utils;

public class Configs {
    public static String DomainUrl = "https://qs.sparknets.cn"; //http请求服务器地址
    public static String SocketHost = "ws://103.19.2.172:2346"; //websocket请求地址
    public static String SendPayUrl = "/api/index/sendPaySendList"; //http的发送支付结果的接口
    public static String SendQrUrl = "/api/index/sendQrSendList"; //http的发送二维码的接口
    public static String GetQrUrl = "/api/index/getQrGetList"; //http的获取生成二维码列表的接口
    public static String InitUrl = "/api/index/serverInit"; //http的启动接口

    public static String DeviceId = ""; //设备唯一序列号
    public static String Appid = "";
    public static String Security ="";
    public static String Did = ""; //设备在服务器端的唯一识别码
    public static Integer HEART_BEAT_RATE = 3000; //定时心跳检测的间隔时长
    public static Long sendTime = 0L; //最后一次发送心跳检测的时间
    public static Integer ALIPAY_BILL_TIME = 1200 * 1000; //软件首次启动后，只处理支付最近xxx秒的订单，默认为只处理最近20分钟的订单

    public static String LastTradeNo = ""; //最后一次处理的订单编号

    public static long MakerQrTime = 0; //开始创建二维码的时间（系统首先会判断是否有任务正在运行，如果正在运行就判断开始运行的时间，如果时间超过5ms，认为已经超时，开始进行下一个创建任务）
    public static boolean MakerQrStat = false; //当前是否有创建二维码任务正在进行（二维码创建任务发起时会修改此状态）

    public static OrderData MarkCode = null; //正在进行中的生成二维码的任务信息

    public static Boolean SocketStart = false;
    public static Boolean HttpStart = false;

    public static String NotifyStart = "true"; //是否开启通知栏监控模式，建议在VXP关闭此模式，仅使用金额固码或不方便使用VXP的用户可以使用开启此模式的版本
}
