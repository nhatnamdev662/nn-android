# Security

NN Agent lets AI agents use your phone, so its approval rules are its most important part: an agent must not send, post, buy, pay or delete anything without your OK on NN Agent's card.

## Reporting a vulnerability

Found a way for an agent to act without your approval, to approve for you, or to reach NN Agent's phone tools without its token? Please don't post it publicly. Use [private vulnerability reporting](https://github.com/nhatnamdev662/nn-android/security/advisories/new) instead.

Helpful details: your NN Agent version (GitHub or F-Droid), the agent and model, what you asked, and the steps that let the agent get around the approval.

## What counts

- An agent (or a web page, message or notification it reads) gets a risky action done without an approval card, or with an approval meant for something else.
- Another app on the phone uses NN Agent's phone tools server (127.0.0.1:8767) or its events without the per-install token.
- A notification rule's run acts outside what its rule allows.
- Private data (chats, screen text, sign-ins) leaves the phone other than to the AI service you chose.

The files that decide what an agent may do without asking are `Guard.java`, `Reviewer.java`, the approval cards in `Bubble.java`, and `linux/system-prompt.md`.

## Supported versions

Only the latest release on GitHub.
