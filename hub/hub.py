#!/usr/bin/env python3
"""NoteLink hub — local web UI for the notes repo, with two-way git sync.

The hub folder holds a clone of your private notes repo. Edits made in the
browser are committed and pushed immediately; a background thread pulls every
30 seconds so edits made on the phone show up automatically.
"""

import argparse
import subprocess
import sys
import threading
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path

from flask import Flask, jsonify, request, send_from_directory

STATIC_DIR = Path(__file__).resolve().parent / "static"


def now_ms():
    return int(time.time() * 1000)


def iso_from_ms(ms):
    return (
        datetime.fromtimestamp(ms / 1000, tz=timezone.utc)
        .isoformat(timespec="milliseconds")
        .replace("+00:00", "Z")
    )


def ms_from_iso(text):
    try:
        return int(datetime.fromisoformat(text.replace("Z", "+00:00")).timestamp() * 1000)
    except (ValueError, AttributeError):
        return 0


def parse_note(text, fallback_id):
    header, body = {}, text
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            for line in text[3:end].splitlines():
                if ": " in line:
                    key, value = line.split(": ", 1)
                    header[key.strip()] = value.strip()
            body = text[end + 4:]
            if body.startswith("\r\n"):
                body = body[2:]
            elif body.startswith("\n"):
                body = body[1:]
    return {
        "id": header.get("id") or fallback_id,
        "title": header.get("title", ""),
        "body": body,
        "pinned": header.get("pinned", "false").lower() == "true",
        "color": header.get("color", "default"),
        "updated": ms_from_iso(header.get("updated", "")),
        "deleted": header.get("deleted", "false").lower() == "true",
    }


def serialize_note(note):
    return (
        "---\n"
        f"id: {note['id']}\n"
        f"title: {note['title'].replace(chr(10), ' ')}\n"
        f"pinned: {'true' if note['pinned'] else 'false'}\n"
        f"color: {note['color']}\n"
        f"updated: {iso_from_ms(note['updated'])}\n"
        f"deleted: {'true' if note['deleted'] else 'false'}\n"
        "---\n"
    ) + note["body"]


class Hub:
    def __init__(self, repo_dir: Path):
        self.repo = repo_dir.resolve()
        self.notes_dir = self.repo / "notes"
        self.lock = threading.RLock()
        self.status = {"last_pull": None, "last_push": None, "last_error": None}

    def git(self, *args):
        return subprocess.run(
            [
                "git", "-C", str(self.repo),
                "-c", "user.name=NoteLink Hub",
                "-c", "user.email=notelink@hub.local",
                *args,
            ],
            capture_output=True,
            text=True,
            timeout=180,
        )

    @staticmethod
    def _last_line(result):
        text = (result.stderr or result.stdout).strip()
        return text.splitlines()[-1] if text else None

    def pull(self):
        with self.lock:
            result = self.git("pull", "--rebase", "-X", "ours", "--autostash")
            if result.returncode == 0:
                self.status["last_pull"] = time.time()
                self.status["last_error"] = None
            else:
                self.status["last_error"] = self._last_line(result) or "pull failed"
            return result

    def push(self):
        result = self.git("push")
        if result.returncode != 0:
            # Lost a race with the phone — rebase our commit on top, push again.
            self.git("pull", "--rebase", "-X", "ours", "--autostash")
            result = self.git("push")
        if result.returncode == 0:
            self.status["last_push"] = time.time()
            self.status["last_error"] = None
        else:
            self.status["last_error"] = self._last_line(result) or "push failed"

    def list_notes(self, include_deleted=False):
        notes = []
        if self.notes_dir.is_dir():
            for path in sorted(self.notes_dir.glob("*.md")):
                try:
                    text = path.read_text(encoding="utf-8")
                except OSError:
                    continue
                note = parse_note(text, path.stem)
                if note["deleted"] and not include_deleted:
                    continue
                notes.append(note)
        notes.sort(key=lambda n: (not n["pinned"], -n["updated"]))
        return notes

    def note_path(self, note_id):
        if not note_id or "/" in note_id or "\\" in note_id or note_id.startswith("."):
            raise ValueError("bad note id")
        return self.notes_dir / f"{note_id}.md"

    def get_note(self, note_id):
        path = self.note_path(note_id)
        if not path.is_file():
            return None
        return parse_note(path.read_text(encoding="utf-8"), note_id)

    def write_note(self, note_id, title, body, pinned, color):
        note = {
            "id": note_id,
            "title": title,
            "body": body,
            "pinned": bool(pinned),
            "color": color or "default",
            "updated": now_ms(),
            "deleted": False,
        }
        with self.lock:
            self.notes_dir.mkdir(exist_ok=True)
            self.note_path(note_id).write_text(serialize_note(note), encoding="utf-8")
            self.git("add", "-A")
            self.git("commit", "-m", f"NoteLink hub: update {note_id[:8]}")
            self.push()
        return note

    def delete_note(self, note_id):
        with self.lock:
            existing = self.get_note(note_id)
            if existing is None:
                return None
            existing["deleted"] = True
            existing["pinned"] = False
            existing["updated"] = now_ms()
            self.note_path(note_id).write_text(serialize_note(existing), encoding="utf-8")
            self.git("add", "-A")
            self.git("commit", "-m", f"NoteLink hub: delete {note_id[:8]}")
            self.push()
        return existing


def pull_loop(hub: Hub, stop: threading.Event):
    while not stop.wait(30):
        hub.pull()
        # Push too: commits made while the PC was offline sit in the local
        # clone until the connection returns; this flushes them automatically.
        hub.push()


def create_app(hub: Hub) -> Flask:
    app = Flask(__name__, static_folder=str(STATIC_DIR), static_url_path="")

    @app.get("/")
    def index():
        return send_from_directory(STATIC_DIR, "index.html")

    @app.get("/api/notes")
    def api_notes():
        return jsonify({"notes": hub.list_notes(), "status": hub.status})

    @app.post("/api/notes")
    def api_create():
        data = request.get_json(silent=True) or {}
        note = hub.write_note(
            uuid.uuid4().hex,
            data.get("title", ""),
            data.get("body", ""),
            data.get("pinned", False),
            data.get("color", "default"),
        )
        return jsonify(note)

    @app.get("/api/notes/<note_id>")
    def api_note(note_id):
        note = hub.get_note(note_id)
        if note is None:
            return jsonify({"error": "not found"}), 404
        return jsonify(note)

    @app.put("/api/notes/<note_id>")
    @app.post("/api/notes/<note_id>")  # POST alias so beforeunload sendBeacon saves work
    def api_update(note_id):
        data = request.get_json(silent=True) or {}
        note = hub.write_note(
            note_id,
            data.get("title", ""),
            data.get("body", ""),
            data.get("pinned", False),
            data.get("color", "default"),
        )
        return jsonify(note)

    @app.post("/api/notes/<note_id>/delete")
    def api_delete(note_id):
        if hub.delete_note(note_id) is None:
            return jsonify({"error": "not found"}), 404
        return jsonify({"ok": True})

    @app.post("/api/sync")
    def api_sync():
        hub.pull()
        return jsonify(hub.status)

    @app.get("/api/status")
    def api_status():
        return jsonify(hub.status)

    return app


def main():
    parser = argparse.ArgumentParser(description="NoteLink hub")
    parser.add_argument("--repo", default=str(Path(__file__).resolve().parent / "notes-repo"))
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()

    repo_dir = Path(args.repo)
    if not (repo_dir / ".git").is_dir():
        print("The hub needs a git clone of your notes repo next to hub.py:\n")
        print(f'  git clone https://github.com/<you>/notelink-notes "{repo_dir}"\n')
        print("Then start the hub again.")
        sys.exit(1)

    hub = Hub(repo_dir)
    hub.pull()

    stop = threading.Event()
    threading.Thread(target=pull_loop, args=(hub, stop), daemon=True).start()

    app = create_app(hub)
    print(f"NoteLink hub  →  http://{args.host}:{args.port}   (repo: {repo_dir})")
    try:
        app.run(host=args.host, port=args.port, debug=False, threaded=True)
    finally:
        stop.set()


if __name__ == "__main__":
    main()
