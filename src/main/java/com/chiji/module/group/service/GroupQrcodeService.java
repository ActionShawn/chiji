package com.chiji.module.group.service;

import com.chiji.module.group.dto.GroupQrcodeUpdateRequest;
import com.chiji.module.group.vo.GroupQrcodeVO;

/**
 * 用户交流群二维码配置服务。
 * <p>
 * 单行配置语义：读取不懒创建（无行即空配置，用户端渲染空态）；写入懒创建行并对
 * 唯一键冲突幂等回读。写入为 ADMIN 专属（路由层 {@code /api/admin/**} 统一强制校验）。
 */
public interface GroupQrcodeService {

    /**
     * 读取当前二维码配置。
     *
     * @return 配置视图；未配置时 imageUrl/updatedAt 均为 null（不报错）
     */
    GroupQrcodeVO getQrcode();

    /**
     * 更新二维码地址（管理员操作）。
     *
     * @param request     更新请求（含 imageUrl）
     * @param adminUserId 操作管理员用户 ID（落 updatedBy 审计列）
     * @return 保存后的整读（供管理端即时刷新预览）
     */
    GroupQrcodeVO updateQrcode(GroupQrcodeUpdateRequest request, Long adminUserId);
}
