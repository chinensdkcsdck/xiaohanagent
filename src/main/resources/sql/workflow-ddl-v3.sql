-- 四大流程完整数据库脚本（MySQL 8+）
-- 包含：候补改约、就诊前准备、检查检验预约、复诊预约

CREATE TABLE IF NOT EXISTS `exam_slot` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `exam_type` VARCHAR(64) NOT NULL,
  `date` DATE NOT NULL,
  `time` VARCHAR(16) NOT NULL,
  `doctor_name` VARCHAR(64) NULL,
  `capacity` INT NOT NULL DEFAULT 20,
  `reserved` INT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS `uk_exam_slot`
  ON `exam_slot` (`exam_type`, `date`, `time`, `doctor_name`);

CREATE TABLE IF NOT EXISTS `exam_booking` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `id_card` VARCHAR(32) NOT NULL,
  `exam_type` VARCHAR(64) NOT NULL,
  `date` DATE NOT NULL,
  `time` VARCHAR(16) NOT NULL,
  `doctor_name` VARCHAR(64) NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'BOOKED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS `idx_exam_booking_slot`
  ON `exam_booking` (`exam_type`, `date`, `time`, `doctor_name`, `status`);

CREATE UNIQUE INDEX IF NOT EXISTS `uk_exam_booking_user_slot`
  ON `exam_booking` (`username`, `id_card`, `exam_type`, `date`, `time`, `status`);

CREATE TABLE IF NOT EXISTS `follow_up_plan` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `username` VARCHAR(64) NOT NULL,
  `id_card` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) NOT NULL,
  `doctor_name` VARCHAR(64) NULL,
  `recommended_date` DATE NOT NULL,
  `recommended_time` VARCHAR(16) NOT NULL,
  `reason` VARCHAR(255) NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'CREATED',
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS `idx_follow_up_status`
  ON `follow_up_plan` (`status`, `recommended_date`, `department`);

CREATE TABLE IF NOT EXISTS `visit_preparation` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `department` VARCHAR(64) NOT NULL,
  `exam_type` VARCHAR(64) NOT NULL,
  `prep_content` TEXT NOT NULL,
  `risk_notice` VARCHAR(255) NULL,
  `enabled` TINYINT NOT NULL DEFAULT 1
);

CREATE UNIQUE INDEX IF NOT EXISTS `uk_visit_preparation`
  ON `visit_preparation` (`department`, `exam_type`);

CREATE TABLE IF NOT EXISTS `waitlist_request` (
  `id` BIGINT PRIMARY KEY AUTO_INCREMENT,
  `biz_type` VARCHAR(16) NOT NULL,
  `username` VARCHAR(64) NOT NULL,
  `id_card` VARCHAR(32) NOT NULL,
  `department` VARCHAR(64) NULL,
  `exam_type` VARCHAR(64) NULL,
  `date` DATE NOT NULL,
  `time` VARCHAR(16) NOT NULL,
  `doctor_name` VARCHAR(64) NULL,
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  `note` VARCHAR(255) NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS `idx_waitlist_pending`
  ON `waitlist_request` (`biz_type`, `status`, `date`, `time`);

INSERT INTO `visit_preparation` (`department`, `exam_type`, `prep_content`, `risk_notice`, `enabled`)
VALUES
('内科', '常规复诊', '就诊前一晚保证休息；携带既往检查报告和用药清单；建议提前15分钟到院。', '如出现持续胸闷胸痛，请直接急诊。', 1),
('神经内科', '头颅MRI', '检查前去除金属物品；如有植入物需提前告知；建议携带以往影像报告。', '幽闭恐惧或体内金属植入者需先评估。', 1),
('检验科', '空腹抽血', '至少空腹8小时，可少量饮水；检查前避免剧烈运动。', '糖尿病患者请先咨询医生再调整进食。', 1)
ON DUPLICATE KEY UPDATE
  prep_content = VALUES(prep_content),
  risk_notice = VALUES(risk_notice),
  enabled = VALUES(enabled);
