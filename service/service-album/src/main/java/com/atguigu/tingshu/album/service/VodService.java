package com.atguigu.tingshu.album.service;

import com.atguigu.tingshu.model.album.TrackInfo;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface VodService {

    //根据声音文件在腾讯云服务器的文件ID获取其媒体信息
    TrackMediaInfoVo getTrackMediaInfo(String mediaFileId);

    //上传声音文件
    Map<String, Object> uploadTrack(MultipartFile file);
    //根据声音文件在腾讯云服务器的文件ID删除声音文件
    void removeTrackMedia(String mediaFileId);
}
