# Dynamic Tool Selection & App Discovery

## The Concept
The Agent is not hardcoded to use specific apps. Instead, it **discovers** capabilities at runtime.
*   **User Request:** "Book a hotel near me."
*   **Decision Matrix:**
    1.  *Is there a Hotel App installed?* (e.g., Airbnb, Booking.com) -> **YES**: Use Native App (Better UX).
    2.  *Is it not installed?* -> **NO**: Fallback to Chrome (Universal capability).

## 1. App Capability Registry
The Orchestrator maintains a dynamic registry of "Tools" mapped to Android Intents.

| Category | Capability | Detected Packages (Examples) | Priority |
| :--- | :--- | :--- | :--- |
| **Maps** | `find_location` | `com.google.android.apps.maps` | 1 |
| **Travel** | `book_hotel` | `com.airbnb.android`, `com.booking` | 1 |
| **Browser** | `web_search` | `com.android.chrome`, `com.brave.browser` | 99 (Fallback) |

## 2. The Decision Logic (Pseudocode)

```kotlin
function selectTool(intent: String): Tool {
    // 1. Identify required capability
    // Intent: "Book Hotel" -> Capability: "book_hotel" OR "web_search"
    
    // 2. Check installed apps
    installedApps = PackageManager.getInstalledPackages()
    candidateApps = registry.filter { it.capability == intent.capability && it.package in installedApps }
    
    // 3. Select Best Tool
    if candidateApps.isNotEmpty():
        return NativeAppTool(candidateApps.first()) // Use Airbnb
    else:
        return BrowserTool("Search for hotels near [Location]") // Use Chrome
}
```

## 3. Location Handling Strategy
The agent acts like a human. It doesn't need a hidden API to get location if it can't access GPS.
1.  **Primary:** Ask Android System Location API.
2.  **Fallback (Human-like):**
    *   Open **Google Maps**.
    *   Wait for the "Blue Dot" to center.
    *   Read the "My Location" text or search "Hotels near me" directly in the Maps search bar.
