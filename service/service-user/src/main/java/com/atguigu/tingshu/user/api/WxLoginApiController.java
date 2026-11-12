package com.atguigu.tingshu.user.api;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.util.MD5;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
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

    /** H5 开发登录开关：默认关闭，本地/内网开发环境通过配置或环境变量开启 */
    @Value("${tingshu.h5.dev-login:false}")
    private boolean h5DevLoginEnabled;

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
     * H5 网页端开发登录：网页没有 wx.login，无法换取 code 走微信登录。
     * 按 userId 直接签发与 wxLogin 同构的 token（Redis 存储、30 分钟过期），
     * 供 H5 前端（tingshu-web）联调使用。
     *
     * <p>安全门禁：受 tingshu.h5.dev-login 属性控制，默认关闭；仅在本地/内网
     * 开发环境开启（容器部署通过环境变量注入），生产环境与公网部署必须保持关闭。</p>
     */
    @Operation(summary = "H5开发登录（属性开关门禁，默认关闭）")
    @GetMapping("/devLogin/{userId}")
    public Result devLogin(@PathVariable("userId") Long userId) {
        if (!h5DevLoginEnabled) {
            return Result.fail().message("H5 dev login is disabled");
        }
        UserInfo userInfo = userInfoService.getById(userId);
        if (userInfo == null) {
            return Result.fail().message("用户不存在");
        }
        // 与 wxLogin 相同的 token 签发方式：UUID 作为 key，用户信息作为 value，30 分钟过期
        String token = UUID.randomUUID().toString().replaceAll("-","");
        redisTemplate.opsForValue().set(token, userInfo, 30, TimeUnit.MINUTES);
        HashMap<String, Object> map = new HashMap<>();
        map.put("token",token);
        return Result.ok(map);
    }

    /**
     * H5 手机号+密码登录（多端登录体系：小程序微信登录 / H5 密码登录，共用 token 体系与 user 表）。
     * 密码使用 MD5 摘要存储（项目既有约定；生产环境建议 BCrypt，已记录到面试话术）。
     */
    @Operation(summary = "H5 手机号密码登录")
    @PostMapping("/phoneLogin")
    public Result phoneLogin(@RequestBody Map<String, String> params) {
        String phone = params.get("phone");
        String password = params.get("password");
        if (phone == null || phone.isBlank() || password == null || password.isBlank()) {
            return Result.fail().message("手机号和密码不能为空");
        }
        UserInfo userInfo = userInfoService.getOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getPhone, phone));
        if (userInfo == null) {
            return Result.fail().message("账号不存在，请先注册");
        }
        if (!MD5.encrypt(password).equals(userInfo.getPassword())) {
            return Result.fail().message("密码错误");
        }
        String token = UUID.randomUUID().toString().replaceAll("-","");
        redisTemplate.opsForValue().set(token, userInfo, 30, TimeUnit.MINUTES);
        HashMap<String, Object> map = new HashMap<>();
        map.put("token", token);
        return Result.ok(map);
    }

    /**
     * H5 注册：手机号+密码+昵称。与微信注册同链路（saveUserAndInitAccount 含 Feign 账户初始化），
     * 注册成功直接签发 token（省一次登录请求）。
     */
    @Operation(summary = "H5 注册")
    @PostMapping("/register")
    public Result register(@RequestBody Map<String, String> params) {
        String phone = params.get("phone");
        String password = params.get("password");
        String nickname = params.get("nickname");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            return Result.fail().message("手机号格式不正确");
        }
        if (password == null || password.length() < 6) {
            return Result.fail().message("密码至少 6 位");
        }
        UserInfo exists = userInfoService.getOne(new LambdaQueryWrapper<UserInfo>().eq(UserInfo::getPhone, phone));
        if (exists != null) {
            return Result.fail().message("该手机号已注册");
        }
        UserInfo userInfo = new UserInfo();
        userInfo.setPhone(phone);
        userInfo.setPassword(MD5.encrypt(password));
        // wx_open_id 为 NOT NULL + 唯一索引且默认空串，H5 账户没有微信身份。
        // 用保留前缀写入稳定占位值，避免第二个 H5 账户撞空串唯一键；
        // 微信登录只按真实 openid 查询，不会命中此前缀。
        userInfo.setWxOpenId("h5_" + phone);
        userInfo.setNickname(nickname == null || nickname.isBlank() ? "听友" + System.currentTimeMillis() : nickname);
        userInfo.setAvatarUrl("https://oss.aliyuncs.com/aliyun_id_photo_bucket/default_handsome.jpg");
        userInfoService.saveUserAndInitAccount(userInfo);

        String token = UUID.randomUUID().toString().replaceAll("-","");
        redisTemplate.opsForValue().set(token, userInfo, 30, TimeUnit.MINUTES);
        HashMap<String, Object> map = new HashMap<>();
        map.put("token", token);
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