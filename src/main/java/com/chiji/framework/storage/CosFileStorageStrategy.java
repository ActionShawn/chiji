package com.chiji.framework.storage;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.region.Region;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Optional;

/**
 * 腾讯云 COS 存储策略。
 * <p>
 * 路径规则：{@code {userId}/{yyyy/MM/dd}/{snowflakeId}.{ext}}，与本地策略保持一致。
 * 仅在 {@code chiji.storage.type=cos} 时装配，供云托管等无状态容器环境使用（容器本地盘不可持久化）。
 * COS 客户端懒加载：首次使用时创建，未配置环境变量时不阻塞应用启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chiji.storage", name = "type", havingValue = "cos")
public class CosFileStorageStrategy implements FileStorageStrategy {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 预签名有效期 15 分钟（小程序选图到上传完成通常远小于此窗口）。 */
    private static final long PRESIGN_EXPIRATION_MILLIS = 15L * 60 * 1000;

    private final StorageProperties props;

    /** COS 客户端（volatile + 双重检查，保证线程安全）。 */
    private volatile COSClient cosClient;

    @Override
    public StoredFile store(MultipartFile file, Long userId) {
        CosStorageProperties cos = props.getCos();
        String original = file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(original);
        if (!StringUtils.hasText(ext)) {
            // 无扩展名时按 contentType 退化推断，避免文件无后缀
            ext = extFromContentType(file.getContentType());
        }

        String relative = userId + "/"
                + LocalDate.now().format(DATE_DIR) + "/"
                + IdWorker.getIdStr() + "." + ext;

        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        metadata.setContentType(file.getContentType());
        try (InputStream in = file.getInputStream()) {
            client().putObject(cos.getBucket(), relative, in, metadata);
        } catch (IOException e) {
            log.error("COS 存储失败, userId={}, relative={}", userId, relative, e);
            throw new BusinessException(ErrorCode.OSS_ERROR, "文件保存失败");
        }

        String url = cos.getBaseUrl() + "/" + relative;
        log.info("文件已存储(COS), userId={}, relative={}, size={}", userId, relative, file.getSize());
        return new StoredFile(url, relative, file.getSize(), file.getContentType(), original);
    }

    @Override
    public String type() {
        return "cos";
    }

    @Override
    public Optional<DirectUpload> presign(String relativePath, String contentType) {
        CosStorageProperties cos = props.getCos();
        Date expiration = new Date(System.currentTimeMillis() + PRESIGN_EXPIRATION_MILLIS);
        GeneratePresignedUrlRequest request =
                new GeneratePresignedUrlRequest(cos.getBucket(), relativePath, HttpMethodName.PUT);
        request.setExpiration(expiration);
        URL signedUrl = client().generatePresignedUrl(request);
        String url = cos.getBaseUrl() + "/" + relativePath;
        log.info("生成 COS 直传签名, relative={}, 有效期={}ms", relativePath, PRESIGN_EXPIRATION_MILLIS);
        return Optional.of(new DirectUpload(signedUrl.toString(), url, contentType));
    }

    /**
     * 获取 COS 客户端（懒加载 + 配置完整性校验）。
     *
     * @return COS 客户端
     */
    private COSClient client() {
        COSClient local = cosClient;
        if (local == null) {
            synchronized (this) {
                local = cosClient;
                if (local == null) {
                    CosStorageProperties cos = props.getCos();
                    if (!StringUtils.hasText(cos.getSecretId()) || !StringUtils.hasText(cos.getSecretKey())
                            || !StringUtils.hasText(cos.getBucket()) || !StringUtils.hasText(cos.getBaseUrl())) {
                        throw new BusinessException(ErrorCode.OSS_ERROR, "COS 尚未配置，请检查相关环境变量");
                    }
                    BasicCOSCredentials credentials = new BasicCOSCredentials(cos.getSecretId(), cos.getSecretKey());
                    ClientConfig clientConfig = new ClientConfig(new Region(cos.getRegion()));
                    local = new COSClient(credentials, clientConfig);
                    cosClient = local;
                }
            }
        }
        return local;
    }

    /**
     * 按 contentType 推断扩展名。
     */
    private String extFromContentType(String ct) {
        if (ct == null) {
            return "bin";
        }
        return switch (ct) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp", "image/gif" -> "webp";
            case "video/mp4" -> "mp4";
            case "video/quicktime" -> "mov";
            default -> "bin";
        };
    }
}
