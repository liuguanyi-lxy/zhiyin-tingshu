package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.mapper.TrackStatMapper;
import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.model.album.TrackStat;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

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
		trackInfo.setUserId(1L);
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
