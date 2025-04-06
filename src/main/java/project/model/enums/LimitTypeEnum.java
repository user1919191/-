package project.model.enums;

import org.apache.commons.lang3.ObjectUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum LimitTypeEnum {
    REJECT_IP("拒绝IP","REJECT_IP"),
    REJECT_USER("拒绝用户","REJECT_USER"),
    REJECT_ALL("拒绝所有","REJECT_ALL");

    /**
     * 拒绝策略
     */
    private String LimitType;

    /**
     * 拒绝码
     */
    private String LimitTypeCode;

    /**
     * 构造方法
     * @param limitType
     * @param limitTypeCode
     */
    LimitTypeEnum(String limitType, String limitTypeCode) {
        this.LimitType = limitType;
        this.LimitTypeCode = limitTypeCode;
    }
    public List<String> ListRejectTyper()
    {
        return Arrays.stream(values()).map(item ->item.getLimitType()).collect(Collectors.toList());
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value
     * @return
     */
    public static LimitTypeEnum getEnumByValue(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        for (LimitTypeEnum anEnum : LimitTypeEnum.values()) {
            if (anEnum.LimitTypeCode.equals(value)) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 获取拒绝策略
     * @return
     */
    public String getLimitType() {
        return LimitType;
    }

    /**
     * 获取拒绝码
     * @return
     */
    public String getLimitTypeCode() {
        return LimitTypeCode;
    }
}
