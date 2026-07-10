package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.AlbumInfoService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "专辑管理")
@RestController
@RequestMapping("api/album/albumInfo")
@SuppressWarnings({"all"})
public class AlbumInfoApiController {

	@Autowired
	private AlbumInfoService albumInfoService;
	//保存专辑
	@LoginVerification
	@Operation(summary = "保存专辑")
	@PostMapping("saveAlbumInfo")
	public Result saveAlbumInfo(@RequestBody @Validated AlbumInfoVo albumInfoVo) {
		albumInfoService.saveAlbumInfo(albumInfoVo);
		return Result.ok();
	}
	//查询专辑列表
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
	//根据专辑ID删除专辑
	@LoginVerification
	@Operation(summary = "根据专辑ID删除专辑")
	@DeleteMapping("removeAlbumInfo/{albumId}")
	public Result removeAlbumInfo(@PathVariable("albumId") String albumId) {
		albumInfoService.removeAlbumInfo(albumId);
		return Result.ok();
	}
	//会被做远程调用接口
	//修改之前先回显专辑信息-根据专辑id获取专辑数据
	@Operation(summary = "根据专辑ID获取专辑信息")
	@GetMapping("getAlbumInfo/{albumId}")
	public Result<AlbumInfo> getAlbumInfo(@PathVariable Long albumId) {
		AlbumInfo albumInfo = albumInfoService.getAlbumInfo(albumId);
		return Result.ok(albumInfo);
	}
	//修改专辑信息
	@LoginVerification
	@Operation(summary = "根据专辑ID修改专辑信息,回显给前端的专辑信息被修改后传回给后端进行保存")
	@PutMapping("updateAlbumInfo/{albumId}")
	public Result updateAlbumInfo(@PathVariable Long albumId,@RequestBody @Validated AlbumInfoVo albumInfoVo) {
		albumInfoService.updateAlbumInfo(albumId,albumInfoVo);
		return Result.ok();
	}
	//获取用户的专辑列表
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
}

