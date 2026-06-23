package com.atguigu.tingshu.common.util;

import lombok.SneakyThrows;
import org.joda.time.DateTime;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

//文件上传工具类，处理文件上传，并将上传的文件保存到服务器的本地临时目录中。
public class UploadFileUtil {

    /**
     * 文件上传到临时路径
     * @param file
     * @return
     */
    @SneakyThrows
    public static String uploadTempPath(String tempPath, MultipartFile file) {
        if (null == file) return "";
        String date = new DateTime().toString("yyyyMMdd");
        String filePath = tempPath + File.separator + date;
        File curFlie = new File(filePath);
        if (!curFlie.exists()) {
            curFlie.mkdirs();
        }
        filePath = filePath + File.separator + file.getOriginalFilename();
        file.transferTo(new File(filePath));
        return filePath;
    }
}
