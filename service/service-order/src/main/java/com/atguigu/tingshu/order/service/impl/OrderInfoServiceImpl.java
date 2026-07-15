package com.atguigu.tingshu.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.order.OrderInfo;
import com.atguigu.tingshu.model.user.VipServiceConfig;
import com.atguigu.tingshu.order.helper.SignHelper;
import com.atguigu.tingshu.order.mapper.OrderInfoMapper;
import com.atguigu.tingshu.order.service.OrderInfoService;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.user.client.VipServiceConfigFeignClient;
import com.atguigu.tingshu.vo.order.OrderDerateVo;
import com.atguigu.tingshu.vo.order.OrderDetailVo;
import com.atguigu.tingshu.vo.order.OrderInfoVo;
import com.atguigu.tingshu.vo.order.TradeVo;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
        //对JSON字符串转换成Map集合
        Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
        //使用MD5加密
        String sign = SignHelper.getSign(map);
        //将加密后的sign设置到orderInfoVo对象中，与明文数据一起传输，用户接收端处理订单时，将明文数据加密后与sign进行比较
        //相同则说明没有被篡改，不相同则说明明文数据被篡改
        orderInfoVo.setSign(sign);
        return orderInfoVo;
    }
}
