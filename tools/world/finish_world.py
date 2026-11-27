"""Turns the dedicated-server world from make-world.ps1 into ready single-player worlds for the friends pack.

    python tools/world/finish_world.py <server world folder> <out folder> [copies]

Each copy gets its own name in the world list ("Бодикам: Больница 1", 2, 3), cheats on (the host needs /bh
commands), adventure mode and normal difficulty, and no server leftovers (player data, stats, session lock).
level.dat is gzip NBT; a small reader and writer below, no extra packages.
"""
import gzip
import io
import shutil
import struct
import sys
from pathlib import Path

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)


class Tag:
    def __init__(self, kind, value):
        self.kind, self.value = kind, value


def read_payload(f, kind):
    if kind == BYTE: return struct.unpack(">b", f.read(1))[0]
    if kind == SHORT: return struct.unpack(">h", f.read(2))[0]
    if kind == INT: return struct.unpack(">i", f.read(4))[0]
    if kind == LONG: return struct.unpack(">q", f.read(8))[0]
    if kind == FLOAT: return struct.unpack(">f", f.read(4))[0]
    if kind == DOUBLE: return struct.unpack(">d", f.read(8))[0]
    if kind == BYTES:
        n = struct.unpack(">i", f.read(4))[0]
        return f.read(n)
    if kind == STRING:
        n = struct.unpack(">H", f.read(2))[0]
        return f.read(n).decode("utf-8")
    if kind == LIST:
        sub = f.read(1)[0]
        n = struct.unpack(">i", f.read(4))[0]
        return (sub, [read_payload(f, sub) for _ in range(n)])
    if kind == COMPOUND:
        out = {}
        while True:
            t = f.read(1)[0]
            if t == END:
                return out
            name = read_payload(f, STRING)
            out[name] = Tag(t, read_payload(f, t))
    if kind == INTS:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}i", f.read(4 * n)))
    if kind == LONGS:
        n = struct.unpack(">i", f.read(4))[0]
        return list(struct.unpack(f">{n}q", f.read(8 * n)))
    raise ValueError(f"unknown tag {kind}")


def write_payload(f, kind, v):
    if kind == BYTE: f.write(struct.pack(">b", v))
    elif kind == SHORT: f.write(struct.pack(">h", v))
    elif kind == INT: f.write(struct.pack(">i", v))
    elif kind == LONG: f.write(struct.pack(">q", v))
    elif kind == FLOAT: f.write(struct.pack(">f", v))
    elif kind == DOUBLE: f.write(struct.pack(">d", v))
    elif kind == BYTES:
        f.write(struct.pack(">i", len(v))); f.write(v)
    elif kind == STRING:
        b = v.encode("utf-8"); f.write(struct.pack(">H", len(b))); f.write(b)
    elif kind == LIST:
        sub, items = v
        f.write(bytes([sub])); f.write(struct.pack(">i", len(items)))
        for it in items: write_payload(f, sub, it)
    elif kind == COMPOUND:
        for name, tag in v.items():
            f.write(bytes([tag.kind])); write_payload(f, STRING, name); write_payload(f, tag.kind, tag.value)
        f.write(bytes([END]))
    elif kind == INTS:
        f.write(struct.pack(">i", len(v))); f.write(struct.pack(f">{len(v)}i", *v))
    elif kind == LONGS:
        f.write(struct.pack(">i", len(v))); f.write(struct.pack(f">{len(v)}q", *v))


def load(path):
    f = io.BytesIO(gzip.decompress(path.read_bytes()))
    kind = f.read(1)[0]
    name = read_payload(f, STRING)
    return name, read_payload(f, kind)


def save(path, name, root):
    f = io.BytesIO()
    f.write(bytes([COMPOUND])); write_payload(f, STRING, name); write_payload(f, COMPOUND, root)
    path.write_bytes(gzip.compress(f.getvalue()))


def main():
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    copies = int(sys.argv[3]) if len(sys.argv) > 3 else 3
    if not (src / "level.dat").exists():
        sys.exit(f"no level.dat in {src}")
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)
    skip = {"playerdata", "stats", "advancements", "session.lock", "level.dat_old", "uid.dat"}
    for i in range(1, copies + 1):
        title = f"Бодикам: Больница {i}"
        dst = out / f"Bodycam Bolnitsa {i}"
        shutil.copytree(src, dst, ignore=lambda d, names: [n for n in names if n in skip])
        name, root = load(dst / "level.dat")
        data = root["Data"].value
        data["LevelName"] = Tag(STRING, title)
        data["allowCommands"] = Tag(BYTE, 1)
        data["GameType"] = Tag(INT, 2)
        data["Difficulty"] = Tag(BYTE, 2)
        data.pop("Player", None)
        save(dst / "level.dat", name, root)
        print(f"{dst.name}: {title}")


if __name__ == "__main__":
    main()
