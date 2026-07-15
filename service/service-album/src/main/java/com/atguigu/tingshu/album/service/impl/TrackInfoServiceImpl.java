package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.user.client.UserInfoFeignClient;
import com.atguigu.tingshu.vo.album.*;
import com.atguigu.tingshu.vo.user.UserInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class TrackInfoServiceImpl extends ServiceImpl<TrackInfoMapper, TrackInfo> implements TrackInfoService {

	@Autowired
	private TrackInfoMapper trackInfoMapper;
	@Autowired
	private VodService vodService;
	@Autowired
	private AlbumInfoMapper albumInfoMapper;
	@Autowired
	private TrackStatMapper trackStatMapper;
	@Autowired
	private UserInfoFeignClient userInfoFeignClient;
	@Autowired
	private AlbumInfoService albumInfoService;
	//根据选择本集的声音ID和选择购买的声音数量批量获取下单付费声音列表
	@Override
	public List<TrackInfo> findPaidTrackInfoList(Long trackId, Integer trackCount) {
		//1.根据声音ID获取声音对象
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		//定义接收选择购买声音列表
		List<TrackInfo> trackInfoList=new ArrayList<>();
		//2.根据声音对象中的专辑ID和用户ID查询用户购买过的声音ID列表
		//trackCount大于0
		if(trackCount>0){
			Result<List<Long>> result = userInfoFeignClient.findUserIsPaidTrack(trackInfo.getAlbumId());
			List<Long> paidTrackIdList = result.getData();
			//3.获取当前专辑中排在本集后面的声音列表,要排除用户已经购买专辑中声音的ID
			LambdaQueryWrapper<TrackInfo> queryWrapper = new LambdaQueryWrapper<>();
			queryWrapper.eq(TrackInfo::getAlbumId,trackInfo.getAlbumId());
			queryWrapper.gt(TrackInfo::getOrderNum,trackInfo.getOrderNum());
			queryWrapper.notIn(!CollectionUtils.isEmpty(paidTrackIdList),TrackInfo::getId,paidTrackIdList);
			//设置查询出来的声音数量为trackCount
			queryWrapper.last(" limit "+trackCount);
			trackInfoList = trackInfoMapper.selectList(queryWrapper);
		}else{
			//trackCount==0 购买本集
			trackInfoList.add(trackInfo);
		}
		return trackInfoList;
	}

	//购买声音时显示可以购买声音的各种套餐类型，比如本集、后十集、后二十集之类的
	@Override
	public List<Map<String, Object>> findUserTrackPaidList(Long trackId) {
		//1.根据声音ID获取声音对象
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		//2.根据声音ID获取声音的专辑对象
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
		//3.远程调用接口，根据专辑ID和用户ID查询用户购买过哪些声音
		Result<List<Long>> result = userInfoFeignClient.findUserIsPaidTrack(trackInfo.getAlbumId());
		List<Long> paidTrackIdList = result.getData();
		//4.获取当前专辑中排在本集后面的声音列表
		LambdaQueryWrapper<TrackInfo> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(TrackInfo::getAlbumId,trackInfo.getAlbumId());
		queryWrapper.gt(TrackInfo::getOrderNum,trackInfo.getOrderNum());
		List<TrackInfo> trackInfoList = trackInfoMapper.selectList(queryWrapper);
		//5.获取当前专辑中排在本集后面的声音ID列表
		List<Long> trackIdList = trackInfoList.stream().map(TrackInfo::getId).collect(Collectors.toList());
		//6.把当前专辑中排在本集后面的声音ID列表去除掉已经购买声音的ID
		//如果购买声音ID列表为null则可以不进行这步
		if(!CollectionUtils.isEmpty(paidTrackIdList)){
			trackIdList.removeAll(paidTrackIdList);
		}
		//7.构建点击购买本集后弹出的各种套餐显示内容,每部分由map集合存储，加入到list中
		List<Map<String, Object>> TrackPaidPlanList = new ArrayList<>();
		//7.1 购买本集,最后的可购买声音ID列表大小为0
		if(trackIdList.size()>=0){
			Map<String, Object> TrackPaidMap = new HashMap<>();
			//套餐名称
			TrackPaidMap.put("name","本集");
			//套餐价格
			TrackPaidMap.put("price",albumInfo.getPrice());
			//本集之后选择购买声音集数
			TrackPaidMap.put("trackCount",0);
			TrackPaidPlanList.add(TrackPaidMap);
		}
		//可购买声音数量大于0小于10
		if(trackIdList.size()>0&&trackIdList.size()<10){
			Map<String, Object> TrackPaidMap = new HashMap<>();
            //套餐名称
            TrackPaidMap.put("name","后"+trackIdList.size()+"集");
            //套餐价格
            TrackPaidMap.put("price",albumInfo.getPrice().multiply(new BigDecimal(trackIdList.size())));
			//本集之后选择购买声音集数
			TrackPaidMap.put("trackCount",trackIdList.size());
			TrackPaidPlanList.add(TrackPaidMap);
		}
		//可购买声音数量大于等于10
		if(trackIdList.size()>=10){
			Map<String, Object> TrackPaidMap = new HashMap<>();
			//套餐名称
			TrackPaidMap.put("name","后10集");
			//套餐价格
			TrackPaidMap.put("price",albumInfo.getPrice().multiply(new BigDecimal("10")));
			//本集之后选择购买声音集数
			TrackPaidMap.put("trackCount",10);
			TrackPaidPlanList.add(TrackPaidMap);
		}
		//可购买声音数量大于等于10小于20
		// 后十几集
		if(trackIdList.size() > 10 && trackIdList.size() < 20) {
			Map<String, Object> map = new HashMap<>();
			int count = trackIdList.size();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(count));
			map.put("name", "后"+count+"集");
			map.put("price", price);
			map.put("trackCount", count);
			TrackPaidPlanList.add(map);
		}
		// 后20集
		if(trackIdList.size() >=20) {
			Map<String, Object> map = new HashMap<>();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(20));
			map.put("name", "后20集");
			map.put("price", price);
			map.put("trackCount", 20);
			TrackPaidPlanList.add(map);
		}

		//后二十几集
		if(trackIdList.size() > 20 && trackIdList.size() < 30) {
			Map<String, Object> map = new HashMap<>();
			int count = trackIdList.size();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(count));
			map.put("name", "后"+count+"集");
			map.put("price", price);
			map.put("trackCount", count);
			TrackPaidPlanList.add(map);
		}
		//后30集
		if(trackIdList.size() >=30) {
			Map<String, Object> map = new HashMap<>();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(30));
			map.put("name", "后30集");
			map.put("price", price);
			map.put("trackCount", 30);
			TrackPaidPlanList.add(map);
		}

		//后三十多集
		if(trackIdList.size() > 30 && trackIdList.size() < 40) {
			Map<String, Object> map = new HashMap<>();
			int count = trackIdList.size();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(count));
			map.put("name", "后"+count+"集");
			map.put("price", price);
			map.put("trackCount", count);
			TrackPaidPlanList.add(map);
		}

		//后40集
		if(trackIdList.size() >=40) {
			Map<String, Object> map = new HashMap<>();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(40));
			map.put("name", "后40集");
			map.put("price", price);
			map.put("trackCount", 40);
			TrackPaidPlanList.add(map);
		}
		//后四十多集
		if(trackIdList.size() > 40 && trackIdList.size() < 50) {
			Map<String, Object> map = new HashMap<>();
			int count = trackIdList.size();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(count));
			map.put("name", "后"+count+"集");
			map.put("price", price);
			map.put("trackCount", count);
			TrackPaidPlanList.add(map);
		}
		// 最多购买50集;
		if(trackIdList.size() >=50) {
			Map<String, Object> map = new HashMap<>();
			BigDecimal price = albumInfo.getPrice().multiply(new BigDecimal(50));
			map.put("name", "后50集");
			map.put("price", price);
			map.put("trackCount", 50);
			TrackPaidPlanList.add(map);
		}
		return TrackPaidPlanList;
	}


	//更新专辑和声音的播放量
	@Override
	public void updateStat(TrackStatMqVo trackStatMqVo) {
		LambdaQueryWrapper<TrackStat> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(TrackStat::getTrackId,trackStatMqVo.getTrackId());
		wrapper.eq(TrackStat::getStatType,trackStatMqVo.getStatType());
		//根据声音ID的统计类型获取对应的数据
		TrackStat trackStat = trackStatMapper.selectOne(wrapper);
		Assert.notNull(trackStat,"声音统计信息不存在");
		//修改声音的播放量的值
		trackStat.setStatNum(trackStat.getStatNum()+trackStatMqVo.getCount());
		//更新声音的播放量
		trackStatMapper.updateById(trackStat);
		//更新专辑的播放量
		albumInfoService.updateStat(trackStatMqVo.getAlbumId(),trackStatMqVo.getStatType(),trackStatMqVo.getCount());
	}

	//根据用户ID、专辑ID、分页数据查询专辑对应声音列表，并且根据用户ID查询用户信息来决定声音是否对该用户显示收费
	@Override
	public IPage<AlbumTrackListVo> findAlbumTrackPage(Page<AlbumTrackListVo> pageParam, Long albumId, Long userId) {
		//根据专辑id分页查询得到专辑对应的声音列表数据
		IPage<AlbumTrackListVo> pageInfo = trackInfoMapper.findAlbumTrackPage(pageParam,albumId);
		List<AlbumTrackListVo> albumTrackListVoList = pageInfo.getRecords();
		//根据专辑ID获取专辑信息
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		//1 根据userId判断当前是否登录
		//1.1 如果没有登录
		//如果专辑不是免费的，可以试看的声音免费的，其他收费
		// isShowPaidMark=flase免费的  isShowPaidMark=true收费的
		if(userId==null)
		{
			if(!SystemConstant.ALBUM_PAY_TYPE_FREE.equals(albumInfo.getPayType()))
			{
				//获取专辑对应的试听集数
				Integer FreeNum = albumInfo.getTracksForFree();
				//对专辑声音列表进行过滤，order_num>FreeNum的声音设置收费
				albumTrackListVoList.forEach(item->{
					if(item.getOrderNum()>FreeNum)
                        item.setIsShowPaidMark(true);
				});
			}
		}else{
			//1.2 如果登录状态
			//判断如果专辑vip免费的
			//* 如果用户没有开通vip，     收费
			//* 如果用户开通VIP但是过期了，收费
			boolean isNeedPaid= false;
			if(albumInfo.getPayType().equals(SystemConstant.ALBUM_PAY_TYPE_VIPFREE))
			{
				//根据用户ID远程调用用户微服务模块的Client的对应接口获取用户信息
				Result<UserInfoVo> result = userInfoFeignClient.getUserInfoVo(userId);
				UserInfoVo userInfoVo = result.getData();
				Assert.notNull(userInfoVo,"用户信息不存在");
				//判断用户是否开通VIP
				if(userInfoVo.getIsVip().equals(SystemConstant.USER_NOT_VIP))
				{
					//没有开通VIP，收费
                   isNeedPaid=true;
				}
				//开通了vip但是已经过期了收费
				if(userInfoVo.getIsVip().equals(SystemConstant.USER_IS_VIP)&&userInfoVo.getVipExpireTime().before(new Date()))
				{
					isNeedPaid=true;
				}
				//判断专辑为付费类型， 收费
			}else if(albumInfo.getPayType().equals(SystemConstant.ALBUM_PAY_TYPE_REQUIRE))
			{
				isNeedPaid=true;
			}
			//收费情况处理
			if(isNeedPaid)
			{
				//* 判断当前用户是否购买专辑，购买过，免费
				// 如果用户购买整个专辑，专辑里面所有声音免费
				// 如果用户只是购买了专辑某些声音，购买的声音免费的，其他收费
				//获取专辑对应的试听集数
				Integer FreeNum = albumInfo.getTracksForFree();
					//获取专辑中声音的所有ID，远程调用接口，查询用户是否购买专辑或者专辑中的某些声音，返回的是map，key为声音ID，value为是否购买的标志
					List<Long> TrackIdList = albumTrackListVoList
							.stream()
							.map(item -> item.getTrackId())
							.collect(Collectors.toList());
					Result<Map<Long, Integer>> mapResult = userInfoFeignClient.userIsPaidTrack(userId, TrackIdList);
					Map<Long, Integer> TrackPaidMap = mapResult.getData();
					Assert.notNull(TrackPaidMap,"用户是否购买专辑或者声音信息不存在");
					//遍历albumTrackListVoList，判断声音的排序是否大于试听数量并且根据声音ID取出mapResult的value判断是否购买，如果声音排序大于FreeNum并且未购买，将isShowPaidMark设置为true
					albumTrackListVoList.forEach(item->{
						if(item.getOrderNum()>FreeNum&&TrackPaidMap.get(item.getTrackId()).equals(SystemConstant.TRACK_NOT_BUY))
						{
							item.setIsShowPaidMark(true);
						}
					});
		}
	}
		//将处理后的声音列表数据重新设置到分页对象中
		pageInfo.setRecords(albumTrackListVoList);
		return pageInfo;
	}
	//根据前端传入的修改后的声音信息对后端相关数据进行修改
	@Override
	public void updateTrackInfo(Long trackId, TrackInfoVo trackInfoVo) {
		//先根据声音ID查询数据库中声音的信息，将声音在腾讯云的文件ID跟前端传入的文件ID进行比较
		TrackInfo trackInfo = trackInfoMapper.selectById(trackId);
		if (!trackInfo.getMediaFileId().equals(trackInfoVo.getMediaFileId())) {
			//不相同说明声音已经修改，需要删除腾讯云中的旧的声音文件，然后获取新的声音文件的信息，比如时长、类型等
			vodService.removeTrackMedia(trackInfo.getMediaFileId());
			TrackMediaInfoVo trackMediaInfo = vodService.getTrackMediaInfo(trackInfoVo.getMediaFileId());
			//在我们修改过程中声音可能被管理员删除，所以需要判空，其实查询一般都需要判空
			if(trackMediaInfo==null)throw new RuntimeException(String.valueOf(ResultCodeEnum.DATA_DELETED));
			//将新的声音信息保存到trackInfo对象中
			trackInfo.setMediaDuration(trackMediaInfo.getDuration());
			trackInfo.setMediaSize(trackMediaInfo.getSize());
			trackInfo.setMediaType(trackMediaInfo.getType());
			trackInfo.setMediaUrl(trackMediaInfo.getMediaUrl());
		}
		//将前端传入的声音信息复制到trackInfo对象中,被修改的数据会覆盖原本的数据
		BeanUtils.copyProperties(trackInfoVo,trackInfo);
		//保存修改后的声音信息到数据库中
		trackInfoMapper.updateById(trackInfo);
	}

	//根据声音ID删除声音及其相关数据
	@Override
	public void removeTrackInfo(Long id) {
		//先获取声音的信息，得到对应的专辑ID，用户第二步修改专辑包含声音数量
		TrackInfo trackInfo = trackInfoMapper.selectById(id);
		//1.删除声音基本信息，删除track_info表中的记录
		trackInfoMapper.deleteById(id);
		//2.修改专辑包含声音数量-1
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfo.getAlbumId());
		albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount()-1);
		albumInfoMapper.updateById(albumInfo);
		//3.删除声音统计信息
		LambdaQueryWrapper<TrackStat> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(TrackStat::getTrackId,id);
		trackStatMapper.delete(wrapper);
		//4.删除声音媒体文件,通过腾讯云点播服务器的模版代码从腾讯云上删除对应的声音
		vodService.removeTrackMedia(trackInfo.getMediaFileId());
	}

	//根据分页条件和查询条件查询用户的声音列表
	@Override
	public IPage<TrackListVo> findUserTrackPage(Page<TrackListVo> pageParam, TrackInfoQuery trackInfoQuery) {
		return trackInfoMapper.selectUserTrackPage(pageParam,trackInfoQuery);
	}

	//保存声音的信息
	@Override
	public void saveTrackInfo(TrackInfoVo trackInfoVo) {
		//1.添加声音基本信息到track_info表
		//先将前端传递给后端的trackInfoVo对象中对应的属性值复制到trackInfo对象中
		TrackInfo trackInfo = new TrackInfo();
		BeanUtils.copyProperties(trackInfoVo,trackInfo);
		//前端传入的声音信息不完整，需要补充
		//TODO 后续登录和用户管理模块实现再补充用户ID
		Long userId = AuthContextHolder.getUserId();
		trackInfo.setUserId(userId);
		//声音在专辑中的排序值,需要获取专辑中声音排序数最大值
		Integer MaxOrderNum = trackInfoMapper.selectMaxOrderNUm(trackInfoVo.getAlbumId());
		//因为专辑中的声音可能为0，作为第一条声音，排序值为1
		trackInfo.setOrderNum(MaxOrderNum==null?1:MaxOrderNum+1);
		//声音的其他信息，比如声音时长、大小、媒体类型等需要根据声音的媒体文件ID从腾讯云获取
		TrackMediaInfoVo trackMediaInfo = vodService.getTrackMediaInfo(trackInfoVo.getMediaFileId());
		//如果腾讯云返回了声音信息，则将声音信息保存到trackInfo对象中
		trackInfo.setMediaDuration(trackMediaInfo.getDuration());
		trackInfo.setMediaSize(trackMediaInfo.getSize());
		trackInfo.setMediaType(trackMediaInfo.getType());
		trackInfo.setMediaUrl(trackMediaInfo.getMediaUrl());
		//声音基本信息收集完成，保存到数据库track_info中
		trackInfoMapper.insert(trackInfo);
		//2.操作专辑表album_info,修改专辑包含的声音数量+1
		AlbumInfo albumInfo = albumInfoMapper.selectById(trackInfoVo.getAlbumId());
		albumInfo.setIncludeTrackCount(albumInfo.getIncludeTrackCount()+1);
		albumInfoMapper.updateById(albumInfo);
		//3.添加声音的统计信息到album_stat表中，每个统计值初始值都为0
		trackStatMapper.insert(new TrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_PLAY,0));//播放量
		trackStatMapper.insert(new TrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_COLLECT,0));//收藏量
		trackStatMapper.insert(new TrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_PRAISE,0));//点赞量
		trackStatMapper.insert(new TrackStat(trackInfo.getId(), SystemConstant.TRACK_STAT_COMMENT,0));//评论量
	}
}
