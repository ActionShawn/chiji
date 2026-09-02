package com.chiji.module.file.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.enums.MediaKindEnum;
import com.chiji.framework.storage.DirectUpload;
import com.chiji.framework.storage.FileStorageStrategy;
import com.chiji.framework.storage.StoredFile;
import com.chiji.module.file.service.FileService;
import com.chiji.module.file.vo.FileUploadVO;
import com.chiji.module.file.vo.UploadUrlVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 文件上传服务实现。
 * <p>
 * 提供两条能力：
 * <ul>
 *   <li>{@code /api/files/upload}（multipart，Swagger/浏览器联调用）：类型/大小/时长校验后委托存储策略落盘；</li>
 *   <li>{@code /api/files/upload-url}（COS 直传签名，小程序主通道）：按扩展名判定类型并校验视频时长，
 *       生成与落盘同规则的相对路径后签发 PUT 预签名地址，文件内容由小程序直传 COS，后端不转发字节流。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    /** 图片大小上限 10MB */
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    /** 视频大小上限 20MB */
    private static final long MAX_VIDEO_SIZE = 20L * 1024 * 1024;
    /** 视频时长上限 15 秒 */
    private static final int MAX_VIDEO_DURATION = 15;

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 扩展名 → contentType 映射（直传通道无真实文件头，按 fileName 扩展名判定类型） */
    private static final Map<String, String> EXT_CONTENT_TYPE = Map.ofEntries(
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("heic", "image/heic"),
            Map.entry("heif", "image/heif"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("mov", "video/quicktime"),
            Map.entry("m4v", "video/x-m4v"),
            Map.entry("3gp", "video/3gpp"));

    private final FileStorageStrategy storage;

    @Override
    public FileUploadVO upload(MultipartFile file, Long userId, Integer duration) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要上传的文件");
        }
        MediaKindEnum kind = resolveKind(file.getContentType());
        long size = file.getSize();
        validateSize(kind, size);
        Integer safeDuration = validateDuration(kind, duration);

        StoredFile stored = storage.store(file, userId);
        log.info("文件上传成功(后端落盘), userId={}, type={}, size={}, url={}", userId, kind.getCode(), size, stored.url());
        return new FileUploadVO(stored.url(), kind.getCode(), safeDuration, size, stored.originalFilename());
    }

    @Override
    public UploadUrlVO createUploadUrl(String fileName, Integer duration, Long userId) {
        String contentType = resolveContentType(fileName);
        MediaKindEnum kind = resolveKind(contentType);
        Integer safeDuration = validateDuration(kind, duration);
        // 注意：直传不经后端转发，文件大小无法在此校验，由前端选图压缩与业务约束保证

        // 与落盘通道保持同一相对路径规则，保证直传与中转上传的 URL 格式一致
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        String relative = userId + "/"
                + LocalDate.now().format(DATE_DIR) + "/"
                + IdWorker.getIdStr() + "." + ext;

        DirectUpload ticket = storage.presign(relative, contentType)
                .orElseThrow(() -> new BusinessException(ErrorCode.OSS_ERROR, "当前存储模式不支持直传，请启用 COS 存储"));
        log.info("签发直传签名成功, userId={}, type={}, relative={}", userId, kind.getCode(), relative);
        return new UploadUrlVO(ticket.uploadUrl(), ticket.url(), kind.getCode(), contentType, fileName);
    }

    /**
     * 大小校验：图片 ≤10MB、视频 ≤20MB。
     */
    private void validateSize(MediaKindEnum kind, long size) {
        if (kind == MediaKindEnum.IMAGE && size > MAX_IMAGE_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "图片不能超过10MB");
        }
        if (kind == MediaKindEnum.VIDEO && size > MAX_VIDEO_SIZE) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE, "视频不能超过20MB");
        }
    }

    /**
     * 视频时长校验（duration 由前端从 wx.chooseMedia 透传）；图片强制置空，落库对齐 {@code record_media.duration} 语义。
     *
     * @return 安全时长：视频返回校验通过的 duration，图片返回 null
     */
    private Integer validateDuration(MediaKindEnum kind, Integer duration) {
        if (kind == MediaKindEnum.VIDEO) {
            if (duration == null || duration <= 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少视频时长");
            }
            if (duration > MAX_VIDEO_DURATION) {
                throw new BusinessException(ErrorCode.VIDEO_DURATION_TOO_LONG);
            }
            return duration;
        }
        return null;
    }

    /**
     * 按 contentType 判定媒体类型，非图片/视频抛 {@link ErrorCode#FILE_TYPE_NOT_SUPPORTED}。
     */
    private MediaKindEnum resolveKind(String contentType) {
        if (contentType == null) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        if (contentType.startsWith("image/")) {
            return MediaKindEnum.IMAGE;
        }
        if (contentType.startsWith("video/")) {
            return MediaKindEnum.VIDEO;
        }
        throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
    }

    /**
     * 按文件名扩展名推导 contentType，未知扩展名抛 {@link ErrorCode#FILE_TYPE_NOT_SUPPORTED}。
     */
    private String resolveContentType(String fileName) {
        if (fileName == null || fileName.indexOf('.') < 0) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        String contentType = EXT_CONTENT_TYPE.get(ext);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
        }
        return contentType;
    }
}
