# RivetIT Mobile — Android App

[![Latest release](https://img.shields.io/github/v/release/TheTractorHacker/rivetit-mobile?include_prereleases&label=release)](https://github.com/TheTractorHacker/rivetit-mobile/releases/latest)
[![Build APK](https://github.com/TheTractorHacker/rivetit-mobile/actions/workflows/build.yml/badge.svg)](https://github.com/TheTractorHacker/rivetit-mobile/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android-3ddc84)

> **By [TractorHacker](https://github.com/TheTractorHacker)** — the native Android companion for
> [RivetIT](https://github.com/TheTractorHacker/ITFlow-Internal-IT), an open-source internal IT
> operations platform built on [ITFlow](https://github.com/itflow-org/itflow). RivetIT is not the
> official ITFlow app and is not affiliated with or endorsed by the upstream ITFlow project.

Built with Kotlin + Jetpack Compose + Material 3.

---

## Screenshots

<table>
<tr>
<td align="center" width="34%"><b>New Ticket</b></td>
<td align="center" width="33%"><b>Tickets</b></td>
<td align="center" width="33%"><b>Live Chat</b></td>
</tr>
<tr>
<td><img src="docs/screenshots/create_ticket.png" width="100%"></td>
<td><img src="docs/screenshots/tickets.png" width="100%"></td>
<td><img src="docs/screenshots/ticket_chat.png" width="100%"></td>
</tr>
</table>

---

## Features

**Service Desk**
- Dashboard — open ticket counts, recent activity, and system alerts at a glance
- Tickets — view, reply (live chat-style thread), change status, assign technicians, and add time charges
- Appointments — view and manage scheduled on-site and remote appointments
- Alerts — system and RMM alerts in one list

**Devices & Assets**
- Assets — browse client assets with full detail view; scan barcodes and QR codes to look up assets instantly

**People & Documentation**
- Clients — full client/organization list with contacts, locations, and contracts
- Credentials — view stored credentials (behind biometric unlock)
- Knowledge Base — browse KB articles, with inline images served through the server's signed media links
- Worksheets — view and fill out worksheet and outtake-sign responses from your phone

**Billing** (where the connected server has it enabled)
- Quotes, Invoices, and Expenses
- Projects and Contracts

**Reporting**
- A Reports hub covering service desk volume, technician utilization and performance, time summaries,
  CSAT, RMM health, unbilled tickets, income/profit-and-loss and expense summaries, and more — whichever
  reports the connected server exposes

**Everywhere else**
- Global Search — search across tickets, clients, and assets in one place
- Push Notifications — get notified on new tickets and assignments via Firebase
- Home screen widget — live open-ticket count
- Biometric unlock for the app and for viewing credentials
- Light/dark theme with selectable accent color

---

## Requirements

- Android Studio Hedgehog or newer
- Android SDK 35
- A **RivetIT** (or compatible ITFlow-based) server running **v2.4.12+**
- `google-services.json` from your Firebase project for push notifications

---

## Setup

1. Clone the repo and open in Android Studio
2. Add your `google-services.json` to `app/`
3. Run the app — enter your RivetIT server URL on first launch
4. Log in with your technician credentials

---

## Branches

| Branch | Purpose |
|--------|---------|
| `beta` | Active development — new features land here first |
| `release` | Stable builds only |

---

## Related

- Server: [RivetIT](https://github.com/TheTractorHacker/ITFlow-Internal-IT), also compatible with
  [TheTractorHacker/itflow](https://github.com/TheTractorHacker/itflow) (the MSP-oriented fork it
  descends from)
- Upstream ITFlow: [itflow-org/itflow](https://github.com/itflow-org/itflow)

## Attribution

This app and the RivetIT server it talks to both trace back to
[ITFlow](https://github.com/itflow-org/itflow) (GPL-3.0), by way of
[TheTractorHacker/itflow](https://github.com/TheTractorHacker/itflow), an MSP-focused fork. All
original credit for that lineage goes to the ITFlow contributors.
