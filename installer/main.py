#!/usr/bin/env python3
"""Transactional, reversible Android TV installation; no root or uninstall commands."""
import argparse
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import platform
import re
import shlex
import shutil
import socket
import subprocess
import sys
import time
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APP = "io.github.blk185.xiaomicleanhome"
HOME = APP + "/.RowHomeActivity"
DREAM = APP + "/.PhotoDream"
VERSION = "1.0.0"
REPOSITORY = "BLK185/xiaomi-clean-home"
PACKAGE = re.compile(r"[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+\Z")
COMPONENT = re.compile(r"[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+\Z")
PROTECTED = {
    APP, "android", "com.android.systemui", "com.android.shell", "com.android.phone",
    "com.android.settings", "com.android.tv.settings", "com.android.bluetooth",
    "com.android.inputdevices", "com.android.networkstack.inprocess", "com.android.externalstorage",
    "com.android.keychain", "com.android.captiveportallogin", "com.android.certinstaller",
    "com.android.se", "com.android.vpndialogs", "com.android.inputmethod.latin",
    "com.google.android.inputmethod.latin", "com.google.android.apps.mediashell",
    "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
    "com.google.android.katniss", "com.google.android.tts", "com.google.android.marvin.talkback",
    "com.google.android.tv.remote.service", "com.xiaomi.mitv.updateservice",
    "com.google.android.tungsten.setupwraith", "com.xiaomi.android.tvsetup.partnercustomizer",
    "com.mi.bluetooth", "mitv.service", "com.droidlogic", "com.google.android.backdrop",
}
CORE = re.compile(r"^(?:com\.android\.providers\.|com\.(?:google\.)?android\.(?:permissioncontroller|packageinstaller|ext\.|modulemetadata)|com\.google\.android\.overlay\.|com\.android\.networkstack|com\.android\.wifi|com\.google\.android\.networkstack)")


class InstallationError(RuntimeError):
    pass


def check_package(name):
    if not PACKAGE.fullmatch(name) or name in PROTECTED or CORE.match(name):
        raise InstallationError("Paquet protege ou invalide : " + name)


def endpoint(value):
    host, separator, port = value.partition(":")
    try:
        ip = ipaddress.IPv4Address(host)
    except ValueError as error:
        raise InstallationError("Saisir une adresse IPv4 locale, visible dans les parametres reseau de la TV.") from error
    if not (ip.is_private or ip in ipaddress.IPv4Network("100.64.0.0/10")) or ip.is_loopback or ip.is_multicast or ip.is_unspecified:
        raise InstallationError("Une adresse locale de la TV est requise.")
    if separator and port != "5555":
        raise InstallationError("Ce guide utilise le debogage USB/ADB reseau, port 5555.")
    return str(ip) + ":5555"


def download(url, destination):
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(url, headers={"User-Agent": "Xiaomi-Clean-Home/" + VERSION})
    temporary = destination.with_suffix(destination.suffix + ".part")
    try:
        with urllib.request.urlopen(request, timeout=60) as response, temporary.open("wb") as output:
            shutil.copyfileobj(response, output)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)


def safe_extract(archive, destination):
    destination = Path(destination).resolve()
    with zipfile.ZipFile(archive) as package:
        for entry in package.infolist():
            target = (destination / entry.filename).resolve()
            if not target.is_relative_to(destination) or (entry.external_attr >> 16) & 0o170000 == 0o120000:
                raise InstallationError("Archive contenant un chemin non autorise.")
        package.extractall(destination)


def find_adb(explicit=None):
    if explicit:
        executable = Path(explicit).expanduser().resolve()
        if not executable.is_file():
            raise InstallationError("ADB introuvable : " + str(executable))
        return str(executable)
    if shutil.which("adb"):
        return shutil.which("adb")
    system = {"Windows": "windows", "Darwin": "darwin", "Linux": "linux"}.get(platform.system())
    if not system:
        raise InstallationError("Installer les platform-tools Android de Google pour ce systeme.")
    cache = ROOT / ".cache" / ("adb-" + system)
    executable = cache / "platform-tools" / ("adb.exe" if system == "windows" else "adb")
    if not executable.is_file():
        archive = cache / "platform-tools.zip"
        print("Telechargement d'ADB depuis le site officiel Google...", flush=True)
        download("https://dl.google.com/android/repository/platform-tools-latest-" + system + ".zip", archive)
        safe_extract(archive, cache)
        if system != "windows":
            executable.chmod(0o755)
    return str(executable)


class Device:
    def __init__(self, adb, address):
        self.adb, self.address = adb, endpoint(address)

    def run(self, *args, timeout=40, check=True):
        try:
            process = subprocess.run([self.adb, "-s", self.address, *args], capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise InstallationError("Commande ADB interrompue : " + " ".join(args[:2])) from error
        if check and process.returncode:
            raise InstallationError((process.stderr or process.stdout).strip()[:1500])
        return process.stdout.strip()

    def connect(self):
        process = subprocess.run([self.adb, "connect", self.address], capture_output=True, text=True, timeout=35)
        if process.returncode:
            raise InstallationError(process.stderr.strip() or process.stdout.strip())
        try:
            state = self.run("get-state")
        except InstallationError as error:
            raise InstallationError("Allumer la TV, activer le debogage et accepter l'autorisation affichee sur la TV.") from error
        if state != "device":
            raise InstallationError("Accepter l'autorisation ADB sur la TV, puis relancer.")

    def shell(self, *words, check=True):
        return self.run("shell", " ".join(shlex.quote(str(word)) for word in words), check=check)

    def identity(self):
        keys = {"manufacturer": "ro.product.manufacturer", "model": "ro.product.model", "device": "ro.product.device", "sdk": "ro.build.version.sdk", "serial": "ro.serialno"}
        result = {key: self.shell("getprop", value) for key, value in keys.items()}
        try:
            result["sdk"] = int(result["sdk"])
        except ValueError as error:
            raise InstallationError("Impossible de lire la version Android.") from error
        return result

    def packages(self, disabled=False):
        return {line.removeprefix("package:").strip() for line in self.shell("pm", "list", "packages", *(["-d"] if disabled else [])).splitlines() if line.startswith("package:")}

    def package_state(self, name):
        text = self.shell("dumpsys", "package", name)
        match = re.search(r"User 0:.*?\benabled=(\d+)", text)
        if not match:
            raise InstallationError("Etat du paquet illisible : " + name)
        return int(match.group(1)), text

    def home(self):
        output = self.shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        components = [line.strip() for line in output.splitlines() if COMPONENT.fullmatch(line.strip())]
        if len(components) != 1:
            raise InstallationError("Accueil Android actuel ambigu ; choisir un accueil sur la TV avant de continuer.")
        return components[0]

    def setting(self, namespace, name):
        return self.shell("settings", "get", namespace, name)

    def put_setting(self, namespace, name, value):
        if value == "null":
            return self.shell("settings", "delete", namespace, name)
        return self.shell("settings", "put", namespace, name, value)


def select_profile(identity, install_only=False):
    if identity["manufacturer"].lower() != "xiaomi" or identity["sdk"] < 26:
        raise InstallationError("Une Xiaomi sous Android TV 8 ou plus est requise. Aucun changement effectue.")
    if install_only:
        return {"id": "installation-only", "label": "Installation seule, aucun nettoyage", "disable": [], "keep": []}
    for path in sorted((ROOT / "profiles").glob("*.json")):
        profile = json.loads(path.read_text(encoding="utf-8"))
        if all(identity.get(key) == value for key, value in profile["match"].items()):
            for name in profile["disable"]:
                check_package(name)
            return profile
    raise InstallationError("Pas de profil de nettoyage verifie pour ce modele/version. Utiliser --install-only pour installer seulement l'accueil, ou ajouter un profil teste.")


def save_json(path, value):
    path = Path(path)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(path)


def prepare(device, identity, profile, keep=()):
    installed = device.packages()
    if "feature:android.software.leanback" not in device.shell("pm", "list", "features"):
        raise InstallationError("Cet appareil ne declare pas Android TV ; aucune modification.")
    keep = set(keep) | set(profile.get("keep", ()))
    active_vpn = device.setting("secure", "always_on_vpn_app")
    if active_vpn and active_vpn != "null":
        keep.add(active_vpn)
    candidates = [name for name in profile["disable"] if name in installed and name not in keep]
    states, descriptions = {}, {}
    for name in candidates:
        check_package(name)
        state, description = device.package_state(name)
        # Manifest request, not permissions inherited from a shared UID.
        requested = description.split("requested permissions:", 1)[-1].split("install permissions:", 1)[0]
        if "android.permission.TV_INPUT_HARDWARE" in requested:
            raise InstallationError("Protection HDMI/tuner : " + name)
        states[name], descriptions[name] = state, description
    home = device.home()
    if not COMPONENT.fullmatch(home):
        raise InstallationError("Accueil invalide.")
    settings = {"global/" + key: device.setting("global", key) for key in ("window_animation_scale", "transition_animation_scale")}
    settings["secure/screensaver_components"] = device.setting("secure", "screensaver_components")
    return {"format": 1, "app": APP, "device": identity, "endpoint": device.address, "profile": profile["id"], "home": home, "package_states": states, "settings": settings, "installed_before": APP in installed, "attempted_packages": [], "settings_attempted": [], "home_attempted": False, "complete": False}, descriptions


def backup(device, state, descriptions, directory):
    directory = Path(directory)
    directory.mkdir(parents=True, exist_ok=False)
    for filename, command in {"packages.txt": ("pm", "list", "packages", "-f"), "disabled.txt": ("pm", "list", "packages", "-d"), "getprop.txt": ("getprop",), "settings-global.txt": ("settings", "list", "global"), "settings-secure.txt": ("settings", "list", "secure"), "settings-system.txt": ("settings", "list", "system")}.items():
        (directory / filename).write_text(device.shell(*command), encoding="utf-8")
    for name, description in descriptions.items():
        (directory / (name + ".txt")).write_text(description, encoding="utf-8")
    save_json(directory / "state.json", state)
    return directory / "state.json"


def ensure_home(device, timeout=30):
    device.shell("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        foreground = device.shell("dumpsys", "activity", "activities")
        active = any(APP + "/" in line and "RowHomeActivity" in line for line in foreground.splitlines() if "mResumedActivity" in line or "topResumedActivity" in line)
        if active and device.home().split("/")[0] == APP:
            return
        time.sleep(.8)
    raise InstallationError("Le nouvel accueil n'est pas confirme au premier plan. Nettoyage interrompu.")


def restore(device, path, verify_identity=True):
    path = Path(path)
    state = json.loads(path.read_text(encoding="utf-8"))
    if state.get("format") != 1 or state.get("app") != APP:
        raise InstallationError("Sauvegarde incompatible.")
    if verify_identity:
        current = device.identity()
        for key in ("model", "device", "serial"):
            if current.get(key) != state["device"].get(key):
                raise InstallationError("Cette sauvegarde appartient a un autre appareil.")
    errors = []
    operations = {0: "default-state", 1: "enable", 2: "disable", 3: "disable-user", 4: "disable-until-used"}
    for name in reversed(state["attempted_packages"]):
        check_package(name)
        old = state["package_states"][name]
        if old not in operations:
            raise InstallationError("Etat de sauvegarde invalide.")
        try:
            device.shell("pm", operations[old], "--user", "0", name)
        except InstallationError as error:
            errors.append(str(error))
    for item in reversed(state["settings_attempted"]):
        if item not in {"global/window_animation_scale", "global/transition_animation_scale", "secure/screensaver_components"}:
            raise InstallationError("Reglage non autorise dans la sauvegarde.")
        namespace, key = item.split("/")
        try:
            device.put_setting(namespace, key, state["settings"][item])
        except InstallationError as error:
            errors.append(str(error))
    if state["home_attempted"]:
        if not COMPONENT.fullmatch(state["home"]):
            raise InstallationError("Accueil de sauvegarde invalide.")
        try:
            device.shell("cmd", "package", "set-home-activity", "--user", "0", state["home"])
            device.shell("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
        except InstallationError as error:
            errors.append(str(error))
    if errors:
        raise InstallationError("Restauration partielle : " + "; ".join(errors))
    state["restored"] = True
    save_json(path, state)
    print("Ancien accueil, etats des paquets et reglages restaures. Aucune donnee effacee.")


def apply(device, state, path, apk):
    try:
        print("Installation de l'accueil signe...", flush=True)
        result = device.run("install", "-r", str(apk), timeout=120)
        if "Success" not in result:
            raise InstallationError("Installation refusee ; aucune desinstallation ne sera tentee. " + result)
        state["home_attempted"] = True
        save_json(path, state)
        device.shell("cmd", "package", "set-home-activity", "--user", "0", HOME)
        ensure_home(device)
        for name, old in state["package_states"].items():
            if old in (2, 3, 4):
                continue
            check_package(name)
            # Write ahead: a transport failure after execution must still be undoable.
            state["attempted_packages"].append(name)
            save_json(path, state)
            result = device.shell("pm", "disable-user", "--user", "0", name)
            if name not in device.packages(disabled=True):
                raise InstallationError("Desactivation refusee : " + name + " " + result)
            print("Desactive : " + name, flush=True)
        for item, value in {"global/window_animation_scale": "0.5", "global/transition_animation_scale": "0.5", "secure/screensaver_components": DREAM}.items():
            state["settings_attempted"].append(item)
            save_json(path, state)
            namespace, key = item.split("/")
            device.put_setting(namespace, key, value)
        ensure_home(device)
        state["complete"] = True
        save_json(path, state)
    except Exception:
        print("Installation interrompue. Restauration des changements deja appliques...", file=sys.stderr)
        try:
            restore(device, path, verify_identity=False)
        except Exception as recovery_error:
            print("La connexion est peut-etre interrompue. Reprendre la restauration avec : " + str(path), file=sys.stderr)
            print(str(recovery_error), file=sys.stderr)
        raise


def verify_apk(path, expected):
    digest = hashlib.sha256(Path(path).read_bytes()).hexdigest()
    if not re.fullmatch(r"[a-fA-F0-9]{64}", expected or "") or digest.lower() != expected.lower():
        raise InstallationError("SHA-256 de l'APK incorrect : installation annulee.")


def release_apk(explicit=None, checksum=None):
    manifest = json.loads((ROOT / "release-manifest.json").read_text(encoding="utf-8-sig"))
    name = manifest["apk"]
    if Path(name).name != name or not re.fullmatch(r"[a-zA-Z0-9._-]+\.apk", name):
        raise InstallationError("Nom d'APK invalide.")
    apk = Path(explicit).resolve() if explicit else ROOT / "dist" / name
    if not apk.is_file():
        url = "https://github.com/" + REPOSITORY + "/releases/download/v" + manifest["version"] + "/" + name
        download(url, apk)
    verify_apk(apk, checksum or manifest["sha256"])
    return apk


def reboot_and_verify(device, state):
    print("Redemarrage et verification de l'accueil...", flush=True)
    device.run("reboot", timeout=15)
    time.sleep(10)
    until = time.monotonic() + 120
    last = None
    while time.monotonic() < until:
        try:
            device.connect()
            if device.shell("getprop", "sys.boot_completed") == "1":
                ensure_home(device, timeout=12)
                missing = set(state["attempted_packages"]) - device.packages(disabled=True)
                if missing:
                    raise InstallationError("Le firmware reactive : " + ", ".join(sorted(missing)))
                print("Accueil et nettoyage confirmes apres redemarrage.")
                return
        except InstallationError as error:
            last = error
        time.sleep(4)
    raise InstallationError("Verification apres redemarrage incomplete. Sauvegarde disponible. " + str(last))


def close_debugging(device):
    try:
        device.shell("settings", "put", "global", "adb_enabled", "0", check=False)
    except InstallationError:
        pass
    host = device.address.split(":")[0]
    try:
        with socket.create_connection((host, 5555), timeout=3):
            print("Fermer le debogage USB manuellement sur la TV : le port repond encore.")
    except ConnectionRefusedError:
        print("Debogage ferme : la TV refuse la connexion au port 5555.")
    except OSError:
        print("Fermeture demandee. Verifier le debogage sur la TV ; un delai reseau ne prouve pas sa fermeture.")


def confirm(message, yes=False):
    return yes or input(message + " [o/N] ").strip().lower() in {"o", "oui", "y", "yes"}


def main(argv=None):
    parser = argparse.ArgumentParser(description="Installation reversible de Xiaomi Clean Home. Terminer d'abord la configuration initiale de la TV.")
    parser.add_argument("command", choices=["install", "plan", "restore"], nargs="?", default="install")
    parser.add_argument("--host", help="Adresse IPv4 de la TV")
    parser.add_argument("--adb", help="Chemin d'ADB existant (sinon telechargement officiel Google)")
    parser.add_argument("--install-only", action="store_true", help="Installer l'accueil sans desactiver de paquets sur un modele Xiaomi non profile")
    parser.add_argument("--keep-app", action="append", default=[], help="Paquet supplementaire a conserver")
    parser.add_argument("--apk", help="APK local signe")
    parser.add_argument("--sha256", help="SHA-256 attendu de cet APK")
    parser.add_argument("--backup", help="state.json a restaurer")
    parser.add_argument("--yes", action="store_true", help="Appliquer le plan sans question sur le PC (la TV exige toujours son autorisation)")
    parser.add_argument("--skip-reboot", action="store_true", help="Ne pas verifier apres redemarrage")
    parser.add_argument("--keep-debugging", action="store_true", help="Developpement : ne pas fermer ADB en fin de session")
    args = parser.parse_args(argv)
    address = args.host or input("Adresse IPv4 de la Xiaomi (Parametres > Reseau) : ").strip()
    device = Device(find_adb(args.adb), address)
    device.connect()
    if args.command == "restore":
        files = sorted((ROOT / "backups").glob("*/state.json"))
        path = Path(args.backup) if args.backup else files[-1] if files else None
        if not path:
            raise InstallationError("Aucune sauvegarde ; utiliser --backup chemin/state.json.")
        if confirm("Restaurer " + str(path) + " ?", args.yes):
            restore(device, path)
            if not args.keep_debugging:
                close_debugging(device)
        return 0
    identity = device.identity()
    profile = select_profile(identity, args.install_only)
    state, descriptions = prepare(device, identity, profile, args.keep_app)
    print("Appareil : " + identity["model"] + " / Android SDK " + str(identity["sdk"]))
    print("Profil : " + profile["label"])
    planned = [p for p, old in state["package_states"].items() if old not in (2, 3, 4)]
    print("A desactiver : " + (", ".join(planned) or "aucun paquet"))
    print("Chromecast, recherche vocale, mises a jour, Google Play, clavier et Bluetooth sont proteges.")
    if args.command == "plan":
        print("Diagnostic uniquement : aucune modification de la TV.")
        return 0
    apk = release_apk(args.apk, args.sha256)
    if not confirm("Installer l'accueil, sauvegarder et appliquer ce plan ?", args.yes):
        return 0
    stamp = time.strftime("%Y%m%d-%H%M%S") + "-" + str(time.time_ns() % 1000000)
    path = backup(device, state, descriptions, ROOT / "backups" / stamp)
    print("Sauvegarde locale privee : " + str(path))
    try:
        apply(device, state, path, apk)
        installed = device.packages()
        missing = [name for name in profile.get("keep", ()) if name not in installed]
        if missing:
            print("Applications a installer depuis Google Play (aucun APK proprietaire redistribue) :")
            for name in missing:
                print("  https://play.google.com/store/apps/details?id=" + name)
                if not args.yes and confirm("Ouvrir sa fiche sur la TV : " + name + " ?"):
                    device.shell("am", "start", "-a", "android.intent.action.VIEW", "-d", "market://details?id=" + name)
                    input("Terminer l'installation avec la telecommande, puis Entree... ")
        if not args.skip_reboot:
            reboot_and_verify(device, state)
        print("Installation terminee. Tester un cast avec image et son depuis le telephone.")
        print("Photos et reglages : Parametres de l'accueil > Veille photo. Accepter uniquement votre code sur la TV.")
    finally:
        if not args.keep_debugging:
            close_debugging(device)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (InstallationError, OSError, ValueError) as error:
        print("ERREUR : " + str(error), file=sys.stderr)
        raise SystemExit(1)
