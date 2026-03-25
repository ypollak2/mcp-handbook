# 入门指南

> **时间：** 15 分钟 | **难度：** 入门 | **前置要求：** 无

## 什么是 MCP？

MCP（Model Context Protocol，模型上下文协议）是一个开放标准，让 AI 助手能够连接到你的工具和数据。可以把它想象成 AI 应用的 USB-C 接口 — 一个标准协议，适用于所有工具。

```
没有 MCP：                        有 MCP：
┌──────────┐                     ┌──────────┐
│ AI 助手   │                     │ AI 助手   │
│          ├── 自定义 API ──►     │          ├── MCP ──► 任何工具
│          ├── 自定义 API ──►     │          │
│          ├── 自定义 API ──►     └──────────┘
└──────────┘                     一个协议，所有工具
每个工具一个集成
```

### 三大核心概念

| 概念 | 作用 | 类比 |
|------|------|------|
| **工具（Tools）** | AI 可以调用的函数 | REST API 端点 |
| **资源（Resources）** | AI 可以读取的数据 | GET 请求/文件 |
| **提示词（Prompts）** | 可复用的提示模板 | API 文档中的示例 |

### 工作原理

```
用户："查询上季度的销售数据"
  │
  ▼
AI 助手（Claude、Cursor 等）
  │
  ├── 发现你的 MCP 服务器有 "query" 工具
  ├── 调用：query({ sql: "SELECT ... FROM sales" })
  │
  ▼
MCP 服务器
  │
  ├── 验证输入
  ├── 执行查询
  ├── 返回结果
  │
  ▼
AI 助手将结果格式化后展示给用户
```

## 构建你的第一个服务器

### TypeScript

```bash
mkdir my-mcp-server && cd my-mcp-server
npm init -y
npm install @modelcontextprotocol/sdk zod
```

```typescript
// server.ts
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({
  name: "my-first-server",
  version: "1.0.0",
});

// 定义一个工具
server.tool(
  "greet",
  "为某人生成问候语",
  { name: z.string().describe("要问候的人的名字") },
  async ({ name }) => ({
    content: [{ type: "text", text: `你好，${name}！欢迎使用 MCP。` }],
  })
);

// 定义一个资源
server.resource("info://server", "服务器信息", async () => ({
  contents: [{
    uri: "info://server",
    text: "这是一个示例 MCP 服务器。",
  }],
}));

// 启动服务器
const transport = new StdioServerTransport();
await server.connect(transport);
```

### Python

```bash
mkdir my-mcp-server && cd my-mcp-server
pip install mcp
```

```python
# server.py
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("my-first-server")

@mcp.tool()
def greet(name: str) -> str:
    """为某人生成问候语。"""
    return f"你好，{name}！欢迎使用 MCP。"

@mcp.resource("info://server")
def server_info() -> str:
    """返回服务器信息。"""
    return "这是一个示例 MCP 服务器。"

if __name__ == "__main__":
    mcp.run()
```

## 测试你的服务器

使用 MCP Inspector — 一个可视化调试工具：

```bash
# TypeScript
npx @modelcontextprotocol/inspector npx tsx server.ts

# Python
npx @modelcontextprotocol/inspector python server.py
```

Inspector 会在浏览器中打开，你可以：
- 查看所有注册的工具和资源
- 用自定义输入调用工具
- 读取资源内容
- 查看原始 JSON-RPC 消息

## 连接到 AI 客户端

### Claude Desktop

编辑配置文件：

**macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
**Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

```json
{
  "mcpServers": {
    "my-server": {
      "command": "npx",
      "args": ["tsx", "/你的项目的绝对路径/server.ts"]
    }
  }
}
```

重启 Claude Desktop，你的工具就会出现在工具列表中。

### VS Code / Cursor

在项目根目录创建 `.vscode/mcp.json`：

```json
{
  "servers": {
    "my-server": {
      "command": "npx",
      "args": ["tsx", "server.ts"]
    }
  }
}
```

### Claude Code

```bash
claude mcp add my-server npx tsx /绝对路径/server.ts
```

## 常见问题

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| "服务器未找到" | PATH 环境变量不同 | 在配置中使用绝对路径 |
| 工具列表为空 | 工具在连接后注册 | 在 `server.connect()` 前注册工具 |
| JSON 解析错误 | stdout 被污染 | 用 `console.error` 代替 `console.log` |
| Inspector 正常但客户端不行 | 缺少环境变量 | 在配置的 `env` 中添加变量 |

## 下一步

- **[教程 02：工具、资源和提示词](02-core-concepts.md)** — 深入了解三大构建模块
- **[教程 03：架构模式](03-architecture-patterns.md)** — 生产级设计方案
