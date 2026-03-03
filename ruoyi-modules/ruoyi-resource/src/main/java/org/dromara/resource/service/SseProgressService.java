package org.dromara.resource.service;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 进度推送服务
 * 管理 SseEmitter 连接，向前端实时推送标书生成进度
 *
 * @author ruoyi
 * @date 2026-03-03
 */
@Slf4j
@Service
public class SseProgressService {

    /**
     * 管理所有活跃的 SSE 连接（submissionId -> SseEmitter）
     */
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 创建 SSE 连接（前端订阅时调用）
     *
     * @param submissionId 投标项目ID
     * @return SseEmitter 实例
     */
    public SseEmitter createEmitter(Long submissionId) {
        // 超时时间 30 分钟（生成过程可能较长）
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitter.onCompletion(() -> {
            emitters.remove(submissionId);
            log.debug("SSE 连接完成，submissionId: {}", submissionId);
        });
        emitter.onTimeout(() -> {
            emitters.remove(submissionId);
            log.debug("SSE 连接超时，submissionId: {}", submissionId);
        });
        emitter.onError(ex -> {
            emitters.remove(submissionId);
            log.debug("SSE 连接错误，submissionId: {}", submissionId);
        });

        emitters.put(submissionId, emitter);
        log.debug("SSE 连接已建立，submissionId: {}", submissionId);
        return emitter;
    }

    /**
     * 推送进度事件到前端
     *
     * @param submissionId 投标项目ID
     * @param data         进度数据对象（会序列化为 JSON）
     */
    public void push(Long submissionId, Object data) {
        SseEmitter emitter = emitters.get(submissionId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(
                SseEmitter.event()
                    .name("progress")
                    .data(JSON.toJSONString(data))
            );
        } catch (IOException e) {
            log.warn("SSE 推送失败，submissionId: {}, 原因: {}", submissionId, e.getMessage());
            emitters.remove(submissionId);
        }
    }

    /**
     * 完成 SSE 连接（生成结束时调用）
     *
     * @param submissionId 投标项目ID
     */
    public void complete(Long submissionId) {
        SseEmitter emitter = emitters.get(submissionId);
        if (emitter != null) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("complete")
                        .data("{\"status\":\"completed\"}")
                );
                emitter.complete();
            } catch (IOException e) {
                log.warn("SSE 完成发送失败，submissionId: {}", submissionId);
            } finally {
                emitters.remove(submissionId);
            }
        }
    }

    /**
     * 通知前端生成失败
     *
     * @param submissionId 投标项目ID
     * @param errorMessage 错误信息
     */
    public void fail(Long submissionId, String errorMessage) {
        SseEmitter emitter = emitters.get(submissionId);
        if (emitter != null) {
            try {
                emitter.send(
                    SseEmitter.event()
                        .name("error")
                        .data("{\"status\":\"failed\",\"error\":\"" + errorMessage.replace("\"", "'") + "\"}")
                );
                emitter.complete();
            } catch (IOException e) {
                log.warn("SSE 错误发送失败，submissionId: {}", submissionId);
            } finally {
                emitters.remove(submissionId);
            }
        }
    }

}
