package com.atguigu.tingshu.album.api;

import com.atguigu.tingshu.album.service.TrackInfoService;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.result.Result;
import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.query.album.TrackInfoQuery;
import com.atguigu.tingshu.vo.album.TrackInfoVo;
import com.atguigu.tingshu.vo.album.TrackListVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "声音管理")
@RestController
@RequestMapping("api/album/trackInfo")
@SuppressWarnings({"all"})
public class TrackInfoApiController {

	@Autowired
	private TrackInfoService trackInfoService;
	@Autowired
	private VodService vodService;
	//修改编辑声音之前先查询声音信息进行数据回显
	@Operation(summary = "根据声音ID查询声音信息，进行数据回显给前端")
	@GetMapping("getTrackInfo/{trackId}")
	public Result getTrackInfo(@PathVariable("trackId") Long trackId) {
		TrackInfo trackInfo = trackInfoService.getById(trackId);
		return Result.ok(trackInfo);
	}
	//修改声音
	@Operation(summary = "修改声音信息，将前端修改后的声音信息返回给后端进行修改")
	@PutMapping("updateTrackInfo/{trackId}")
	public Result updateTrackInfo(@PathVariable("trackId") Long trackId,
								  @RequestBody @Validated TrackInfoVo trackInfoVo) {
		trackInfoService.updateTrackInfo(trackId,trackInfoVo);
		return Result.ok();
	}
	/**
	 * 上传声音文件
	 * @param file
	 * @return
	 */
	@Operation(summary = "上传声音文件到腾讯云点播服务器")
	@PostMapping("uploadTrack")
	public Result<Map<String,Object>> uploadTrack(MultipartFile file) {

		// 调用服务层方法
		// 上传之后，腾讯云返回两个值，文件在腾讯云id 和 生成音频文件播放地址给前端
		Map<String,Object> map = vodService.uploadTrack(file);
		return Result.ok(map);
	}
	/**
	 * 保存声音
	 * @param trackInfoVo
	 * @return
	 */
	@Operation(summary = "给指定专辑新增声音")
	@PostMapping("saveTrackInfo")
	public Result saveTrackInfo(@RequestBody @Validated TrackInfoVo trackInfoVo) {
		trackInfoService.saveTrackInfo(trackInfoVo);
		return Result.ok();
	}
	//声音列表
	@Operation(summary = "获取当前用户声音分页列表")
	@PostMapping("findUserTrackPage/{page}/{limit}")
	public Result<IPage<TrackListVo>> findUserTrackPage
	(@PathVariable Long page,
	 @PathVariable Long limit,
	 @RequestBody TrackInfoQuery trackInfoQuery){
		trackInfoQuery.setUserId(1L);
		Page<TrackListVo> pageParam = new Page<>(page, limit);
		IPage<TrackListVo> trackListVoIPage = trackInfoService.findUserTrackPage(pageParam,trackInfoQuery);
		// 返回数据
		return Result.ok(trackListVoIPage);
	}
	//删除声音
	@Operation(summary = "删除指定ID的声音")
	@DeleteMapping("removeTrackInfo/{id}")
	public Result removeTrackInfo(@PathVariable Long id) {
		trackInfoService.removeTrackInfo(id);
		return Result.ok();
	}
}

