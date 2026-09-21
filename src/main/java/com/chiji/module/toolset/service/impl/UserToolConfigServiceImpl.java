package com.chiji.module.toolset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chiji.common.core.exception.BusinessException;
import com.chiji.common.core.exception.ErrorCode;
import com.chiji.entity.UserToolConfig;
import com.chiji.module.toolset.dto.ToolKeysUpdateRequest;
import com.chiji.module.toolset.mapper.UserToolConfigMapper;
import com.chiji.module.toolset.service.UserToolConfigService;
import com.chiji.module.toolset.vo.ToolConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 首页常用工具配置服务实现。
 * <p>
 * 读取不懒创建（无行即空配置）；写入懒创建行并对唯一键冲突幂等回读。
 * key 规范：{@code [a-z0-9_-]{1,32}}，去重保序，至多 {@value #MAX_TOOLS} 个；
 * 未知 key 不拦截——工具字典在客户端，允许客户端先于服务端引入新工具。
 */
@Service
@RequiredArgsConstructor
public class UserToolConfigServiceImpl implements UserToolConfigService {

    /** 工具 key 规范：小写字母/数字/下划线/中划线，1-32 位 */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_-]{1,32}$");

    /** 常用工具数量上限 */
    private static final int MAX_TOOLS = 12;

    private final UserToolConfigMapper userToolConfigMapper;

    @Override
    public ToolConfigVO getToolConfig(Long userId) {
        UserToolConfig row = userToolConfigMapper.selectOne(new LambdaQueryWrapper<UserToolConfig>()
                .eq(UserToolConfig::getUserId, userId));
        return new ToolConfigVO(parseKeys(row == null ? null : row.getToolKeys()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ToolConfigVO updateToolKeys(Long userId, ToolKeysUpdateRequest req) {
        if (req == null || req.toolKeys() == null) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_PARAM_INVALID, "工具列表不能为空");
        }
        List<String> keys = normalize(req.toolKeys());
        UserToolConfig row = ensureConfig(userId);
        row.setToolKeys(String.join(",", keys));
        userToolConfigMapper.updateById(row);
        return new ToolConfigVO(keys);
    }

    /** 规范化 key 列表：trim/转小写、去空、去重保序、逐个校验格式与数量上限。 */
    private List<String> normalize(List<String> raw) {
        List<String> keys = new ArrayList<>();
        for (String item : raw) {
            String key = item == null ? "" : item.trim().toLowerCase();
            if (key.isEmpty()) {
                continue;
            }
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw new BusinessException(ErrorCode.TOOL_CONFIG_PARAM_INVALID, "工具标识不合法：" + key);
            }
            if (!keys.contains(key)) {
                keys.add(key);
            }
        }
        if (keys.size() > MAX_TOOLS) {
            throw new BusinessException(ErrorCode.TOOL_CONFIG_PARAM_INVALID, "最多选择 " + MAX_TOOLS + " 个常用工具");
        }
        return keys;
    }

    /** 解析存储的 key 列表（逗号分隔；空串/空行即空配置）。 */
    private List<String> parseKeys(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return Arrays.stream(stored.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** 懒创建 user_tool_config 行（唯一键冲突幂等回读）。 */
    private UserToolConfig ensureConfig(Long userId) {
        UserToolConfig row = userToolConfigMapper.selectOne(new LambdaQueryWrapper<UserToolConfig>()
                .eq(UserToolConfig::getUserId, userId));
        if (row != null) {
            return row;
        }
        UserToolConfig created = new UserToolConfig();
        created.setUserId(userId);
        created.setToolKeys("");
        try {
            userToolConfigMapper.insert(created);
        } catch (DuplicateKeyException e) {
            // 并发首次创建，另一线程已插入，直接回读
        }
        return userToolConfigMapper.selectOne(new LambdaQueryWrapper<UserToolConfig>()
                .eq(UserToolConfig::getUserId, userId));
    }
}
