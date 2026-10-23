"""Maintainer helper: use an existing Git credential without writing or printing its token."""
import json
import os
import subprocess
import urllib.error
import urllib.request


class GitHub:
    def __init__(self, owner="BLK185"):
        self.owner = owner
        self.token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
        if not self.token:
            environment = dict(os.environ, GIT_TERMINAL_PROMPT="0", GCM_INTERACTIVE="never")
            result = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\nusername=" + owner + "\n\n", capture_output=True, text=True, env=environment, timeout=35)
            values = dict(line.split("=", 1) for line in result.stdout.splitlines() if "=" in line)
            self.token = values.get("password")
        if not self.token:
            raise RuntimeError("Authentifier GitHub dans Git Credential Manager ou definir GH_TOKEN pour publier.")

    def request(self, method, path, data=None, binary=None, mime="application/octet-stream"):
        url = path if path.startswith("https://uploads.github.com/") else "https://api.github.com" + path
        payload = binary if binary is not None else json.dumps(data).encode() if data is not None else None
        headers = {"Authorization": "Bearer " + self.token, "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "Xiaomi-Clean-Home-Maintainer", "Content-Type": mime if binary is not None else "application/json"}
        request = urllib.request.Request(url, data=payload, headers=headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                content = response.read()
                return json.loads(content) if content else None
        except urllib.error.HTTPError as error:
            try:
                message = json.loads(error.read()).get("message", "GitHub error")
            except ValueError:
                message = "GitHub error"
            raise RuntimeError("GitHub HTTP " + str(error.code) + ": " + message) from None
