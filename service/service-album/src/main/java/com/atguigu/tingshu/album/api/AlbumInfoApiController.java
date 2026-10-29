package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
import com.atguigu.tingshu.common.annotation.Log;
import com.atguigu.tingshu.common.constant.RedisConstant;
import com.atguigu.tingshu.common.enums.BusinessType;
import com.atguigu.tingshu.common.enums.OperatorType;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.login.LoginVerification;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.common.util.AuthContextHolder;
import com.atguigu.tingshu.model.album.AlbumAttributeValue;
import com.atguigu.tingshu.model.album.AlbumInfo;
import com.atguigu.tingshu.query.album.AlbumInfoQuery;
import com.atguigu.tingshu.vo.album.AlbumInfoVo;
import com.atguigu.tingshu.vo.album.AlbumListVo;
import com.atguigu.tingshu.vo.album.AlbumStatVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 专辑相关 API。
 *
 * <p>这个 Controller 同时服务于三类调用方：创作者管理端、C 端页面，以及其他微服务的 Feign 调用。
 * 因此，带 {@code @LoginVerification} 的方法通常是管理端或用户私有数据；没有该注解的方法通常是公开查询或内部查询接口。</p>
 */
@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album/albumInfo")
@SuppressWarnings({"all"})
public class AlbumInfoApiController {
    private static final Logger logger = LoggerFactory.getLogger(AlbumInfoApiController.class);

	@Autowired
	private AlbumInfoService albumInfoService;
    @Autowired
    private RedissonClient redissonClient;
	/** 保存创作者提交的专辑基本信息和标签信息。 */
	@LoginVerification
	@Log(title = "保存专辑", businessType = BusinessType.INSERT, operatorType = OperatorType.MOBILE)
	@Operation(summary = "保存专辑")
	@PostMapping("saveAlbumInfo")
	public Result saveAlbumInfo(@RequestBody @Validated AlbumInfoVo albumInfoVo) {
		albumInfoService.saveAlbumInfo(albumInfoVo);
		return Result.ok();
	}
	/** 分页查询当前登录创作者自己的专辑列表。 */
	@LoginVerification
	@Operation(summary = "分页查询专辑列表")
    @PostMapping("findUserAlbumPage/{page}/{limit}")
    public Result findUserAlbumPage(@PathVariable Long page,
									@PathVariable Long limit,
									@RequestBody @Parameter(description = "查询条件") AlbumInfoQuery albumInfoQuery) {
		Long userId = AuthContextHolder.getUserId();
		albumInfoQuery.setUserId(userId);
		//创建Page对象记录分页参数，显示第几页和每页多少数据
		Page<AlbumListVo> pageParam = new Page<>(page, limit);
		IPage<AlbumListVo>  AlbumList=albumInfoService.selectAlbumListPage(pageParam, albumInfoQuery);
		return Result.ok(AlbumList);
	}
	/** 删除当前用户拥有的专辑。权限和关联数据处理在 Service 层完成。 */
	@LoginVerification
	@Log(title = "删除专辑", businessType = BusinessType.DELETE, operatorType = OperatorType.MOBILE)
	@Operation(summary = "根据专辑ID删除专辑")
	@DeleteMapping("removeAlbumInfo/{albumId}")
	public Result removeAlbumInfo(@PathVariable("albumId") String albumId) {
		albumInfoService.removeAlbumInfo(albumId);
		return Result.ok();
	}
	/**
	 * 【压测基线】LIKE 模糊查询（%kw% 不走索引），与 ES 检索形成性能对照。
	 * 配合 docs/压测指引.md 使用；压测时直连本服务 8501 端口（不经网关，隔离对照变量）。
	 */
	@LoginVerification(required = false)
	@Operation(summary = "LIKE 检索性能基线（压测用）")
	@GetMapping("likeBenchmark/{keyword}")
	public Result likeBenchmark(@PathVariable String keyword) {
		return Result.ok(albumInfoService.likeBenchmark(keyword));
	}

	/**
	 * 查询专辑详情。
	 *
	 * <p>先用布隆过滤器拦截明显不存在的 ID，再进入缓存切面和数据库查询，避免恶意随机 ID 直接穿透到数据库。</p>
	 */
	@Operation(summary = "根据专辑ID获取专辑信息")
	@GetMapping("getAlbumInfo/{albumId}")
	public Result<AlbumInfo> getAlbumInfo(@PathVariable Long albumId) {
		logger.info("开始查询专辑，ID: {}", albumId);
		// 布隆过滤器在应用启动时加载已有专辑 ID；不命中时可以确定数据库中不存在该 ID。
		RBloomFilter<Object> bloomFilter = redissonClient.getBloomFilter(RedisConstant.ALBUM_BLOOM_FILTER);
		logger.info("获取布隆过滤器，检查专辑ID {} 是否存在", albumId);
		//布隆过滤器不包含该专辑ID
		if(!bloomFilter.contains(albumId)) {
			logger.warn("布隆过滤器中不存在专辑ID: {}，抛出异常", albumId);
			throw new GuiguException(228,"该专辑不存在"+albumId);
		}
		logger.info("布隆过滤器中存在专辑ID: {}，继续查询数据库", albumId);
		AlbumInfo albumInfo = albumInfoService.getAlbumInfo(albumId);
		logger.info("从数据库查询到专辑: {}", albumInfo);
		return Result.ok(albumInfo);
	}
	/** 修改专辑基本信息、标签，并根据公开状态变化通知搜索服务上下架。 */
	@LoginVerification
	@Log(title = "修改专辑", businessType = BusinessType.UPDATE, operatorType = OperatorType.MOBILE)
	@Operation(summary = "根据专辑ID修改专辑信息,回显给前端的专辑信息被修改后传回给后端进行保存")
	@PutMapping("updateAlbumInfo/{albumId}")
	public Result updateAlbumInfo(@PathVariable Long albumId,@RequestBody @Validated AlbumInfoVo albumInfoVo) {
		albumInfoService.updateAlbumInfo(albumId,albumInfoVo);
		return Result.ok();
	}
	/** 查询当前创作者拥有的专辑简表，供管理端选择专辑使用。 */
	@LoginVerification
	@Operation(summary = "获取用户的专辑列表")
    @GetMapping("findUserAllAlbumList")
	public Result findUserAlbumList() {
		Long userId = AuthContextHolder.getUserId();
		List<AlbumInfo> userAlbumList = albumInfoService.findUserAlbumList(userId);
		return Result.ok(userAlbumList);
	}
	//远程调用：根据专辑id获取四个统计数据
	@Operation(summary = "根据专辑id获取四个统计数据")
	@GetMapping("getAlbumInfoStat/{albumId}")
	public Result<AlbumStatVo> getAlbumInfoStat(@PathVariable Long albumId) {
		//因为没有统计信息对应的实体类就用map集合接收
		AlbumStatVo albumStatVoList = albumInfoService.getAlbumInfoStat(albumId);
		return Result.ok(albumStatVoList);
	}
	/**
	 * 远程调用:根据专辑Id 获取到专辑属性列表
	 * @param albumId
	 * @return
	 */
	@Operation(summary = "获取专辑标签列表")
	@GetMapping("findAlbumAttributeValue/{albumId}")
	public Result<List<AlbumAttributeValue>> findAlbumAttributeValue(@PathVariable Long albumId) {
		// 获取到专辑属性集合
		List<AlbumAttributeValue> albumAttributeValueList = albumInfoService.findAlbumAttributeValueByAlbumId(albumId);
		return Result.ok(albumAttributeValueList);
	}
	/**
	 * 远程调用:根据专辑ID列表批量获取专辑信息。
	 *
	 * <p>供用户服务组装"我的订阅/收听历史"列表使用，一次查询代替逐条调用，避免 N+1。</p>
	 */
	@Operation(summary = "根据专辑ID列表批量获取专辑信息")
	@PostMapping("findAlbumInfoList")
	public Result<List<AlbumInfo>> findAlbumInfoList(@RequestBody List<Long> albumIds) {
		return Result.ok(albumInfoService.findAlbumInfoList(albumIds));
	}
}

