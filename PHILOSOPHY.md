# CtrlDevice Philosophy: The "Ghost in the Shell"

## Core Vision
CtrlDevice is not just an automation tool; it is an **embodied agent** living within the Android operating system. Unlike traditional automation apps (Tasker, MacroDroid) that rely on strict "If This Then That" triggers, CtrlDevice is designed to be **Agentic**—it understands goals, perceives the screen, and plans actions dynamically.

## The Architecture of Agency

### 1. The Body (ControllerService)
The `ControllerService` (Accessibility Service) acts as the physical interface.
- **Senses ("Proprioception")**: It reads the screen content, node hierarchy, and system events.
- **Actuators**: It performs clicks, scrolls, and gestures.
- **Reflexes**: It has a "spinal cord" safety layer that immediately pauses execution if the user touches the screen (`userInterrupts`), ensuring the user is always in control.

### 2. The Brain (AgentEngine)
The intelligence layer is decoupled from the body.
- **RuleBasedBrain / LLMBrain**: The decision-making unit. It receives "Sensory Inputs" (Screen Context) and "Memory" (History) to propose a "Thought" and a "Tool Call".
- **The Ralph Loop**: The execution cycle is resilient.
  - **Act**: Try an approach.
  - **Observe**: Verify if the world changed as expected.
  - **Reflect & Pivot**: If it failed, don't just error out. Try a different strategy (Scroll down, Wait, Go Back).

### 3. The Governor (Safety)
An AI agent with control over a device is powerful. Power requires containment.
- **The Governor**: A dedicated component that monitors the agent's actions.
- **Loop Detection**: Stops the agent if it gets stuck doing the same thing.
- **Emergency Stop**: Hard overrides available to the user.

## Design Principles

1.  **User Sovereignty**: The agent is a guest. The user's physical touch always overrides the agent's digital action.
2.  **Resilience over Perfection**: The agent should assume the UI is messy and dynamic. Blind clicks are forbidden; verification is mandatory.
3.  **Local First**: Where possible, processing happens on-device or via direct API, minimizing latency and privacy exposure.
4.  **Transparent Reasoning**: The user should always know *why* the agent is doing something. The UI exposes the "Brain's Thoughts" in real-time.
