# MEMORY

## Project Overview
**CtrlDevice: Agentic Android App**
CtrlDevice is an agentic Android application designed to provide advanced control and automation capabilities on Android devices using agentic AI.

## Architecture Summary
- **MainActivity**: The main entry point and UI for the application.
- **ControllerService**: An accessibility service that handles device interactions and monitoring.
- **SystemAgent**: The core intelligence component that processes requests and coordinates actions.

## Build Instructions
- **JDK**: Use JDK 21.
- **Path**: `C:\Program Files\Android\Android Studio\jbr`
- **Gradle**: Use the provided Gradle wrapper or version 8.5.

## Current Status
- Voice control UI added.
- Regex parsing implemented for command processing.
- Base agent engine coordination logic (GraphExecutor, ResourceManager, StateManager, TaskGraph) is being developed.
