package com.fisher.tsc.msg.controller;


import com.fisher.tsc.msg.common.JsonUtils;
import com.fisher.tsc.msg.convert.MessageConvert;
import com.fisher.tsc.msg.dto.MessageLogDto;
import com.fisher.tsc.msg.pojo.MessageLog;
import com.fisher.tsc.msg.service.IMessageLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("message")
@Slf4j
public class MessageLogController {

    @Autowired
    IMessageLogService iMessageLogService;

    @PostMapping("saveMessageWaitingConfirm")
    public String  saveMessageWaitingConfirm(@RequestBody MessageLogDto messageLogDto){
        log.info("saveMessageWaitingConfirm messageLogDto:{}",messageLogDto);
        MessageLog messageLog = MessageConvert.MAPPER.dtoToPojo(messageLogDto);
        messageLog.setMessageId(UUID.randomUUID().toString().replace("-",""));
        messageLogDto.getMessageBodyMap().put("messageId",messageLog.getMessageId());
        messageLog.setMessageBody(JsonUtils.toString(messageLogDto.getMessageBodyMap()));
        iMessageLogService.saveMessageWaitingConfirm(messageLog);
        return messageLog.getMessageId();
    }

    @PostMapping("confirmAndSendMessage/{messageId}")
    public boolean confirmAndSendMessage(@PathVariable String messageId){
        log.info("confirmAndSendMessage messageId:{}",messageId);

        return iMessageLogService.confirmAndSendMessage(messageId);
    }


    @PostMapping("consumerSuccess/{messageId}")
    public boolean consumerSuccess(@PathVariable String messageId){
        log.info("consumerSuccess messageId:{}",messageId);
        try {
            iMessageLogService.confirmConsumeSuccess(messageId);
        }catch (Exception e){
            log.error("confirmConsumeSuccess failure:",e);
            return false;
        }
        return true;
    }




}
