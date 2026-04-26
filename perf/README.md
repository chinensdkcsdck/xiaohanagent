# HTTP A/B 压测说明（RAG埋点开关）

## 1. 目的
对比两组服务性能：
1. A组：开启 `ObservedContentRetriever` 埋点
2. B组：关闭 `ObservedContentRetriever` 埋点

通过同样的 HTTP 压测脚本，比较 QPS、P95、失败率。

## 2. 开关配置
项目已支持环境变量开关：
`APP_RAG_OBSERVED_ENABLED=true|false`

## 3. 启动服务
### A组（开埋点）
```powershell
$env:APP_RAG_OBSERVED_ENABLED="true"
mvn spring-boot:run
```

### B组（关埋点）
```powershell
$env:APP_RAG_OBSERVED_ENABLED="false"
mvn spring-boot:run
```

每次只跑一组，压完后停服务再切另一组。

## 4. 压测脚本
### 4.1 API基线压测（推荐先跑）
```powershell
k6 run .\perf\k6-api-baseline.js
```

可调参数：
```powershell
$env:VUS="50"
$env:DURATION="120s"
$env:BASE_URL="http://127.0.0.1:8080"
k6 run .\perf\k6-api-baseline.js
```

### 4.2 Chat端到端压测（含模型与RAG）
```powershell
k6 run .\perf\k6-chat-e2e.js
```

可调参数：
```powershell
$env:VUS="8"
$env:DURATION="120s"
$env:BASE_URL="http://127.0.0.1:8080"
k6 run .\perf\k6-chat-e2e.js
```

## 5. 结果怎么看
关注 k6 输出中的：
1. `http_reqs` 和测试时长（估算 QPS）
2. `http_req_duration` 的 `p(95)` / `p(99)`
3. `http_req_failed` 失败率

对比规则：
1. A、B 使用相同 VUS、相同时长、相同脚本。
2. 先看失败率是否接近，再看 P95/P99。
3. 若 A 相比 B 仅小幅变差（例如 <3%），可认为埋点开销可接受。

## 6. 指标联动观测
压测同时看：
1. `/actuator/metrics/app.rag.latency`
2. `/actuator/metrics/app.rag.requests`
3. `/actuator/metrics/app.rag.hit`
4. `/actuator/metrics/app.rag.recall.rate`

用于确认“流量上来后，RAG链路是否退化”。
