# Contributing to Marksheet

Thank you for your interest in contributing to **Marksheet**! We aim to keep the app fast, accurate, offline-first, and free of bloat.

## Development Guidelines

1. **Keep It Focused**: Marksheet is intentionally a clean, fast, offline exam score calculator with per-question records and session history. Avoid adding unnecessary network dependencies, analytics, or permissions.
2. **Exact Arithmetic Only**: All mark and score calculations must use `java.math.BigDecimal` via `ScoreMath` — never `Float` or `Double`.
3. **Database Atomicity**: Multi-step Room mutations in `MarksheetRepository` must be wrapped inside `database.withTransaction { ... }` and guarded by `operationMutex`.
4. **Theme Consistency**:
   - Light mode uses warm off-white alabaster paper tones (avoid harsh `#FFFFFF`).
   - Dark mode uses strictly neutral graphite grey / AMOLED black (`R == G == B`, no blue tint).
   - Reference colors through `MaterialTheme.colorScheme` and `LocalMarksheetColors.current` instead of hardcoding hex colors in Composables.
5. **Accessibility & Responsiveness**:
   - Keep touch targets at least `48.dp`.
   - Provide `contentDescription` on interactive icons and `Modifier.testTag(...)` on primary controls.
   - Verify both compact portrait phones and wide/landscape layouts.

## Workflow

1. Fork the repository and create a feature branch (`git checkout -b feat/your-feature`).
2. Make your changes and add/update unit tests in `app/src/test/java/com/example/`.
3. Verify your build, unit tests, and lint pass locally:
   ```bash
   gradle :app:assembleDebug
   gradle :app:testDebugUnitTest --tests "com.example.ScoreMathTest"
   gradle :app:lintDebug
   ```
4. Open a pull request describing what changed, why, and how it was tested.

## Commit Style

We recommend concise, descriptive commit messages (e.g., `fix: prevent overflow on 4-decimal score display`, `feat: add CSV export for session history`).
