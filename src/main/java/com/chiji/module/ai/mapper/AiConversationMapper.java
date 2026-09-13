package com.chiji.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chiji.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 问答会话 Mapper。
 * <p>
 * 仅允许被 module 的 service 层访问（ArchUnit 守护）。
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {
}
