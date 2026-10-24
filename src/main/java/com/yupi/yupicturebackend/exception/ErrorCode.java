package com.yupi.yupicturebackend.exception;

import lombok.Getter;

@Getter

//内置的异常类，不带任何的业务逻辑！

public enum ErrorCode {     //这种异常的处理机制代码，并不是我们的业务逻辑代码，不用去管这些东西，写一次后面直接复制就行了。

    SUCCESS(0, "ok"),      // 抛出对应的异常，然后去做对应的处理！
    PARAMS_ERROR(40000, "请求参数错误"),
    NOT_LOGIN_ERROR(40100, "未登录"),
    NO_AUTH_ERROR(40101, "无权限"),
    NOT_FOUND_ERROR(40400, "请求数据不存在"),
    FORBIDDEN_ERROR(40300, "禁止访问"),
    SYSTEM_ERROR(50000, "系统内部异常"),
    OPERATION_ERROR(50001, "操作失败");

    /**
     * 状态码
     */
    private final int code;

    /**
     * 信息
     */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
