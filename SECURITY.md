# Security Policy

## Supported Versions

The following versions of PackaTrack are currently supported with security updates:

| Version | Supported          |
| ------- | ------------------ |
| 2.0.x   | :white_check_mark: |
| < 2.0   | :x:                |

## Reporting a Vulnerability

PackaTrack takes the security of your parcel data and encryption keys seriously. We appreciate the efforts of security researchers in keeping our users safe.

**Please do not report security vulnerabilities through public GitHub issues.**

If you believe you have found a security vulnerability, please report it through the **GitHub Security Advisory** feature on the repository's "Security" tab. Alternatively, you can contact the project maintainers directly.

### Our Commitment

If you report a vulnerability, we will:
- Acknowledge receipt of your report within 48 hours.
- Provide an estimated timeline for a fix.
- Notify you once the vulnerability has been resolved.

## Security Architecture

For transparency, PackaTrack implements the following security measures:
- **At-Rest Encryption**: User databases are encrypted using **SQLCipher** (AES-256).
- **Key Management**: Encryption keys and API credentials (like Australia Post keys) are stored in the **Android Keystore System**, ensuring they cannot be easily extracted from the device.
- **Privacy**: Tracking data is fetched directly from carriers to your device. No intermediate servers are used to store your tracking history.
