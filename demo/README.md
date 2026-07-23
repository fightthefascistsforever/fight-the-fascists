# Product demo recording

Automated Playwright walkthrough of all major features.

## Prerequisites

- Backend on `:8080`, frontend on `:5173`
- `pip install pyotp` for steward TOTP generation

## Record

```bash
cd demo
npm install
npx playwright install chromium
node record-demo.mjs
```

Output: `/opt/cursor/artifacts/fight-the-fascists-demo.mp4`

## Sections covered

1. Chapter picker (multi-chapter)
2. Need board + heat advisory
3. Post a need (F1, PoW)
4. Claim with handoff code (F2)
5. Volunteer shifts (F4)
6. Announcements (F7)
7. First aid directory (F5)
8. Bulk give pledges (F8)
9. Transparency / stats (P2)
10. Hindi ↔ English toggle
11. Steward console (F6)
12. Lite mode (zero-JS)
13. Final live board
