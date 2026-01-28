# Data Leakage Prevention & Security

## 1. The "Clean Room" Principle
The agent operates on the user's most personal device. We must assume **Zero Trust** for the LLM provider, especially if using a cloud API.

## 2. Input Sanitization (Pre-LLM)

Before any screen data is sent to the LLM (Prompt Engineering), it acts through a **Sanitizer Layer**.

### Text Redaction
*   **Regex Filters:**
    *   **Email:** `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}` -> `<EMAIL>`
    *   **Phone:** `(\+\d{1,2}\s)?\(?\d{3}\)?[\s.-]?\d{3}[\s.-]?\d{4}` -> `<PHONE>`
    *   **Credit Card:** `\d{4}[ -]?\d{4}[ -]?\d{4}[ -]?\d{4}` -> `<CARD_NUM>`
    *   **IPv4/IPv6 Addresses**

### Visual Redaction (Screenshots)
If the agent uses Vision (multimodal):
1.  **Bounding Box Identification:** Use local OCR (ML Kit) to find coordinates of sensitive text.
2.  **Blackout:** Draw black rectangles over these coordinates on the Bitmap *before* encoding it to base64.
3.  **No PII in Vision:** Never send a raw screenshot of a banking app or password field.

## 3. Output Restrictions (Post-LLM)

The LLM might generate text that includes sensitive data it "remembered" or hallucinated.
*   **Clipboard Guard:** If the agent performs a `COPY` action, analyze the clipboard content. If it looks like a password, DO NOT save it to the agent's long-term memory logs.

## 4. Local-Only Mode
For highly sensitive apps (Banking, Health), the agent should switch to **"Local Only"** mode.
*   **Trigger:** App Package Name matches list (e.g., `com.chase.sig.android`).
*   **Behavior:**
    *   Disconnects Cloud LLM.
    *   Uses smaller, on-device quantized model (e.g., Gemma 2B via MediaPipe).
    *   If on-device model is too weak, PAUSE and ask user to complete the sensitive step manually.

## 5. Storage Security
*   **Vector DB:** Do not store raw email bodies or chat logs. Store *summaries* or *embeddings* of non-sensitive intent.
*   **Room DB:** Encrypt the database using `SQLCipher` if possible.
