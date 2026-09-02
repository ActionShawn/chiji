// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\module\file\service\FileService.java
package com.chiji.module.file.service;

import com.chiji.module.file.vo.FileUploadVO;
import com.chiji.module.file.vo.UploadUrlVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务。
 * <p>
 * 负责类型/大小/时长校验，委托 {@link com.chiji.framework.storage.FileStorageStrategy} 完成落盘，
 * 最终组装为 {@link FileUploadVO} 返回。
 */
public interface FileService {

    /**
     * 上传单个媒体文件（multipart，Swagger/浏览器联调用）。
     *
     * @param file     multipart 文件
     * @param userId   当前用户 ID（用于隔离存储目录）
     * @param duration 视频时长（秒），图片传 null
     * @return 上传结果
     */
    FileUploadVO upload(MultipartFile file, Long userId, Integer duration);

    /**
     * 获取 COS 直传签名（小程序主通道）。
     * <p>
     * 小程序直传对象存储，后端不再转发文件内容：按扩展名判定媒体类型并校验视频时长，
     * 生成与 {@link #upload} 同规则的相对路径后，委托存储策略签发 PUT 预签名地址。
     *
     * @param fileName 文件名（扩展名用于判定媒体类型）
     * @param duration 视频时长（秒），图片传 null
     * @param userId   当前用户 ID（用于隔离存储目录）
     * @return 直传签名（uploadUrl 供 PUT，url 为最终访问地址）
     */
    UploadUrlVO createUploadUrl(String fileName, Integer duration, Long userId);
}
