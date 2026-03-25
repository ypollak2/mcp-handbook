"""
File Search MCP Server

Gives AI assistants the ability to search, read, and explore files
within a sandboxed directory. Demonstrates the File System pattern.

Pattern: File System Server (see guides/03-architecture-patterns.md)
"""

import os
import glob as globlib
from pathlib import Path

from mcp.server.fastmcp import FastMCP

mcp = FastMCP("file-search")

# Sandbox: only allow access within this directory
ALLOWED_ROOT = os.environ.get("FILE_ROOT", os.getcwd())


def validate_path(file_path: str) -> Path:
    """Resolve and validate that a path is within the allowed root."""
    resolved = Path(file_path).resolve()
    root = Path(ALLOWED_ROOT).resolve()

    if not str(resolved).startswith(str(root)):
        raise ValueError(
            f"Access denied: {file_path} is outside the allowed directory ({ALLOWED_ROOT})"
        )

    return resolved


# --- Tools ---


@mcp.tool()
def search_files(pattern: str, max_results: int = 20) -> str:
    """Search for files matching a glob pattern.

    Args:
        pattern: Glob pattern like '**/*.py' or 'src/**/*.ts'
        max_results: Maximum number of results to return
    """
    root = Path(ALLOWED_ROOT).resolve()
    search_pattern = str(root / pattern)
    matches = globlib.glob(search_pattern, recursive=True)

    # Filter to files only (not directories) and limit results
    files = [m for m in matches if os.path.isfile(m)][:max_results]

    if not files:
        return f'No files found matching "{pattern}" in {ALLOWED_ROOT}'

    # Show relative paths for cleaner output
    relative = [os.path.relpath(f, ALLOWED_ROOT) for f in files]
    return "\n".join(relative)


@mcp.tool()
def read_file(path: str, max_lines: int = 200) -> str:
    """Read the contents of a file.

    Args:
        path: File path (relative to the root directory)
        max_lines: Maximum number of lines to return
    """
    full_path = validate_path(os.path.join(ALLOWED_ROOT, path))

    if not full_path.exists():
        return f"File not found: {path}"

    if not full_path.is_file():
        return f"Not a file: {path}"

    # Check file size to avoid reading huge files
    size_mb = full_path.stat().st_size / (1024 * 1024)
    if size_mb > 10:
        return f"File too large ({size_mb:.1f} MB). Maximum is 10 MB."

    text = full_path.read_text(errors="replace")
    lines = text.splitlines()

    if len(lines) > max_lines:
        truncated = lines[:max_lines]
        return "\n".join(truncated) + f"\n\n... ({len(lines) - max_lines} more lines)"

    return text


@mcp.tool()
def search_content(pattern: str, file_pattern: str = "**/*", max_results: int = 20) -> str:
    """Search for text content within files (like grep).

    Args:
        pattern: Text to search for (case-insensitive)
        file_pattern: Glob pattern to filter which files to search
        max_results: Maximum number of matches to return
    """
    root = Path(ALLOWED_ROOT).resolve()
    search_glob = str(root / file_pattern)
    files = [f for f in globlib.glob(search_glob, recursive=True) if os.path.isfile(f)]

    matches: list[str] = []
    pattern_lower = pattern.lower()

    for file_path in files:
        if len(matches) >= max_results:
            break

        try:
            with open(file_path, "r", errors="replace") as f:
                for line_num, line in enumerate(f, 1):
                    if pattern_lower in line.lower():
                        rel_path = os.path.relpath(file_path, ALLOWED_ROOT)
                        matches.append(f"{rel_path}:{line_num}: {line.rstrip()}")

                        if len(matches) >= max_results:
                            break
        except (PermissionError, IsADirectoryError):
            continue

    if not matches:
        return f'No matches for "{pattern}" in files matching "{file_pattern}"'

    return "\n".join(matches)


@mcp.tool()
def list_directory(path: str = ".") -> str:
    """List contents of a directory with file sizes.

    Args:
        path: Directory path relative to root (default: root directory)
    """
    full_path = validate_path(os.path.join(ALLOWED_ROOT, path))

    if not full_path.is_dir():
        return f"Not a directory: {path}"

    entries: list[str] = []
    for entry in sorted(full_path.iterdir()):
        rel = os.path.relpath(entry, ALLOWED_ROOT)
        if entry.is_dir():
            entries.append(f"  {rel}/")
        else:
            size = entry.stat().st_size
            if size < 1024:
                size_str = f"{size} B"
            elif size < 1024 * 1024:
                size_str = f"{size / 1024:.1f} KB"
            else:
                size_str = f"{size / (1024 * 1024):.1f} MB"
            entries.append(f"  {rel}  ({size_str})")

    if not entries:
        return f"Directory is empty: {path}"

    return f"Contents of {path}/\n\n" + "\n".join(entries)


# --- Resources ---


@mcp.resource("file://overview")
def directory_overview() -> str:
    """Overview of the root directory structure."""
    root = Path(ALLOWED_ROOT).resolve()

    file_count = 0
    dir_count = 0
    total_size = 0
    extensions: dict[str, int] = {}

    for entry in root.rglob("*"):
        if entry.is_file():
            file_count += 1
            total_size += entry.stat().st_size
            ext = entry.suffix or "(no extension)"
            extensions[ext] = extensions.get(ext, 0) + 1
        elif entry.is_dir():
            dir_count += 1

    size_mb = total_size / (1024 * 1024)
    top_extensions = sorted(extensions.items(), key=lambda x: x[1], reverse=True)[:10]

    lines = [
        f"Directory: {ALLOWED_ROOT}",
        f"Files: {file_count}",
        f"Directories: {dir_count}",
        f"Total size: {size_mb:.1f} MB",
        "",
        "Top file types:",
    ]

    for ext, count in top_extensions:
        lines.append(f"  {ext}: {count} files")

    return "\n".join(lines)


# --- Start ---

if __name__ == "__main__":
    mcp.run()
