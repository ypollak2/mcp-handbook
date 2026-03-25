"""
Web Scraper MCP Server

Gives AI assistants the ability to fetch and extract content from web pages.
Uses only the standard library — no external dependencies beyond `mcp`.

Pattern: API Wrapper variant (see guides/03-architecture-patterns.md)
"""

import re
import urllib.request
import urllib.error
from html.parser import HTMLParser

from mcp.server.fastmcp import FastMCP

mcp = FastMCP("web-scraper")

MAX_CONTENT_LENGTH = 1_000_000  # 1 MB max download
TIMEOUT_SECONDS = 15
USER_AGENT = "MCP-WebScraper/1.0 (Educational Example)"


# --- HTML Text Extractor ---


class TextExtractor(HTMLParser):
    """Extracts visible text from HTML, ignoring scripts and styles."""

    def __init__(self) -> None:
        super().__init__()
        self.result: list[str] = []
        self._skip = False
        self._skip_tags = {"script", "style", "noscript", "svg"}

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in self._skip_tags:
            self._skip = True
        if tag in ("br", "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "li", "tr"):
            self.result.append("\n")

    def handle_endtag(self, tag: str) -> None:
        if tag in self._skip_tags:
            self._skip = False

    def handle_data(self, data: str) -> None:
        if not self._skip:
            cleaned = data.strip()
            if cleaned:
                self.result.append(cleaned)

    def get_text(self) -> str:
        text = " ".join(self.result)
        # Collapse whitespace
        text = re.sub(r"\n{3,}", "\n\n", text)
        text = re.sub(r" {2,}", " ", text)
        return text.strip()


def extract_text(html: str) -> str:
    """Extract readable text from HTML."""
    parser = TextExtractor()
    parser.feed(html)
    return parser.get_text()


def extract_links(html: str, base_url: str) -> list[dict[str, str]]:
    """Extract links from HTML."""
    links: list[dict[str, str]] = []
    pattern = r'<a\s[^>]*href=["\']([^"\']*)["\'][^>]*>(.*?)</a>'

    for match in re.finditer(pattern, html, re.IGNORECASE | re.DOTALL):
        href = match.group(1).strip()
        text = re.sub(r"<[^>]+>", "", match.group(2)).strip()

        if not href or href.startswith(("#", "javascript:", "mailto:")):
            continue

        # Resolve relative URLs
        if href.startswith("/"):
            from urllib.parse import urlparse

            parsed = urlparse(base_url)
            href = f"{parsed.scheme}://{parsed.netloc}{href}"
        elif not href.startswith("http"):
            href = f"{base_url.rstrip('/')}/{href}"

        if text:
            links.append({"text": text[:100], "url": href})

    return links


# --- Fetch Helper ---


def fetch_page(url: str) -> str:
    """Fetch a web page with safety limits."""
    if not url.startswith(("http://", "https://")):
        raise ValueError("URL must start with http:// or https://")

    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})

    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as response:
            content_length = response.headers.get("Content-Length")
            if content_length and int(content_length) > MAX_CONTENT_LENGTH:
                raise ValueError(f"Page too large: {int(content_length)} bytes")

            return response.read(MAX_CONTENT_LENGTH).decode("utf-8", errors="replace")
    except urllib.error.HTTPError as e:
        raise ValueError(f"HTTP {e.code}: {e.reason}") from e
    except urllib.error.URLError as e:
        raise ValueError(f"Connection failed: {e.reason}") from e


# --- Tools ---


@mcp.tool()
def get_page_text(url: str, max_length: int = 5000) -> str:
    """Fetch a web page and extract its readable text content.

    Args:
        url: The URL to fetch (must start with http:// or https://)
        max_length: Maximum characters of text to return
    """
    try:
        html = fetch_page(url)
        text = extract_text(html)

        if len(text) > max_length:
            text = text[:max_length] + f"\n\n... (truncated, {len(text)} total characters)"

        return f"Content from {url}:\n\n{text}"
    except ValueError as e:
        return f"Error fetching {url}: {e}"


@mcp.tool()
def get_page_links(url: str, max_links: int = 20) -> str:
    """Fetch a web page and extract all links from it.

    Args:
        url: The URL to fetch
        max_links: Maximum number of links to return
    """
    try:
        html = fetch_page(url)
        links = extract_links(html, url)[:max_links]

        if not links:
            return f"No links found on {url}"

        formatted = [f"- [{link['text']}]({link['url']})" for link in links]
        return f"Links from {url}:\n\n" + "\n".join(formatted)
    except ValueError as e:
        return f"Error fetching {url}: {e}"


@mcp.tool()
def get_page_title(url: str) -> str:
    """Fetch a web page and return just its title.

    Args:
        url: The URL to fetch
    """
    try:
        html = fetch_page(url)
        match = re.search(r"<title[^>]*>(.*?)</title>", html, re.IGNORECASE | re.DOTALL)
        title = match.group(1).strip() if match else "No title found"
        return f"{title} — {url}"
    except ValueError as e:
        return f"Error: {e}"


# --- Start ---

if __name__ == "__main__":
    mcp.run()
