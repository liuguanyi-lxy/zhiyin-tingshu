package com.atguigu.tingshu.order.handler;

import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.order.service.OrderInfoService;
import jakarta.annotation.PostConstruct;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RedisDelayHandle {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private OrderInfoService orderInfoService;
    //监听方法,监听延迟队列
    /**
     * 使用@PostConstruct注解标记的方法，在Bean初始化完成后执行
     * 该方法创建一个新线程，用于持续监听Redisson中创建的延迟队列
     */
    @PostConstruct
    public void listener() {
        //创建新线程一直监听延迟队列
        new Thread(()->{
            while(true) {
                try {
                    //从指定的交换机获取绑定的对应延迟队列
                    RBlockingQueue<String> blockingQueue = redissonClient.getBlockingQueue(MqConst.EXCHANGE_CANCEL_ORDER);

                    //从延迟队列中获取消息
                    String orderId = blockingQueue.take();
                    //获取的订单ID不为空就调用取消订单的方法
                    if(StringUtils.hasText(orderId)) {
                        //调用方法，根据orderId取消订单
                        orderInfoService.cancelOrder(Long.parseLong(orderId));
                    }

                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }
}
