# NN Agent phone bridge (stdio)

**This needs the free NN Agent app on your Android phone: [download the APK](https://github.com/nhatnamdev662/nn-android/releases/latest/download/NN_AGENT.apk) or see [NN Agent on GitHub](https://github.com/nhatnamdev662/nn-android).** NN Agent lets AI agents read your screen, tap and type, and asks before anything risky. No root, no adb, no PC.

NN Agent's phone tools (read the screen, tap, type, scroll, open apps, screenshots, notifications, the background browser…) are a local MCP server inside the NN Agent app on your Android phone: `http://127.0.0.1:8765/mcp`. Most agents connect to it directly; NN Agent's **Settings → Phone tools in your own sessions** gives the command for Claude Code, Codex, OpenCode, Antigravity and Grok.

This bridge is for MCP clients that can only start a server as a command. It speaks MCP over stdio and passes every request on to NN Agent. It has no dependencies (Node 18+).

```bash
BUDDY_TOKEN=<token from NN Agent's Settings> npx -y nn-phone-bridge
```

Or from a copy of this folder: `BUDDY_TOKEN=<token> node bridge.mjs`.

MCP client config:

```json
{
  "mcpServers": {
    "phone": {
      "command": "npx",
      "args": ["-y", "nn-phone-bridge"],
      "env": { "BUDDY_TOKEN": "<token from NN Agent's Settings>" }
    }
  }
}
```

- `BUDDY_TOKEN`: the token NN Agent shows (with or without `Bearer `).
- `BUDDY_URL`: NN Agent's address, if not `http://127.0.0.1:8765/mcp`.

Run it on the phone itself (Termux, for example): NN Agent only listens on `127.0.0.1`. When NN Agent can't be reached, the bridge still lists the tools (from `tools.json`, a copy of NN Agent's list) and each call says why it couldn't run; that's also what happens in the Dockerfile's container, which has no phone.

NN Agent is an independent project, not made by or affiliated with Anthropic, OpenAI, Google or xAI.
