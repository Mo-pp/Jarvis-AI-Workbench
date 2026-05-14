package com.msz.resume.ai.chat.prompt.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserContext 单元测试
 */
@DisplayName("UserContext 测试")
class UserContextTest {

    @Test
    @DisplayName("empty() 应返回默认值的上下文")
    void testEmpty() {
        UserContext context = UserContext.empty();

        assertNull(context.userId());
        assertNull(context.username());
        assertNull(context.role());
        assertEquals("zh-CN", context.language());
        assertEquals("concise", context.outputStyle());
        assertTrue(context.businessContext().isEmpty());
    }

    @Test
    @DisplayName("Builder 应正确构建完整上下文")
    void testBuilderFull() {
        Map<String, Object> businessCtx = Map.of("key", "value");

        UserContext context = UserContext.builder()
                .userId("user-123")
                .username("张三")
                .role("admin")
                .language("en-US")
                .outputStyle("detailed")
                .businessContext(businessCtx)
                .build();

        assertEquals("user-123", context.userId());
        assertEquals("张三", context.username());
        assertEquals("admin", context.role());
        assertEquals("en-US", context.language());
        assertEquals("detailed", context.outputStyle());
        assertEquals(businessCtx, context.businessContext());
    }

    @Test
    @DisplayName("Builder 应使用默认值")
    void testBuilderDefaults() {
        UserContext context = UserContext.builder()
                .userId("user-456")
                .build();

        assertEquals("user-456", context.userId());
        assertNull(context.username());
        assertNull(context.role());
        assertEquals("zh-CN", context.language());
        assertEquals("concise", context.outputStyle());
        assertTrue(context.businessContext().isEmpty());
    }

    @Test
    @DisplayName("Builder 方法应支持链式调用")
    void testBuilderChaining() {
        UserContext.Builder builder = UserContext.builder();

        assertSame(builder, builder.userId("test"));
        assertSame(builder, builder.username("test"));
        assertSame(builder, builder.role("test"));
        assertSame(builder, builder.language("test"));
        assertSame(builder, builder.outputStyle("test"));
        assertSame(builder, builder.businessContext(Map.of()));
    }

    @Test
    @DisplayName("record 构造器应直接创建上下文")
    void testRecordConstructor() {
        Map<String, Object> businessCtx = Map.of("department", "IT");

        UserContext context = new UserContext(
                "user-789",
                "李四",
                "user",
                "zh-CN",
                "concise",
                businessCtx
        );

        assertEquals("user-789", context.userId());
        assertEquals("李四", context.username());
        assertEquals("user", context.role());
        assertEquals("zh-CN", context.language());
        assertEquals("concise", context.outputStyle());
        assertEquals(businessCtx, context.businessContext());
    }

    @Test
    @DisplayName("字段可为 null")
    void testNullableFields() {
        UserContext context = new UserContext(null, null, null, null, null, null);

        assertNull(context.userId());
        assertNull(context.username());
        assertNull(context.role());
        assertNull(context.language());
        assertNull(context.outputStyle());
        assertNull(context.businessContext());
    }
}
