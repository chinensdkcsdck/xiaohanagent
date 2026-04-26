package com.atguigu.java.ai.langchain4j.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DbLockMapper {

    @Select("SELECT GET_LOCK(#{lockKey}, #{timeoutSeconds})")
    Integer acquireLock(@Param("lockKey") String lockKey, @Param("timeoutSeconds") int timeoutSeconds);

    @Select("SELECT RELEASE_LOCK(#{lockKey})")
    Integer releaseLock(@Param("lockKey") String lockKey);
}
