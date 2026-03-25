# Recipe: Path Sandboxing

Restrict all file operations to an allowed directory. Prevents path traversal attacks.

## TypeScript

```typescript
import { resolve, relative } from "path";
import { stat } from "fs/promises";

class Sandbox {
  private root: string;

  constructor(rootDir: string) {
    this.root = resolve(rootDir);
  }

  /** Resolve a path and verify it's within the sandbox */
  resolve(inputPath: string): string {
    const resolved = resolve(this.root, inputPath);

    if (!resolved.startsWith(this.root + "/") && resolved !== this.root) {
      throw new Error(
        `Access denied: "${inputPath}" resolves outside the sandbox.\n` +
        `Allowed directory: ${this.root}`
      );
    }

    return resolved;
  }

  /** Get a path relative to the sandbox root (for display) */
  relative(absolutePath: string): string {
    return relative(this.root, absolutePath);
  }

  /** Check if a path exists within the sandbox */
  async exists(inputPath: string): Promise<boolean> {
    try {
      const resolved = this.resolve(inputPath);
      await stat(resolved);
      return true;
    } catch {
      return false;
    }
  }

  /** Validate a path and check it's a file (not a directory) */
  async resolveFile(inputPath: string): Promise<string> {
    const resolved = this.resolve(inputPath);
    const stats = await stat(resolved);

    if (!stats.isFile()) {
      throw new Error(`"${inputPath}" is not a file.`);
    }

    return resolved;
  }

  /** Validate a path and check it's a directory */
  async resolveDir(inputPath: string): Promise<string> {
    const resolved = this.resolve(inputPath);
    const stats = await stat(resolved);

    if (!stats.isDirectory()) {
      throw new Error(`"${inputPath}" is not a directory.`);
    }

    return resolved;
  }
}

// Usage
const sandbox = new Sandbox(process.env.FILE_ROOT || process.cwd());

server.tool("read_file", "Read a file", { path: z.string() }, async ({ path }) => {
  const resolved = await sandbox.resolveFile(path);
  const content = await readFile(resolved, "utf-8");
  return { content: [{ type: "text", text: content }] };
});
```

## Python

```python
from pathlib import Path


class Sandbox:
    def __init__(self, root_dir: str):
        self.root = Path(root_dir).resolve()

    def resolve(self, input_path: str) -> Path:
        resolved = (self.root / input_path).resolve()

        if not str(resolved).startswith(str(self.root)):
            raise PermissionError(
                f'Access denied: "{input_path}" resolves outside the sandbox.\n'
                f"Allowed directory: {self.root}"
            )

        return resolved

    def relative(self, absolute_path: Path) -> str:
        return str(absolute_path.relative_to(self.root))

    def resolve_file(self, input_path: str) -> Path:
        resolved = self.resolve(input_path)
        if not resolved.is_file():
            raise FileNotFoundError(f'"{input_path}" is not a file.')
        return resolved

    def resolve_dir(self, input_path: str) -> Path:
        resolved = self.resolve(input_path)
        if not resolved.is_dir():
            raise NotADirectoryError(f'"{input_path}" is not a directory.')
        return resolved


# Usage
sandbox = Sandbox(os.environ.get("FILE_ROOT", os.getcwd()))

@mcp.tool()
def read_file(path: str) -> str:
    """Read a file within the allowed directory."""
    resolved = sandbox.resolve_file(path)
    return resolved.read_text(errors="replace")
```

## Attacks This Blocks

| Attack | Input | Result |
|--------|-------|--------|
| Parent traversal | `../../etc/passwd` | Blocked — resolves outside sandbox |
| Absolute path | `/etc/shadow` | Blocked — outside sandbox |
| Null byte | `file.txt\x00.png` | Blocked — Python's Path handles this |
| Symlink escape | `link -> /etc/` | Blocked — resolve() follows symlinks |
