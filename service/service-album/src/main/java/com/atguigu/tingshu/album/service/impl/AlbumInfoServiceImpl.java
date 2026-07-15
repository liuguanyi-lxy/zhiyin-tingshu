package com.atguigu.tingshu.album.service.impl;

import com.atguigu.tingshu.album.mapper.AlbumAttributeValueMapper;
import com.atguigu.tingshu.album.mapper.AlbumInfoMapper;
import com.atguigu.tingshu.album.mapper.AlbumStatMapper;
import com.atguigu.tingshu.album.mapper.TrackInfoMapper;
import com.atguigu.tingshu.album.service.AlbumAttributeValueService;
import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.RedisCache.annotation.RedisCache;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.constant.SystemConstant;
import com.atguigu.tingshu.common.rabbit.constant.MqConst;
import com.atguigu.tingshu.common.rabbit.service.RabbitService;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.model.album.AlbumStat;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumAttributeValueVo;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@SuppressWarnings({"all"})
public class AlbumInfoServiceImpl extends ServiceImpl<AlbumInfoMapper, AlbumInfo> implements AlbumInfoService {

	@Autowired
	private AlbumInfoMapper albumInfoMapper;
	@Autowired
	private AlbumStatMapper albumStatMapper;
	@Autowired
	private AlbumAttributeValueMapper albumAttributeValueMapper;
	@Autowired
	private AlbumAttributeValueService albumAttributeValueService;
	@Autowired
	private TrackInfoMapper trackInfoMapper;
	@Autowired
	private RabbitService rabbitService;
	//根据专辑ID和专辑对应统计类型修改播放量
	@Override
	public void updateStat(Long albumId, String statType,Integer count) {
		//根据专辑ID和统计类型查询专辑统计信息
		LambdaQueryWrapper<AlbumStat> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(AlbumStat::getAlbumId,albumId);
		wrapper.eq(AlbumStat::getStatType,SystemConstant.ALBUM_STAT_PLAY);
		AlbumStat albumStat = albumStatMapper.selectOne(wrapper);
		Assert.notNull(albumStat, "专辑统计信息不存在");
		//对专辑播放量进行修改
		albumStat.setStatNum(albumStat.getStatNum()+count);
		albumStatMapper.updateById(albumStat);
	}

	//根据专辑ID获取专辑的标签列表
	@Override
	public List<AlbumAttributeValue> findAlbumAttributeValueByAlbumId(Long albumId) {
		LambdaQueryWrapper<AlbumAttributeValue> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(AlbumAttributeValue::getAlbumId,albumId);
		List<AlbumAttributeValue> albumAttributeValueList = albumAttributeValueMapper.selectList(wrapper);
		// 返回集合数据
		return albumAttributeValueList;
	}

	//根据专辑ID获取专辑的四个统计数据
	@Override
	public AlbumStatVo getAlbumInfoStat(Long albumId) {
		return albumStatMapper.getAlbumInfoStat(albumId);
	}

	//根据用户ID查询专辑列表
	@Override
	public List<AlbumInfo> findUserAlbumList(Long userId) {
		//创建Page对象，默认是显示专辑列表的第一页，每页一百条
		Page<AlbumInfo> page = new Page<>(1, 100);
		//创建QueryWrapper对象，根据用户ID查询专辑列表
		LambdaQueryWrapper<AlbumInfo> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(AlbumInfo::getUserId, userId);
		//根据ID降序排列，优先显示最新添加的专辑
		queryWrapper.orderByDesc(AlbumInfo::getId);
		//指定查询字段，专辑ID和专辑名称
		queryWrapper.select(AlbumInfo::getId, AlbumInfo::getAlbumTitle);
		IPage<AlbumInfo> albumInfoPage = albumInfoMapper.selectPage(page, queryWrapper);
		List<AlbumInfo> albumInfoList = albumInfoPage.getRecords();
		//将专辑列表返回给前端
		return albumInfoList;
	}

	//根据前端传入的专辑ID和专辑信息进行修改
	@Override
	public void updateAlbumInfo(Long albumId, AlbumInfoVo albumInfoVo) {
		//1.修改专辑基本信息album_info
		//根据专辑ID查询旧的专辑基本信息，然后将新的专辑基本信息赋值给旧的，不同的属性值会被新的覆盖
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		//获取专辑原本是否公开的状态
		String isOpened = albumInfo.getIsOpen();
		//再获取修改后的专辑是否公开的状态
		String isOpen = albumInfoVo.getIsOpen();
		BeanUtils.copyProperties(albumInfoVo, albumInfo);
		//然后进行更新
		albumInfoMapper.updateById(albumInfo);
		//2.修改专辑对应的标签名称和标签值,因为专辑ID不是专辑标签表的主键，不能直接根据专辑ID进行修改，所以使用先删除再添加的方式进行修改
		//2.1删除专辑对应的标签名称和标签值
		LambdaQueryWrapper<AlbumAttributeValue> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(AlbumAttributeValue::getAlbumId, albumId);
		albumAttributeValueMapper.delete(wrapper);
		//2.2添加专辑对应的标签名称和标签值
		List<AlbumAttributeValueVo> albumAttributeValueVoList = albumInfoVo.getAlbumAttributeValueVoList();
		//对专辑对应的标签名称和标签值进行判空后循环处理添加到album_attribute_value
		if (!CollectionUtils.isEmpty(albumAttributeValueVoList)) {
			//对专辑对应的标签名称和标签值进行循环处理添加到album_attribute_value
			//将albumAttributeValueVoList转换成albumAttributeValue对象集合
			List<AlbumAttributeValue> AlbumAttributeValueList = albumAttributeValueVoList
					.stream()
					.map(albumAttributeValueVo -> {
					AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
					BeanUtils.copyProperties(albumAttributeValueVo, albumAttributeValue);
					//设置专辑ID
					albumAttributeValue.setAlbumId(albumId);
					return albumAttributeValue;
			}).collect(Collectors.toList());
			//批量添加专辑对应的标签名称和标签值
			albumAttributeValueService.saveBatch(AlbumAttributeValueList);
		}
		//3.判断专辑修改前后是否公开的状态变化，从而决定是否专辑从es中上下架
		//如果原本是私密，现在公开，就上架
		if ("0".equals(isOpened) && "1".equals(isOpen)) {
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, albumId);
		}
		//如果原本是公开，现在私密，就下架
		if ("1".equals(isOpened) && "0".equals(isOpen)) {
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, albumId);
		}
	}
	//根据专辑ID获取专辑信息
	//直接使用专辑ID作为key容易与其他数据的key重复所以添加前缀
	@RedisCache(prefix = RedisConstant.ALBUM_INFO_PREFIX)
	@Override
	public AlbumInfo getAlbumInfo(Long albumId) {
		//需要查询专辑基本信息表album_info和专辑标签关系表album_attribute_value
		AlbumInfo albumInfo = albumInfoMapper.selectById(albumId);
		//查询专辑标签关系表album_attribute_value获取专辑对应的标签和标签值然后赋值给属性albumAttributeValueVoList
		LambdaQueryWrapper<AlbumAttributeValue> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(AlbumAttributeValue::getAlbumId, albumId);
		List<AlbumAttributeValue> albumAttributeValueList = albumAttributeValueMapper.selectList(queryWrapper);
		//将专辑标签列表赋值给专辑基本信息对象albumInfo
		albumInfo.setAlbumAttributeValueList(albumAttributeValueList);
		return albumInfo;
	}

	//根据专辑ID删除专辑相关的数据
	@Override
	public void removeAlbumInfo(String albumId) {
		//删除专辑之前需要判断专辑是否包含声音，包含就不能删除
		LambdaQueryWrapper<TrackInfo> queryWrapper = new LambdaQueryWrapper<>();
		queryWrapper.eq(TrackInfo::getAlbumId, albumId);
		Long count = trackInfoMapper.selectCount(queryWrapper);
		if(count > 0){
			log.info("该专辑包含声音，不能删除");
			throw new RuntimeException("该专辑包含声音，不能删除");
		}
		//1.删除专辑基本信息
        albumInfoMapper.deleteById(albumId);
        //2.删除专辑对应的标签名称和标签值,因为专辑ID不是专辑标签表的主键，不能直接根据专辑ID进行删除
		LambdaQueryWrapper<AlbumAttributeValue> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(AlbumAttributeValue::getAlbumId, albumId);
		albumAttributeValueMapper.delete(wrapper);
		//3.删除专辑统计信息
		LambdaQueryWrapper<AlbumStat> queryWrapper1 = new LambdaQueryWrapper<>();
		queryWrapper1.eq(AlbumStat::getAlbumId, albumId);
		albumStatMapper.delete(queryWrapper1);
		//发送mq消息
		//对es中的专辑信息进行下架
		rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_LOWER, albumId);
	}
	//根据条件分页查询专辑列表
	@Override
	public IPage<AlbumListVo> selectAlbumListPage(Page<AlbumListVo> pageParam, AlbumInfoQuery albumInfoQuery) {
		return albumInfoMapper.selectAlbumListPage(pageParam, albumInfoQuery);
	}
	//保存专辑
	//当前操作操作多张表，保证多张表数据一致性，添加事务
	//当前这些表在一个数据库里面，这种事务称为本地事务
	@Override
	@Transactional
	public void saveAlbumInfo(AlbumInfoVo albumInfoVo) {
		//1.添加专辑基本信息album_info
		//将albumInfoVo对象中对应的属性值复制到albumInfo对象中
		AlbumInfo albumInfo = new AlbumInfo();
		BeanUtils.copyProperties(albumInfoVo, albumInfo);
		//用于专辑保存的albumInfo对象有几个属性需要自行赋值
		Long userId = AuthContextHolder.getUserId();
		albumInfo.setUserId(userId);
		//专辑状态默认设置通过
		albumInfo.setStatus(SystemConstant.ALBUM_STATUS_PASS);
		//设置收费专辑免费试听集数为前三集
		String payType = albumInfo.getPayType();
		if(!SystemConstant.ALBUM_PAY_TYPE_FREE.equals(payType)){
			albumInfo.setTracksForFree(3);
		}
		albumInfoMapper.insert(albumInfo);
		//2.添加专辑对应的标签名称和标签值，专辑标签关系表album_attribute_value
		List<AlbumAttributeValueVo> albumAttributeValueVoList = albumInfoVo.getAlbumAttributeValueVoList();
		//对专辑对应的标签名称和标签值进行判空后循环处理添加到album_attribute_value
		if(!CollectionUtils.isEmpty(albumAttributeValueVoList)){
			//对专辑对应的标签名称和标签值进行循环处理添加到album_attribute_value
			//将albumAttributeValueVoList转换成albumAttributeValue对象集合
			List<AlbumAttributeValue> AlbumAttributeValueList = albumAttributeValueVoList.stream().map(albumAttributeValueVo -> {
				AlbumAttributeValue albumAttributeValue = new AlbumAttributeValue();
				BeanUtils.copyProperties(albumAttributeValueVo, albumAttributeValue);
				//设置专辑ID
				albumAttributeValue.setAlbumId(albumInfo.getId());
				return albumAttributeValue;
			}).collect(Collectors.toList());
			//批量添加专辑对应的标签名称和标签值
			albumAttributeValueService.saveBatch(AlbumAttributeValueList);
		}
		//3.添加专辑统计信息表album_stat,因为是新专辑所以这些统计数据都是0
		saveAlbumStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_PLAY);//播放量
		saveAlbumStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_SUBSCRIBE);//订阅量
		saveAlbumStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_BROWSE);//购买量
		saveAlbumStat(albumInfo.getId(), SystemConstant.ALBUM_STAT_COMMENT);//评论数
		//判断专辑是否为公开，公开则通过mq发送消息专辑上架对应的微服务service-search，将专辑信息添加到索引库中
		if("1".equals(albumInfo.getIsOpen())){
			rabbitService.sendMessage(MqConst.EXCHANGE_ALBUM, MqConst.ROUTING_ALBUM_UPPER, albumInfo.getId());
		}
	}

	//保存专辑指定统计数据的方法
	public void saveAlbumStat(Long albumId,String statType){
		AlbumStat albumStat = new AlbumStat();
		albumStat.setAlbumId(albumId);
		albumStat.setStatType(statType);
		albumStat.setStatNum(0);
		albumStatMapper.insert(albumStat);
	}
}
