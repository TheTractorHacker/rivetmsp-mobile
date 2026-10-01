# RivetMSP company branding

An MSP's uploaded logo replaces the product logo in company-facing app chrome. The approved RivetMSP artwork is only the unconfigured fallback. Authentication, package IDs, Firebase configuration, routes, API contracts, signing identity and stored preference keys remain unchanged.

The provider loads `GET /api/v1/branding` on server selection and foreground resume. The response contains only `name` and `logo_path`, matching the public web login. Images must resolve to the same server origin under `/uploads/settings/`; the unauthenticated branding client follows no redirects and uses the existing certificate trust policy. Downloads and decoded dimensions are bounded.

Branding is cached separately for each server. Switching servers resets visible identity immediately. The first frame waits for the configured server to load instead of flashing product artwork. A configured logo that cannot be loaded falls back to the company name. If a server cannot supply branding and no cache exists, its hostname is used rather than assuming the RivetMSP logo should override it. Removing a company logo clears its cached image at the next successful refresh.

Login, main app chrome and the biometric lock screen share `CompanyLogo`. The Android launcher and operating-system splash use bundled approved RivetMSP artwork: these resources exist before a server has been selected and are not runtime company-logo slots. User-facing app labels, device labels, notifications, widget heading and About text use RivetMSP. The upstream affiliation notice remains attribution.

Source is based on `origin/beta` commit `0d9da2a` in the isolated `rivetmsp-branding` branch. This patch implements branding and company-logo precedence, not the wider ticket UX migration in the audit.

Build and tests:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The debug APK retains the existing beta application ID and signing key. It is not a production-signed release. Device-level visual and interaction validation is still required; no device/emulator was attached during this build.
