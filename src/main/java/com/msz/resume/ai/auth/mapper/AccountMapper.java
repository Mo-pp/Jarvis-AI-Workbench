/**
 * 账户数据访问层
 *
 * 作用：提供 db_account 表的 CRUD 操作
 *
 * 继承 MyBatis-Plus 的 BaseMapper 后自动拥有：
 * - insert: 插入
 * - deleteById: 按 ID 删除
 * - updateById: 按 ID 更新
 * - selectById: 按 ID 查询
 * - selectOne: 条件查询单条
 * - exists: 判断是否存在
 *
 * 无需编写 XML，直接调用即可
 */
package com.msz.resume.ai.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.msz.resume.ai.auth.entity.Account;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AccountMapper extends BaseMapper<Account> {
}
