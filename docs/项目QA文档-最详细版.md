# java_ai_langcgain4j 项目 QA 文档（最详细版）

> 版本：v1.0  
> 生成时间：2026-04-19  
> 适用分支：当前工作区代码（Spring Boot + LangChain4j 医疗智能体）

---

## 0. 文档说明

### Q0.1 这份 QA 文档覆盖什么？
A：覆盖本项目从开发、联调、测试、上线、排障到面试说明的完整问答，包括：
- 系统架构与模块边界
- 环境配置与启动
- 鉴权与会话
- AI 路由、Tool Calling、RAG
- 业务 API（/api）与 AI 入口（/xiaohan/chat）
- 数据库、Flyway、并发与锁、MQ 异步
- 监控指标与追踪
- 测试清单与压测建议
- 常见故障定位与修复
- 上线核对清单

### Q0.2 这份文档基于哪些真实代码？
A：核心依据为以下文件（节选）：
- `README.md`
- `pom.xml`
- `src/main/resources/application.properties`
- `src/main/java/com/atguigu/java/ai/langchain4j/controller/*.java`
- `src/main/java/com/atguigu/java/ai/langchain4j/service/impl/*.java`
- `src/main/java/com/atguigu/java/ai/langchain4j/config/*.java`
- `src/main/java/com/atguigu/java/ai/langchain4j/mq/*.java`
- `src/main/resources/db/migration/*.sql`
- `src/test/java/com/atguigu/java/ai/**/*.java`

### Q0.3 QA 在这里是 “Quality Assurance” 还是 “Question Answer”？
A：本文件采用“双重语义”：
- 形式上是问答式（Q/A）文档，便于面试与知识传递
- 内容上是质量保障导向，包含可执行检查项与测试建议

---

## 1. 项目总览

### Q1.1 项目是做什么的？
A：这是一个医疗导诊与预约智能体项目，提供：
- 智能对话与多轮会话记忆
- 预约挂号、改约、取消、候补
- 检查预约与取消
- 复诊计划创建与确认
- 就诊准备知识查询与维护
- 八大扩展医疗能力（分诊、费用预估、用药安全、报告解读等）

### Q1.2 技术栈是什么？
A：
- Java 17
- Spring Boot 3.2.6
- LangChain4j 1.0.0-beta3
- MyBatis-Plus
- MySQL
- MongoDB（聊天记忆）
- Pinecone（可选向量库）
- Redis（可选分布式锁）
- RabbitMQ（可选异步消息）
- Micrometer + Actuator（监控）

### Q1.3 核心模块划分？
A：
- `controller`：HTTP API 入口
- `service` / `service.impl`：业务逻辑
- `tools`：LLM 可调用工具
- `assistant`：不同技能 Agent
- `skill`：技能匹配、冲突消解、槽位校验
- `config`：模型、RAG、锁、鉴权、监控配置
- `mq`：事件发布与消费
- `mapper` + `entity`：持久层
- `store`：Mongo 聊天记忆存储

---

## 2. 运行与配置 QA

### Q2.1 启动最低依赖是什么？
A：
- 必需：MySQL
- 推荐：MongoDB（否则聊天记忆不可用）
- 可选：Pinecone、Redis、RabbitMQ

### Q2.2 启动命令？
A：
```bash
mvn spring-boot:run
```

### Q2.3 最关键环境变量有哪些？
A：
- 模型：`DASHSCOPE_API_KEY`
- MySQL：`MYSQL_URL` `MYSQL_USERNAME` `MYSQL_PASSWORD`
- Mongo：`MONGODB_URI`
- 可选 Pinecone：`PINECONE_API_KEY` 等

### Q2.4 未配置 Pinecone 会怎样？
A：系统自动降级到 `InMemoryEmbeddingStore`（内存向量库）。适合本地调试，不适合生产（重启丢失向量数据）。

### Q2.5 Flyway 是否自动建表？
A：是。`spring.flyway.enabled=true`，启动时执行 `db/migration` 目录 SQL。

### Q2.6 配置里有哪些上线风险？
A：当前 `application.properties` 与 `application-example.properties` 中出现了明文密码/密钥示例，生产环境必须改为安全注入（KMS/Vault/密钥平台）。

---

## 3. 鉴权与会话 QA

### Q3.1 鉴权方式是什么？
A：基于 `HttpSession`。登录成功后把 `LoginUser` 放到 session 中，业务接口通过拦截器校验。

### Q3.2 哪些路径需要登录？
A：`/api/**` 与 `/xiaohan/**`，由 `WebMvcAuthConfig` 注册的 `AuthInterceptor` 拦截。

### Q3.3 哪些路径免登录？
A：`/auth/**`、静态资源、Swagger、`/actuator/**` 等。

### Q3.4 登录相关接口？
A：
- `POST /auth/login`
- `POST /auth/register`
- `GET /auth/me`
- `POST /auth/logout`

### Q3.5 用户密码是否加密？
A：当前实现是明文比对（`UserAuthServiceImpl` 直接比较字符串），生产必须升级为哈希（BCrypt/Argon2）。

---

## 4. AI 对话链路 QA

### Q4.1 对话入口是什么？
A：`POST /xiaohan/chat`，返回 `text/event-stream`。

### Q4.2 `/xiaohan/chat` 的核心流程？
A：
1. 会话鉴权（未登录抛 `UNAUTHORIZED`）
2. 记录计时与工具追踪状态
3. 调用 `SkillOrchestratorService.chat(userId, message)`
4. 记录 chat/tool 指标
5. 返回结果（当前是 `Flux.just(answer)` 单段输出）

### Q4.3 技能编排核心规则是什么？
A：
- 先急症关键词兜底（胸痛、呼吸困难等）
- `SkillMatcher` 打分路由
- 分数接近触发冲突澄清
- 动作冲突触发澄清
- 槽位不足触发追问（有最大追问轮次）
- 工具白名单限制 + 路由到对应 Agent

### Q4.4 `SkillMatcher` 当前打分机制是什么？
A：
- 关键词命中：每个 +4 分，最多记 3 个命中（防长文本刷分）
- 意图样例命中：每个 +2 分，最多记 2 个命中
- 负向关键词命中：每个 -5 分，最多记 2 个命中（用于跨技能去歧义）
- token 长度小于 2 的词不参与匹配（降低噪声）
- 总分小于 0 按 0 处理，仅 `score > 0` 才作为候选技能
- 同分时按 `priority` 决策

### Q4.5 工具越权调用如何防？
A：`ToolInvocationTracker.setAllowedTools()` 在当前请求线程设置白名单，AOP 调用工具时校验，不在白名单会抛异常。

### Q4.6 槽位追问无限循环怎么防？
A：`maxAskTurns` 达到上限后执行 fallback 回复，避免无限问答。

---

## 5. RAG QA

### Q5.1 RAG 检索参数是什么？
A：`maxResults=3`，`minScore=0.75`（`XiaohanAgentConfig`）。

### Q5.2 RAG 监控如何做？
A：通过 `ObservedContentRetriever` 包装 delegate，记录：
- `app.rag.requests{status}`
- `app.rag.hit{hit}`
- `app.rag.retrieved.count`
- `app.rag.score.avg`
- `app.rag.latency`
- Gauge：`app.rag.recall.rate`

### Q5.3 知识入库接口有哪些？
A：
- `POST /api/knowledge/ingest-workflow`
- `POST /api/knowledge/ingest-project-docs`

### Q5.4 维护就诊准备后会自动同步向量吗？
A：会。`PreparationService` 更新后会调用知识同步逻辑（代码中通过 `KnowledgeIngestionService` 支持）。

---

## 6. 业务 API QA（REST）

### Q6.1 预约相关接口有哪些？
A：
- `POST /api/appointments/book`
- `POST /api/appointments/cancel`
- `POST /api/appointments/reschedule`
- `GET /api/appointments/availability`

### Q6.2 候补接口？
A：
- `POST /api/waitlists/appointments`
- `POST /api/waitlists/exams`

### Q6.3 检查与复诊接口？
A：
- `POST /api/exams/book`
- `POST /api/exams/cancel`
- `POST /api/followups/plans`
- `POST /api/followups/plans/{planId}/confirm`

### Q6.4 知识与准备接口？
A：
- `GET /api/preparations`
- `POST /api/preparations`
- `POST /api/knowledge/ingest-workflow`
- `POST /api/knowledge/ingest-project-docs`

### Q6.5 八大扩展能力接口？
A：
- `POST /api/triage`
- `POST /api/exam-journey/reminder`
- `POST /api/chronic/followup-plan`
- `POST /api/cost/estimate`
- `POST /api/medication/safety`
- `POST /api/reports/explain`
- `POST /api/tickets`
- `GET /api/operations/daily-summary`
- `GET /api/operations/dlq-events`

### Q6.6 常见入参格式错误是什么？
A：
- 日期必须是 `yyyy-MM-dd`
- 时段必须能被 `AppointmentPeriod.from()` 解析（上/下午）
- 缺少必填字段会触发 `BusinessException` 或 `IllegalArgumentException`

---

## 7. 核心业务流程 QA

### Q7.1 预约如何防并发超卖？
A：`AppointmentServiceImpl.tryBook()` 采用：
1. 按号源维度加分布式锁
2. 查重（同用户同槽位）
3. 容量校验
4. 入库
5. 事务后可选发 MQ 事件

### Q7.2 改约如何避免死锁？
A：旧槽位锁和新槽位锁按字典序排序后获取，避免循环等待。

### Q7.3 检查预约的容量控制逻辑？
A：
- `exam_slot` 记录容量和已预约数
- `tryBookExam` 持锁后判断 reserved < capacity
- 成功后 `reserved + 1`
- 取消后 `reserved - 1`（最小不低于 0）

### Q7.4 复诊确认是如何落预约的？
A：`confirmFollowUp(planId)` 会把复诊计划转换成预约请求，调用统一 `appointmentService.tryBook()`，成功后计划状态改为 `BOOKED`。

### Q7.5 候补当前是否自动转正？
A：当前主要是登记 `waitlist_request`（PENDING），自动撮合逻辑未在本项目中实现。

---

## 8. 锁与一致性 QA

### Q8.1 支持哪些分布式锁实现？
A：
- `mysql`（默认）
- `redis`（`app.lock.type=redis` 时启用 Redisson）

### Q8.2 锁参数有哪些？
A：
- `app.lock.wait-ms`（默认 200）
- `app.lock.lease-ms`（默认 5000）

### Q8.3 如何观测锁表现？
A：通过 `MetricsDistributedLockClient` 对锁调用做指标封装（可用于统计成功率/等待/失败趋势）。

### Q8.4 当前锁方案的风险点？
A：
- `leaseMs` 过短可能在长事务下提前失效
- 热点号源会形成锁竞争，需限流和容量规划

---

## 9. MQ 异步 QA

### Q9.1 MQ 何时启用？
A：`app.mq.enabled=true` 时启用拓扑、发布者和消费者。

### Q9.2 发布时机如何保证？
A：`BookingEventPublisher.publishAfterCommit()` 在事务提交后发送消息。

### Q9.3 消费端如何幂等？
A：消费 `BookingCreatedEvent` 时，先查/写 `message_consume_log` 的唯一键 `message_id`，重复消息直接跳过。

### Q9.4 死信如何处理？
A：业务队列配置 DLX，进入死信队列后由 `BookingDlxConsumer` 落库 `mq_dead_letter_event` 并打告警指标。

### Q9.5 MQ 重试策略？
A：`RabbitListenerConfig` 使用 `RetryInterceptorBuilder.stateless()`，最大重试次数与退避参数来自 `MqProperties`。

---

## 10. 数据库与迁移 QA

### Q10.1 Flyway 当前迁移版本有哪些？
A：
- V1：核心业务表
- V2：`sys_user`
- V3：`message_consume_log`
- V4：`mq_dead_letter_event`

### Q10.2 关键唯一索引？
A：
- `uk_appointment_user_slot`
- `uk_exam_slot`
- `uk_exam_booking_user_slot`
- `uk_visit_preparation`
- `uk_message_consume_log_message_id`

### Q10.3 初始化账号？
A：
- `zhangsan / 123456`
- `lisi / 123456`

### Q10.4 初始化账号的生产风险？
A：必须在生产环境移除默认弱口令，并强制改密策略。

---

## 11. 异常处理与错误码 QA

### Q11.1 全局异常返回结构由谁统一？
A：`GlobalExceptionHandler`，返回 `ApiErrorResponse` 并带 `traceId`。

### Q11.2 主要错误码？
A：
- `40001 INVALID_PARAM`
- `40002 BIZ_ERROR`
- `40100 UNAUTHORIZED`
- `50010 TOOL_ERROR`
- `50000 INTERNAL_ERROR`

### Q11.3 traceId 如何生成和透传？
A：`TraceIdFilter` 从请求头 `X-Trace-Id` 获取；若无则生成，写入 MDC 与响应头。

---

## 12. 监控与可观测 QA

### Q12.1 指标入口？
A：
- `GET /actuator/metrics`
- `GET /actuator/prometheus`

### Q12.2 工具调用指标有哪些？
A：
- `app.tool.calls{tool,status}`
- `app.tool.latency{tool}`

### Q12.3 对话指标有哪些？
A：
- `app.chat.requests{status}`
- `app.chat.latency`
- `app.chat.tool.hit{hit}`
- `app.chat.tool.calls_per_request`
- `app.chat.tool.hit.rate`（Gauge）

### Q12.4 请求日志里会输出哪些运营关键字段？
A：`RequestMetricsLoggingFilter` 会打印请求耗时、chat/tool/rag 成败计数、命中率、CPU、堆内存等关键指标。

---

## 13. 测试 QA

### Q13.1 现有测试有哪些？
A：
- `AppointmentServiceTest`
- `AppointmentServiceConcurrencyIT`（Testcontainers + MySQL）
- `ObservedContentRetrieverABBenchmarkTest`

### Q13.2 并发测试验证了什么？
A：
- 20 并发抢号在容量=5 场景下，成功数和落库数都应为 5
- 同用户同槽位重复预约被拒绝，数据库仅 1 条

### Q13.3 RAG A/B 基准测试验证了什么？
A：验证 `ObservedContentRetriever` 装饰器在不同延迟场景的额外开销，支持评估可观测性对性能影响。

### Q13.4 还缺哪些测试？
A：
- 控制器层集成测试（鉴权、错误码、序列化）
- MQ 集成测试（重试、死信、幂等）
- Redis 锁路径测试
- 端到端 SSE 行为测试

---

## 14. 性能与容量 QA

### Q14.1 性能压测脚本在哪里？
A：`perf/` 目录，包含 `k6-api-baseline.js` 与 `k6-chat-e2e.js`。

### Q14.2 高并发关键瓶颈可能在哪？
A：
- 热点号源锁竞争
- MySQL 连接池上限
- LLM 响应波动
- 向量检索延迟

### Q14.3 调优优先级建议？
A：
1. 先保一致性（锁、唯一索引、幂等）
2. 再保可用性（限流、超时、降级）
3. 再做吞吐优化（池化参数、异步化、扩容）

---

## 15. 安全与合规 QA

### Q15.1 目前安全短板是什么？
A：
- 会话鉴权可用但非零信任体系（未使用 JWT/OAuth2）
- 密码明文存储风险
- 示例配置存在敏感信息暴露风险

### Q15.2 医疗场景建议补哪些能力？
A：
- 敏感字段加密与脱敏
- 操作审计日志
- 风险问题兜底提示与人工升级
- RBAC 权限模型

---

## 16. 上线检查清单（可执行）

### Q16.1 上线前必须逐项确认什么？
A：
- 基础设施：MySQL/Mongo/Redis/RabbitMQ（按启用项）可用
- 配置：生产密钥来自安全平台，不在仓库明文
- Flyway：迁移版本正确，无脏迁移
- 鉴权：未登录访问 `/api/**` 与 `/xiaohan/**` 返回 401
- 业务：预约/改约/取消/检查/复诊流程冒烟通过
- 幂等：MQ 重复消费不会重复落业务
- 监控：Prometheus 能抓到核心指标
- 告警：失败率、时延、DLQ、锁失败告警已配置
- 回滚：数据库与应用回滚预案已演练

---

## 17. 高频故障排障 QA

### Q17.1 现象：未登录也能访问业务接口
A：检查 `WebMvcAuthConfig` 是否正确加载，确认路径匹配与排除路径没有误配。

### Q17.2 现象：`/xiaohan/chat` 返回 401
A：先调用 `/auth/login`，并确保客户端携带同一会话 Cookie。

### Q17.3 现象：启动失败，数据库不支持错误
A：确认 `pom.xml` 已引入 `flyway-mysql`（当前已引入），并检查 MySQL 驱动和 Flyway 版本兼容。

### Q17.4 现象：Access denied using password NO
A：检查环境变量覆盖关系，确认 `spring.datasource` 与 `spring.flyway` 均正确绑定账号密码。

### Q17.5 现象：RAG 无召回
A：
1. 检查是否执行知识入库接口
2. 检查 Pinecone 配置/连通性
3. 查看 `app.rag.requests` 与 `app.rag.hit` 指标
4. 适当调整 `minScore`

### Q17.6 现象：高并发下预约失败率高
A：先看 `app.booking.lock.fail`、连接池指标、数据库慢查询，再判断是锁竞争还是资源瓶颈。

### Q17.7 现象：MQ 堆积或出现死信
A：
1. 查看消费者日志与重试次数
2. 排查消息体兼容性
3. 查看 `mq_dead_letter_event` 表
4. 修复后按事件类型做重放

---

## 18. 面试问答（项目实战口径）

### Q18.1 你这个项目最大的工程化亮点是什么？
A：不是“会聊天”，而是“能安全执行交易动作”。通过技能编排、工具白名单、分布式锁、唯一索引、事务后消息、可观测指标，把 AI 输出接成可控业务链路。

### Q18.2 如何证明你考虑了并发一致性？
A：预约/改约流程都在服务层显式加锁；改约双锁按排序获取防死锁；数据库有唯一索引兜底；并发集成测试验证容量约束。

### Q18.3 如何证明你考虑了可观测性？
A：对话、工具、RAG、MQ、请求日志都做了指标采集，并统一 traceId，可从 `/actuator/prometheus` 对接监控系统。

### Q18.4 你如何处理 AI 误调用工具？
A：通过 `ToolInvocationTracker` 的 allow-list 机制在运行时硬拦截越权工具，避免模型“想调就调”。

---

## 19. 改进路线图（下一版本建议）

### Q19.1 P0（上线前必须）
A：
- 密码哈希化（替换明文）
- 清理明文密钥与密码
- 完善接口级自动化测试

### Q19.2 P1（上线后 1-2 迭代）
A：
- Outbox 本地消息表（增强消息可靠性）
- 槽位状态持久化与 TTL
- Redis/MySQL 锁切换演练与告警

### Q19.3 P2（持续优化）
A：
- 技能路由准确率评测集
- RAG 离线评测体系（不仅在线命中）
- 医疗合规策略与人工升级机制

---

## 20. QA 执行模板（团队可复用）

### Q20.1 每次发版最小回归集（建议）
A：
1. 鉴权回归：登录/未登录访问权限
2. 核心交易：预约、改约、取消
3. 检查链路：预约+取消
4. 复诊链路：创建+确认
5. AI 对话：普通问答、工具调用、急症兜底
6. 可观测性：核心指标可见、traceId 可串联
7. 异常链路：非法参数、重复预约、容量满、MQ 死信

### Q20.2 缺陷记录最少字段
A：
- 标题
- 环境与版本
- 复现步骤
- 期望结果
- 实际结果
- 日志/traceId
- 严重级别
- 根因分析
- 修复方案
- 回归结果

---

## 21. 附录：建议的接口验收样例

### Q21.1 预约挂号样例
A：
```http
POST /api/appointments/book
Content-Type: application/json

{
  "department": "内科",
  "date": "2026-04-20",
  "time": "上午",
  "doctorName": "王医生"
}
```

### Q21.2 AI 对话样例
A：
```http
POST /xiaohan/chat
Content-Type: application/json

{
  "memoryId": 1001,
  "message": "我想预约下周一上午内科"
}
```

### Q21.3 检查预约样例
A：
```http
POST /api/exams/book
Content-Type: application/json

{
  "examType": "头颅MRI",
  "date": "2026-04-21",
  "time": "下午",
  "doctorName": "李医生"
}
```

---

## 22. 结论

### Q22.1 用一句话总结当前项目质量状态？
A：该项目已具备“可演示、可联调、可监控”的工程骨架，核心风险主要集中在生产安全基线（密码与密钥治理）和自动化测试覆盖深度，补齐后可显著提升上线可靠性。
