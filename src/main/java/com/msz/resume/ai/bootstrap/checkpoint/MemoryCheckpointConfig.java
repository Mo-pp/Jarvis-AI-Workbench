package com.msz.resume.ai.bootstrap.checkpoint;

import org.bsc.langgraph4j.checkpoint.MemorySaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


/**
 * 内存版的 CheckpointSaver 配置（开发测试用）
 * LangGraph4j 3.x+ 内置 MemorySaver，开箱即用
 */
@Configuration
@Profile("dev")  // 仅在 dev 环境生效
public class MemoryCheckpointConfig {

}
