# Changelog

All notable changes to **Marksheet** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-11-22

### Added
- **Exam Score Calculator** with three thumb-zone operational buttons: **Add Mark**, **Negative**, and **Skip Question**.
- **Exact `BigDecimal` Math Engine (`ScoreMath`)** preventing floating-point drift across fractional marking schemes (default `+1`, `-0.25`, `100` questions).
- **Per-Question Mark Recording** backed by Room SQLite with ACID transactions, monotonic timestamps, and composite `(sessionId, questionNumber)` primary keys.
- **Live Question Tape & Full Question Sheet Grid** (`Q1`–`QN`) with color legend, gap-filling support, inline mark editing, and bounded reversible **Undo**.
- **Session History & Session Detail Audit** with aggregate statistics, status filters (`All`, `Correct`, `Wrong`, `Skipped`), full-sheet inspection, renaming, deletion, and calculator reopen.
- **Persistent Configuration (DataStore)** for positive mark, negative deduction, total questions (`1`–`1000`), quick exam presets (Standard, JEE/NEET, NEET Full, UPSC Prelims, SSC/Banking, No Negative), tactile haptics, and safe mid-session scheme updates.
- **Warm Paper Light, Neutral Graphite Dark & Pure AMOLED Black Themes** with zero cold-start theme flash, 1-tap top-bar toggle, and bundled `Plus Jakarta Sans` + tabular `JetBrains Mono` typography.
- **Adaptive Layout & Publishing Readiness**: Edge-to-edge insets, phone `NavigationBar` and tablet `NavigationRail` + two-pane calculator, custom adaptive launcher icon, zero permissions, Android Auto Backup rules, and R8/ProGuard keep rules.
