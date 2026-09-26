# Accounts and authentication

All account code lives in `:shared` (`net.dyrox.shared.auth`, `.account`, `.vault`) so the in-game
alt manager can reuse it later.

## Account types

| Type | Works on | Stored secrets |
|---|---|---|
| **Microsoft** | Everything, including online-mode servers | Microsoft refresh token, Minecraft access token |
| **Offline** | Singleplayer and offline-mode servers only; clearly labelled "Offline" in the UI | None. The UUID is the vanilla offline UUID (`OfflinePlayer:<name>`), so inventories match vanilla offline servers |

## Microsoft sign-in chain

1. **Microsoft OAuth 2.0.** Tenant `consumers`, scope `XboxLive.signin offline_access`. Two flows:
   - **Browser:** authorization code with PKCE (S256), `state` check, and a loopback redirect to
     `http://localhost:<random port>` (`LoopbackRedirectReceiver`).
   - **Device code:** the user enters a code at `microsoft.com/link`. Polling handles
     `authorization_pending` and `slow_down`.
2. **Xbox Live.** `POST user.auth.xboxlive.com/user/authenticate` with `RpsTicket: d=<ms token>`.
3. **XSTS.** `POST xsts.auth.xboxlive.com/xsts/authorize` with relying party
   `rp://api.minecraftservices.com/`.
4. **Minecraft.** `POST api.minecraftservices.com/authentication/login_with_xbox` with
   `XBL3.0 x=<uhs>;<xsts>`, then `GET /minecraft/profile`. The XUID is read from the Minecraft
   token's JWT payload. It's informational only and the signature isn't verified.

### Error mapping

| Where | Response | Shown as |
|---|---|---|
| OAuth | `authorization_declined`, `expired_token`, `invalid_client` | `MicrosoftAuthException` with a readable message |
| OAuth refresh | `invalid_grant` | `InvalidRefreshTokenException`: the account becomes "Sign-in required" |
| XSTS | `XErr` 2148916233 / 35 / 36 / 37 / 38 / 27 / 29 | `XboxAuthException`: no Xbox profile, region, adult verification, child account, banned, parental controls |
| `login_with_xbox` | 403 "Invalid app registration" | `AppNotApprovedException` |
| `/minecraft/profile` | 404 | `NoMinecraftProfileException` (doesn't own Java Edition) |
| `/minecraft/profile` | 401 | `MinecraftTokenRejectedException`, which triggers a refresh |

### Azure app ID (required for Microsoft accounts)

Microsoft sign-in only works with an Azure app that Mojang has approved for Minecraft:

1. Azure portal → App registrations → New registration → **Personal Microsoft accounts only**.
2. Authentication → add platform **Mobile and desktop applications** with redirect URI `http://localhost`.
   Turn on **Allow public client flows** (needed for the device-code flow).
3. Submit the Application (client) ID with Mojang's review form: <https://aka.ms/mce-reviewappid>
   (background: <https://aka.ms/AppRegInfo>). Until approval, step 4 fails with HTTP 403.
4. Paste the ID in **Settings → Microsoft sign-in**. Alternatively set `DYROX_MS_CLIENT_ID`, which
   overrides the saved value.

## Token lifecycle

- **Minecraft access tokens** last 24 h. `AccountManager.session()` refreshes a token when it is within
  **10 minutes** of expiring, so a launch never starts with a dying token.
- **Refreshing** runs the whole chain again from the Microsoft refresh token. Microsoft rotates refresh
  tokens, and the new one is saved immediately.
- A **revoked refresh token** (password change, revoked consent) clears the credentials. The account
  then shows **Sign-in required** until the user signs in again.
- **Check** (per account, or **Check all**) asks Minecraft services whether the token works. It
  refreshes on a 401 and picks up name or skin changes.
- All refreshes are serialised, so two launches can't use the same rotating refresh token at once.

## Encrypted storage

`accounts.vault` holds `"DYRXVLT1"` + a 12-byte random nonce + AES-256-GCM(JSON). GCM detects any
modification. The 256-bit master key is random and protected by the OS:

| OS | Key store | File on disk |
|---|---|---|
| Windows | **DPAPI** (current Windows user, extra entropy) | `vault.key.dpapi` (DPAPI blob) |
| Linux | **Secret Service** via `secret-tool` (GNOME Keyring / KWallet) | `vault.key.secret-service` (marker only) |
| Linux without a keyring, or macOS | Owner-only key file (`rw-------`) | `vault.key` |

Rules:
- **Passwords are never handled.** Sign-in always happens on Microsoft's pages.
- **Tokens never appear in logs.** `toString()` of every credential-carrying class omits them, and the
  launch command redacts the access token.
- **An existing vault is never overwritten.** If its key is missing (vault copied to another PC or user),
  the launcher reports it. **Start fresh** moves the old file aside as `accounts.vault.unreadable-<time>`
  and doesn't delete it.
- **Access is locked across processes** (`accounts.vault.lock`), so parallel game instances and the
  launcher can't corrupt the vault.

## Import / export

Exports are JSON (`"format": "dyrox-accounts"`) with **type, name and UUID only**, never tokens.
Importing skips accounts that already exist. Imported Microsoft accounts show **Sign-in required**
until the user signs in on this PC.

## In-game alt manager (planned)

The Alt Manager button on the multiplayer screen and the in-game session swap need the client mod
(Phase 5) and the launcher↔game IPC channel (Phase 4). The game will ask the launcher for a fresh
`GameSession` over IPC, so only the launcher refreshes tokens. Launches without the launcher (dev
runs) will read the vault directly through the same `AccountManager`.
