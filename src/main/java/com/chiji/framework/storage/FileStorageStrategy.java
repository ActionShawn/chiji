// D:\Java Work Place\personal-develop\teeth-trace\server\src\main\java\com\chiji\framework\storage\FileStorageStrategy.java
package com.chiji.framework.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * 文件存储策略。
 * <p>
 * 抽象底层存储介质（本地磁盘 / OSS / 其他对象存储），上层 FileService 仅依赖本接口，
 * 通过 {@code chiji.storage.type} 切换具体实现：local（默认）/ oss（待扩展）。
 * 新增存储介质只需实现本接口并用 {@code @ConditionalOnProperty} 装配，业务层零改动。
 */
public interface FileStorageStrategy {

    /**
     * 存储文件到目标介质。
     *
     * @param file   上传的文件
     * @param userId 当前用户 ID（用于隔离存储目录）
     * @return 存储结果（含可访问 url、相对路径、大小、内容类型等）
     */
    StoredFile store(MultipartFile file, Long userId);

    /**
     * 存储类型标识，用于日志与诊断。
     *
     * @return 类型名（如 "local" / "oss"）
     */
    String type();

    /**
     * 生成客户端直传所需的 PUT 预签名信息。
     * <p>
     * 云托管场景下 callContainer 不适合传输文件，小程序改为直传对象存储：
     * 上层服务先生成与 {@link #store} 同规则的相对路径，再委托本方法签发签名。
     * 仅对象存储类介质支持直传，本地磁盘等无法接收外部 PUT 的实现返回 empty，
     * 上层据此提示「未启用 COS 直传」。
     *
     * @param relativePath 目标相对路径（与 {@link #store} 的路径规则一致）
     * @param contentType  上传对象的 Content-Type（如 image/jpeg），前端 PUT 需保持一致
     * @return 直传签名信息；不支持直传时返回 {@link Optional#empty()}
     */
    default Optional<DirectUpload> presign(String relativePath, String contentType) {
        return Optional.empty();
    }
}
