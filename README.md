# RivetMSP

[![Latest release](https://img.shields.io/github/v/release/TheTractorHacker/rivetmsp-mobile?include_prereleases&label=release)](https://github.com/TheTractorHacker/rivetmsp-mobile/releases/latest)
[![Build APK](https://github.com/TheTractorHacker/rivetmsp-mobile/actions/workflows/build.yml/badge.svg)](https://github.com/TheTractorHacker/rivetmsp-mobile/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android-3ddc84)

> **By [TractorHacker](https://github.com/TheTractorHacker)** — the native Android companion for [RivetMSP](https://github.com/TheTractorHacker/RivetMSP).
> This is **not** the official ITFlow app and is not affiliated with or endorsed by ITFlow LLC.

Built with Kotlin + Jetpack Compose + Material 3.

---

## Screenshots

Captured on a Pixel 7 (Android 36) against a demo server with fictional data.

<table>
<tr><td align="center" width="25%"><b>Sign in</b></td><td align="center" width="25%"><b>Dashboard</b></td><td align="center" width="25%"><b>Tickets</b></td><td align="center" width="25%"><b>Ticket detail</b></td></tr>
<tr><td><img src="docs/screenshots/01_login.png" width="100%" alt="Sign in"></td><td><img src="docs/screenshots/02_dashboard.png" width="100%" alt="Dashboard"></td><td><img src="docs/screenshots/03_tickets.png" width="100%" alt="Tickets"></td><td><img src="docs/screenshots/04_ticket_detail.png" width="100%" alt="Ticket detail"></td></tr>
</table>

<table>
<tr><td align="center" width="25%"><b>Clients</b></td><td align="center" width="25%"><b>Assets</b></td><td align="center" width="25%"><b>New ticket</b></td></tr>
<tr><td><img src="docs/screenshots/05_clients.png" width="100%" alt="Clients"></td><td><img src="docs/screenshots/06_assets.png" width="100%" alt="Assets"></td><td><img src="docs/screenshots/07_create_ticket.png" width="100%" alt="New ticket"></td></tr>
</table>


---

## Features

- **Dashboard** — open ticket counts, recent activity, and system alerts at a glance
- **Tickets** — view, reply, change status, assign technicians, and add time charges
- **Assets** — browse client assets with full detail view; scan barcodes and QR codes to look up assets instantly
- **Clients** — full client list with contacts, locations, credentials, and contracts
- **Worksheets** — view and fill out worksheet responses from your phone
- **Global Search** — search across tickets, clients, and assets in one place
- **Push Notifications** — get notified on new tickets and assignments via Firebase
- **Appointments** — view and manage scheduled on-site and remote appointments

---

## Requirements

- Android Studio Hedgehog or newer
- Android SDK 35
- **RivetMSP** server running **v2.4.12+** ([TheTractorHacker/RivetMSP](https://github.com/TheTractorHacker/RivetMSP))
- `google-services.json` from your Firebase project for push notifications

---

## Setup

1. Clone the repo and open in Android Studio
2. Add your `google-services.json` to `app/`
3. Run the app — enter your ITFlow server URL on first launch
4. Log in with your ITFlow technician credentials

---

## Branches

| Branch | Purpose |
|--------|---------|
| `beta` | Active development — new features land here first |
| `release` | Stable builds only |

---

## Related

- Web app: [TheTractorHacker/RivetMSP](https://github.com/TheTractorHacker/RivetMSP)
- Upstream ITFlow: [itflow-org/itflow](https://github.com/itflow-org/itflow)
