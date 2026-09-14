# Java AI LangChain4j - 医疗导诊与预约 Agent

基于 `Spring Boot 3 + LangChain4j + MyBatis-Plus + MongoDB + MySQL` 的医疗助手示例项目，支持：
- 多轮对话（MongoDB 持久化记忆）
- RAG 检索增强（Chroma/Pinecone，可选；未配置时自动降级 InMemory）
- 混合检索（向量 + BM25 多路召回、Metadata Filtering、RRF 融合排序）
- AI 工具调用（门诊预约、候补改约、检查预约、复诊预约、就诊前准备）
- 流式输出（SSE）
- Flyway 自动迁移（启动建表）
- 统一错误码与 `traceId`
- Micrometer 指标埋点（Tool 调用与业务成功率）

## 技术栈
- Java 17
- Spring Boot 3.2.6
- LangChain4j 1.0.0-beta3
- MyBatis-Plus
- MySQL / MongoDB
- Pinecone / Chroma（可选）

## 项目结构
- `assistant/`：AI Service 接口定义
- `tools/`：可被模型调用的业务工具
- `service/`：预约业务逻辑
- `config/`：Agent、EmbeddingStore 配置
- `store/`：MongoDB 聊天记忆存储
- `controller/`：HTTP 接口

## 本地启动
1. 准备 MySQL、MongoDB。
2. 通过环境变量注入密钥和连接信息（参考 `src/main/resources/application-example.properties`）。
3. 启动应用：
   ```bash
   mvn spring-boot:run
   ```
4. 调用接口：
   - `POST /xiaohan/chat`
   - Body:
   ```json
   {
     "memoryId": 1001,
     "message": "我想预约下周一上午内科"
   }
   ```

## 环境变量（必填）
- `DASHSCOPE_API_KEY`：DashScope 模型 key。
- `MYSQL_URL`、`MYSQL_USERNAME`、`MYSQL_PASSWORD`：MySQL 连接。
- 可选：`MONGODB_URI`、`PINECONE_API_KEY` 等。

## 登录与会话
- 登录接口：
  - `POST /auth/login`
  - `POST /auth/register`
  - `GET /auth/me`
  - `POST /auth/logout`
- 业务接口与 `/xiaohan/chat` 需要先登录，采用 `HttpSession` 会话。
- 登录后系统自动绑定用户 `userId/username/idCard`，前端不再手工输入这些字段。

## 用户表
- Flyway 新增：`V2__create_sys_user.sql`
- 表：`sys_user`
- 初始化账号：
  - `zhangsan / 123456`
  - `lisi / 123456`

## 安全说明
- 不要把任何 API Key、数据库密码提交到仓库。
- 建议为生产环境配置：
  - 独立配置中心/密钥管理（如 Nacos/Vault/KMS）
  - 访问鉴权（JWT/OAuth2）
  - 速率限制与审计日志

## 下一步建议
- 增加医生排班表和号源库存表，避免仅按预约记录推断余号。
- 增加集成测试（Testcontainers）与接口契约测试。
- 增加监控指标（调用耗时、工具命中率、RAG 召回率）。
- 完善医疗合规策略（高风险问题兜底与免责声明）。

## 数据库增强脚本
- 文件：`src/main/resources/sql/appointment-ddl-v2.sql`
- 用途：
  - 增加防重复预约唯一索引
  - 增加号源查询复合索引
- 执行方式：在 MySQL 中手动执行该脚本（建议先在测试库验证）。

## 四大流程完整脚本
- 文件：`src/main/resources/sql/workflow-ddl-v3.sql`
- 覆盖流程：
  - 候补与改约（`waitlist_request`）
  - 就诊前准备（`visit_preparation`）
  - 检查检验预约（`exam_slot`、`exam_booking`）
  - 复诊预约（`follow_up_plan`）
- 执行方式：在 MySQL 中执行该脚本，然后在对话中调用工具 `同步业务流程到知识库` 完成向量入库。

## Flyway 迁移
- 目录：`src/main/resources/db/migration`
- 当前版本：`V1__init_schema.sql`
- 启动时自动执行迁移，不再依赖手工建表。

## 向量同步策略
- 当调用工具 `维护就诊前准备` 新增或更新准备内容时，系统会自动同步该条内容到向量库。
- `同步业务流程到知识库` 仍可作为全量补偿同步工具使用。

## 混合检索与知识库评测
- 入库采用语义边界切分（句末标点优先）和重叠切割，片段带有 `source`、`parentId`、`chunkIndex` 元数据；同一流程的片段可通过 `parentId` 关联。
- 检索由 `HybridContentRetriever` 编排：BM25 词法召回 + EmbeddingStore 向量召回，再以 Reciprocal Rank Fusion（RRF）合并。
- 可选配置 `CHROMA_ENABLED=true`、`CHROMA_URL` 和 `CHROMA_COLLECTION` 将片段异步 upsert 到 Chroma；本地 BM25 与现有 EmbeddingStore 仍作为可用回退。
- 评测样例位于 `eval/ragas-sample.jsonl`，运行 `python eval/ragas_eval.py eval/ragas-sample.jsonl` 输出 Hit@3；安装 `ragas` 后可接入其语义指标评测。

## 监控与排障
- 指标接口：`GET /actuator/metrics`
- Prometheus 抓取：`GET /actuator/prometheus`
- 关键指标：
  - `app.tool.calls{tool,status}`
  - `app.tool.latency{tool}`
  - `app.chat.latency`
  - `app.chat.requests{status}`
  - `app.chat.tool.hit{hit}`
  - `app.chat.tool.calls_per_request`
  - `app.chat.tool.hit.rate`
  - `app.rag.latency`
  - `app.rag.requests{status}`
  - `app.rag.hit{hit}`
  - `app.rag.retrieved.count`
  - `app.rag.score.avg`
  - `app.rag.recall.rate`
  - `app.booking.*`
  - `app.exam.*`
- 工具命中率口径：`app.chat.tool.hit{hit="true"}` / `app.chat.requests{status="success"}`（同时提供 gauge：`app.chat.tool.hit.rate`）。
- RAG 召回率口径：`app.rag.hit{hit="true"}` / `app.rag.requests{status="success"}`（同时提供 gauge：`app.rag.recall.rate`）。
- 错误响应统一带 `traceId`，可用于日志追踪。

## 面试演示
- 统一手册：`docs/interview-handbook.md`

## 新增八大业务扩展（智能体工具）
- AI 分诊导诊：`AI分诊导诊`
- 检查检验全流程助手：`检查检验流程提醒`
- 慢病随访管理：`慢病随访计划`
- 费用与医保助手：`费用与医保预估`
- 用药安全助手：`用药安全评估`
- 报告摘要与术语解释：`报告解读`
- 医患沟通工单中心：`医患工单`
- 运营侧智能看板：`运营看板`

说明：
- 入口类：`src/main/java/com/atguigu/java/ai/langchain4j/tools/AdvancedMedicalTools.java`
- 当前为“可演示规则版”，便于面试展示业务闭环；后续可逐步升级为模型+规则+知识库混合决策。

## 业务 REST API（非 AI 入口）
统一前缀：`/api`

- 预约与候补：
  - `POST /api/appointments/book`
  - `POST /api/appointments/cancel`
  - `POST /api/appointments/reschedule`
  - `GET /api/appointments/availability`
  - `POST /api/waitlists/appointments`
  - `POST /api/waitlists/exams`
- 检查与复诊：
  - `POST /api/exams/book`
  - `POST /api/exams/cancel`
  - `POST /api/followups/plans`
  - `POST /api/followups/plans/{planId}/confirm`
- 就诊准备与知识：
  - `GET /api/preparations`
  - `POST /api/preparations`
  - `POST /api/knowledge/ingest-workflow`
  - `POST /api/knowledge/ingest-project-docs`
- 八大扩展业务：
  - `POST /api/triage`
  - `POST /api/exam-journey/reminder`
  - `POST /api/chronic/followup-plan`
  - `POST /api/cost/estimate`
  - `POST /api/medication/safety`
  - `POST /api/reports/explain`
  - `POST /api/tickets`
  - `GET /api/operations/daily-summary`
  - `GET /api/operations/dlq-events`

## 后端问题排障面试题

### 题1 流式接口第一问不出结果 第二问才出现
- 现象：
  - `/xiaohan/chat` 第一条问题看起来不返回
  - 第二条问题发出后第一条答案才出现
- 排查：
  - 用 `curl -N` 直连验证 SSE 实时性
  - 观察服务端首包时间、分片间隔、结束时间
  - 检查分片边界和容器缓冲刷新时机
- 根因：
  - 流式分片输出与 flush 时机不稳定
  - 分片边界在不同环境下兼容性不足
- 解决：
  - 统一 SSE 输出格式
  - 每个分片写出后立即刷新
  - 增加 traceId 和流式日志
- 结果：
  - 首问实时返回
  - 多轮连续提问稳定

### 题2 应用启动失败 Unsupported Database MySQL 8.0
- 现象：
  - Spring Boot 启动时报 Flyway 不支持数据库
- 排查：
  - 检查 classpath 只有 `flyway-core` 无数据库扩展
  - 对照 Flyway 版本与 MySQL 驱动版本
- 根因：
  - Flyway 缺少 MySQL 方言模块
- 解决：
  - 引入 `org.flywaydb:flyway-mysql`
  - 重新加载 Maven 依赖并重启
- 结果：
  - Flyway 成功建表与版本校验
  - 启动链路恢复

### 题3 启动失败 Access denied for user root localhost using password NO
- 现象：
  - Hikari 和 Flyway 初始化时报账号密码为空
- 排查：
  - 核对 `application.properties` 与环境变量覆盖关系
  - 检查运行配置中是否有空值覆盖
- 根因：
  - 运行时配置覆盖导致密码被置空
- 解决：
  - 数据源与 Flyway 统一使用同一套配置
  - 显式配置 `spring.flyway.url user password` 绑定 datasource
- 结果：
  - 数据库连接稳定
  - 启动流程可重复

### 题4 工具链可观测性不足 难以定位慢请求
- 现象：
  - 业务接口偶发慢 无法快速定位是模型慢还是数据库慢
- 排查：
  - 缺少工具调用计数和耗时指标
  - 缺少端到端 traceId 关联
- 根因：
  - 监控埋点不完整
- 解决：
  - 增加 `ToolMetricsAspect`
  - 输出 `app.tool.calls` `app.tool.latency` 指标
  - 通过 `TraceIdFilter` 全链路透传
- 结果：
  - 可在 `/actuator/metrics` 快速定位慢点
  - 排障效率明显提升

### 题5 多数据源职责边界不清导致业务不稳定
- 现象：
  - 有些结构化知识放在向量库后难维护
- 排查：
  - 比较结构化查询与向量召回的适用场景
- 根因：
  - 把强结构化配置数据放到不适合的存储
- 解决：
  - 结构化主数据保留 MySQL
  - 变更后自动同步到向量库用于检索增强
- 结果：
  - 后台可维护性和检索效果同时提升
