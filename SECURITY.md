# Security Policy

## Threat Model & Privacy Architecture

**Marksheet** is designed with a minimal attack surface:
- **Minimal Permissions**: Only the standard install-time `VIBRATE` permission is declared (for optional tactile haptic feedback). No `INTERNET`, storage, camera, location, or contacts permissions are declared or requested.
- **100% Local Storage**: Session records are stored locally in Room SQLite (`marksheet_db`) and configuration preferences in Jetpack DataStore (`marksheet_settings`).
- **No Third-Party Telemetry or Ads**: No tracking SDKs, crash reporters, or remote endpoints are included.
- **No Committed Secrets**: Production signing keys are injected strictly via environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).

## Supported Versions

| Version | Supported |
| :--- | :--- |
| `1.0.x` | Yes |

## Reporting a Vulnerability

If you discover a security or data-integrity issue, please **do not** open a public issue with exploit details. Instead, use GitHub's **Private Vulnerability Reporting** (Security Advisories tab) or contact the repository maintainer directly with:
- A clear description of the issue and affected component.
- Steps to reproduce.
- Android version and device/emulator details (if applicable).

We will acknowledge reports promptly and coordinate a fix before public disclosure.
