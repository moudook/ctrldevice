<!-- INSTRUCTION: NO EMOJIS ALLOWED IN THIS CODEBASE. -->
# CtrlDevice: Enhanced Master Project Structure

## Philosophy
This project follows **Clean Architecture** (MVVM) with **Multi-Module** principles (simulated within a monorepo for simplicity).

*   **Presentation Layer:** Jetpack Compose (UI) + ViewModels.
*   **Domain Layer:** Pure Kotlin UseCases (Business Logic).
*   **Data Layer:** Repositories, Room DB, Retrofit (Network).
*   **DI:** Hilt (Dependency Injection).

## Directory Manifest

```
android/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── prompts/             # System prompts (e.g., "planner_prompt.txt")
│   │   ├── java/com/ctrldevice/
│   │   │   ├── app/                 # Application class (Hilt setup)
│   │   │   ├── di/                  # Hilt Modules (Network, DB, Bindings)
│   │   │   ├── core/                # Shared Utilities
│   │   │   │   ├── extensions/
│   │   │   │   └── result/          # Result<T> wrapper
│   │   │   ├── domain/              # PURE KOTLIN (No Android dependencies)
│   │   │   │   ├── models/          # Data Classes (Task, Step)
│   │   │   │   ├── repository/      # Interfaces
│   │   │   │   └── usecases/        # "ExecuteTaskUseCase", "ParseScreenUseCase"
│   │   │   ├── data/                # Implementation
│   │   │   │   ├── local/           # Room DB, DataStore
│   │   │   │   ├── remote/          # Retrofit Service
│   │   │   │   └── repository/      # Repository Implementations
│   │   │   ├── features/            # Feature-based organization
│   │   │   │   ├── agent_engine/    # The "Brain" (State Machine)
│   │   │   │   ├── accessibility/   # The "Hands" (Service)
│   │   │   │   ├── overlay/         # The "Face" (Floating Window)
│   │   │   │   ├── chat/            # The "Interface" (Main Activity UI)
│   │   │   │   └── settings/        # Config UI
│   │   │   └── navigation/          # Navigation Graph
│   │   └── res/                     # Android Resources
│   ├── build.gradle.kts
│   └── libs.versions.toml           # Version Catalog (Best Practice)
├── docs/
│   ├── architecture/
│   ├── guides/
│   └── manuals/                     # User & Dev Manuals
├── templates/
│   ├── architecture/                # UseCase, Repository Templates
│   ├── ui/                          # Compose Templates
│   └── di/                          # Hilt Module Templates
└── scripts/                         # CI/CD & Setup
```
