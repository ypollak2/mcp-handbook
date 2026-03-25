# 工具、资源和提示词

> **时间：** 20 分钟 | **难度：** 入门 | **前置要求：** [教程 01](01-getting-started.md)

MCP 服务器通过三种基本元素（primitives）向 AI 暴露功能。理解何时使用哪一种是构建优秀 MCP 服务器的关键。

## 概览

```
┌─────────────────────────────────────────────────────────┐
│                     MCP 服务器                           │
│                                                         │
│  工具（Tools）      资源（Resources）   提示词（Prompts） │
│  ┌───────────┐    ┌───────────┐      ┌───────────┐     │
│  │ 执行操作   │    │ 暴露数据   │      │ 模板提示   │     │
│  │ 有副作用   │    │ 只读      │      │ 可参数化   │     │
│  │ 需要参数   │    │ 有 URI    │      │ 编码专业   │     │
│  └───────────┘    └───────────┘      │ 知识       │     │
│                                      └───────────┘     │
└─────────────────────────────────────────────────────────┘
```

## 工具（Tools）

工具是 AI 可以调用的函数。它们是 MCP 中最常用的特性。

### 何时使用工具

- 操作有**副作用**（写入、发送、创建、删除）
- 需要在调用时提供**输入参数**
- 返回**操作结果**

### TypeScript

```typescript
import { z } from "zod";

// 简单工具
server.tool(
  "search_users",
  "根据姓名搜索用户",
  {
    query: z.string().describe("搜索关键词"),
    limit: z.number().min(1).max(100).default(10).describe("最大返回数量"),
  },
  async ({ query, limit }) => {
    const users = await db.searchUsers(query, limit);
    return {
      content: [{
        type: "text",
        text: users.map((u) => `${u.name} (${u.email})`).join("\n"),
      }],
    };
  }
);

// 有副作用的工具
server.tool(
  "send_email",
  "发送一封电子邮件",
  {
    to: z.string().email(),
    subject: z.string().max(200),
    body: z.string().max(10000),
  },
  async ({ to, subject, body }) => {
    await emailService.send({ to, subject, body });
    return {
      content: [{ type: "text", text: `邮件已发送给 ${to}` }],
    };
  }
);
```

### Python

```python
@mcp.tool()
def search_users(query: str, limit: int = 10) -> str:
    """根据姓名搜索用户。

    Args:
        query: 搜索关键词
        limit: 最大返回数量（1-100）
    """
    users = db.search_users(query, limit)
    return "\n".join(f"{u.name} ({u.email})" for u in users)

@mcp.tool()
async def send_email(to: str, subject: str, body: str) -> str:
    """发送一封电子邮件。"""
    await email_service.send(to=to, subject=subject, body=body)
    return f"邮件已发送给 {to}"
```

### 工具设计最佳实践

| 原则 | 好的 | 不好的 |
|------|------|--------|
| 名称清晰 | `search_users` | `do_thing` |
| 参数有描述 | `z.string().describe("SQL 查询")` | `z.string()` |
| 返回有意义的结果 | `"找到 3 条记录：..."` | `"完成"` |
| 优雅处理错误 | `{ isError: true, text: "..." }` | 抛出未处理的异常 |
| 限制参数范围 | `.max(100)`, `.enum(["a","b"])` | 无约束的字符串 |

---

## 资源（Resources）

资源暴露只读数据，通过 URI 标识。AI 客户端可以读取和缓存资源。

### 何时使用资源

- 暴露**只读数据**（文件、配置、数据库记录）
- 数据有自然的 **URI** 标识符
- 客户端可能需要**缓存**或**订阅**更新

### TypeScript

```typescript
// 静态资源
server.resource(
  "config://app",
  "应用程序配置",
  async () => ({
    contents: [{
      uri: "config://app",
      text: JSON.stringify(appConfig, null, 2),
      mimeType: "application/json",
    }],
  })
);

// 动态资源模板
server.resource(
  "db://users/{id}",
  "根据 ID 获取用户",
  async (uri) => {
    const id = uri.pathname.split("/").pop();
    const user = await db.findUser(id);
    return {
      contents: [{
        uri: uri.href,
        text: JSON.stringify(user),
        mimeType: "application/json",
      }],
    };
  }
);
```

### Python

```python
@mcp.resource("config://app")
def get_config() -> str:
    """返回应用程序配置。"""
    return json.dumps(app_config, indent=2)

@mcp.resource("db://users/{user_id}")
def get_user(user_id: str) -> str:
    """根据 ID 获取用户信息。"""
    user = db.find_user(user_id)
    return json.dumps(user)
```

---

## 提示词（Prompts）

提示词是预定义的模板，引导 AI 如何处理特定任务。

### 何时使用提示词

- 提供可复用的**提示模板**
- 引导 AI 的**处理方式**
- 编码**领域专业知识**

### TypeScript

```typescript
server.prompt(
  "review-code",
  "对代码进行安全审查",
  { code: z.string().describe("要审查的代码") },
  ({ code }) => ({
    messages: [{
      role: "user",
      content: {
        type: "text",
        text: `请对以下代码进行安全审查，关注以下方面：
1. 输入验证
2. SQL 注入
3. XSS 漏洞
4. 敏感数据暴露

代码：
${code}`,
      },
    }],
  })
);
```

### Python

```python
@mcp.prompt()
def review_code(code: str) -> str:
    """对代码进行安全审查。"""
    return f"""请对以下代码进行安全审查，关注以下方面：
1. 输入验证
2. SQL 注入
3. XSS 漏洞
4. 敏感数据暴露

代码：
{code}"""
```

---

## 决策指南

```
你需要暴露什么？
│
├── 需要执行操作？（写入、发送、创建）
│   └── 使用 工具（Tool）
│
├── 需要暴露数据？（文件、配置、记录）
│   └── 使用 资源（Resource）
│
├── 需要引导 AI 的行为？
│   └── 使用 提示词（Prompt）
│
└── 不确定？
    └── 先用 工具 — 它是最灵活的
```

## 下一步

- **[教程 03：架构模式](03-architecture-patterns.md)** — 常见场景的设计方案
- **[示例](../../examples/)** — 查看这些概念的实际应用
