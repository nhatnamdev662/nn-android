# NN Agent privacy policy

*Last updated: November 2026*

NN Agent is an open-source Android app (Apache 2.0). It lets an AI agent of your choice (Claude Code, Codex, OpenCode, Antigravity or Grok) use your phone's apps for you. This policy explains what data NN Agent handles. The source code is public at https://github.com/nhatnamdev662/nn-android, so every statement here can be checked.

## NN Agent has no servers

NN Agent doesn't run any servers or analytics. It doesn't collect, store, sell or share your data with us or with advertisers, and it shows no ads.

## Accessibility service

NN Agent uses Android's AccessibilityService API so the agent can carry out what you ask: it reads what's on the screen and taps, types and scrolls in other apps, and it shows the floating bubble. NN Agent explains this and asks for your agreement in the app before you turn the service on.

- The screen content is read only while the agent works on a request you made.
- That content, which can include personal information shown on screen (such as messages, names, addresses or order details), is sent to the AI service of the agent you chose, only to carry out your request (see below).
- Before anything that sends, posts, buys or deletes, NN Agent shows the action and waits for your approval.
- You can turn the service off at any time in Android's accessibility settings.

## Notification access (notification rules)

Notification rules are optional. NN Agent reads notifications only if you add a rule and give NN Agent notification access in Android's settings; NN Agent explains this and asks for your agreement in the app first.

- NN Agent checks each new notification against your rules on your phone. Notifications that no rule matches are ignored right away: they are not stored and not sent anywhere.
- When a notification matches a rule, its app name, sender and text are sent to the agent you picked for that rule (and so to that agent's AI service, see below), together with your instruction, to carry out the rule.
- NN Agent can answer through a notification's Reply button. It asks for your approval first, unless you gave the rule a countdown, in which case the reply is sent when the countdown ends unless you stop it.
- The rule's chat and a short activity list (time, app, sender and what was done) are kept on your phone.
- You can pause or delete rules in the app, and remove the access in Android's settings at any time.

## Data sent to the AI service you choose

The agent runs inside the app and talks directly to its own provider. What you type or say, the attachments you add, the screen content the agent reads, and the notifications your rules pass on are sent to:

- **Claude Code:** Anthropic, under your Claude account. See https://www.anthropic.com/legal/privacy
- **Codex:** OpenAI, under your ChatGPT account. See https://openai.com/policies/privacy-policy
- **OpenCode:** the provider of the model you pick (by default OpenCode's free models). See https://opencode.ai
- **Antigravity:** Google, under your Google account. See https://policies.google.com/privacy
- **Grok:** xAI, under your Grok account. See https://x.ai/legal/privacy-policy

You sign in with these services yourself; NN Agent never sees your passwords. Their own privacy policies apply to what they receive.

## Voice

If you use the microphone, your voice is turned into text by the engine you choose in Settings: your phone's speech recognizer, Claude's dictation (Anthropic) or ChatGPT's (OpenAI). Replies read aloud use the phone's voice or ChatGPT's voices (OpenAI).

## Data stored on your phone

Your chats, settings, sign-ins and the agent's files are stored only in NN Agent's private storage on your phone. Uninstalling NN Agent deletes them. You can delete a chat at any time in the app.

## Downloads

To set up an agent, NN Agent downloads a small Linux system from Alpine Linux (dl-cdn.alpinelinux.org) and the agent from its official source (Anthropic, OpenAI, Google, xAI or npm). These downloads don't include any of your personal data. The version of NN Agent from GitHub also asks GitHub (api.github.com) about once a day whether a newer version is out; the version from Google Play is updated by Play.

## Permissions

Every permission NN Agent asks for, why, and what happens without it:

- **Accessibility service:** described above. NN Agent's core: without it, agents can't see or use the screen.
- **Microphone (`RECORD_AUDIO`):** only when you tap the mic or start live voice. Without it, you type instead.
- **Audio settings (`MODIFY_AUDIO_SETTINGS`):** voice input and live voice record through Android's WebView (WebRTC), which needs it to set up the microphone. NN Agent doesn't change your volume or sound settings.
- **Notifications (`POST_NOTIFICATIONS`, `POST_PROMOTED_NOTIFICATIONS`):** task status, approvals while the phone is locked, and on Android 16 the "NN Agent is using your phone" chip in the status bar. Without them, you only see these in the app and the bubble.
- **Vibration (`VIBRATE`):** a short buzz when an approval card or a reply arrives.
- **Notification access:** only for notification rules, described above. Without it, rules don't run.
- **Keep awake (`WAKE_LOCK`):** while a notification rule's or a scheduled task's run works, so it can finish with the screen off.
- **Battery optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`):** lets the setup screen ask Android not to stop NN Agent in the background. If you say no, Android may stop it, and rules or scheduled tasks may run late or not at all.
- **Exact alarms and start at boot (`SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`):** scheduled tasks run on time, and are set again after a restart. Without exact alarms they run a few minutes late.
- **Install apps (`REQUEST_INSTALL_PACKAGES`, GitHub version only):** installing a NN Agent update when you tap Update, and an app an agent built when you ask it to. Android's installer asks you each time. Without it, you install those yourself.
- **Internet and network state (`INTERNET`, `ACCESS_NETWORK_STATE`):** to reach the AI service you chose and the downloads above.
- **Seeing installed apps (`<queries>` for launchable apps):** so agents can open apps by name. NN Agent doesn't send the list anywhere except to the AI you chose, when an agent asks for it.

## Children

NN Agent is not directed at children under 13.

## Changes and contact

Changes to this policy are published in this file, with their history in the repository. Questions: open an issue at https://github.com/nhatnamdev662/nn-android/issues.
