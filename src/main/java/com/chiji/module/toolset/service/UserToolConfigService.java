package com.chiji.module.toolset.service;

import com.chiji.module.toolset.dto.ToolKeysUpdateRequest;
import com.chiji.module.toolset.vo.ToolConfigVO;

/**
 * 首页常用工具配置服务。
 * <p>
 * 工具字典（名称/图标/页面地址/默认顺序）由客户端承载，服务端仅存储与回读用户的选择与排序；
 * key 合法性/数量上限由服务端兜底校验，未知 key 不拦截（允许客户端先行发版引入新工具）。
 */
public interface UserToolConfigService {

    /**
     * 读取当前用户的常用工具配置。
     *
     * @param userId 用户 ID
     * @return 工具 key 有序列表；未自定义时为空列表（客户端回退默认顺序）
     */
    ToolConfigVO getToolConfig(Long userId);

    /**
     * 全量覆盖保存常用工具配置（顺序即展示顺序）。
     *
     * @param userId 用户 ID
     * @param req    更新请求（toolKeys 必填，允许空数组=清空自定义）
     * @return 保存后的配置
     */
    ToolConfigVO updateToolKeys(Long userId, ToolKeysUpdateRequest req);
}
