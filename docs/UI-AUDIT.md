# Android UI audit — 2026-10-23 (build 1.30.1-beta, API 36 emulator, 1080×2400)

Method: every drawer destination, the five tabs and the detail screens were captured against the demo
server in light and dark mode (`docs/user-guide/screenshots/`).

## Fixed in this pass

| # | Problem | Fix |
|---|---|---|
| 1 | Self-signed-cert dialog tells users to type `61:7D:49` but the check required `617D49`, so **Trust & Connect** never enabled when following the instruction. | Input is compared with colons/spaces ignored (same 6 characters required). `ServerSetupScreen.kt` |
| 2 | Every non-root screen had a ~100 px dead gap above its title (status-bar inset applied by the outer Scaffold *and* the screen's own TopAppBar). | Outer Scaffold drops the top inset when it isn't showing the brand bar. `MainScreen.kt` |
| 3 | Notifications, Credentials, Quotes, Invoices, Expenses showed two headers (brand bar + their own back-arrow bar). | Removed from `ROOT_ROUTES`; Notifications gained a back arrow. |
| 4 | Quotes/Invoices/Expenses on a server without billing showed "Something went wrong / Not found". | 404 now shows "X aren't enabled on this server." |
| 5 | Notifications error state could show raw exception text. | Uses `userMessage()` like every other screen. |
| 6 | Dark mode on a light-themed phone: status-bar clock/battery invisible (dark on dark). | System bar style now follows the app's resolved theme. `MainActivity.kt` |
| 7 | Floating bottom nav sat under the gesture handle. | `navigationBarsPadding()`. `AppChrome.kt` |

Screenshot capture needs `FLAG_SECURE` off. Added build property `-PallowScreenshots=true` (default off; release
builds hard-code false) — normal builds keep screenshots blocked.

## Open — recommended next

1. **Branding:** splash, top bar, login and app icon still show the old "F." mark; accent preset is still named "FoleyIT". Should be the RivetIT mark.
2. **Drawer shows billing items regardless of server.** Proper fix is a server capabilities/modules field (e.g. in `/api/v1/me`) so the app hides Quotes/Invoices/Expenses (and Contracts/Projects where absent).
3. **Dashboard tiles** use arbitrary pastel colors (grey, red, green, purple) with no meaning; "Today's Appts" and "On-Site" are grey while "Due Today" is purple. Use color only for state (overdue/alerts = error, rest neutral). Also "7 tickets in My Open" reads oddly → "7 open tickets assigned to you".
4. **Ticket cards** are mid-grey on near-white with low contrast; an empty client shows a bare building icon (`QA-maestro-ticket` rows) → hide the icon when there is no client.
5. **Login / server setup** content is top-heavy with a large empty lower half; center vertically or add the last-used servers.
6. **Disabled "Trust & Connect"** looks identical to enabled-but-dim; add helper text ("Enter the last 6 characters").
7. **Client detail tab row** clips "Files…" with no fade/scroll hint.
8. **Appointments** empty state is generic; add "Schedule" action.
9. Screens loading from the network show a bare spinner for several seconds on first open; consider skeleton rows.

## Not covered
iOS: cannot be built or run on this Linux host, so it was reviewed by reading `rivetit-mobile-ios` only.
Fixes 1–7 above are Android-specific or already handled on iOS:

- Fix 1: iOS `ServerSetupInput.fingerprintSuffixMatches` already ignores colons/spaces and has unit tests.
- Fix 3: iOS uses a native `NavigationStack` with one bar per tab, so there is no double header.
- Fix 4: iOS has no Quotes/Invoices/Expenses screens; Projects already shows "aren't available on this server" on a 404.

Open items 1 and 3–9 are worth checking on iOS visually (CI uploads a first-launch screenshot) but were not verified.
