package com.yupi.yupicturebackend.model.dto.user;
import lombok.Data;
import java.io.Serializable;

/*
这里放的是用户的请求类--user
封装类，帮助我们去封装请求，用get、set方法来做的----lombok的注解！
*/

/*
dto就是封装了前后端数据传递的包
*/


//用户的注册请求
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    /**
     * 账号
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;
}
