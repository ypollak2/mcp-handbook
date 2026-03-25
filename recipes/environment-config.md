# Recipe: Environment Config

Load and validate environment variables at server startup. Fail fast with clear messages if required config is missing.

## TypeScript

```typescript
interface ServerConfig {
  apiKey: string;
  apiBaseUrl: string;
  maxResults: number;
  debug: boolean;
}

function loadConfig(): ServerConfig {
  const missing: string[] = [];

  const apiKey = process.env.API_KEY;
  if (!apiKey) missing.push("API_KEY");

  const apiBaseUrl = process.env.API_BASE_URL;
  if (!apiBaseUrl) missing.push("API_BASE_URL");

  if (missing.length > 0) {
    console.error(`Missing required environment variables: ${missing.join(", ")}`);
    console.error("Set them in your MCP client config or shell environment.");
    process.exit(1);
  }

  return {
    apiKey: apiKey!,
    apiBaseUrl: apiBaseUrl!,
    maxResults: parseInt(process.env.MAX_RESULTS || "50", 10),
    debug: process.env.DEBUG === "true",
  };
}

// Use at the top of your server file
const config = loadConfig();
```

## Python

```python
import os
import sys
from dataclasses import dataclass


@dataclass(frozen=True)
class ServerConfig:
    api_key: str
    api_base_url: str
    max_results: int = 50
    debug: bool = False


def load_config() -> ServerConfig:
    missing = []

    api_key = os.environ.get("API_KEY")
    if not api_key:
        missing.append("API_KEY")

    api_base_url = os.environ.get("API_BASE_URL")
    if not api_base_url:
        missing.append("API_BASE_URL")

    if missing:
        print(f"Missing required environment variables: {', '.join(missing)}", file=sys.stderr)
        print("Set them in your MCP client config or shell environment.", file=sys.stderr)
        sys.exit(1)

    return ServerConfig(
        api_key=api_key,  # type: ignore
        api_base_url=api_base_url,  # type: ignore
        max_results=int(os.environ.get("MAX_RESULTS", "50")),
        debug=os.environ.get("DEBUG", "").lower() == "true",
    )


config = load_config()
```

## Client Config

Pass environment variables in the MCP client configuration:

```json
{
  "mcpServers": {
    "my-server": {
      "command": "npx",
      "args": ["tsx", "server.ts"],
      "env": {
        "API_KEY": "your-key-here",
        "API_BASE_URL": "https://api.example.com",
        "MAX_RESULTS": "100",
        "DEBUG": "true"
      }
    }
  }
}
```
