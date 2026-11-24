#!/usr/bin/env python3
"""Check public deployment routes without creating accounts or changing data."""

import argparse
import json
import sys
from urllib.error import HTTPError, URLError
from urllib.request import urlopen


def check(url, expected, predicate=lambda body: True):
    try:
        try:
            response = urlopen(url, timeout=15)
        except HTTPError as error:
            response = error
        with response:
            body = response.read().decode("utf-8")
            passed = response.status == expected and predicate(body)
            print(f'{"PASS" if passed else "FAIL"} {url}: HTTP {response.status}, expected {expected}')
            return passed
    except (URLError, TimeoutError, ValueError) as error:
        print(f"FAIL {url}: {type(error).__name__}")
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--api", required=True)
    parser.add_argument("--frontend", required=True)
    args = parser.parse_args()
    api = args.api.rstrip("/")
    frontend = args.frontend.rstrip("/")
    results = [
        check(f"{api}/", 200, lambda body: body == "Server running"),
        check(f"{api}/api/auth/me", 401, lambda body: "error" in json.loads(body)),
        check(f"{frontend}/", 200, lambda body: "MTOGO" in body),
        check(f"{frontend}/auth/login", 200, lambda body: "<form" in body),
        check(f"{frontend}/api/auth/me", 401, lambda body: "error" in json.loads(body)),
    ]
    return 0 if all(results) else 1


if __name__ == "__main__":
    sys.exit(main())
