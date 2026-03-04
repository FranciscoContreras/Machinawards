#!/usr/bin/env python3
"""Minimal RCON client for sending commands to a local Minecraft server."""
import socket
import struct
import sys


def rcon(host: str, port: int, password: str, command: str) -> str:
    with socket.socket() as s:
        s.settimeout(5)
        s.connect((host, port))

        def send(req_id: int, ptype: int, payload: str):
            body = payload.encode("utf-8") + b"\x00\x00"
            s.sendall(struct.pack("<iii", len(body) + 8, req_id, ptype) + body)

        def recv() -> tuple[int, int, str]:
            raw = b""
            while len(raw) < 4:
                raw += s.recv(4096)
            length = struct.unpack("<i", raw[:4])[0]
            while len(raw) < 4 + length:
                raw += s.recv(4096)
            req_id, ptype = struct.unpack("<ii", raw[4:12])
            payload = raw[12 : 4 + length - 2].decode("utf-8")
            return req_id, ptype, payload

        # Login
        send(1, 3, password)
        req_id, _, _ = recv()
        if req_id == -1:
            raise RuntimeError("RCON authentication failed")

        # Command
        send(2, 2, command)
        _, _, response = recv()
        return response


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: rcon.py <password> <command>")
        sys.exit(1)
    try:
        result = rcon("127.0.0.1", 25575, sys.argv[1], " ".join(sys.argv[2:]))
        if result:
            print(result)
    except Exception as e:
        print(f"RCON error: {e}", file=sys.stderr)
        sys.exit(1)
