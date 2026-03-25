# Contributing to The MCP Handbook

Thank you for your interest in contributing! This handbook is a community effort, and every contribution makes it better for developers everywhere.

## Ways to Contribute

### Low effort, high impact
- **Fix typos or unclear wording** — If something confused you, it'll confuse others
- **Add a link** — Know a useful MCP resource? Add it
- **Report an issue** — Found an error or outdated info? Open an issue

### Medium effort
- **Improve an existing guide** — Add a missing section, better examples, or clearer diagrams
- **Add a new example server** — Build a working MCP server that demonstrates a pattern
- **Translate a guide** — Help make MCP accessible in more languages

### High effort
- **Write a new guide** — Cover a topic that's missing from the handbook
- **Create a template** — Build a copy-paste starter for a common use case
- **Build testing utilities** — Help improve the MCP testing story

## Guidelines

### Writing Style
- **Be concise.** Developers skim. Use short paragraphs, bullet points, and code examples.
- **Be practical.** Show working code, not pseudocode. Every example should run.
- **Be honest.** Acknowledge trade-offs. Don't oversell approaches.
- **Use clear headings.** Readers should find what they need from the table of contents alone.

### Code Examples
- All code examples must be complete and runnable
- Include both TypeScript and Python where possible
- Add comments for non-obvious decisions only
- Test your examples before submitting

### Example Servers
Each example server should include:
```
examples/<language>/<name>/
├── README.md          # What it does, how to run it, what it teaches
├── src/               # Source code
├── package.json       # (TypeScript) or pyproject.toml (Python)
└── tests/             # At least one test
```

### Commit Messages
Use conventional commits:
```
feat: add database explorer example
fix: correct TypeScript SDK import path
docs: improve error handling guide clarity
```

## Submitting Changes

1. Fork the repository
2. Create a branch: `git checkout -b feat/my-contribution`
3. Make your changes
4. Test any code examples
5. Submit a pull request

## Code of Conduct

Be kind. Be helpful. We're all learning together.

## Questions?

Open a [Discussion](https://github.com/ypollak2/mcp-handbook/discussions) — we're happy to help you contribute.
