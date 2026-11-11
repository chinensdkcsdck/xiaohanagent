# 小韩智能医疗导诊与预约 Agent

面向医院门诊导诊、预约和就诊准备场景的 Java 智能体项目，基于 **Spring Boot 3 + LangChain4j** 实现。项目把自然语言理解、Skill 路由、槽位补全、知识检索和业务 Tool 执行连接起来，同时保留登录、权限、预约状态和异常处理等后端约束。

## 项目链路

~~~text
用户输入
  -> SkillMatcher / SkillOrchestratorService：识别意图并选择 Skill
  -> 槽位提取、状态合并、参数完整性校验
  -> Appointment / Advanced / General Agent
  -> 混合 RAG 或业务 Tool
  -> 返回流式结果并记录指标
~~~

Planner 表示组合任务的拆分和推进，当前实现为 agent/Planner；入口编排由 SkillOrchestratorService 完成，Skill 定义位于 src/main/resources/skills/*.yaml。预约类请求在科室、日期和时段等必要信息不完整时先追问，参数完整后才允许进入预约 Agent，并通过工具白名单限制可调用范围。急危重症关键词会先走安全兜底，不直接进入普通预约流程。

## 主要能力

- 门诊预约、取消、改约和候补登记
- 检查预约与取消、复诊计划和就诊前准备
- AI 分诊、报告解读、用药安全和费用预估
- 多轮对话记忆、槽位补全和 Tool 调用治理
- SSE 对话响应、统一异常处理、traceId 和 Micrometer 指标
- MySQL 业务数据、MongoDB 对话记忆、Redis 分布式锁和 RabbitMQ 异步事件

## Agent 与 Skill

Skill 通过 YAML 配置意图示例、关键词、必填槽位、路由 Agent 和 Tool 白名单。SkillMatcher 对候选 Skill 排序，分数接近时要求用户澄清；SkillOrchestratorService 负责冲突处理、槽位状态合并和路由；SlotChecker 在执行前校验参数，ToolInvocationTracker 记录工具调用。

当前主要 Skill 包括预约、通用问答和高级医疗能力，对应 AppointmentSkillAgent、GeneralSkillAgent 和 AdvancedSkillAgent。新增业务通常需要增加 Skill 配置、Agent 接口和 Tool 实现。

TypedState 保存当前任务、待处理任务、Skill、槽位、Tool 结果和任务状态；CheckpointStore 将可恢复进度写入 Redis，保留 24 小时，并在 Redis 不可用时降级到进程内状态。它只用于恢复 Agent 流程，预约最终状态仍以 MySQL 为准。

## RAG 检索链路

知识进入系统后按语义边界切分，保留来源、父文档和 Chunk 元数据。检索阶段由 HybridContentRetriever 统一入口：BM25 负责关键词匹配，向量检索负责语义匹配，多路结果使用 RRF 按名次融合；支持 Metadata Filtering，必要时回填父文档上下文。向量存储可以接 Pinecone 或 Chroma，本地开发可回退到 InMemory。

相关实现：

- rag/HybridContentRetriever：混合检索入口
- rag/KnowledgeIndex：本地索引和 RRF 排序
- rag/ChromaVectorStoreClient：Chroma REST 适配
- service/impl/KnowledgeIngestionServiceImpl：知识切分、元数据和向量入库
- eval/ragas_eval.py：RAG 评测脚本

## 数据与状态

- MySQL：预约、检查预约、用户和其他业务结果
- MongoDB：LangChain4j ChatMemory 的对话历史，窗口上限为 20 条消息
- Redis：预约热点资源的分布式锁；锁类型和租期通过配置切换
- RabbitMQ：预约创建后的通知事件，可通过配置启用

业务结果以 MySQL 为准，Redis 不替代业务数据库。

## 本地启动

环境要求：Java 17、Maven、MySQL；完整对话还需要 MongoDB，使用向量服务时需要对应的 Pinecone 或 Chroma 配置。

1. 复制 src/main/resources/application-example.properties，按本机环境设置模型、数据库和中间件参数，或设置同名环境变量。
2. 初始化 MySQL 数据库；迁移脚本位于 src/main/resources/db/migration。
3. 启动应用：

~~~bash
mvn spring-boot:run
~~~

默认端口是 8080。

## 访问入口

启动成功后：

- Web 页面：http://localhost:8080/
- Swagger 接口文档：http://localhost:8080/swagger-ui.html
- 对话接口：POST http://localhost:8080/xiaohan/chat
- 业务接口：http://localhost:8080/api/**
- 指标接口：http://localhost:8080/actuator/metrics

对话接口需要先登录并携带会话，示例请求体：

~~~json
{"memoryId":1001,"message":"我想预约明天上午的心内科"}
~~~

## 验证

~~~bash
mvn -q test
python eval/ragas_eval.py eval/ragas-sample.jsonl
~~~

完整集成测试依赖可用的 MySQL、MongoDB 及模型配置。

