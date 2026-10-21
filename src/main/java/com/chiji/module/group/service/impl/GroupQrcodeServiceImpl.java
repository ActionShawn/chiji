package com.chiji.module.group.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.GroupQrcodeConfig;
import com.chiji.module.group.dto.GroupQrcodeUpdateRequest;
import com.chiji.module.group.mapper.GroupQrcodeConfigMapper;
import com.chiji.module.group.service.GroupQrcodeService;
import com.chiji.module.group.vo.GroupQrcodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户交流群二维码配置服务实现。
 * <p>
 * 读取不懒创建（无行即空配置，返回 imageUrl=null 的 VO，用户端渲染空态而非报错）；
 * 写入懒创建行并对唯一键冲突幂等回读。地址校验：仅接受 https（真机 image 组件对 http
 * 远端图不加载）、长度 ≤ {@value #MAX_URL_LENGTH}；不回源校验可达性（上传通道已保证文件存在）。
 * <p>
 * updatedAt 刷新说明：全局 {@code MyMetaObjectHandler#updateFill} 为 strictUpdateFill
 * （仅对 null 字段填充），读出的行 updatedAt 非空不会被自动刷新，故更新路径显式
 * {@code setUpdatedAt(now())} 保证「更新于」时间随每次换图推进。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupQrcodeServiceImpl implements GroupQrcodeService {

    /** 单行配置键 */
    private static final String CONFIG_KEY = "GROUP_QRCODE";

    /** 二维码地址长度上限（对齐表列 VARCHAR(512)） */
    private static final int MAX_URL_LENGTH = 512;

    private final GroupQrcodeConfigMapper groupQrcodeConfigMapper;

    @Override
    public GroupQrcodeVO getQrcode() {
        GroupQrcodeConfig row = selectRow();
        if (row == null || !StringUtils.hasText(row.getImageUrl())) {
            return new GroupQrcodeVO(null, null);
        }
        return new GroupQrcodeVO(row.getImageUrl(), row.getUpdatedAt());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupQrcodeVO updateQrcode(GroupQrcodeUpdateRequest request, Long adminUserId) {
        String url = normalizeUrl(request);
        LocalDateTime now = LocalDateTime.now();

        GroupQrcodeConfig row = selectRow();
        if (row == null) {
            row = new GroupQrcodeConfig();
            row.setConfigKey(CONFIG_KEY);
            row.setImageUrl(url);
            row.setUpdatedBy(adminUserId);
            row.setUpdatedAt(now);
            try {
                groupQrcodeConfigMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发首次创建：另一管理员已插入行，转为更新语义重写（幂等兜底）
                log.info("交流群二维码配置并发首插冲突，转更新, adminUserId={}", adminUserId);
                row = selectRow();
                applyUpdate(row, url, adminUserId, now);
            }
        } else {
            applyUpdate(row, url, adminUserId, now);
        }
        log.info("交流群二维码已更新, adminUserId={}, url={}", adminUserId, url);
        // 写后整读：updatedAt 以落库值为准（fill 语义与并发时序不依赖内存对象）
        return getQrcode();
    }

    /** 更新既有行：换图 + 刷新审计列 + 显式推进 updatedAt（strictUpdateFill 不覆盖非空字段）。 */
    private void applyUpdate(GroupQrcodeConfig row, String url, Long adminUserId, LocalDateTime now) {
        row.setImageUrl(url);
        row.setUpdatedBy(adminUserId);
        row.setUpdatedAt(now);
        groupQrcodeConfigMapper.updateById(row);
    }

    /** 规范化并校验入参地址：trim、必须 https、长度上限。 */
    private String normalizeUrl(GroupQrcodeUpdateRequest request) {
        String url = request == null || request.imageUrl() == null ? "" : request.imageUrl().trim();
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码地址不能为空");
        }
        if (!url.startsWith("https://")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码地址必须为 https 链接");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二维码地址过长");
        }
        return url;
    }

    /** 读取单行配置（无行返回 null，读取方按空配置兜底）。 */
    private GroupQrcodeConfig selectRow() {
        return groupQrcodeConfigMapper.selectOne(new LambdaQueryWrapper<GroupQrcodeConfig>()
                .eq(GroupQrcodeConfig::getConfigKey, CONFIG_KEY)
                .last("LIMIT 1"));
    }
}
