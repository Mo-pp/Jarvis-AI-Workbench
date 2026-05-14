/**
 * MyBatis-Plus 配置类
 * 注册分页插件，后续会话列表分页查询会用到
 */
package com.msz.resume.ai.bootstrap.persistence;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@MapperScan({
        "com.msz.resume.ai.chat.session.mapper",
        "com.msz.resume.ai.auth.mapper"
})
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

    @Bean
    public SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        return factory.getObject();
    }

    /**
     * 提供 Jackson 2.x 的 ObjectMapper Bean
     * Spring Boot 4 默认使用 Jackson 3.x，但部分库（如 LangChain4j）仍需要 Jackson 2.x
     *
     * <p>配置说明：
     * <ul>
     *   <li>注册 JavaTimeModule 支持 Java 8 日期时间类型</li>
     *   <li>禁用 WRITE_DATES_AS_TIMESTAMPS 使用 ISO-8601 格式</li>
     *   <li>禁用 FAIL_ON_EMPTY_BEANS 避免空对象序列化失败</li>
     * </ul>
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
}
