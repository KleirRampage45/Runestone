# Taste (Continuously Learned by [CommandCode][cmd])

[cmd]: https://commandcode.ai/

# workflow
- Do NOT commit changes unless explicitly asked. Make edits but leave them uncommitted. Confidence: 0.85
- Do NOT modify files outside those specified in the task. Only edit the explicitly listed files. Confidence: 0.75
- After making code changes, verify the build with: `./gradlew :app:compileDebugKotlin`. Confidence: 0.85

# code-style
- Do NOT add emojis to the codebase. Use SVG icons instead. Confidence: 0.80
- All UI views are programmatic Kotlin — no XML layouts. Keep this pattern for all UI work. Confidence: 0.70

