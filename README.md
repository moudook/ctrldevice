# CtrlDevice: AI-Powered End-to-End Android Device Control

## Overview
CtrlDevice is an advanced AI agent system designed to autonomously control Android devices to perform complex, multi-step tasks. By leveraging large language models (LLMs) and Android Accessibility Services, CtrlDevice understands natural language user requests—from "forward my latest internship email" to "find and play a study playlist"—and executes them on the device just like a human user would.

## Project Structure

The codebase is organized as a monorepo containing both the Android client and the Python-based Agent Core.

```
ctrldevice/
├── android/                 # Native Android Application (The "Body")
│   ├── app/src/main/java/   # Kotlin source code
│   │   ├── accessibility/   # Accessibility Service logic (UI interaction)
│   │   ├── services/        # Background services (Network, Overlay)
│   │   └── ui/              # User Interface (Chat, Settings)
│   └── ...
├── agent-core/              # Python Agent Backend (The "Brain")
│   ├── src/agent/           # Core Logic (Planner, Memory, Action Engine)
│   ├── src/tools/           # Skill definitions (Gmail, Chrome, YouTube)
│   ├── src/llm/             # LLM Interface & Provider wrappers
│   └── ...
├── docs/                    # Documentation & Research
│   ├── architecture/        # System design diagrams & docs
│   ├── research/            # Competitor analysis & State-of-the-Art
│   └── security/            # Security protocols & Privacy policies
├── templates/               # Standardized templates for new files
└── scripts/                 # Setup & Deployment automation
```

## Key Features
*   **Natural Language Understanding:** Interprets vague or complex user intents.
*   **Multi-Step Planning:** Breaks down goals into executable sub-tasks (e.g., Open App -> Search -> Click).
*   **Resilience:** Self-correcting retry logic (e.g., "Email not found, scrolling down...").
*   **Context Awareness:** Understands on-screen content and user history.

## Getting Started

### Prerequisites
*   Android Studio Koala or later.
*   Python 3.10+.
*   ADB (Android Debug Bridge).

### Setup
Please refer to [docs/guides/setup.md](docs/guides/setup.md) for detailed installation instructions.

## Contributing
We follow strict architectural guidelines. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a PR.
