// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\storage\LocalFileStorageStrategy.java
package com.chiji.framework.storage;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 本地磁盘存储策略。
 * <p>
 * 路径规则：{@code {base-path}/{userId}/{yyyy/MM/dd}/{snowflakeId}.{ext}}。
 * 默认启用（{@code chiji.storage.type} 未配置或为 local 时生效）。
 * 访问 URL 经 {@link WebConfig} 的 {@code /uploads/**} 静态资源映射对外暴露。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "chiji.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageStrategy implements FileStorageStrategy {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties props;

    @Override
    public StoredFile store(MultipartFile file, Long userId) {
        LocalStorageProperties local = props.getLocal();
        String original = file.getOriginalFilename();
        String ext = StringUtils.getFilenameExtension(original);
        if (!StringUtils.hasText(ext)) {
            // 无扩展名时按 contentType 退化推断，避免文件无后缀
            ext = extFromContentType(file.getContentType());
        }

        String relative = userId + "/"
                + LocalDate.now().format(DATE_DIR) + "/"
                + IdWorker.getIdStr() + "." + ext;
        Path target = Paths.get(local.getBasePath()).resolve(relative).toAbsolutePath().normalize();

        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("本地存储失败, userId={}, relative={}", userId, relative, e);
            throw new BusinessException(ErrorCode.OSS_ERROR, "文件保存失败");
        }

        String url = local.getBaseUrl() + "/" + relative;
        log.info("文件已存储(本地), userId={}, relative={}, size={}", userId, relative, file.getSize());
        return new StoredFile(url, relative, file.getSize(), file.getContentType(), original);
    }

    @Override
    public String type() {
        return "local";
    }

    @Override
    public void delete(String url) {
        if (!StringUtils.hasText(url)) {
            return;
        }
        LocalStorageProperties local = props.getLocal();
        String prefix = normalizeBaseUrl(local.getBaseUrl());
        if (prefix == null || !url.startsWith(prefix)) {
            // URL 不属于本介质（如已切换到 COS），记日志后静默跳过，不抛异常
            log.warn("URL 不属于本地存储介质，跳过删除, url={}", url);
            return;
        }
        String relative = url.substring(prefix.length());
        Path base = Paths.get(local.getBasePath()).toAbsolutePath().normalize();
        Path target = base.resolve(relative).normalize();
        if (!target.startsWith(base)) {
            // 防目录穿越：反解后的目标必须落在存储根目录内
            log.warn("本地删除路径越界，跳过, relative={}", relative);
            return;
        }
        try {
            boolean existed = Files.deleteIfExists(target);
            log.info("本地文件已删除, relative={}, existed={}", relative, existed);
        } catch (IOException e) {
            // 删除失败只记 warn，绝不影响已完成的注销
            log.warn("本地文件删除失败, relative={}", relative, e);
        }
    }

    /**
     * 归一化对外 URL 前缀为「以 / 结尾」形式，便于剥前缀得到相对路径。
     *
     * @param baseUrl 配置的 URL 前缀
     * @return 归一化前缀；未配置时为 null
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
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
