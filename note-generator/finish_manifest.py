"""Writes the finished mod.json from the source manifest and the mod kit's hooks.json.

Follows "The finished mod.json" in Patchbay's BML/docs/sdk/building.md.
Usage: python3 finish_manifest.py <source mod.json> <hooks.json> <finished mod.json>
"""
import json
import re
import sys


def target_id(target, targets):
    for known, value in targets.items():
        if value == target:
            return known
    if target["kind"] == "class":
        base = target["class"]
    else:
        base = target["owner"] + "." + target["name"]
    base = re.sub(r"[^A-Za-z0-9_.\-]", "", base.replace("/", ".").replace("$", "_"))
    name, n = base, 1
    while name in targets:
        n += 1
        name = f"{base}-{n}"
    targets[name] = target
    return name


def main():
    source, hooks_path, out = sys.argv[1:4]
    manifest = json.load(open(source, encoding="utf-8"))
    built = json.load(open(hooks_path, encoding="utf-8"))
    for generated in ("targets", "hooks", "helper", "opens"):  # the source may be an earlier finished manifest
        manifest.pop(generated, None)
    targets = manifest.setdefault("targets", {})
    hooks = manifest.setdefault("hooks", [])

    for hook in built.get("hooks", []):
        entry = {"id": hook["id"], "target": target_id(hook["target"], targets), "transform": hook["transform"]}
        if "returns" in hook:
            entry["returns"] = hook["returns"]
        if "method_from" in hook:
            entry["method"] = {"from_target": target_id(hook["method_from"], targets)}
            entry["body"] = {"template": hook["body"]}
        if "match" in hook:
            match = hook["match"]
            if "target" in match:
                entry["match"] = {"opcode": match["opcode"], "target": target_id(match["target"], targets),
                                  "occurrence": match["occurrence"]}
            else:
                entry["match"] = match
        entry["bridge"] = hook["bridge"]
        hooks.append(entry)

    if built.get("fields"):
        raise SystemExit("hooks.json has added fields: this script doesn't write them")

    classes = built.get("hook_classes", [])
    if classes:
        manifest["helper"] = {"jar": "helper.jar", "class": classes[0]}

    opens = sorted(target_id(entry, targets) for entry in built.get("access", []))
    if opens:
        manifest["opens"] = opens

    json.dump(manifest, open(out, "w", encoding="utf-8"), indent=2)
    print(f"MANIFEST_OK hooks={len(hooks)} targets={len(targets)} opens={len(opens)}")


if __name__ == "__main__":
    main()
