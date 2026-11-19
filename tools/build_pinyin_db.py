from __future__ import annotations

import lzma
import os
import re
import sqlite3
import sys
import time
import urllib.request
from pathlib import Path

REF = os.environ.get("RIME_FROST_REF", "60e84477cadd07db60bd29a852212f075f0814a9")
SOURCE_DIR = os.environ.get("RIME_FROST_SOURCE_DIR", "")
ROOT = Path(__file__).resolve().parent.parent
CACHE = ROOT / "tools" / ".cache" / "rime-frost" / REF
DB_VERSION = 1
OUT = ROOT / "app" / "src" / "main" / "assets" / f"pinyin-v{DB_VERSION}.db.xz"

CORE_FILES = ["8105", "41448", "GB18030-2022", "base", "ext", "others", "tencent"]
CELL_FILES = [
    "idiom", "history", "geography", "place", "sport", "exthot",
    "food", "game", "medication", "literature", "name2", "name",
    "animal", "industry_product", "shulihua", "luna",
    "chess", "chess2", "computer", "media", "music", "inputmethod",
]

MIN_CORE_MULTI_WEIGHT = int(os.environ.get("MIN_CORE_MULTI_WEIGHT", "21"))
MAX_PER_KEY = int(os.environ.get("MAX_PER_KEY", "80"))
MAX_WORD_LEN = 12
MAX_PREFIX_TOP = 300
INITIAL_TOP_BY_LENGTH = {1: 16, 2: 32, 3: 16}
INITIAL_SOUNDS = ("zh", "ch", "sh")

HAN = re.compile(r"^[\u3400-\u4dbf\u4e00-\u9fff\u3005\u3007·]+$")
CODE = re.compile(r"^[a-z]+$")


def log(message: str) -> None:
    print(f"[dict] {message}", flush=True)


def fetch(name: str, folder: str) -> str:
    if SOURCE_DIR:
        path = Path(SOURCE_DIR) / folder / f"{name}.dict.yaml"
        return path.read_text(encoding="utf-8")
    target = CACHE / folder / f"{name}.dict.yaml"
    if target.is_file():
        return target.read_text(encoding="utf-8")
    target.parent.mkdir(parents=True, exist_ok=True)
    url = f"https://raw.githubusercontent.com/gaboolic/rime-frost/{REF}/{folder}/{name}.dict.yaml"
    for attempt in range(3):
        try:
            log(f"download {folder}/{name}.dict.yaml")
            request = urllib.request.Request(url, headers={"User-Agent": "XiaoyuTyper-dict-builder"})
            with urllib.request.urlopen(request, timeout=120) as response:
                data = response.read()
            target.write_bytes(data)
            return data.decode("utf-8")
        except Exception as exc:
            if attempt == 2:
                raise
            log(f"retry {folder}/{name}: {exc}")
            time.sleep(2)
    raise RuntimeError("unreachable")


def collect(
    entries: dict[str, dict[str, int]],
    initials: dict[str, dict[str, int]],
    files: list[str],
    folder: str,
    core: bool,
) -> tuple[int, int]:
    kept = 0
    dropped = 0
    for name in files:
        text = fetch(name, folder)
        body = False
        for raw_line in text.split("\n"):
            line = raw_line.replace("\r", "")
            if not body:
                if line.strip() == "...":
                    body = True
                continue
            if not line or line.startswith("#"):
                continue
            parts = line.split("\t")
            if len(parts) < 2:
                continue
            word = parts[0].strip()
            code = parts[1].strip()
            weight = 0
            if len(parts) >= 3:
                try:
                    weight = int(float(parts[2]))
                except ValueError:
                    weight = 0
            if not HAN.match(word) or len(word) > MAX_WORD_LEN:
                dropped += 1
                continue
            syllables = [part for part in re.split(r"\s+", code.lower()) if part]
            key = "".join(syllables)
            if not CODE.match(key):
                dropped += 1
                continue
            if core and len(word) > 1 and weight < MIN_CORE_MULTI_WEIGHT:
                dropped += 1
                continue
            bucket = entries.setdefault(key, {})
            previous = bucket.get(word)
            if previous is None or weight > previous:
                bucket[word] = weight
            collect_initials(initials, word, syllables, weight)
            kept += 1
    return kept, dropped


def collect_initials(
    initials: dict[str, dict[str, int]],
    word: str,
    syllables: list[str],
    weight: int,
) -> None:
    if not syllables or len(syllables) != len(word):
        return

    def add(key: str) -> None:
        if not 1 <= len(key) <= 3:
            return
        bucket = initials.setdefault(key, {})
        previous = bucket.get(word)
        if previous is None or weight > previous:
            bucket[word] = weight

    def initial(syllable: str) -> str:
        for prefix in INITIAL_SOUNDS:
            if syllable.startswith(prefix):
                return prefix
        return syllable[0]

    first = syllables[0][0]
    if len(word) == 1:
        add(first)
        sound = initial(syllables[0])
        if sound != first:
            add(sound)
        return
    if len(word) > 3:
        return

    brief = "".join(syllable[0] for syllable in syllables)
    sound = "".join(initial(syllable) for syllable in syllables)
    add(brief)
    if sound != brief:
        add(sound)


def main() -> int:
    started = time.time()
    log(f"rime-frost ref={REF}")
    entries: dict[str, dict[str, int]] = {}
    initials: dict[str, dict[str, int]] = {}
    kept = 0
    dropped = 0
    for files, folder, core in ((CORE_FILES, "cn_dicts", True), (CELL_FILES, "cn_dicts_cell", False)):
        k, d = collect(entries, initials, files, folder, core)
        kept += k
        dropped += d
        log(f"{folder}: scanned={k} dropped={d} keys={len(entries)} initials={len(initials)}")

    word_freq: dict[str, int] = {}
    for bucket in entries.values():
        for word, weight in bucket.items():
            previous = word_freq.get(word)
            if previous is None or weight > previous:
                word_freq[word] = weight
    log(f"unique words={len(word_freq)} keys={len(entries)} kept={kept} dropped={dropped}")

    db_path = ROOT / "tools" / ".cache" / f"pinyin-v{DB_VERSION}.db"
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()
    connection = sqlite3.connect(db_path)
    try:
        cursor = connection.cursor()
        cursor.executescript(
            """
            PRAGMA journal_mode=OFF;
            PRAGMA synchronous=OFF;
            PRAGMA temp_store=MEMORY;
            PRAGMA cache_size=-131072;
            PRAGMA page_size=4096;
            CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
            CREATE TABLE words(id INTEGER PRIMARY KEY, word TEXT NOT NULL UNIQUE, freq INTEGER NOT NULL);
            CREATE TABLE entries(key TEXT NOT NULL, word_id INTEGER NOT NULL, PRIMARY KEY(key, word_id)) WITHOUT ROWID;
            CREATE TABLE prefix_top(prefix TEXT NOT NULL, word_id INTEGER NOT NULL, freq INTEGER NOT NULL, PRIMARY KEY(prefix, word_id)) WITHOUT ROWID;
            CREATE TABLE initial_top(key TEXT NOT NULL, word_id INTEGER NOT NULL, PRIMARY KEY(key, word_id)) WITHOUT ROWID;
            """
        )
        word_ids: dict[str, int] = {}
        ordered_words = sorted(word_freq.items(), key=lambda item: (-item[1], item[0]))
        batch = []
        for index, (word, freq) in enumerate(ordered_words, 1):
            word_ids[word] = index
            batch.append((index, word, freq))
            if len(batch) >= 10000:
                cursor.executemany("INSERT INTO words(id, word, freq) VALUES (?, ?, ?)", batch)
                batch.clear()
        if batch:
            cursor.executemany("INSERT INTO words(id, word, freq) VALUES (?, ?, ?)", batch)
        log("words table inserted")

        batch = []
        entry_count = 0
        for key in sorted(entries):
            bucket = entries[key]
            selected = sorted(
                bucket.items(),
                key=lambda item: (-word_freq[item[0]], -item[1], item[0]),
            )[:MAX_PER_KEY]
            for word, _weight in selected:
                batch.append((key, word_ids[word]))
            entry_count += len(selected)
            if len(batch) >= 50000:
                cursor.executemany("INSERT INTO entries(key, word_id) VALUES (?, ?)", batch)
                batch.clear()
        if batch:
            cursor.executemany("INSERT INTO entries(key, word_id) VALUES (?, ?)", batch)
        log(f"entries table inserted: {entry_count}")

        batch = []
        initial_count = 0
        for key in sorted(initials):
            limit = INITIAL_TOP_BY_LENGTH.get(len(key), 0)
            if limit == 0:
                continue
            bucket = initials[key]
            selected = sorted(
                bucket.items(),
                key=lambda item: (-word_freq[item[0]], -item[1], item[0]),
            )[:limit]
            for word, _weight in selected:
                batch.append((key, word_ids[word]))
                initial_count += 1
            if len(batch) >= 50000:
                cursor.executemany("INSERT INTO initial_top(key, word_id) VALUES (?, ?)", batch)
                batch.clear()
        if batch:
            cursor.executemany("INSERT INTO initial_top(key, word_id) VALUES (?, ?)", batch)
        log(f"initial_top inserted: {initial_count}")

        for length in (1, 2):
            cursor.execute(
                f"""
                INSERT INTO prefix_top(prefix, word_id, freq)
                SELECT prefix, word_id, freq FROM (
                    SELECT SUBSTR(e.key, 1, {length}) AS prefix,
                           e.word_id AS word_id,
                           MAX(w.freq) AS freq,
                           ROW_NUMBER() OVER (
                               PARTITION BY SUBSTR(e.key, 1, {length})
                               ORDER BY MAX(w.freq) DESC, e.word_id
                           ) AS rn
                    FROM entries e JOIN words w ON w.id = e.word_id
                    WHERE LENGTH(e.key) >= {length}
                    GROUP BY prefix, e.word_id
                ) WHERE rn <= {MAX_PREFIX_TOP}
                """
            )
            log(f"prefix_top len={length} inserted: {cursor.rowcount}")

        cursor.executemany(
            "INSERT INTO meta(key, value) VALUES (?, ?)",
            [
                ("version", str(DB_VERSION)),
                ("source", "rime-frost"),
                ("source_ref", REF),
                ("source_license", "GPL-3.0"),
                ("built_at", time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())),
                ("words", str(len(word_freq))),
                ("entries", str(entry_count)),
                ("keys", str(len(entries))),
                ("max_per_key", str(MAX_PER_KEY)),
                ("initial_keys", str(len(initials))),
                ("initial_entries", str(initial_count)),
            ],
        )
        cursor.execute("ANALYZE")
        cursor.execute(f"PRAGMA user_version = {DB_VERSION}")
        connection.commit()
        cursor.execute("PRAGMA optimize")
        cursor.execute("VACUUM")
        connection.commit()
    finally:
        connection.close()

    size = db_path.stat().st_size
    log(f"sqlite size={size / 1e6:.1f}MB; compressing")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(db_path, "rb") as source, lzma.open(
        OUT, "wb", preset=6
    ) as target:
        while True:
            chunk = source.read(1024 * 1024)
            if not chunk:
                break
            target.write(chunk)
    xz_size = OUT.stat().st_size
    log(f"done: db={size / 1e6:.1f}MB xz={xz_size / 1e6:.1f}MB -> {OUT.relative_to(ROOT)}")
    log(f"elapsed={time.time() - started:.0f}s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
