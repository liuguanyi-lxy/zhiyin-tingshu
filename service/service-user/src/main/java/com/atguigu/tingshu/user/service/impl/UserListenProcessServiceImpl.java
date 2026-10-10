package com.atguigu.tingshu.user.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.client.AlbumInfoFeignClient;
import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.MongoUtil;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.UserListenProcess;
import com.atguigu.tingshu.user.service.UserListenProcessService;
import com.atguigu.tingshu.vo.album.TrackStatMqVo;
import com.atguigu.tingshu.vo.user.UserListenProcessListVo;
import com.atguigu.tingshu.vo.user.UserListenProcessVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.bson.types.ObjectId;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@SuppressWarnings({"all"})
public class UserListenProcessServiceImpl implements UserListenProcessService {

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private RedisTemplate redisTemplate;

	@Autowired
	private RabbitService rabbitService;

	@Autowired
	private TrackInfoFeignClient trackInfoFeignClient;

	@Autowired
	private AlbumInfoFeignClient albumInfoFeignClient;

	//根据当前用户ID和指定声音ID查询MongoDB中的数据，获取声音的播放进度
	@Override
	public BigDecimal getTrackBreakSecond(Long userId, Long trackId) {
		Query query = new Query(Criteria.where("userId").is(userId).and("trackId").is(trackId));
		UserListenProcess userListenProcess = mongoTemplate.findOne(query, UserListenProcess.class,
				MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
		if(ObjectUtils.isNotEmpty(userListenProcess))
		{
			return userListenProcess.getBreakSecond();
		}else{
			return BigDecimal.ZERO;
		}
	}
	//根据登录校验得到的当前用户ID和前端传入的关于指定专辑的声音和播放进度更新MongoDB中的数据
	//保存（更新）声音播放进度
	@Override
	public void saveOrUpdateListenProcess(Long userId, UserListenProcessVo userListenProcessVo) {
		//1.查询，根据用户ID和声音ID查询MongoDB中是否有对应的播放进度
		Criteria criteria = Criteria.where("userId").is(userId)
				.and("trackId").is(userListenProcessVo.getTrackId());
		Query query = new Query(criteria);
		//根据查询条件、接收查询结果对应的实体类的类信息、查询集合名(集合名是userListenProcess_userId%100)进行查询
		UserListenProcess userListenProcess = mongoTemplate.findOne(query, UserListenProcess.class, MongoUtil.getCollectionName(
				MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS,
				userId));
		//2.如果在MongoDB中已经有了指定用户指定声音下的播放进度，就进行播放进度的更新
		if(ObjectUtils.isNotEmpty(userListenProcess))
		{
			//将声音播放进度更新为前端传入的声音播放进度
			userListenProcess.setBreakSecond(userListenProcessVo.getBreakSecond());
			//修改声音更新时间
			userListenProcess.setUpdateTime(new Date());
			//重新收听已被删除(隐藏)的记录时恢复显示
			userListenProcess.setIsShow(1);
			//将修改好的关于指定用户指定声音的声音播放进度实体类更新到MongoDB指定集合中
			mongoTemplate.save(userListenProcess, MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
		}else{
			//3.如果在MongoDB中没有指定用户指定声音下的播放进度，就进行播放进度的保存
            //创建声音播放进度实体类
            UserListenProcess userListenProcess1 = new UserListenProcess();
			BeanUtils.copyProperties(userListenProcessVo,userListenProcess1);
			//将声音播放进度更新为前端传入的用户ID
            userListenProcess1.setUserId(userId);
			//设置ID
			userListenProcess1.setId(ObjectId.get().toString());
			//设置声音播放进度更新时间
			userListenProcess1.setUpdateTime(new Date());
			//设置声音播放进度创建时间
			userListenProcess1.setCreateTime(new Date());
			//设置是否显示
			userListenProcess1.setIsShow(1);
			//将创建好的关于指定用户指定声音的声音播放进度实体类保存到MongoDB指定集合中
			mongoTemplate.save(userListenProcess1, MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
			}
		//更新声音播放量
		//对应同一个用户，同一个声音的播放量在24小时之内只能增加一次
		//使用Redis的bitmap数据类型 key:user:userId,offset:trackId,value:1 设置过期时间为24h
		//根据用户ID和声音ID查询Redis，只要还没有到过期时间就能查的到该数据，查的到就不能更新播放量，查不到才更新播放量
		String key="user:"+userId;
		Long offset=userListenProcessVo.getTrackId();
		Boolean isExist = redisTemplate.opsForValue().setBit(key, offset, true);
		//2.如果在Redis中查询不到记录则说明可以进行播放量更新
		if(!isExist){
			//使用setBit方法放数据到Redis
			redisTemplate.opsForValue().setBit(key, offset, true);
			//设置该数据的过期时间为24h，过期则可以进行下一次的播放量更新
			redisTemplate.expire(key,24*60*60, TimeUnit.SECONDS);
			//使用rabbitmq发送消息给service-album微服务模块，更新声音播放量
			//封装声音统计信息实体类
			TrackStatMqVo trackStatMqVo = new TrackStatMqVo();
			trackStatMqVo.setBusinessNo(UUID.randomUUID().toString().replaceAll("-",""));
			trackStatMqVo.setAlbumId(userListenProcessVo.getAlbumId());
			trackStatMqVo.setTrackId(userListenProcessVo.getTrackId());
			trackStatMqVo.setStatType(SystemConstant.TRACK_STAT_PLAY);
			trackStatMqVo.setCount(1);
			//发送消息，将 Java 对象转换为 JSON 格式的字符串，用于网络传输
			rabbitService.sendMessage(MqConst.EXCHANGE_TRACK, MqConst.ROUTING_TRACK_STAT_UPDATE, JSON.toJSONString(trackStatMqVo));
		}
	}

	/**
	 * 分页查询"我的收听历史"。
	 *
	 * <p>只展示未隐藏(isShow=1)且定位到具体声音(trackId>0)的记录，按最近收听时间倒序；
	 * 声音与专辑信息一次批量 Feign 拉取组装，避免逐条调用的 N+1。</p>
	 */
	@Override
	public IPage<UserListenProcessListVo> findUserPage(Long page, Long limit, Long userId) {
		String collectionName = MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId);
		Criteria criteria = Criteria.where("userId").is(userId).and("isShow").is(1).and("trackId").gt(0);
		long total = mongoTemplate.count(new Query(criteria), UserListenProcess.class, collectionName);

		Query query = new Query(criteria)
				.with(Sort.by(Sort.Direction.DESC, "updateTime"))
				.skip((page - 1) * limit)
				.limit(limit.intValue());
		List<UserListenProcess> processes = mongoTemplate.find(query, UserListenProcess.class, collectionName);

		Page<UserListenProcessListVo> pageModel = new Page<>(page, limit);
		pageModel.setTotal(total);
		if (CollectionUtils.isEmpty(processes)) {
			return pageModel;
		}

		//1.批量拉取声音信息
		List<Long> trackIds = processes.stream().map(UserListenProcess::getTrackId).distinct().collect(Collectors.toList());
		Result<List<TrackInfo>> trackResult = trackInfoFeignClient.findTrackInfoListByIds(trackIds);
		Map<Long, TrackInfo> trackMap = (trackResult == null || CollectionUtils.isEmpty(trackResult.getData()))
				? Collections.emptyMap()
				: trackResult.getData().stream()
						.collect(Collectors.toMap(TrackInfo::getId, Function.identity(), (a, b) -> a));

		//2.批量拉取声音归属的专辑信息（用于展示专辑标题）
		List<Long> albumIds = trackMap.values().stream()
				.map(TrackInfo::getAlbumId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
		Map<Long, AlbumInfo> albumMap = Collections.emptyMap();
		if (!albumIds.isEmpty()) {
			Result<List<AlbumInfo>> albumResult = albumInfoFeignClient.findAlbumInfoList(albumIds);
			if (albumResult != null && !CollectionUtils.isEmpty(albumResult.getData())) {
				albumMap = albumResult.getData().stream()
						.collect(Collectors.toMap(AlbumInfo::getId, Function.identity(), (a, b) -> a));
			}
		}

		//3.组装列表 VO
		Map<Long, AlbumInfo> finalAlbumMap = albumMap;
		List<UserListenProcessListVo> records = processes.stream().map(process -> {
			UserListenProcessListVo vo = new UserListenProcessListVo();
			vo.setId(process.getId());
			vo.setAlbumId(process.getAlbumId());
			vo.setTrackId(process.getTrackId());
			vo.setBreakSecond(process.getBreakSecond());
			TrackInfo trackInfo = trackMap.get(process.getTrackId());
			if (trackInfo != null) {
				vo.setTrackTitle(trackInfo.getTrackTitle());
				vo.setCoverUrl(trackInfo.getCoverUrl());
				if (trackInfo.getMediaDuration() != null) {
					vo.setMediaDuration(BigDecimal.valueOf(trackInfo.getMediaDuration()));
				}
				AlbumInfo albumInfo = finalAlbumMap.get(trackInfo.getAlbumId());
				if (albumInfo != null) {
					vo.setAlbumTitle(albumInfo.getAlbumTitle());
					if (vo.getCoverUrl() == null) {
						//声音没有独立封面时回退用专辑封面
						vo.setCoverUrl(albumInfo.getCoverUrl());
					}
				}
				//播放进度百分比（前端直接展示"已播 xx%"）
				if (trackInfo.getMediaDuration() != null && trackInfo.getMediaDuration() > 0 && process.getBreakSecond() != null) {
					int percent = process.getBreakSecond()
							.multiply(BigDecimal.valueOf(100))
							.divide(BigDecimal.valueOf(trackInfo.getMediaDuration()), 0, RoundingMode.HALF_UP)
							.intValue();
					vo.setPlayRate(Math.min(percent, 100) + "%");
				}
			}
			return vo;
		}).collect(Collectors.toList());
		pageModel.setRecords(records);
		return pageModel;
	}

	/**
	 * 删除指定收听历史（软删除）：仅置 isShow=0 保留断点数据，重新收听会自动恢复显示。
	 * 查询条件带 userId，防止越权删除他人记录。
	 */
	@Override
	public void deleteListenProcess(Long userId, String id) {
		Query query = new Query(Criteria.where("_id").is(id).and("userId").is(userId));
		mongoTemplate.updateFirst(query, new Update().set("isShow", 0), UserListenProcess.class,
				MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
	}

	/**
	 * 最近一次收听记录（未隐藏、声音维度），供播放页无参进入时续播。
	 * 无历史时返回空对象而不是 null，避免前端从 null 解构字段报错。
	 */
	@Override
	public UserListenProcess getLatelyTrack(Long userId) {
		Query query = new Query(Criteria.where("userId").is(userId).and("isShow").is(1).and("trackId").gt(0))
				.with(Sort.by(Sort.Direction.DESC, "updateTime"))
				.limit(1);
		UserListenProcess latest = mongoTemplate.findOne(query, UserListenProcess.class,
				MongoUtil.getCollectionName(MongoUtil.MongoCollectionEnum.USER_LISTEN_PROCESS, userId));
		return latest == null ? new UserListenProcess() : latest;
	}
}
