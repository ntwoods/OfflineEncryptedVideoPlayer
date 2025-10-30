# encrypt_videos.py
# Examples:
#   # Encrypt MP4s to assets/videos + make videos.json
#   python encrypt_videos.py --in ./my_mp4s --out ./app/src/main/assets/videos --asset-prefix videos --ext mp4 --index videos.json
#
#   # Encrypt PDFs to assets/docs + make docs.json
#   python encrypt_videos.py --in ./my_docs --out ./app/src/main/assets/docs --asset-prefix docs --ext pdf --index docs.json
#
#   # Encrypt EVERYTHING from a folder (any extension), no index (app scans .enc)
#   python encrypt_videos.py --in ./input --out ./app/src/main/assets/docs --asset-prefix docs --ext "*"

import os, argparse, secrets, base64, json
from pathlib import Path
from typing import Iterable
from Crypto.Cipher import AES

def encrypt_file(in_path: Path, out_path: Path, key: bytes):
    iv = secrets.token_bytes(12)                     # 96-bit IV (GCM best practice)
    cipher = AES.new(key, AES.MODE_GCM, nonce=iv)
    plaintext = in_path.read_bytes()
    ciphertext, tag = cipher.encrypt_and_digest(plaintext)
    out_path.write_bytes(iv + ciphertext + tag)      # [IV][cipher+tag]

def iter_inputs(in_dir: Path, exts: Iterable[str], recursive: bool) -> Iterable[Path]:
    if exts == ["*"]:
        pattern = "**/*" if recursive else "*"
        for p in in_dir.glob(pattern):
            if p.is_file():
                yield p
        return

    # normalize extensions like ["mp4","pdf"] -> [".mp4",".pdf"]
    norm = {e.lower() if e.startswith(".") else f".{e.lower()}" for e in exts}
    pattern = "**/*" if recursive else "*"
    for p in in_dir.glob(pattern):
        if p.is_file() and p.suffix.lower() in norm:
            yield p

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="in_dir", required=True)
    ap.add_argument("--out", dest="out_dir", required=True)
    ap.add_argument("--key-out", dest="key_out", required=False, default=None,
                    help="Optional: write the Base64 key copy to this path")
    ap.add_argument("--ext", default="mp4",
                    help='Comma separated extensions to include (e.g. "mp4,pdf" or "*" for all). Default: mp4')
    ap.add_argument("--asset-prefix", default="videos",
                    help='Prefix used in assetPath (usually "videos" or "docs"). Default: videos')
    ap.add_argument("--index", default=None,
                    help='Optional JSON index filename to write in the OUT folder (e.g. videos.json or docs.json)')
    ap.add_argument("--recursive", action="store_true", help="Recurse into subfolders")
    args = ap.parse_args()

    in_dir = Path(args.in_dir)
    out_dir = Path(args.out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)

    # Key handling
    key_b64 = os.environ.get("AES_KEY_B64", "").strip()
    if not key_b64:
        key_b64 = input("Paste AES_KEY_B64 from BuildConfig: ").strip()
    key = base64.b64decode(key_b64)

    if args.key_out:
        Path(args.key_out).write_text(key_b64, encoding="utf-8")

    # Collect inputs
    exts = [e.strip() for e in args.ext.split(",")] if args.ext else ["mp4"]
    inputs = list(iter_inputs(in_dir, exts, args.recursive))
    if not inputs:
        print(f"No input files found in {in_dir} for extensions: {exts}")
        return

    entries = []
    for src in inputs:
        out_name = f"{src.name}.enc"   # keep original filename + .enc (e.g., Doc1.pdf.enc)
        dst = out_dir / out_name
        encrypt_file(src, dst, key)
        entries.append({
            "title": src.stem,  # filename without extension
            "assetPath": f"{args.asset_prefix}/{out_name}"
        })

    print(f"Encrypted {len(entries)} file(s) → {out_dir}")

    # Optional JSON index
    if args.index:
        index_path = out_dir / args.index
        existing = []
        if index_path.exists():
            try:
                existing = json.loads(index_path.read_text(encoding="utf-8"))
            except Exception:
                existing = []
        seen = {e["assetPath"] for e in existing}
        for e in entries:
            if e["assetPath"] not in seen:
                existing.append(e)
        index_path.write_text(json.dumps(existing, indent=2), encoding="utf-8")
        print(f"Wrote/updated index: {index_path}")

if __name__ == "__main__":
    main()
