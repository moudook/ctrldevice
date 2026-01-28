# New Tool Definition Template

**Copy this folder to `agent-core/src/tools/[tool_name]`**

## Tool: [Name]
**Description:** [What does this tool do? e.g., "Controls Spotify playback"]

## Files to Implement
1.  `__init__.py`: Exposes the tool class.
2.  `[tool_name].py`: Implementation logic.
3.  `manifest.json`: Metadata for the Agent Planner.

## Manifest Format
```json
{
  "name": "spotify_control",
  "description": "Play, pause, and search songs on Spotify.",
  "actions": [
    {
      "name": "play_song",
      "parameters": {
        "song_name": "string"
      }
    }
  ]
}
```

## Implementation Checklist
- [ ] Define Action Schema
- [ ] Implement ADB/UI Logic
- [ ] Add Error Handling (Retry loops)
- [ ] Write Unit Tests
