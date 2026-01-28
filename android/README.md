# Android Client (The Body)

This is the Android application that runs on the target device. It provides:
1.  **Accessibility Service:** To read the screen and perform clicks/scrolls.
2.  **Overlay Service:** To show the agent's status.
3.  **Communication Layer:** To receive commands from the `agent-core`.

## Key Components
*   **AccessibilityService:** `src/main/java/com/ctrldevice/service/accessibility/`
    *   This is the critical component that allows the app to "see" and "touch".

## Building
Open this folder in Android Studio.
Run `./gradlew assembleDebug` to build the APK.
