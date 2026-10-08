package com.chiji.module.message.service.impl;

import com.chiji.entity.Message;
import com.chiji.entity.User;
import com.chiji.enums.MessageCategoryEnum;
import com.chiji.framework.wechat.WxSubscribeClient;
import com.chiji.module.auth.mapper.UserMapper;
import com.chiji.module.message.mapper.MessageMapper;
import com.chiji.module.message.service.TakeoffNotifyService;
import com.chiji.module.wear.support.TakeoffTimeoutSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 摘下超时提醒下发与留痕实现。见 {@link TakeoffNotifyService}。
 * <p>
 * 留痕策略：scene_date = NULL（唯一键不参与 NULL 比较，当日多次摘下互不冲突）；
 * {@code push_status = NONE} 先落库，下发成功后回写 {@code PUSHED + pushed_at}；
 * 下发失败保留 NONE（信箱仍可见提醒内容兜底），额度由调用方（handler）预扣、失败不回补。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TakeoffNotifyServiceImpl implements TakeoffNotifyService {

    private static final String PUSH_STATUS_NONE = "NONE";
    private static final String PUSH_STATUS_PUSHED = "PUSHED";

    private final UserMapper userMapper;
    private final MessageMapper messageMapper;
    private final WxSubscribeClient wxSubscribeClient;

    @Override
    public SendOutcome sendAndArchive(SendCommand cmd) {
        User user = cmd.userId() == null ? null : userMapper.selectById(cmd.userId());
        String openid = user == null ? null : user.getOpenid();
        if (openid == null || openid.isBlank()) {
            archive(cmd, PUSH_STATUS_NONE, null);
            return new SendOutcome(false, -1, "openid 缺失");
        }
        Long messageId = archive(cmd, PUSH_STATUS_NONE, null);
        Map<String, Object> data = buildTemplateData(cmd.takeoffAtText(), cmd.thing4());
        boolean success = wxSubscribeClient.sendTakeoffTimeoutMessage(openid, data);
        if (success) {
            if (messageId != null) {
                Message pushed = new Message();
                pushed.setId(messageId);
                pushed.setPushStatus(PUSH_STATUS_PUSHED);
                pushed.setPushedAt(LocalDateTime.now());
                messageMapper.updateById(pushed);
            }
            return new SendOutcome(true, 0, "ok");
        }
        return new SendOutcome(false, -1, "下发失败（详见服务端日志）");
    }

    /** 落一条 message 留痕，返回消息 ID（写库失败返回 null，不阻断下发）。 */
    private Long archive(SendCommand cmd, String pushStatus, LocalDateTime pushedAt) {
        try {
            Message message = new Message();
            message.setUserId(cmd.userId());
            message.setCategory(MessageCategoryEnum.WEAR.getCode());
            message.setReminderType(TakeoffTimeoutSpec.TASK_TYPE);
            message.setSceneDate(null);
            message.setPriority(1);
            message.setPushStatus(pushStatus);
            message.setPushedAt(pushedAt);
            message.setTitle(TakeoffTimeoutSpec.THING1);
            message.setBody(cmd.thing4());
            message.setRead(false);
            messageMapper.insert(message);
            return message.getId();
        } catch (Exception e) {
            log.warn("摘下超时提醒留痕失败, userId={}, sessionId 留痕降级", cmd.userId(), e);
            return null;
        }
    }

    /** 模板数据：thing1 固定标题 / thing4 最终文案 / time6 摘下时刻。 */
    private Map<String, Object> buildTemplateData(String takeoffAtText, String thing4) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("thing1", Map.of("value", TakeoffTimeoutSpec.THING1));
        data.put("thing4", Map.of("value", thing4));
        data.put("time6", Map.of("value", takeoffAtText));
        return data;
    }
}
