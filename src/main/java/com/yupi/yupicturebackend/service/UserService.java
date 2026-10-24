package com.yupi.yupicturebackend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.yupi.yupicturebackend.model.dto.user.UserQueryRequest;
import com.yupi.yupicturebackend.model.entity.User;
import com.yupi.yupicturebackend.model.vo.LoginUserVO;
import com.yupi.yupicturebackend.model.vo.UserVO;
import javax.servlet.http.HttpServletRequest;
import java.util.List;



/**
 * 用户服务接口----接口定义了哪些功能，但是不涉及具体的实现，service声明我们的业务功能，后续要实现哪些方法，然后去对应的实现类里面去实现这些方法---serviceimpl（实现类）！
 */
public interface UserService extends IService<User> {


    /**
     * 一、用户注册
     */
    long userRegister(String userAccount, String userPassword, String checkPassword);


    /**
     * 二、用户登录
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);


    /**
     * 三、获取脱敏后的用户登录信息
     */
    LoginUserVO getLoginUserVO(User user);


    /**
     * 获取脱敏后的用户信息
     */
    UserVO getUserVO(User user);


    /**
     * 获得脱敏后的用户信息列表
     *
     * @param userList 用户列表
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);



    /**
     * 获取加密后的密码
     */
    String getEncryptPassword(String userPassword);



    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);



    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);



    /**
     * 获取查询条件
     *
     * @param userQueryRequest 用户查询请求    --------把前端传来的用户查询参数，转换成 MyBatis-Plus能使用的数据库查询条件。
     * @return 查询条件
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);


    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);



}
