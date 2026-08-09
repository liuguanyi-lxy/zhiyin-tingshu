package com.atguigu.tingshu.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.account.client.UserAccountFeignClient;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.order.OrderDerate;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.order.helper.SignHelper;
import com.atguigu.tingshu.order.mapper.OrderDerateMapper;
import com.atguigu.tingshu.order.mapper.OrderDetailMapper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.user.client.VipServiceConfigFeignClient;
import com.atguigu.tingshu.vo.account.AccountLockVo;
import com.atguigu.tingshu.vo.order.OrderDerateVo;
import com.atguigu.tingshu.vo.order.OrderDetailVo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import com.atguigu.tingshu.model.order.OrderDetail;
@Slf4j
@Service
@SuppressWarnings({"all"})
public class OrderInfoServiceImpl extends ServiceImpl<OrderInfoMapper, OrderInfo> implements OrderInfoService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private UserInfoFeignClient userInfoFeignClient;
    @Autowired
    private AlbumInfoFeignClient albumInfoFeignClient;
    @Autowired
    private VipServiceConfigFeignClient vipServiceConfigFeignClient;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private TrackInfoFeignClient trackInfoFeignClient;
    @Autowired
    private UserAccountFeignClient userAccountFeignClient;
    @Autowired
    private OrderDerateMapper orderDerateMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private RedissonClient redissonClient;

    //根据订单编号修改订单状态（已经支付），远程调用添加用户购买记录
    @Override
    public void updateOrderSuccess(String orderNo) {
        //根据订单编号修改订单状态（已经支付）
        //根据订单编号查询订单对象
        OrderInfo orderInfo = this.getOrderInfoByOrderNo(orderNo);
        //订单状态是未支付修改
        if (orderInfo.getOrderStatus().equals(SystemConstant.ORDER_STATUS_UNPAID)) {
            //订单对象设置订单状态值为已支付
            //SystemConstant.ORDER_STATUS_PAID
            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_PAID);
            //调用方法进行修改
            orderInfoMapper.updateById(orderInfo);
        }

        //远程调用添加用户购买记录
        UserPaidRecordVo userPaidRecordVo = new UserPaidRecordVo();
        userPaidRecordVo.setOrderNo(orderNo);
        userPaidRecordVo.setUserId(orderInfo.getUserId());
        userPaidRecordVo.setItemType(orderInfo.getItemType());
        List<Long> itemIdList = orderInfo.getOrderDetailList()
                .stream()
                .map(OrderDetail::getItemId)
                .collect(Collectors.toList());
        userPaidRecordVo.setItemIdList(itemIdList);
        //远程调用用户微服务，添加用户购买记录
        userInfoFeignClient.savePaidRecord(userPaidRecordVo);
    }

    //进入用户主页获取用户订单列表，分页查询
    @Override
    public IPage<OrderInfo> findUserPage(Page<OrderInfo> pageParam, Long userId) {
        //  调用mapper 层方法
        IPage<OrderInfo> infoIPage = orderInfoMapper.selectUserPage(pageParam,userId);
        infoIPage.getRecords().forEach(item->{
            //  设置状态名
            item.setOrderStatusName(getOrderStatusName(item.getOrderStatus()));
            item.setPayWayName(getPayWayName(item.getPayWay()));
        });
        return infoIPage;
    }


    //取消订单
    //调用方法，根据orderId取消订单
    @Override
    public void cancelOrder(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        //30分钟到了之后订单还是处于未支付状态就将订单的状态更改为已取消
        if(orderInfo.getOrderStatus().equals(SystemConstant.ORDER_STATUS_UNPAID)) {
            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_CANCEL);
            orderInfoMapper.updateById(orderInfo);
        }
    }

    //立即结算后进入支付界面选择支付方式后提交订单
    @GlobalTransactional
    @Override
    public String submitOrder(OrderInfoVo orderInfoVo, Long userId) {
        //1.从结算界面获取订单签名sign，将orderInfoVo转为map，通过SignHelper工具类将map转为字符串，与结算界面获取的sign进行比较
        //如果一样则没有被篡改，不一样则被篡改
        String jsonString = JSON.toJSONString(orderInfoVo);
        Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
        //因为结算界面给订单默认添加的是微信支付，所以我们这里也要设置为微信支付
        map.put("payWay", SystemConstant.ORDER_PAY_WAY_WEIXIN);
        //使用SignHelper工具类对map进行校验,有问题则会抛出异常
        SignHelper.checkSign(map);
        //2.判断订单信息是否被重复提交
        //获取传入的tradeNo
        String tradeNo = orderInfoVo.getTradeNo();
        //根据前缀和userid从Redis中获取对应的流水号与传入的订单信息中的流水号进行比较
        //相同则说明订单第一次提交，然后删除该缓存数据，查询不到或者不相同则说明重复提交，不进行处理
        //为了保证比较和删除缓存数据操作能够连续执行不被打断就使用lua脚本，保障操作原子性

        String key=RedisConstant.USER_ORDER_KEY_PREFIX+userId;
        //创建默认的Redis执行脚本对象，返回类型为Boolean，判断订单是否重复提交
        DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
        //设置脚本内容,get是Redis操作，KEYS[1]和ARGV[1]事第一个和第二个参数
        String script="if(redis.call('get', KEYS[1]) == ARGV[1]) " +
                " then return redis.call('del', KEYS[1]) " +
                " else return 0 " +
                " end";
        //给Redis脚本对象设置脚本内容
        redisScript.setScriptText(script);
        //设置脚本执行后的返回类型
        redisScript.setResultType(Boolean.class);
        //使用RedisTemplate执行脚本，传入Redis脚本对象和参数
        Boolean isSame = (Boolean)redisTemplate.execute(redisScript, Collections.singletonList(key), tradeNo);
        //如果订单重复提交则抛出异常
        if (!isSame) {
            throw new GuiguException(ResultCodeEnum.REPEAT_BUY_ERROR);
        }
        //判断支付方式
        //生成订单编号
        String orderNo = UUID.randomUUID().toString().replaceAll("-", "");
        //1101-微信 1102-支付宝 1103-账户余额
        String payWay = orderInfoVo.getPayWay();
        if (payWay.equals(SystemConstant.ORDER_PAY_WAY_WEIXIN)) {
            //微信支付
            //  生成订单，未支付状态
            OrderInfo orderInfo =this.generateOrder(orderInfoVo,userId,orderNo);
        }else if( payWay.equals(SystemConstant.ORDER_PAY_ACCOUNT)){
            //余额支付
            //1.远程调用account微服务接口，判断账户余额是否充足,充足就扣减余额，生成订单，在用户、账户、订单三个数据库添加对应数据,账户消费记录
            //不充足则抛出异常
            //构建传递给account微服务的实体类
            AccountLockVo accountLockVo = new AccountLockVo();
            accountLockVo.setOrderNo(orderNo);
            accountLockVo.setUserId(userId);
            accountLockVo.setAmount(orderInfoVo.getOrderAmount());
            accountLockVo.setContent(orderInfoVo.getOrderDetailVoList().get(0).getItemName());
            //远程调用account微服务接口,根据返回结果的状态码判断是否余额充足,不充足则抛出异常,并且对账户数据库的相关数据进行添加修改
            Result result = userAccountFeignClient.checkAndDeduct(accountLockVo);
            if(result.getCode().equals(ResultCodeEnum.ACCOUNT_LESS.getCode())){
                throw new GuiguException(ResultCodeEnum.ACCOUNT_LESS);
            }

            //2.检查和扣减余额成功后生成订单，在order微服务中对order数据库添加对应的记录，订单表、订单详情表、订单减免表
            OrderInfo orderInfo =this.generateOrder(orderInfoVo,userId,orderNo);

            //3.远程调用用户微服务接口，用户信息表、用户购买专辑表、用户购买声音表可能添加记录
            //创建用户购买记录对象
            UserPaidRecordVo userPaidRecordVo = new UserPaidRecordVo();
            userPaidRecordVo.setUserId(userId);
            userPaidRecordVo.setOrderNo(orderNo);
            userPaidRecordVo.setItemType(orderInfoVo.getItemType());
            //获取订单中每件商品的ID列表
            List<Long> itemIdList = orderInfoVo.getOrderDetailVoList()
                    .stream()
                    .map(OrderDetailVo::getItemId)
                    .collect(Collectors.toList());
            userPaidRecordVo.setItemIdList(itemIdList);
            //远程调用用户微服务接口，用户信息表、用户购买专辑表、用户购买声音表可能添加记录
            Result savePaidRecordResult = userInfoFeignClient.savePaidRecord(userPaidRecordVo);
            if(savePaidRecordResult.getCode().equals(ResultCodeEnum.USER_INFO_UPDATE_ERROR.getCode())){
                throw new GuiguException(ResultCodeEnum.USER_INFO_UPDATE_ERROR);
            }
        }
        //生成订单后，关于账户、用户、订单三个数据库的数据添加和修改完成，修改订单支付状态为已支付
        return orderNo;
    }
    //生成订单，未支付状态,向order数据库三个表添加数据
    private OrderInfo generateOrder(OrderInfoVo orderInfoVo, Long userId, String orderNo) {
        //1.order_info: 订单基本信息
        OrderInfo orderInfo = new OrderInfo();
        BeanUtils.copyProperties(orderInfoVo, orderInfo);
        orderInfo.setOrderNo(orderNo);//订单编号
        //将订单第一个商品的名称作为订单名称
        List<OrderDetailVo> orderDetailVoList = orderInfoVo.getOrderDetailVoList();
        String itemName = orderDetailVoList.get(0).getItemName();
        orderInfo.setOrderTitle(itemName);
        //userId
        orderInfo.setUserId(userId);
        //订单状态 0901-未支付 0902-已支付 0903-已取消,初始订单状态是未支付
        orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_UNPAID);
        //调用方法
        orderInfoMapper.insert(orderInfo);

        //2.order_detail：订单明细
        if (!CollectionUtils.isEmpty(orderInfoVo.getOrderDetailVoList())) {
            orderInfoVo.getOrderDetailVoList().forEach(orderDetailVo -> {
                OrderDetail orderDetail = new OrderDetail();
                BeanUtils.copyProperties(orderDetailVo, orderDetail);
                orderDetail.setOrderId(orderInfo.getId());
                orderDetailMapper.insert(orderDetail);
            });
        }

        //3.order_derate：优惠明细
        if (!CollectionUtils.isEmpty(orderInfoVo.getOrderDerateVoList())) {
            orderInfoVo.getOrderDerateVoList().forEach(orderDerateVo -> {
                OrderDerate orderDerate = new OrderDerate();
                BeanUtils.copyProperties(orderDerateVo, orderDerate);
                orderDerate.setOrderId(orderInfo.getId());
                orderDerateMapper.insert(orderDerate);
            });
        }

        //4.微信支付，发送延迟消息，30分钟内未完成支付就会取消订单
        //1101-微信 1102-支付宝 1103-账户余额"
        String payWay = orderInfoVo.getPayWay();
        if(SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payWay)) {
            //发送延迟消息，30分钟不支付订单自动取消
            Long orderId = orderInfo.getId();
            this.sendDelayMessage(orderId);
        }//余额支付
        else if(SystemConstant.ORDER_PAY_ACCOUNT.equals(payWay))
        {
            //余额支付成功，订单信息保存完毕，修改订单状态为已支付
            this.orderPaySuccess(orderNo);
        }
        return orderInfo;
    }
    //根据订单编号修改订单状态
    public void orderPaySuccess(String orderNo) {
        OrderInfo orderInfo = this.getOrderInfoByOrderNo(orderNo);
        //  判断当前订单状态为未支付，就将订单状态改为已支付。
        if (orderInfo.getOrderStatus().equals(SystemConstant.ORDER_STATUS_UNPAID)) {
            //  赋值：
            orderInfo.setOrderStatus(SystemConstant.ORDER_STATUS_PAID);
            //  修改订单状态.
            this.orderInfoMapper.updateById(orderInfo);
        }
    }

    /**
     * 根据订单orderNo 获取订单对象,附带订单明细列表
     * @param orderNo
     * @return
     */
    public OrderInfo getOrderInfoByOrderNo(String orderNo) {
        //根据订单编号查询order_info表对应订单基本信息
        LambdaQueryWrapper<OrderInfo> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(OrderInfo::getOrderNo, orderNo);
        OrderInfo orderInfo = orderInfoMapper.selectOne(queryWrapper);

        //根据订单id查询订单明细数据，比如购买声音的记录
        Long orderId = orderInfo.getId();
        LambdaQueryWrapper<OrderDetail> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderDetail::getOrderId, orderId);
        List<OrderDetail> orderDetails = orderDetailMapper.selectList(wrapper);

        //设置订单明细到订单对象里面
        orderInfo.setOrderDetailList(orderDetails);
        //设置订单的支付方式名称
        orderInfo.setPayWayName(this.getPayWayName(orderInfo.getPayWay()));

        return orderInfo;
    }

    /**
     * 根据payWay 返回支付方式名称
     * @param payWay
     * @return
     */
    private String getPayWayName(String payWay) {
        //  声明一个对象
        return SystemConstant.ORDER_PAY_WAY_WEIXIN.equals(payWay)?"微信支付":"余额支付";
    }

    /**
     * 根据订单状态获取到订单状态名称
     * @param orderStatus
     * @return
     */
    private String getOrderStatusName(String orderStatus) {
        //  声明订单状态名称
        String orderStatusName = "";
        //  判断
        if (SystemConstant.ORDER_STATUS_UNPAID.equals(orderStatus)){
            orderStatusName = "未支付";
        } else if (SystemConstant.ORDER_STATUS_PAID.equals(orderStatus)) {
            orderStatusName = "已支付";
        } else {
            orderStatusName = "已取消";
        }
        //  返回
        return orderStatusName;
    }
    //延迟队列实现30分钟未完成支付取消订单，消息发送到延迟队列，延迟30分钟后被消费端接收，如果此时订单状态还是未支付就会取消订单，更改订单状态
    private void sendDelayMessage(Long orderId) {
        //1.使用redissonClient创建阻塞队列
        RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(MqConst.EXCHANGE_CANCEL_ORDER);
        //2.把阻塞队列变成延迟队列
        RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        //3.向延迟队列添加消息，设置消息过期时间
        delayedQueue.offer(orderId.toString(), 30, TimeUnit.MINUTES);
    }

    //根据传入的购买商品信息和用户ID封装对应的订单信息
    /*思路：

    1. 专辑订单
       1. 判断用户是否购买过专辑
          1. 如果已购买抛出异常
          2. 未购买{要给订单明细，减免金额，总金额等属性赋值}
             1. 判断用户是否属于vip
                1. vip  计算订单总价与折扣价 赋值订单明细与减免明细
                2. 非vip 计算订单总价与折扣价 赋值订单明细与减免明细
    2. vip 订单
       1. 根据Id 获取到vip 配置信息
       2. 赋值原始金额，减免金额，总金额，订单明细，减免金额
    3. 生成一个流水号存储到缓存，防止用户重复提交订单
    4. 给OrderInfoVo 实体类赋值
       1. 防止用户非法操作订单金额，将订单对象OrderInfoVo变为字符串，在转换为map。在通过工具类SignHelper将map 变为字符串赋值给签名字段
    5. 最后返回OrderInfoVo 对象*/
    @Override
    public OrderInfoVo trade(TradeVo tradeVo, Long userId) {
        //1.根据用户id远程调用接口获取用户信息
        Result<UserInfoVo> result = userInfoFeignClient.getUserInfoVo(userId);
        UserInfoVo userInfoVo = result.getData();
        //2.设定订单初始数据
        //  订单原始金额
        BigDecimal originalAmount = new BigDecimal("0.00");
        //  减免总金额
        BigDecimal derateAmount = new BigDecimal("0.00");
        //  订单总价
        BigDecimal orderAmount = new BigDecimal("0.00");
        //  订单明细集合
        List<OrderDetailVo> orderDetailVoList = new ArrayList<>();
        //  订单减免明细列表
        List<OrderDerateVo> orderDerateVoList = new ArrayList<>();
        //3.根据itemType获取购买商品类型
        String itemType = tradeVo.getItemType();
        //1001-专辑，1002-声音，1003-vip会员
        //购买专辑
        if (itemType.equals(SystemConstant.ORDER_ITEM_TYPE_ALBUM)) {
            //远程调用接口，判断用户是否已经购买过专辑
            Result<Boolean> booleanResult = userInfoFeignClient.isPaidAlbum(tradeVo.getItemId());
            Boolean isPaid = booleanResult.getData();
            if (isPaid) {
                throw new GuiguException(300, "您已经购买过该专辑");
            }
            //根据专辑ID获取专辑信息，tradeVo.getItemId() 获取到的是专辑ID
            Result<AlbumInfo> albumInfoResult = albumInfoFeignClient.getAlbumInfo(tradeVo.getItemId());
            AlbumInfo albumInfo = albumInfoResult.getData();
            Assert.notNull(albumInfo, "专辑不存在");
            //判断用户是否属于vip,是vip则购买专辑按照vip折扣vip_discount，否则使用普通折扣discount
            Integer isVip = userInfoVo.getIsVip();
            //记录不同情况下的折扣
            BigDecimal discount = new BigDecimal("0.00");
            //用户是vip的情况，并且vip没有过期
            if (isVip.equals(SystemConstant.USER_IS_VIP) && userInfoVo.getVipExpireTime().after(new Date())) {
                discount = albumInfo.getVipDiscount();
            } else {//用户不是vip的情况,使用普通折扣
                discount = albumInfo.getDiscount();
            }
            //计算订单总价
            //原始金额
            originalAmount = albumInfo.getPrice();
            //优惠金额,原始价格*(10-折扣)/10,四舍五入向上取整
            //判断折扣不为-1，也就是有折扣才计算减免金额
            if(discount.intValue()!=-1)
            {
                derateAmount = originalAmount
                        .multiply(new BigDecimal("10")
                                .subtract(discount))
                        .divide(new BigDecimal("10"), 2, BigDecimal.ROUND_HALF_UP);
            }else{//该专辑没有折扣，减免金额为0
                discount.valueOf(0);
            }
            //最终金额
            orderAmount = originalAmount.subtract(derateAmount);
            //封装订单的其他数据
            //订单中商品信息明细列表
            OrderDetailVo orderDetailVo = new OrderDetailVo();
            orderDetailVo.setItemId(tradeVo.getItemId());
            orderDetailVo.setItemName(albumInfo.getAlbumTitle());
            orderDetailVo.setItemUrl(albumInfo.getCoverUrl());
            orderDetailVo.setItemPrice(orderAmount);
            orderDetailVoList.add(orderDetailVo);

            //订单减免明细列表
            //原始金额 - 最终金额 !=0
            if (originalAmount.subtract(orderAmount).doubleValue() != 0) {
                //记录商品折扣减免信息
                OrderDerateVo orderDerateVo = new OrderDerateVo();
                //设置为专辑折扣类型
                orderDerateVo.setDerateType(SystemConstant.ORDER_DERATE_ALBUM_DISCOUNT);
                //设置专辑折扣金额
                orderDerateVo.setDerateAmount(derateAmount);
                //设置折扣备注
                orderDerateVo.setRemarks("专辑折扣");
                orderDerateVoList.add(orderDerateVo);
            }
        } else if (itemType.equals(SystemConstant.ORDER_ITEM_TYPE_VIP)) {//购买vip
            log.info("尝试获取VIP配置，itemId: {}", tradeVo.getItemId());
            //根据tradeVo里面的itemId获取vip配置信息
            Result<VipServiceConfig> configResult = vipServiceConfigFeignClient.getVipServiceConfig(tradeVo.getItemId());
            log.info("获取VIP配置结果: {}", configResult);
            VipServiceConfig vipServiceConfig = configResult.getData();
            Assert.notNull(vipServiceConfig, "vip配置不存在");
            //计算金额
            //vip原始金额
            originalAmount = vipServiceConfig.getPrice();
            //vip最终金额
            orderAmount = vipServiceConfig.getDiscountPrice();
            //vip优惠金额
            derateAmount = originalAmount.subtract(orderAmount);
            //封装订单的其他数据
            //订单明细
            OrderDetailVo orderDetailVo = new OrderDetailVo();
            //设置订单vipID
            orderDetailVo.setItemId(tradeVo.getItemId());
            //设置订单商品名称
            orderDetailVo.setItemName("VIP会员"+vipServiceConfig.getName());
            //设置订单商品图片
            orderDetailVo.setItemUrl(vipServiceConfig.getImageUrl());
            //设置订单商品价格
            orderDetailVo.setItemPrice(orderAmount);
            orderDetailVoList.add(orderDetailVo);

            //添加订单减免
            if (originalAmount.subtract(orderAmount).doubleValue() != 0) {
                OrderDerateVo orderDerateVo = new OrderDerateVo();
                //设置订单折扣类型为vip折扣
                orderDerateVo.setDerateType(SystemConstant.ORDER_DERATE_VIP_SERVICE_DISCOUNT);
                //设置订单减免金额
                orderDerateVo.setDerateAmount(derateAmount);
                //设置订单折扣备注
                orderDerateVo.setRemarks("vip折扣");
                orderDerateVoList.add(orderDerateVo);
            }
            //购买声音
        }else if(itemType.equals(SystemConstant.ORDER_ITEM_TYPE_TRACK)){
            if(tradeVo.getTrackCount()<0)
            {
                throw new GuiguException(ResultCodeEnum.DATA_ERROR);
            }
            //远程调用，根据本集声音Id+trackCount(本集之后选择购买声音集数)获取可购买声音列表数据
            Result<List<TrackInfo>> trackInfoListResult=trackInfoFeignClient.findPaidTrackInfoList(tradeVo.getItemId(),tradeVo.getTrackCount());
            List<TrackInfo> trackInfoList = trackInfoListResult.getData();
            //因为声音单价在专辑信息中，所以根据声音对象获取专辑ID再查询专辑对象
            Result<AlbumInfo> albumInfoResult = albumInfoFeignClient.getAlbumInfo(trackInfoList.get(0).getAlbumId());
            AlbumInfo albumInfo = albumInfoResult.getData();
            Assert.notNull(albumInfo, "专辑信息不存在");
            //计算金额
            //原始金额
            //trackCount=0就是购买本集，金额为声音单价，否则就是购买多个声音，金额为单价*数量
            originalAmount = tradeVo.getTrackCount()==0
                    ?albumInfo.getPrice()
                    :albumInfo.getPrice().multiply(new BigDecimal(tradeVo.getTrackCount()));
            //最终金额
            orderAmount = originalAmount;
            //封装订单的其他数据
            //将购买的声音信息封装到订单详情列表
            trackInfoList.stream().forEach(trackInfo -> {
                OrderDetailVo orderDetailVo = new OrderDetailVo();
                orderDetailVo.setItemId(trackInfo.getId());
                orderDetailVo.setItemName(trackInfo.getTrackTitle());
                orderDetailVo.setItemUrl(trackInfo.getCoverUrl());
                orderDetailVo.setItemPrice(albumInfo.getPrice());
                orderDetailVoList.add(orderDetailVo);
            });
        }
        //最终封装订单详情对象
        //把最终的数据封装到OrderInfoVo，返回OrderInfoVo对象
        OrderInfoVo orderInfoVo = new OrderInfoVo();
        //购买类型：vip  专辑  声音
        orderInfoVo.setItemType(tradeVo.getItemType());
        //原始金额
        orderInfoVo.setOriginalAmount(originalAmount);
        //优惠金额
        orderInfoVo.setDerateAmount(derateAmount);
        //最终金额
        orderInfoVo.setOrderAmount(orderAmount);
        //订单明细和优惠明细
        orderInfoVo.setOrderDetailVoList(orderDetailVoList);
        orderInfoVo.setOrderDerateVoList(orderDerateVoList);
        //时间戳
        orderInfoVo.setTimestamp(SignHelper.getTimestamp());
        //  支付方式默认值,微信支付
        orderInfoVo.setPayWay(SystemConstant.ORDER_PAY_WAY_WEIXIN);
        //防止结算界面重复购买导致多个相同订单被支付
        //向Redis中添加数据，key为前缀加用户ID，value为设置的随机不重复流水号，
        //后续处理订单时根据key查询Redis中对应value值与tradeNo进行比较，如果相同则为第一次提交订单，可以处理，然后删除该缓存数据，
        //如果不相同或者不存在，则说明已经提交过订单，不能重复提交
        String key = RedisConstant.USER_ORDER_KEY_PREFIX + userId;
        String value= UUID.randomUUID().toString().replaceAll("-","");
        redisTemplate.opsForValue().set(key,value,30, TimeUnit.MINUTES);
        //返回的记录订单详细信息的orderInfoVo对象设置流水号
        orderInfoVo.setTradeNo(value);

        //为了防止订单数据被截取篡改金额，则使用MD5加密对订单详细对象进行加密跟订单详细对象明文数据一起传输
        //对orderInfoVo对象进行MD5加密
        //先将orderInfoVo对象转换成JSON字符串
        String jsonString = JSON.toJSONString(orderInfoVo);
        log.info("加密前的订单信息：" + jsonString);
        //对JSON字符串转换成Map集合
        Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
        //使用MD5加密
        String sign = SignHelper.getSign(map);
        log.info("加密后的标签：" + sign);
        //将加密后的sign设置到orderInfoVo对象中，与明文数据一起传输，用户接收端处理订单时，将明文数据加密后与sign进行比较
        //相同则说明没有被篡改，不相同则说明明文数据被篡改
        orderInfoVo.setSign(sign);
        return orderInfoVo;
    }
}
