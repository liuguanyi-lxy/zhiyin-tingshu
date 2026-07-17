package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.album.client.TrackInfoFeignClient;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.user.*;
import com.atguigu.tingshu.user.mapper.*;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.atguigu.tingshu.vo.user.UserPaidRecordVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.LocalDateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.atguigu.tingshu.common.constant.SystemConstant.TRACK_IS_BUY;
import static com.atguigu.tingshu.common.constant.SystemConstant.TRACK_NOT_BUY;

@Slf4j
@Service
@SuppressWarnings({"unchecked", "rawtypes"})
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

	@Autowired
	private UserInfoMapper userInfoMapper;
	@Autowired
	private UserPaidAlbumMapper userPaidAlbumMapper;
	@Autowired
	private UserPaidTrackMapper userPaidTrackMapper;
	//添加购买记录
	@Autowired
	private TrackInfoFeignClient trackInfoFeignClient;

	@Autowired
	private VipServiceConfigMapper vipServiceConfigMapper;

	@Autowired
	private UserVipServiceMapper userVipServiceMapper;
	@Override
	//对多张表进行修改，如果对其中一个表的操作出现异常就需要回滚对其他表的操作
	@Transactional(rollbackFor = Exception.class)
	public void savePaidRecord(UserPaidRecordVo userPaidRecordVo) {
		//  根据购买类型判断向哪张表插入数据.
		//购买的是专辑
		if (userPaidRecordVo.getItemType().equals(SystemConstant.ORDER_ITEM_TYPE_ALBUM)){
			//  专辑 user_paid_album
			//  防止重复添加.
			LambdaQueryWrapper<UserPaidAlbum> wrapper = new LambdaQueryWrapper<>();
			wrapper.eq(UserPaidAlbum::getUserId,userPaidRecordVo.getUserId());
			//  wrapper.eq(UserPaidAlbum::getAlbumId,userPaidRecordVo.getItemIdList().get(0));
			wrapper.eq(UserPaidAlbum::getOrderNo,userPaidRecordVo.getOrderNo());
			UserPaidAlbum userPaidAlbum = userPaidAlbumMapper.selectOne(wrapper);
			//能查询得到就说明用户购买专辑记录已经添加
			if (null != userPaidAlbum){
				return;
			}
			//  新增：
			userPaidAlbum = new UserPaidAlbum();
			userPaidAlbum.setUserId(userPaidRecordVo.getUserId());
			userPaidAlbum.setOrderNo(userPaidRecordVo.getOrderNo());
			//  专辑Id 声音Id vip_service_config.id ==> order_detial.item_id;
			userPaidAlbum.setAlbumId(userPaidRecordVo.getItemIdList().get(0));
			userPaidAlbumMapper.insert(userPaidAlbum);
			//购买的是声音
		}else if (userPaidRecordVo.getItemType().equals(SystemConstant.ORDER_ITEM_TYPE_TRACK)){
			//  声音 user_paid_track
			LambdaQueryWrapper<UserPaidTrack> wrapper = new LambdaQueryWrapper<>();
			wrapper.eq(UserPaidTrack::getUserId,userPaidRecordVo.getUserId());
			wrapper.eq(UserPaidTrack::getOrderNo,userPaidRecordVo.getOrderNo());
			Long count = userPaidTrackMapper.selectCount(wrapper);
			if (count>0){
				return;
			}
			//  获取专辑Id
			Result<TrackInfo> trackInfoResult = trackInfoFeignClient.getTrackInfo(userPaidRecordVo.getItemIdList().get(0));
			TrackInfo trackInfo = trackInfoResult.getData();
			//  声音Id 有可能一次购买多个.需要循环遍历
			userPaidRecordVo.getItemIdList().stream().forEach(trackId->{
				//  保存数据：
				UserPaidTrack userPaidTrack = new UserPaidTrack();
				userPaidTrack.setTrackId(trackId);
				userPaidTrack.setOrderNo(userPaidRecordVo.getOrderNo());
				userPaidTrack.setUserId(userPaidRecordVo.getUserId());
				//  获取专辑Id
				userPaidTrack.setAlbumId(trackInfo.getAlbumId());
				userPaidTrackMapper.insert(userPaidTrack);
			});
		}else if(userPaidRecordVo.getItemType().equals(SystemConstant.ORDER_ITEM_TYPE_VIP)){
			//  vip user_vip_service 保存： vip:续期
			//  获取用户对象信息.
			UserInfo userInfo = userInfoMapper.selectById(userPaidRecordVo.getUserId());
			//计算新的vip过期时间的基准时间
			Date BaseTime = new Date();
			//  判断是否要做续期：用户为vip并且用户的vip过期时间在现在时间之后
			if (userInfo.getIsVip().intValue()==1 && userInfo.getVipExpireTime().after(new Date())){
				//基准时间设置为用户购买VIP前的过期时间
				BaseTime = userInfo.getVipExpireTime();
			}
			//获取购买的vip套餐的配置信息
			VipServiceConfig vipServiceConfig = vipServiceConfigMapper.selectById(userPaidRecordVo.getItemIdList().get(0));
			//购买的vip时长
			Integer serviceMonth = vipServiceConfig.getServiceMonth();
			//计算购买VIP后的过期时间
			Date expireTime = new LocalDateTime(BaseTime).plusMonths(serviceMonth).toDate();
			//创建用户购买VIP服务对象
			UserVipService userVipService = new UserVipService();
			userVipService.setOrderNo(userPaidRecordVo.getOrderNo());
			userVipService.setUserId(userPaidRecordVo.getUserId());
			userVipService.setStartTime(new Date());
			userVipService.setExpireTime(expireTime);
			//  保存
			userVipServiceMapper.insert(userVipService);
			//  修改user_info.is_vip=1;
			userInfo.setIsVip(1);
			//  设置过期时间
			userInfo.setVipExpireTime(expireTime);
			//  手动模拟异常信息。
			//  int i = 1/0;
			//  修改用户关于VIP部分的信息
			this.userInfoMapper.updateById(userInfo);
		}
	}

	//根据专辑ID和用户ID查询用户购买过哪些声音，返回这些声音ID列表
	@Override
	public List<Long> findPaidUserTrackList(Long userId, Long albumId) {
		LambdaQueryWrapper<UserPaidTrack> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(UserPaidTrack::getUserId,userId).eq(UserPaidTrack::getAlbumId,albumId);
		List<UserPaidTrack> userPaidTracks = userPaidTrackMapper.selectList(queryWrapper);
		List<Long> PaidTrackIdList = userPaidTracks.stream()
				.map(UserPaidTrack::getTrackId)
				.collect(Collectors.toList());
		return PaidTrackIdList;
	}

	//根据专辑ID和用户ID判断专辑是否被购买
	@Override
	public Boolean isPaidAlbum(Long albumId, Long userId) {
		//查询user-paid-album表，根据用户ID和专辑ID查询是否有记录，有则说明购买过该专辑
		LambdaQueryWrapper<UserPaidAlbum> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(UserPaidAlbum::getAlbumId,albumId).eq(UserPaidAlbum::getUserId,userId);
		boolean exists = userPaidAlbumMapper.exists(queryWrapper);
		return exists;
	}

	//根据用户ID、专辑ID、声音ID列表查询user_paid_album、user_paid_track判断声音是否被购买
	@Override
	public Map<Long, Integer> userIsPaidTrack(Long userId, Long albumId, List<Long> trackIdList) {
		//创建一个Hashmap，key为声音ID，value为声音是否被购买
		Map<Long, Integer> map = new HashMap<>();
		//1.首先根据用户ID和专辑ID查询user_paid_album表，判断用户是否购买过该专辑，购买过则其下的所以声音都是对用户免费的
		LambdaQueryWrapper<UserPaidAlbum> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(UserPaidAlbum::getUserId,userId).eq(UserPaidAlbum::getAlbumId,albumId);
		UserPaidAlbum userPaidAlbum = userPaidAlbumMapper.selectOne(wrapper);
		if(ObjectUtils.isNotEmpty(userPaidAlbum)){
			//用户购买过该专辑，则所有声音对用户免费
			//遍历声音ID列表，对map赋值
			trackIdList.stream().forEach(trackId->{
				map.put(trackId,TRACK_IS_BUY);
			});
			return map;
		}
		//2.如果用户没有购买该专辑，则根据用户ID和声音ID列表查询user_paid_track表，判断用户是否购买过这些声音，购买过则这些声音对用户免费
		//先根据用户ID和声音ID列表查询user_paid_track表中的数据
		LambdaQueryWrapper<UserPaidTrack> wrapper1 = new LambdaQueryWrapper<>();
		wrapper1.eq(UserPaidTrack::getUserId,userId).in(UserPaidTrack::getTrackId,trackIdList);
		List<UserPaidTrack> userPaidTracks = userPaidTrackMapper.selectList(wrapper1);
		//如果用户购买该专辑的声音的列表不为空，就获取购买声音的ID列表
		if(CollectionUtils.isNotEmpty(userPaidTracks)){
			List<Long> PaidTrackId = userPaidTracks.stream().map(UserPaidTrack::getTrackId).collect(Collectors.toList());
			//遍历声音ID列表，对map赋值
			trackIdList.stream().forEach(trackId->{
				if(PaidTrackId.contains(trackId)){
					map.put(trackId,TRACK_IS_BUY);
				}else
					map.put(trackId,TRACK_NOT_BUY);
			});
		}else {
			//如果用户没有购买该专辑的声音的列表为空，则遍历声音ID列表，对map赋值
			trackIdList.stream().forEach(trackId->
				map.put(trackId,TRACK_NOT_BUY));
		}
		return map;
	}

	@Override
	public UserInfoVo getUserInfoVoByUserId(Long userId) {
		//	获取到用户信息对象
		UserInfo userInfo = this.getById(userId);
		if (userInfo == null) {
			// 抛出自定义异常或返回 null，视业务需求而定
			throw new GuiguException(ResultCodeEnum.LOGIN_AUTH); // 假设这是你的自定义异常
		}
		//	创建UserInfoVo 对象
		UserInfoVo userInfoVo = new UserInfoVo();
		//	属性拷贝
		BeanUtils.copyProperties(userInfo,userInfoVo);
		return userInfoVo;
	}
}