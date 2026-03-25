# Recipe: REST API Client

A reusable fetch wrapper with authentication, retries, timeouts, and error translation.

## TypeScript

```typescript
interface ApiClientOptions {
  baseUrl: string;
  authHeader?: string;
  timeoutMs?: number;
  maxRetries?: number;
}

class ApiClient {
  private baseUrl: string;
  private authHeader?: string;
  private timeoutMs: number;
  private maxRetries: number;

  constructor(options: ApiClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, "");
    this.authHeader = options.authHeader;
    this.timeoutMs = options.timeoutMs ?? 10_000;
    this.maxRetries = options.maxRetries ?? 2;
  }

  async get<T>(path: string, params?: Record<string, string>): Promise<T> {
    const url = new URL(`${this.baseUrl}${path}`);
    if (params) {
      Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));
    }
    return this.request<T>(url.toString(), { method: "GET" });
  }

  async post<T>(path: string, body: unknown): Promise<T> {
    return this.request<T>(`${this.baseUrl}${path}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
  }

  private async request<T>(url: string, init: RequestInit, attempt = 1): Promise<T> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const headers: Record<string, string> = {
        Accept: "application/json",
        ...(init.headers as Record<string, string>),
      };

      if (this.authHeader) {
        headers["Authorization"] = this.authHeader;
      }

      const response = await fetch(url, {
        ...init,
        headers,
        signal: controller.signal,
      });

      if (response.status === 429 && attempt <= this.maxRetries) {
        const retryAfter = parseInt(response.headers.get("Retry-After") || "5", 10);
        await new Promise((r) => setTimeout(r, retryAfter * 1000));
        return this.request<T>(url, init, attempt + 1);
      }

      if (response.status >= 500 && attempt <= this.maxRetries) {
        await new Promise((r) => setTimeout(r, 1000 * attempt));
        return this.request<T>(url, init, attempt + 1);
      }

      if (!response.ok) {
        const body = await response.text().catch(() => "");
        throw new Error(`API error ${response.status}: ${body || response.statusText}`);
      }

      return (await response.json()) as T;
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        throw new Error(`Request timed out after ${this.timeoutMs}ms: ${url}`);
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  }
}

// Usage
const github = new ApiClient({
  baseUrl: "https://api.github.com",
  authHeader: `Bearer ${process.env.GITHUB_TOKEN}`,
  timeoutMs: 15_000,
});

const repos = await github.get<{ items: any[] }>("/search/repositories", {
  q: "mcp server",
  sort: "stars",
});
```

## Python

```python
import urllib.request
import urllib.error
import json
import time
from dataclasses import dataclass, field


@dataclass
class ApiClient:
    base_url: str
    auth_header: str | None = None
    timeout_seconds: int = 10
    max_retries: int = 2
    _headers: dict[str, str] = field(default_factory=dict, init=False)

    def __post_init__(self):
        self.base_url = self.base_url.rstrip("/")
        self._headers = {"Accept": "application/json"}
        if self.auth_header:
            self._headers["Authorization"] = self.auth_header

    def get(self, path: str, params: dict[str, str] | None = None) -> dict:
        url = f"{self.base_url}{path}"
        if params:
            qs = "&".join(f"{k}={v}" for k, v in params.items())
            url = f"{url}?{qs}"
        return self._request(url)

    def post(self, path: str, body: dict) -> dict:
        url = f"{self.base_url}{path}"
        data = json.dumps(body).encode()
        headers = {**self._headers, "Content-Type": "application/json"}
        return self._request(url, data=data, headers=headers)

    def _request(
        self, url: str, data: bytes | None = None,
        headers: dict | None = None, attempt: int = 1,
    ) -> dict:
        req = urllib.request.Request(url, data=data, headers=headers or self._headers)

        try:
            with urllib.request.urlopen(req, timeout=self.timeout_seconds) as resp:
                return json.loads(resp.read())
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt <= self.max_retries:
                retry_after = int(e.headers.get("Retry-After", "5"))
                time.sleep(retry_after)
                return self._request(url, data, headers, attempt + 1)
            if e.code >= 500 and attempt <= self.max_retries:
                time.sleep(attempt)
                return self._request(url, data, headers, attempt + 1)
            body = e.read().decode(errors="replace")
            raise RuntimeError(f"API error {e.code}: {body or e.reason}") from e


# Usage
github = ApiClient(
    base_url="https://api.github.com",
    auth_header=f"Bearer {os.environ['GITHUB_TOKEN']}",
)
repos = github.get("/search/repositories", {"q": "mcp server", "sort": "stars"})
```

## Features

- **Timeout** — Every request has a deadline (default 10s)
- **Retry** — Automatically retries 429 (rate limit) and 5xx (server error)
- **Auth** — Bearer token added to all requests
- **Error translation** — Raw HTTP errors become readable messages
