package com.yupi.yupicturebackend.common;

import com.yupi.yupicturebackend.exception.ErrorCode;
import lombok.Data;
import java.io.Serializable;

@Data
//响应的封装类，用于前端获取这些信息的！
public class BaseResponse<T> implements Serializable {


    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}

