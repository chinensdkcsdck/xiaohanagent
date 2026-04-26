-- 第二阶段数据库增强脚本（MySQL 8+）
-- 目标：
-- 1) 防止同一用户在同一科室同一天同一时段重复预约
-- 2) 优化号源查询（按科室/日期/时段/医生）

ALTER TABLE `appointment`
    ADD UNIQUE INDEX `uk_appointment_user_slot`
        (`username`, `id_card`, `department`, `date`, `time`);

CREATE INDEX `idx_appointment_slot_doctor`
    ON `appointment` (`department`, `date`, `time`, `doctor_name`);
