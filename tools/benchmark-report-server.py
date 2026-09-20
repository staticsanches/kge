#!/usr/bin/env python3
"""Collects the web benchmark cells a headless browser GET-reports.

The wasmJs sweep fires `fetch("<report>?data=<table>")` for every finished cell;
this server prints each payload to stdout, so a headless run is read from the
terminal instead of a DOM dump. Build the distribution, serve it, then drive it:

    tools/gradle :kge-benchmark:wasmJsBrowserDistribution
    cd kge-benchmark/build/dist/wasmJs/productionExecutable && python3 -m http.server 8765
    python3 tools/benchmark-report-server.py --port 8766
    chrome --headless=new --ignore-gpu-blocklist --use-angle=metal --disable-frame-rate-limit --disable-gpu-vsync "http://127.0.0.1:8765/index.html?sizes=640x360&modes=rAF&highDpi=false&workloads=text-merged-list&warmup=2&measure=5&report=http://127.0.0.1:8766"

Headless Chrome drops the real GPU unless `--ignore-gpu-blocklist` (or
`--use-angle=...`) is passed, and is vsync-paced unless
`--disable-frame-rate-limit --disable-gpu-vsync` is; never time with its
SwiftShader fallback (`--disable-gpu --enable-unsafe-swiftshader`).
"""

import argparse
import http.server
import urllib.parse


class ReportHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        query = urllib.parse.urlparse(self.path).query
        data = urllib.parse.parse_qs(query).get("data", [""])[0]
        if data:
            print(data, flush=True)
            print("====CELL-END====", flush=True)
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", "2")
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, *args: object) -> None:
        pass


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8766)
    args = parser.parse_args()
    print(f"listening on http://127.0.0.1:{args.port}", flush=True)
    http.server.HTTPServer(("127.0.0.1", args.port), ReportHandler).serve_forever()


if __name__ == "__main__":
    main()
