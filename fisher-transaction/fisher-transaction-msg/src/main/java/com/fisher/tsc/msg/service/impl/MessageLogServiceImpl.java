package com.fisher.tsc.msg.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fisher.tsc.msg.common.IOCUtil;
import com.fisher.tsc.msg.common.MessageStatusEnum;
import com.fisher.tsc.msg.common.PublicEnum;
import com.fisher.tsc.msg.dto.EventTypeEnum;
import com.fisher.tsc.msg.mapper.MessageLogMapper;
import com.fisher.tsc.msg.pojo.MessageLog;
import com.fisher.tsc.msg.service.IMessageEventHandler;
import com.fisher.tsc.msg.service.IMessageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;


@Service
@Slf4j
public class MessageLogServiceImpl extends ServiceImpl<MessageLogMapper, MessageLog>
        implements IMessageLogService, InitializingBean {

    @Autowired
    MessageLogMapper messageLogMapper;

    @Override
    public String saveMessageWaitingConfirm(MessageLog messageLog) {
        messageLog.setStatus(MessageStatusEnum.WAITING_CONFIRM.name());
        messageLog.setDead(PublicEnum.NO.getCode());
        messageLog.setCreateTime(new Date());
        messageLog.setUpdateTime(new Date());
        boolean saveFlag = save(messageLog);
        if (saveFlag){
            return messageLog.getMessageId();
        }
        return null;
    }

    @Override
    public boolean confirmAndSendMessage(String messageId) {
        MessageLog messageLog = messageLogMapper.queryMessageLogByMessageId(messageId);
        if (messageLog != null){
            String messageBody = messageLog.getMessageBody();
            String eventType = messageLog.getEventType();
            IMessageEventHandler iMessageEventHandler = handlers.get(eventType);//获取该事件对应的处理器
            if (iMessageEventHandler != null){
                iMessageEventHandler.sendMsg(messageLog.getMessageId(),messageBody);//发送消息
                messageLog.setStatus(MessageStatusEnum.SENDING.name());
                updateById(messageLog);//更新消息的状态为发送中
                return true;
            }else{
                log.warn("confirmAndSendMessage iMessageEventHandler not exist：{}",messageLog);
            }
        }else{
            log.warn("messageLog not exist:{}",messageId);
        }
        return false;
    }

    @Override
    public void confirmConsumeSuccess(String messageId) {
        MessageLog messageLog = messageLogMapper.queryMessageLogByMessageId(messageId);
        if (messageLog == null){
            throw new RuntimeException("未找到该消息");
        }
        IMessageEventHandler iMessageEventHandler = handlers.get(messageLog.getEventType());
        if (iMessageEventHandler == null){
            throw new RuntimeException("未找到该事件对应的处理器");
        }
        iMessageEventHandler.confirmConsumeSuccess(messageLog);
    }

    @Override
    public void doBatchHandleWaitingMessage() {
        Calendar nowTime = Calendar.getInstance();
        nowTime.add(Calendar.MINUTE, -1);//当前时间减去1分钟

        IPage<MessageLog> iPage = messageLogMapper.selectPage(new Page<MessageLog>(1, 50),
                new QueryWrapper<MessageLog>()
                        .eq("status", MessageStatusEnum.WAITING_CONFIRM.getCode())//
                        .le("create_time",nowTime.getTime())
        );
        List<MessageLog> records = iPage.getRecords();
        records.stream().forEach(item -> {
            String eventType = item.getEventType();
            IMessageEventHandler iMessageEventHandler = handlers.get(eventType);
            if (iMessageEventHandler == null){
                throw new RuntimeException("未找到该事件对应的处理器");
            }
            iMessageEventHandler.doHandleWaitingMessage(item);
        });
    }

    @Override
    public void doBatchHandleSendingMessage() {
        Calendar nowTime = Calendar.getInstance();
        nowTime.add(Calendar.MINUTE, -1);//当前时间减去1分钟

        IPage<MessageLog> iPage = messageLogMapper.selectPage(new Page<MessageLog>(1, 50),
                new QueryWrapper<MessageLog>()
                        .eq("status", MessageStatusEnum.SENDING.getCode())//
                        .eq("dead", PublicEnum.NO.getCode())
                        .le("create_time",nowTime.getTime())
        );
        List<MessageLog> records = iPage.getRecords();
        records.stream().forEach(item -> {
            String eventType = item.getEventType();
            IMessageEventHandler iMessageEventHandler = handlers.get(eventType);
            if (iMessageEventHandler == null){
                throw new RuntimeException("未找到该事件对应的处理器");
            }
            iMessageEventHandler.doHandleSendingMessage(item);
        });
    }


    private HashMap<String,IMessageEventHandler> handlers;

    @Override
    public void afterPropertiesSet() throws Exception {
        handlers = new HashMap<>();
        handlers.put(EventTypeEnum.CAPITAL_TO_TREASURE.getCode(),
                IOCUtil.getBean(MessageEventCapitalToTreasureHandler.class));
    }


}
