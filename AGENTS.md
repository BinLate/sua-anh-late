<!-- gemini-and-chatgpt:begin -->
# Automatic Gemini + ChatGPT PR Review Gate
For every implementation, bug fix, refactor, or feature coding task, automatically apply `.agents/skills/gemini-and-chatgpt/SKILL.md`. Do not wait for the user to mention the skill.

For STANDARD/COMPLEX tasks, ensure project context is fresh using `scripts/project_context_scan.py`.

For non-trivial work, consult the durable architectural memory at `PROJECT_MAP.md` (Project Intelligence) via the relevant lazy slices before broad repository exploration; verify important claims against current source code. Source code remains the ultimate implementation truth.

Mandatory order after implementation: verify locally -> ensure GitHub authentication and `origin` remote -> commit/push a dedicated branch -> create or update a GitHub Pull Request -> obtain the exact full 40-character PR HEAD SHA -> only then run `scripts/review_round.py` for independent review. Never open ChatGPT for the code-review phase before a GitHub PR exists. If GitHub authentication or the remote is missing, stop and tell the user to rerun `gemini-and-chatgpt\INSTALL-ANTIGRAVITY.bat`.

Review submissions MUST be performed exclusively by launching the Antigravity `browser_subagent` tool: open https://chatgpt.com/, start a fresh ChatGPT conversation EVERY round (never reuse or reopen any previous conversation URL), then VERIFY the address bar does not contain "/c/" and the conversation area is empty (no previous messages) â€” click the sidebar "New chat" button until both checks pass â€” click into the composer, TYPE the concise review prompt with normal simulated keyboard input (Shift+Enter creates newlines, plain Enter only submits at the very end), wait until generation finishes, and return the complete response text plus the conversation URL. Clipboard paste, CDP injection, DOM value assignment, and PowerShell direct-fill scripts are forbidden.

Strict Local-Worktree Rule: When ChatGPT returns findings, evaluate and fix them locally in the working repository. Never open browser tabs to GitHub Web pages to read source files that exist locally. Resolve all same-repository GitHub URLs to local workspace paths.

After fixing findings locally, verify again, push the new HEAD, and immediately run `scripts/review_round.py --prepare` again to start the NEXT round in a NEW ChatGPT conversation. Continue automatically until ChatGPT replies exactly PASS. Automatic merge remains OFF unless the user explicitly enables it.

Automatic Shutdown on Task Completion (default, no confirmation): When a coding task is fully finished â€” either the ChatGPT review loop ends with an exact `PASS` reply for the current HEAD SHA (task enters RELEASE_GATE), or the task completes successfully without a ChatGPT review round â€” immediately schedule a Windows shutdown by running this command via cmd: `shutdown /s /t 60`. Do NOT ask the user for confirmation. Tell the user the machine will shut down in 60 seconds and that they can cancel with `shutdown /a`.
<!-- gemini-and-chatgpt:end -->


