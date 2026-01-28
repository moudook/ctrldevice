# Contributing to CtrlDevice

## Codebase Standards

### Directory Structure
*   **Android Code:** Must reside in `android/`. Follow Clean Architecture (Data -> Domain -> UI).
*   **Agent Logic:** Must reside in `agent-core/`.
*   **New Tools:** When adding support for a new app (e.g., Spotify), create a new directory in `agent-core/src/tools/` using the `templates/tool_template`.

### File Naming Conventions
*   **Kotlin:** PascalCase (e.g., `AccessibilityService.kt`, `ChatViewModel.kt`).
*   **Python:** snake_case (e.g., `agent_planner.py`, `tool_manager.py`).
*   **Layouts:** snake_case (e.g., `activity_main.xml`).

### Adding New Features
1.  **Plan:** Create a design doc in `docs/architecture/` if the feature is complex.
2.  **Template:** Use the templates provided in `templates/` for new tools or skills.
3.  **Test:** Add unit tests in `tests/` directories.

### Security
*   **Never** commit API keys. Use `.env` files.
*   **Privacy:** Ensure no user PII is logged to console or disk without encryption.
