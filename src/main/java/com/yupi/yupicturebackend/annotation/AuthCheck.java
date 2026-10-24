package com.yupi.yupicturebackend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)    //运行的时候启动这个注解


//annotation 放的就是我们的注解！

public @interface AuthCheck {

    /**
     * 必须有某个角色
     */
    String mustRole() default "";

}

