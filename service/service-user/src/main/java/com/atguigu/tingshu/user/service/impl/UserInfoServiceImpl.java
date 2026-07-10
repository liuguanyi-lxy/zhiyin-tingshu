package com.atguigu.tingshu.user.service.impl;

import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.user.UserInfo;
import com.atguigu.tingshu.model.user.UserPaidAlbum;
import com.atguigu.tingshu.model.user.UserPaidTrack;
import com.atguigu.tingshu.user.mapper.UserInfoMapper;
import com.atguigu.tingshu.user.mapper.UserPaidAlbumMapper;
import com.atguigu.tingshu.user.mapper.UserPaidTrackMapper;
import com.atguigu.tingshu.user.service.UserInfoService;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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