# NN AGENT

<p align="center">
  <a href="https://github.com/nhatnamdev662/nn-android/releases/latest"><img src="https://img.shields.io/github/v/release/nhatnamdev662/nn-android?style=for-the-badge&label=release" alt="Latest release"></a>
  <a href="https://github.com/nhatnamdev662/nn-android/releases"><img src="https://img.shields.io/github/downloads/nhatnamdev662/nn-android/total?style=for-the-badge" alt="Downloads"></a>
  <img src="https://img.shields.io/badge/Android-11%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 11+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache_2.0-blue?style=for-the-badge" alt="License: Apache-2.0"></a>
</p>

**A floating chat bubble that lets Claude Code, Codex, OpenCode, Antigravity or Grok use your Android phone.**

Type or say what you want, like "reply to Sam that I'm running late" or "add a Switch 2 to my Amazon cart". NN Agent reads the screen, taps and types to get it done, and asks you before anything is sent, posted, bought or deleted.

It runs inside the app, on your own plan: [Claude Code](https://docs.claude.com/en/docs/claude-code) with your Claude plan, Codex with your ChatGPT plan, Google's [Antigravity CLI](https://antigravity.google) with your Google account, xAI's [Grok Build](https://x.ai/build) with your Google or X account, or [OpenCode](https://opencode.ai) with its free models and no account at all. No PC, no adb, no root, and no API key needed (you can add keys for other services, such as DeepSeek or OpenRouter, and use them with Claude Code, Codex or OpenCode).

<p align="center"><a href="https://github.com/nhatnamdev662/nn-android/releases/latest/download/NN_AGENT.apk"><b>Download NN_AGENT.apk</b></a> · Android 11+, 64-bit</p>

## What it can do

| | |
|---|---|
| **Uses your apps** | Messages, shopping, smart home, settings: anything on screen. Add instructions while it works. |
| **Asks first** | Approve, Deny or Change before anything is sent, posted, bought or deleted. |
| **Shows you how** | "Show me how to turn on dark mode": it points at each step and waits for your tap. |
| **Acts on notifications** | "When a customer asks on WhatsApp, reply with our opening hours", with your OK or a countdown you can stop. |
| **Voice** | Talk to it and have replies read aloud. |
| **Background browser** | Web pages in a browser inside NN Agent, without taking over your screen, even while the phone is locked. Watch and use it live in the chat; it marks what you need to do (sign in, a captcha) and never types your passwords. |
| **Builds apps** | Turn on Linux tools in a chat and "make me a calculator app" builds a real APK on the phone; the GitHub version also installs it for you. |
| **Phone tools anywhere** | The same tools as a local MCP server for your own Claude Code, Codex, OpenCode, Antigravity or Grok sessions, or over stdio with [mcp-bridge](mcp-bridge) for clients that start servers as commands. |

> **Status: 1.0.0.** Independent autonomous AI agent framework for Android. NN Agent is an independent project, not made by or affiliated with Anthropic, OpenAI, Google or xAI.

## How it works

```
NN Agent app / bubble ──► NN's Linux (Alpine via proot): claude, codex, opencode, agy or grok
        ▲                                                     │
        └──────────── streamed replies ◄──────────────────────┤
                                                              ▼
NN Agent accessibility service ◄──── MCP on 127.0.0.1 (phone tools)
```

NN Agent is a normal Android app with an accessibility service, the kind of permission screen readers use. The agent runs in a small Linux inside the app and uses NN Agent's phone tools (read the screen, tap, type, scroll, open apps) over a local MCP server. No adb or Wireless debugging needed for core functions, and it works without Wi-Fi.

## Setup

You need a 64-bit Android 11+ phone, plus a Claude, ChatGPT or Google account for those agents (Grok: a Google or X account). OpenCode's free models need no account.

1. **Install NN Agent:** [download NN_AGENT.apk](https://github.com/nhatnamdev662/nn-android/releases/latest/download/NN_AGENT.apk) and open it. It updates itself, and installs the apps it builds for you.
2. **Follow the setup guide** that opens on first launch: pick your agent, turn on the accessibility service, install and sign in, and try a first task.

**Install blocked or "Restricted setting"?** See [Setup in detail](docs/GUIDE.md#setup-in-detail).

## Local models

Run the model on your own computer and nothing goes to an AI company. Start [Ollama](https://ollama.com), LM Studio or llama.cpp's server on a PC (for Ollama, set `OLLAMA_HOST=0.0.0.0` and a context of at least 24k tokens, e.g. `OLLAMA_CONTEXT_LENGTH=24576`, since the phone tools take about 7k). In NN Agent: Settings → API keys → Add API key → **Ollama** (or LM Studio, llama.cpp), put the PC's address on your Wi-Fi instead of `localhost`, leave the key empty, and use it in an OpenCode chat. Plain `http://` works on your own network and over Tailscale.

## Updating

Install the new APK over the old one: chats, settings and sign-ins stay (don't uninstall first). You can also ask NN Agent to *"update yourself"*, or let [Obtainium](https://obtainium.imranr.dev) do it ([how](docs/GUIDE.md#updating)).

## Security

An accessibility service is powerful, so please read this.

- **Local only.** The phone tools listen on `127.0.0.1` with a random per-install token.
- **NN Agent enforces the approvals, not the agent.** Messaging, shopping and money apps start on a hard limit: NN Agent itself stops on buttons like Send, Pay and Delete until you OK them.
- **Prompt injection is the main risk.** A message or web page could try to give the agent orders. On-screen text is treated as content, and the approval card is the backstop, so read it.
- **No passwords.** NN Agent never types passwords, codes or payment details; it hands those to you.
- **What leaves the phone:** only what the agent sends to its provider (Anthropic, OpenAI, Google, xAI, OpenCode's service or the provider you connected) to answer you.

More in [Security in detail](docs/GUIDE.md#security-in-detail).

## Contributing

Bug reports, ideas and translation fixes are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md). If NN Agent is useful to you, a ⭐ helps other people find it.

## For developers

```
git clone --recurse-submodules https://github.com/nhatnamdev662/nn-android.git
./gradlew assembleRelease     # Android Studio / Gradle
```

## License

[Apache-2.0](LICENSE). Geist fonts: SIL Open Font License. Icons: Material Symbols (Apache-2.0). Bundled proot (GPL-2.0), talloc (LGPL-3.0) and libandroid-shmem (BSD-3-Clause): see [third_party](third_party/README.md) for licences and source.
