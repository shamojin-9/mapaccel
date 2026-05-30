# Changelog

## 0.1.0 - 2026-05-29

- Added token-protected LAN remote worker gateway for browser-based compute assist from other PCs, phones, and tablets.
- Added remote worker browser page with polling preview tasks and matching JavaScript preview computation.
- Added remote worker queue, token, bind address, port, batch size, and queue limit configuration.
- Added a public MapAccel API for external mods to request projected movement, area, priority, and forced-priority chunk preloading.
- Added bounded external load queue handling with requester rate limits, pending limits, TTL cleanup, priority ordering, and range caps.
- Added multiplayer cooperative security hardening for hash validation and preview-assist packets.
- Added sender/request matching, duplicate reply rejection, challenge TTL cleanup, and pending challenge limits.
- Added client metrics clamping and inbound packet rate limiting.
- Added persistent trust and mismatch storage for multiplayer validation.
- Added MapAccel status/config command output for API queue visibility.
- Adjusted client setup timing and MapAccel initialization safety for Forge runtime startup.
