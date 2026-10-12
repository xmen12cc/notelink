# NoteLink

A simple, good-looking notes app for your Android phone with a companion
browser UI on your PC. Both sides sync through **your own private GitHub
repo**, so notes follow you between devices — at home on Wi-Fi or out on
mobile data — with full version history for free.

```
Phone (NoteLink app)  ──┐
                        ├──  private GitHub repo (notelink-notes)  ← source of truth
PC (browser hub)      ──┘        one markdown file per note
```

- **Phone**: Kotlin + Jetpack Compose, Material You colors, works offline,
  syncs in the background every 15 minutes, on open, on pull-to-refresh, and
  ~4 s after each edit.
- **PC**: `hub/start_hub.bat` → a polished notes page at
  [http://127.0.0.1:8765](http://127.0.0.1:8765) that commits and pushes every
  edit immediately and pulls fresh notes every 30 seconds.

## Repo layout

| Path        | What it is                                              |
|-------------|---------------------------------------------------------|
| `android/`  | The Android app (Gradle project)                        |
| `hub/`      | The PC-side web UI + local server (Python + Flask)      |
| `.github/`  | CI that builds `NoteLink-debug.apk` into a "latest" release |

Your notes live in a **separate** private repo (default name
`notelink-notes`), so the phone token only ever touches notes — not this code.

## One-time setup

### 1A. Browser sign-in (recommended)

The app signs in through your browser using GitHub's device flow — no tokens
to copy. GitHub requires an *OAuth App* to allow this; you create it once
(about a minute):

1. GitHub → **Settings → Developer settings → OAuth Apps → New OAuth App**
   (or open https://github.com/settings/applications/new )
2. **Application name**: `NoteLink` · **Homepage URL**: anything
   (e.g. `https://github.com/xmen12cc`) · **Callback URL**: `http://localhost`
3. Register, then tick **Enable Device Flow** and copy the **Client ID**
   (no client secret needed).

Get the Client ID into the app one of two ways:

- **In the app**: Settings → paste it under *OAuth App Client ID* → **Save & sign in**, or
- **At build time**: add `NOTELINK_OAUTH_CLIENT_ID=Iv1.xxxx` to
  `android/gradle.properties` (or `~/.gradle/gradle.properties`) before building.

Then tap **Sign in with GitHub**: the browser opens `github.com/login/device`,
you enter the one-time code, approve, and the app finishes on its own — it
fetches your username, creates the private **notelink-notes** repo if it
doesn't exist yet, and starts syncing.

### 1B. Manual token (alternative)

Prefer a token? Create a private repo named `notelink-notes`, create a
**fine-grained personal access token** scoped to that repo with
**Contents: Read and write**, then in Settings open *Advanced: connect with a
personal access token*, fill in username / repo / token, and **Save & test**.

### 2. PC hub

```bat
cd hub
git clone https://github.com/<you>/notelink-notes notes-repo
start_hub.bat
```

`start_hub.bat` installs Flask if needed and opens the browser. Push access
uses your existing git credentials (run `gh auth setup-git` once if pushes
are rejected).

## How notes are stored

One markdown file per note, `notes/<id>.md`:

```markdown
---
id: 3f9c2e…        (file name is the authority)
title: Grocery list
pinned: false
color: default     (default | yellow | green | blue | pink | gray)
updated: 2026-10-11T14:33:05.123Z
deleted: false     (tombstone — both ends hide it; kept for safe deletes)
---
Body text…
```

Because notes are plain files, you can also read/edit them with any editor;
just let the app or hub sync afterwards.

## How sync works

- **Offline-first on both ends.** Edits always land in the local store first
  (Room on the phone, a git commit in the hub's clone on the PC). If there's
  no internet, nothing is lost — changes queue up and upload automatically at
  the next successful connection (the phone retries on app open, pull-to-refresh
  and a 15-minute background check; the hub pushes its backlog every 30 s).
- **Autosave everywhere.** The phone editor saves ~1 s after you stop typing
  plus a flush every few seconds and on leaving the editor. The browser editor
  saves ~1 s after typing, flushes every few seconds, and uses a `sendBeacon`
  when the tab closes. You never lose more than a few seconds of typing.
- Each side tracks what it last pushed. On sync: pull remote files that
  changed, then push local edits.
- Conflicts are resolved **per note, last writer wins** on the `updated`
  timestamp. If you edit the same note on two devices while offline, the
  newer edit survives — fine for a personal notes app, just be aware.
- Deleting creates a tombstone so the delete travels safely; the hub never
  hard-deletes behind the phone's back.
- The PC hub resolves a push race by keeping its own just-saved version
  (`git pull --rebase -X ours`), so what you typed in the browser never
  vanishes.

## Building the app locally

```bat
cd android
gradlew assembleDebug
```

APK: `android/app/build/outputs/apk/debug/app-debug.apk` (signed with the
repo-committed debug keystore, same as CI builds, so updates install over
each other). Every push to `main` rebuilds and republishes the rolling
**latest** release automatically.

## Troubleshooting

- **Sign-in code expired** — codes last ~15 minutes; tap Sign in again for a new one.
- **"Could not create the notes repo"** — the OAuth app wasn't approved yet, or your
  account/org blocks repo creation; create `notelink-notes` manually (private) and tap Reconnect.
- **"Token rejected (401)"** — the token is wrong or expired; generate a new one.
- **"Repo not found (404)"** — username/repo typo, or the token isn't scoped to that repo.
- **Hub push fails** — run `gh auth setup-git`, then restart the hub.
- **Notes not appearing on the hub** — the hub pulls every 30 s; click the
  status chip bottom-right to force a sync.
