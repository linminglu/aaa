package qs.pay.qsp.utils;

import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class SetData extends RealmObject {
    /**
     * 索引主键
     */
    @PrimaryKey
    private Integer id;

    /**
     * 设备唯一识别编码
     */
    private String device_id;

    /**
     * 项目编码
     */
    private String appid;

    /**
     * 项目秘钥
     */
    private String security;

    private String notify;

    public Integer getId() {
        return id == null ? 0 : id;
    }
    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getAppid()
    {
        return appid == null ? "" : appid;
    }

    public String getDevice_id()
    {
        return  device_id == null ? "" : device_id;
    }

    public void setDevice_id(String device_id)
    {
        this.device_id = device_id;
    }

    public void setAppid(String appid)
    {
        this.appid = appid;
    }

    public String getSecurity()
    {
        return security ==null ? "" : security;
    }

    public void setSecurity(String security)
    {
        this.security = security;
    }

    public String getNotify()
    {
        return  notify == null ? "false" : notify;
    }

    public void setNotify(String notify)
    {
        this.notify = notify;
    }
}
