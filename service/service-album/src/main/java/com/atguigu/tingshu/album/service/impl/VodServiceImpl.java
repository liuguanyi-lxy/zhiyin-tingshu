package com.atguigu.tingshu.album.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.tingshu.album.config.VodConstantProperties;
import com.atguigu.tingshu.album.service.VodService;
import com.atguigu.tingshu.common.execption.GuiguException;
import com.atguigu.tingshu.common.result.ResultCodeEnum;
import com.atguigu.tingshu.common.util.UploadFileUtil;
import com.atguigu.tingshu.vo.album.TrackMediaInfoVo;
import com.qcloud.vod.VodUploadClient;
import com.qcloud.vod.model.VodUploadRequest;
import com.qcloud.vod.model.VodUploadResponse;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.vod.v20180717.VodClient;
import com.tencentcloudapi.vod.v20180717.models.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;


@Service
@Slf4j
public class VodServiceImpl implements VodService {
    @Autowired
    private VodConstantProperties vodConstantProperties;
    //根据声音文件在腾讯云服务器的文件ID删除声音文件
    @Override
    @SneakyThrows
    public void removeTrackMedia(String mediaFileId) {
        Credential cred = new Credential(vodConstantProperties.getSecretId(), vodConstantProperties.getSecretKey());
        // 实例化要请求产品的client对象,clientProfile是可选的
        VodClient client = new VodClient(cred, "");
        // 实例化一个请求对象,每个接口都会对应一个request对象
        DeleteMediaRequest req = new DeleteMediaRequest();
        req.setFileId(mediaFileId);
        // 返回的resp是一个DeleteMediaResponse的实例，与请求对象对应
        DeleteMediaResponse response = client.DeleteMedia(req);
        // 输出json格式的字符串回包
        log.info("声音删除返回结课: {}", JSON.toJSONString(response));
    }

    //根据声音文件ID从腾讯云点播服务器获取对应的媒体信息
    @Override
    /**
    * 使用Lombok的@SneakyThrows注解，用于自动抛出已检查的异常(checked exceptions)
    * 该注解会自动在方法体中添加try-catch块，将声明的异常抛出，无需手动编写try-catch代码
    * 这样可以简化异常处理代码，使代码更加简洁
    */
    @SneakyThrows
    public TrackMediaInfoVo getTrackMediaInfo(String mediaFileId) {
        //  初始化认证对象
        Credential cred = new Credential(vodConstantProperties.getSecretId(), vodConstantProperties.getSecretKey());
        // 实例化要请求产品的client对象,clientProfile是可选的
        VodClient client = new VodClient(cred, vodConstantProperties.getRegion());
        // 实例化一个请求对象,每个接口都会对应一个request对象
        DescribeMediaInfosRequest req = new DescribeMediaInfosRequest();
        //  设置当前fileIds
        req.setFileIds(new String[]{mediaFileId});
        // 返回的resp是一个DescribeMediaInfosResponse的实例，与请求对象对应
        DescribeMediaInfosResponse response = client.DescribeMediaInfos(req);
        log.info("声音详细返回结果：{}", JSON.toJSONString(response));
        //  判断对象不为空
        if (response.getMediaInfoSet().length>0){
            //  获取到
            MediaInfo mediaInfo = response.getMediaInfoSet()[0];
            //  创建流媒体信息对象
            TrackMediaInfoVo trackMediaInfoVo = new TrackMediaInfoVo();
            trackMediaInfoVo.setDuration(mediaInfo.getMetaData().getDuration());
            trackMediaInfoVo.setSize(mediaInfo.getMetaData().getSize());
            trackMediaInfoVo.setMediaUrl(mediaInfo.getBasicInfo().getMediaUrl());
            trackMediaInfoVo.setType(mediaInfo.getBasicInfo().getType());
            //  返回数据
            return trackMediaInfoVo;
        }
        return null;
    }
    //上传音频文件到腾讯云点播服务器,腾讯云服务器不能直接读取上传文件的流，而是将用户上传的文件保存到临时路径下，然后根据这个临时路径找到文件上传到腾讯云服务器
    @Override
    public Map<String, Object> uploadTrack(MultipartFile file) {
        //将文件保存在指定的临时路径下，没有就创建
        String tempPath = UploadFileUtil.uploadTempPath(vodConstantProperties.getTempPath(), file);
        //创建 VodUploadClient，设置腾讯云账户id和秘钥，用于与腾讯云点播服务器交互的客户端
        VodUploadClient client = new VodUploadClient(vodConstantProperties.getSecretId(), vodConstantProperties.getSecretKey());
        //创建request对象，封装需要数据
        VodUploadRequest request = new VodUploadRequest();
        //待上传的媒体文件路径。必须为本地路径，不支持 URL
        request.setMediaFilePath(tempPath);
        //设置任务流
        request.setProcedure(vodConstantProperties.getProcedure());
        try {
            //调用upload方法上传,根据文件的临时路径和期望上传地域进行上传，并接收上传后文件的文件ID和文件地址
            VodUploadResponse response = client.upload(vodConstantProperties.getRegion(), request);
            //  创建map 对象
            HashMap<String, Object> map = new HashMap<>();
            map.put("mediaFileId",response.getFileId());
            map.put("mediaUrl",response.getMediaUrl());
            //  返回map 数据
            return map;
        } catch (Exception e) {
            // 业务方进行异常处理
            throw new GuiguException(ResultCodeEnum.UPLOAD_ERROR);
        }
    }
}
