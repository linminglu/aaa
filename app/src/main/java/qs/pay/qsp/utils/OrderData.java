package qs.pay.qsp.utils;

import com.alibaba.fastjson.JSON;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class OrderData extends RealmObject {
    //以后自己可以添加更多支付方式，没必要用枚举
    public static final String WECHAT = "wechat";
    public static final String ALIPAY = "alipay";

    public static final String PAYMENT = "payment";
    public static final String MARKQR = "markqr";

    //二维码任务类型

    /**
     * 索引主键,使用uuid
     */
    @PrimaryKey
    private String id;

    /**
     * 任务类型
     */
    private String order_type;

    /**
     * 渠道类型
     */
    private String channel;//wechat,alipay

    /**
     * 二维码的金额,单位为分
     */
    private Integer money;

    /**
     * 此而二维码的链接
     */
    private String url;

    /**
     * 二维码的收款方备注
     */
    private String mark_sell;

    /**
     * 二维码的付款方备注
     */
    private String mark_buy;

    /**
     * 订单id
     */
    private String order_id;

    /**
     * 订单添加时间戳
     */
    private String  timestamp;

    /**
     * 订单更新时间戳
     */
    private String  uptimestamp;

    /**
     * 数据状态
     */
    private Integer order_status;
    //支付订单状态 1=加入未发送 2=已发送未反馈 （收到反馈后就清除数据了 因此没有状态3）
    //生成订单状态 1=加入未生成 2=生成未发送 3=发送未反馈 （收到反馈后就清除数据了 因此没有状态4）

    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrder_type() {
        return order_type == null ? "" : order_type;
    }

    public void setOrder_type(String order_type) {
        this.order_type = order_type;
    }

    public String getChannel() {
        return channel == null ? "" : channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getMoney() {
        return money == null ? 0 : money;
    }

    public void setMoney(Integer money) {
        this.money = money;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMark_sell() {
        return mark_sell == null ? "" : mark_sell;
    }

    public void setMark_sell(String mark_sell) {
        this.mark_sell = mark_sell;
    }

    public String getMark_buy() {
        return mark_buy == null ? "" : mark_buy;
    }

    public void setMark_buy(String mark_buy) {
        this.mark_buy = mark_buy;
    }

    public String getOrder_id() {
        return order_id == null ? "" : order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public String getTimestamp()
    {
        return timestamp == null ? "0" : timestamp;
    }

    public void setTimestamp(String timestamp)
    {
        this.timestamp = timestamp;
    }

    public String getUptimestamp()
    {
        return uptimestamp == null ? "0" : uptimestamp;
    }

    public void setUptimestamp(String uptimestamp)
    {
        this.uptimestamp = uptimestamp;
    }
    public Integer getOrder_status()
    {
        return order_status == null? 0 : order_status;
    }

    public void setOrder_status(Integer order_status)
    {
        this.order_status = order_status;
    }

}
