# Connecting to LLMs

The `AgentEngine` is agnostic to the LLM provider. It uses an `LlmClient` interface.

## Option 1: Local LLM (USB/Wi-Fi)
**Recommended for Privacy & Cost.**
*   **Host:** Run a server (e.g., `Ollama`, `LocalAI`, or `LM Studio`) on your laptop.
*   **Connect:**
    1.  Connect phone via USB.
    2.  Run `adb reverse tcp:8080 tcp:8080` (Maps phone's localhost:8080 to laptop's localhost:8080).
    3.  App connects to `http://localhost:8080/v1/chat/completions`.

## Option 2: Hugging Face Inference API
**Recommended for Power without a strong Laptop.**
*   **API Key:** Store in `local.properties` (NEVER commit to Git).
*   **Endpoint:** `https://api-inference.huggingface.co/models/[MODEL_NAME]`
*   **Model:** `meta-llama/Llama-2-70b-chat-hf` or `mistralai/Mixtral-8x7B-Instruct-v0.1`.

## Configuration
The app should have a "Settings" screen to toggle between these modes and enter Base URLs/API Keys.
