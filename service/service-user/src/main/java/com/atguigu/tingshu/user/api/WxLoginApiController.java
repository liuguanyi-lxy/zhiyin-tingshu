package com.atguigu.tingshu.user.api;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "微信授权登录接口")
@RestController
@RequestMapping("/api/user/wxLogin")
@Slf4j
//【修复#8】移除类级 @Transactional：事务应收敛在服务层数据库写方法上；
// controller 挂事务会把 code2Session 的 HTTP 调用圈进事务，长时间占用数据库连接
public class WxLoginApiController {

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private WxMaService wxMaService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

    //code是前端请求微信接口服务器后的到的，然后传递给后端服务，后端服务再根据微信公众平台id + 秘钥请求微信服务器接口，返回openid
    @GetMapping("/wxLogin/{code}")
    public Result wxLogin(@PathVariable("code") String code) throws WxErrorException {
        //1 拿着code + 微信公众平台id + 秘钥 通过wxMaService请求微信服务器接口，返回openid
        WxMaJscode2SessionResult sessionInfo = wxMaService.getUserService().getSessionInfo(code);
        String openid = sessionInfo.getOpenid();

        //2 根据openid查询user_info表判断是否第一次登录
        LambdaQueryWrapper<UserInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserInfo::getWxOpenId,openid);
        UserInfo userInfo = userInfoService.getOne(wrapper);

        //如果第一次登录，向user数据库添加用户信息，发送mq消息在account数据库中初始化账户
        if (userInfo == null) {
            //添加用户信息
            userInfo = new UserInfo();
            //  赋值用户昵称
            userInfo.setNickname("听友"+System.currentTimeMillis());
            //  赋值用户头像图片
            userInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
            //openid
            userInfo.setWxOpenId(openid);
            //用户信息的添加和账户的初始化
            userInfoService.saveUserAndInitAccount(userInfo);
//            //调用方法添加
//            userInfoService.save(userInfo);
//
//            //发送mq消息给service-account微服务异步初始化账户
//            rabbitService.sendMessage(MqConst.EXCHANGE_USER, MqConst.ROUTING_USER_REGISTER, userInfo.getId());
        }

        //3 登录后需要生成token和获取用户信息，然后把数据放到redis里面，缓存用户登录信息
        // redis的key是token，value是用户信息，设置redis过期时间
        String token = UUID.randomUUID().toString().replaceAll("-","");
        //数据放到redis里面，
        redisTemplate.opsForValue().set(token, userInfo, 30, TimeUnit.MINUTES);

        //4 返回token给前端，用于前端向后端服务器请求接口时验证用户登录状态
        HashMap<String, Object> map = new HashMap<>();
        map.put("token",token);
        //  返回数据
        return Result.ok(map);
    }
    /**
     * 根据用户Id获取到用户数据
     * @return
     */
    //
    @LoginVerification
    @Operation(summary = "获取登录信息")
    @GetMapping("getUserInfo")
    public Result getUserInfo(){
        //  获取到用户Id
        Long userId = AuthContextHolder.getUserId();
        //  调用服务层方法
        UserInfoVo userInfoVo = userInfoService.getUserInfoVoByUserId(userId);
        //  返回数据
        return Result.ok(userInfoVo);
    }
    /**
     * 更新用户信息
     * @param userInfoVo
     * @return
     */
    @LoginVerification
    @Operation(summary = "更新用户信息")
    @PostMapping("updateUser")
    public Result updateUser(@RequestBody UserInfoVo userInfoVo){
        //  从本地线程中获取到用户Id
        Long userId = AuthContextHolder.getUserId();
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        userInfo.setNickname(userInfoVo.getNickname());
        userInfo.setAvatarUrl(userInfoVo.getAvatarUrl());

        //  执行更新方法
        userInfoService.updateById(userInfo);
        return Result.ok();
    }
}