package com.atguigu.java.ai.langchain4j.mapper;

import com.atguigu.java.ai.langchain4j.entity.Appointment;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppointmentMapper extends BaseMapper<Appointment> {

    Integer acquireLock(@Param("lockKey") String lockKey, @Param("timeoutSeconds") int timeoutSeconds);

    Integer releaseLock(@Param("lockKey") String lockKey);
}
