# 架构模式

> **时间：** 25 分钟 | **难度：** 进阶 | **前置要求：** [教程 02](02-core-concepts.md)

本指南介绍 6 种经过验证的 MCP 服务器设计模式。每种模式解决一类常见的需求。

## 模式一览

| 模式 | 适用场景 | 示例 |
|------|---------|------|
| [API 包装器](#模式-1api-包装器) | 将 REST/GraphQL API 暴露给 AI | GitHub、Slack、天气 API |
| [数据库浏览器](#模式-2数据库浏览器) | 让 AI 查询数据库 | SQLite、PostgreSQL |
| [文件系统服务器](#模式-3文件系统服务器) | 让 AI 访问文件 | 项目文件、日志、配置 |
| [聚合器](#模式-4聚合器) | 整合多个数据源 | 仪表盘、跨系统搜索 |
| [有状态服务器](#模式-5有状态服务器) | 跨调用维持状态 | 会话、缓存、工作流 |
| [管道模式](#模式-6管道模式) | 多步骤数据处理 | ETL、数据分析流水线 |

## 选择模式

```
你的服务器做什么？
│
├── 包装外部 API？
│   └── 使用 API 包装器模式
│
├── 暴露数据库？
│   └── 使用数据库浏览器模式
│
├── 访问文件系统？
│   └── 使用文件系统服务器模式
│
├── 整合多个数据源？
│   └── 使用聚合器模式
│
├── 需要跨调用维持状态？
│   └── 使用有状态服务器模式
│
└── 多步骤数据处理？
    └── 使用管道模式
```

---

## 模式 1：API 包装器

将任何 REST API 转换为 MCP 工具，让 AI 可以直接调用。

```
AI 助手 ──► MCP 服务器 ──► REST API
                │
                ├── 认证管理
                ├── 速率限制
                ├── 响应格式化
                └── 错误处理
```

### 关键设计决策

| 决策 | 建议 |
|------|------|
| 一个工具 vs 多个工具 | 每个 API 端点一个工具 |
| 认证 | 通过环境变量传入 |
| 速率限制 | 在服务器端实现，保护 API |
| 响应格式 | 转换为 AI 友好的文本 |

### TypeScript 示例

```typescript
const API_KEY = process.env.API_KEY;
const BASE_URL = "https://api.example.com";

// 封装 API 调用
async function apiFetch(path: string): Promise<unknown> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${API_KEY}` },
    signal: AbortSignal.timeout(10_000),
  });

  if (!response.ok) {
    throw new Error(`API 错误: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

// 暴露为 MCP 工具
server.tool(
  "list_repos",
  "列出用户的 GitHub 仓库",
  { username: z.string() },
  async ({ username }) => {
    const repos = await apiFetch(`/users/${username}/repos?sort=stars&per_page=10`);
    const formatted = repos.map((r) => `${r.full_name} ⭐ ${r.stargazers_count}`).join("\n");
    return { content: [{ type: "text", text: formatted }] };
  }
);
```

> **完整示例：** [REST API 包装器（HackerNews）](../../examples/typescript/rest-api-wrapper/)

---

## 模式 2：数据库浏览器

让 AI 查询和探索数据库，同时确保安全。

```
AI 助手 ──► MCP 服务器 ──► 数据库
                │
                ├── 只允许 SELECT
                ├── 结果数量限制
                ├── Schema 作为资源暴露
                └── SQL 注入防护
```

### 安全要素

| 安全措施 | 实现方式 |
|----------|---------|
| 只读访问 | 使用只读数据库连接 |
| SQL 白名单 | 只允许 SELECT 语句 |
| 结果限制 | 自动添加 LIMIT |
| 参数化查询 | 防止 SQL 注入 |

### TypeScript 示例

```typescript
server.tool(
  "query",
  "对数据库执行只读 SQL 查询",
  {
    sql: z.string().max(1000).describe("SQL SELECT 查询语句"),
  },
  async ({ sql }) => {
    // 安全验证
    const upper = sql.toUpperCase().trim();
    if (!upper.startsWith("SELECT")) {
      return { content: [{ type: "text", text: "只允许 SELECT 查询" }], isError: true };
    }

    // 添加 LIMIT 保护
    if (!upper.includes("LIMIT")) {
      sql += " LIMIT 100";
    }

    const rows = db.prepare(sql).all();
    return { content: [{ type: "text", text: formatTable(rows) }] };
  }
);

// Schema 作为资源暴露
server.resource("db://schema", "数据库表结构", async () => {
  const tables = db.prepare(
    "SELECT name FROM sqlite_master WHERE type='table'"
  ).all();

  const schema = tables.map((t) => {
    const columns = db.prepare(`PRAGMA table_info(${t.name})`).all();
    return `表 ${t.name}:\n${columns.map((c) => `  ${c.name} (${c.type})`).join("\n")}`;
  }).join("\n\n");

  return { contents: [{ uri: "db://schema", text: schema }] };
});
```

> **完整示例：** [数据库浏览器](../../examples/typescript/database-explorer/)

---

## 模式 3：文件系统服务器

让 AI 安全地访问文件系统。

```
AI 助手 ──► MCP 服务器 ──► 文件系统
                │
                ├── 路径沙盒（只允许指定目录）
                ├── 符号链接检查
                ├── 文件大小限制
                └── 内容搜索
```

### 安全边界

> [!WARNING]
> 文件系统访问是最危险的 MCP 模式。必须实现严格的路径沙盒。

```typescript
// 核心安全：路径验证
function validatePath(filePath: string): string {
  const resolved = path.resolve(ALLOWED_ROOT, filePath);
  if (!resolved.startsWith(ALLOWED_ROOT)) {
    throw new Error("访问被拒绝：路径超出允许范围");
  }
  return resolved;
}
```

> **完整示例：** [文件搜索（Python）](../../examples/python/file-search/)

---

## 模式 4：聚合器

将多个数据源整合成统一的接口。

```
                    ┌── API A
AI 助手 ──► MCP ───┼── API B
                    ├── 数据库
                    └── 文件系统
```

### 何时使用

- AI 需要跨多个系统搜索
- 构建统一的数据仪表盘
- 需要关联不同来源的数据

### TypeScript 示例

```typescript
server.tool(
  "search_everything",
  "在所有数据源中搜索",
  { query: z.string() },
  async ({ query }) => {
    // 并行查询所有数据源
    const [dbResults, apiResults, fileResults] = await Promise.allSettled([
      searchDatabase(query),
      searchAPI(query),
      searchFiles(query),
    ]);

    const sections: string[] = [];

    if (dbResults.status === "fulfilled") {
      sections.push(`## 数据库结果\n${dbResults.value}`);
    }
    if (apiResults.status === "fulfilled") {
      sections.push(`## API 结果\n${apiResults.value}`);
    }
    if (fileResults.status === "fulfilled") {
      sections.push(`## 文件结果\n${fileResults.value}`);
    }

    return { content: [{ type: "text", text: sections.join("\n\n") }] };
  }
);
```

---

## 模式 5：有状态服务器

跨多次工具调用维持状态。

### 常见状态类型

| 状态类型 | 示例 | 存储方式 |
|----------|------|---------|
| 会话数据 | 用户偏好、上下文 | 内存 Map |
| 缓存 | API 响应、查询结果 | TTL 缓存 |
| 工作流进度 | 多步操作的当前步骤 | 内存/数据库 |

```typescript
// 简单的有状态服务器
const sessionState = new Map<string, unknown>();

server.tool("set_preference", "设置用户偏好", {
  key: z.string(),
  value: z.string(),
}, async ({ key, value }) => {
  sessionState.set(key, value);
  return { content: [{ type: "text", text: `已设置 ${key} = ${value}` }] };
});

server.tool("get_preference", "获取用户偏好", {
  key: z.string(),
}, async ({ key }) => {
  const value = sessionState.get(key);
  return { content: [{ type: "text", text: value ? String(value) : "未设置" }] };
});
```

---

## 模式 6：管道模式

多步骤数据处理，每一步都是一个工具。

```
数据输入 ──► 步骤 1（提取） ──► 步骤 2（转换） ──► 步骤 3（加载） ──► 结果
```

### 适用场景

- ETL 数据管道
- 文档处理流水线
- 多步骤分析任务

```typescript
server.tool("extract", "从数据源提取数据", { source: z.string() }, async ({ source }) => {
  const data = await extractData(source);
  return { content: [{ type: "text", text: JSON.stringify(data) }] };
});

server.tool("transform", "转换数据格式", { data: z.string(), format: z.enum(["csv", "json", "markdown"]) }, async ({ data, format }) => {
  const transformed = transformData(JSON.parse(data), format);
  return { content: [{ type: "text", text: transformed }] };
});

server.tool("analyze", "分析数据并生成报告", { data: z.string() }, async ({ data }) => {
  const report = analyzeData(JSON.parse(data));
  return { content: [{ type: "text", text: report }] };
});
```

AI 会自动将这些工具串联起来：先提取，再转换，最后分析。

---

## 模式组合

真实的服务器通常组合多种模式：

| 组合 | 示例 |
|------|------|
| API 包装器 + 缓存 | GitHub 服务器 + 响应缓存 |
| 数据库 + 文件系统 | 数据库查询 + 导出为文件 |
| 聚合器 + 管道 | 多源搜索 + 结果分析 |

## 下一步

- **[教程 04：错误处理](../../guides/04-error-handling.md)** — 优雅地处理失败
- **[教程 05：测试](../../guides/05-testing.md)** — 测试你的服务器
- **[示例](../../examples/)** — 这些模式的完整实现
