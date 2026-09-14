# 小韩智能医疗导诊 Agent

面向医院门诊导诊与预约场景的智能体系统，基于 **Spring Boot 3 + LangChain4j** 构建，覆盖从意图识别、信息补全、知识检索到业务工具执行的完整链路。

## 项目能力

- 门诊预约、取消、改约与候补登记
- 检查检验预约与取消
- 复诊计划创建与确认
- 就诊前准备事项查询
- AI 分诊、报告解读、用药安全、医保费用预估
- 慢病随访、医患工单与运营摘要
- 多轮对话与 MongoDB 会话记忆
- SSE 流式响应、统一异常处理、traceId 链路追踪

## 核心架构

```text
用户请求
   ↓
SkillMatcher 意图识别
   ↓
SkillOrchestrator 路由与冲突消解
   ↓
槽位提取 / 会话状态合并 / 参数完整性校验
   ↓
LangChain4j Agent
   ↓
Tool 白名单校验 → 预约、检查、复诊及医疗辅助服务
```

Skill 通过 YAML 配置定义意图示例、关键词、必填槽位、路由 Agent 和 Tool 白名单。新增业务主要通过增加 Skill 配置、Agent 接口和 Tool 实现，降低模块之间的耦合。

## RAG 检索增强

针对医疗术语多、流程知识分散的问题，项目实现了混合检索链路：

1. 文档入库时按语义边界切分，并保留重叠片段，避免流程语义被截断。
2. 通过 `source`、`parentId`、`chunkIndex` 建立父子文档关系，保留业务上下文。
3. 检索阶段并行使用向量召回和 BM25 召回。
4. 使用 RRF（Reciprocal Rank Fusion）融合多路结果。
5. 支持 Metadata Filtering；向量存储可使用 Pinecone，或通过配置接入 Chroma，本地开发自动回退 InMemory。

相关实现：

- `rag/HybridContentRetriever`：混合检索入口
- `rag/KnowledgeIndex`：BM25、本地索引与 RRF 排序
- `rag/ChromaVectorStoreClient`：Chroma REST 适配
- `KnowledgeIngestionServiceImpl`：知识切分、元数据构建与向量入库

评测脚本位于 `eval/ragas_eval.py`，可输出 Hit@K；安装 `ragas` 后可扩展语义评测指标。

## 多轮交互与 Tool 治理

预约类请求必须先完成科室、日期和时段等槽位。系统会：

- 从当前消息提取槽位并合并到会话状态；
- 按依赖顺序询问缺失信息；
- 在参数完整前阻止 Agent 调用预约类 Tool；
- 通过 Tool 白名单限制 Skill 可调用的工具范围；
- 对急危重症关键词进行安全兜底并引导线下急诊。

## 技术栈

- Java 17 / Spring Boot 3.2
- LangChain4j
- MyBatis-Plus / MySQL
- MongoDB（聊天记忆）
- Pinecone / Chroma / InMemory（向量存储）
- Redis 分布式锁、RabbitMQ 异步事件
- Micrometer + Actuator 指标监控

## 启动方式

准备 Java 17、MySQL、MongoDB，并配置：

```bash
set DASHSCOPE_API_KEY=your-key
set MYSQL_URL=jdbc:mysql://localhost:3306/xiaohan
set MYSQL_USERNAME=root
set MYSQL_PASSWORD=your-password
mvn spring-boot:run
```

对话接口：

```http
POST /xiaohan/chat
Content-Type: application/json

{"memoryId":1001,"message":"我想预约明天上午的心内科"}
```

核心 REST 接口统一使用 `/api` 前缀；监控指标通过 `/actuator/metrics` 和 `/actuator/prometheus` 查看。

## 验证

```bash
mvn -q test -DskipTests
python eval/ragas_eval.py eval/ragas-sample.jsonl
```

完整集成测试需要配置可用的 DashScope Key、MySQL 和 MongoDB。
