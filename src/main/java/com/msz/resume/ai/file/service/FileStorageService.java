package com.msz.resume.ai.file.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.msz.resume.ai.file.dto.ParsedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * 文件存储服务
 *
 * <p>将解析后的文件内容存储在 Redis 中，15 分钟自动过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final String KEY_PREFIX = "jarvis:file:";
    private static final Duration EXPIRATION = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 保存解析后的文件
     *
     * @param parsedFile 解析结果
     */
    public void save(ParsedFile parsedFile) {
        String key = getKey(parsedFile.getFileId());
        try {
            String json = objectMapper.writeValueAsString(parsedFile);
            redisTemplate.opsForValue().set(key, json, EXPIRATION);
            log.debug("[FileStorageService] 保存文件: {}, 过期时间: {} 分钟",
                    parsedFile.getFileId(), EXPIRATION.toMinutes());
        } catch (JsonProcessingException e) {
            log.error("[FileStorageService] 序列化文件失败: {}", parsedFile.getFileId(), e);
            throw new RuntimeException("文件存储失败", e);
        }
    }

    /**
     * 获取解析后的文件
     *
     * @param fileId 文件ID
     * @return 解析结果，不存在返回 empty
     */
    public Optional<ParsedFile> get(String fileId) {
        String key = getKey(fileId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            log.debug("[FileStorageService] 文件不存在或已过期: {}", fileId);
            return Optional.empty();
        }
        try {
            ParsedFile parsedFile = objectMapper.readValue(json, ParsedFile.class);
            return Optional.of(parsedFile);
        } catch (JsonProcessingException e) {
            log.error("[FileStorageService] 反序列化文件失败: {}", fileId, e);
            return Optional.empty();
        }
    }

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     */
    public void delete(String fileId) {
        String key = getKey(fileId);
        redisTemplate.delete(key);
        log.debug("[FileStorageService] 删除文件: {}", fileId);
    }

    /**
     * 检查文件是否存在
     *
     * @param fileId 文件ID
     * @return 是否存在
     */
    public boolean exists(String fileId) {
        String key = getKey(fileId);
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    private String getKey(String fileId) {
        return KEY_PREFIX + fileId;
    }
}
