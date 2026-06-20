package com.atguigu.tingshu.album.service;

import org.springframework.web.multipart.MultipartFile;

public interface FileUploadService {
    //上传文件
    String upload(MultipartFile file);
}
