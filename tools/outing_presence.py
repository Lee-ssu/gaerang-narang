"""Ephemeral, opt-in destination counts for the local Pleos demo."""
import argparse
import json
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock

ROUTE = re.compile(r"^/v1/places/([a-z0-9-]{1,48})/members/([0-9a-f]{32})$")
TTL_SECONDS = 45
members = {}
lock = Lock()


class PresenceHandler(BaseHTTPRequestHandler):
    def respond(self, code, payload):
        data = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        # Route keys include a temporary membership token.
        pass

    def handle_presence(self, method):
        match = ROUTE.fullmatch(self.path)
        if not match:
            self.respond(404, {"error": "unknown_endpoint"})
            return
        place, token = match.groups()
        count = None
        if method == "PUT":
            try:
                size = int(self.headers.get("Content-Length", "0"))
                if not 1 <= size <= 256:
                    raise ValueError()
                body = json.loads(self.rfile.read(size))
                count = body.get("dogs") if isinstance(body, dict) else None
                if type(count) is not int or not 1 <= count <= 8:
                    raise ValueError()
            except (ValueError, UnicodeError):
                self.respond(400, {"error": "invalid_count"})
                return
        now = time.monotonic()
        with lock:
            for expired in [key for key, value in members.items() if value[1] <= now]:
                del members[expired]
            key = (place, token)
            if method == "PUT":
                # A single session can share one destination at a time.
                for old in [entry for entry in members if entry[1] == token and entry != key]:
                    del members[old]
                members[key] = (count, now + TTL_SECONDS)
            elif method == "DELETE":
                members.pop(key, None)
            others = sum(value[0] for (dest, member), value in members.items()
                         if dest == place and member != token)
        self.respond(200, {"otherDogs": others})

    def do_GET(self):
        self.handle_presence("GET")

    def do_PUT(self):
        self.handle_presence("PUT")

    def do_DELETE(self):
        self.handle_presence("DELETE")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    ThreadingHTTPServer(("127.0.0.1", args.port), PresenceHandler).serve_forever()
