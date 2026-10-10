"""Explicit assets for authorized Texture Kit heads, folders and glass hinges."""
import copy
import json

def encode(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

def resources(original):
    result = {}
    prefix = "assets/zombierool/"
    for lang in ("en_us", "fr_fr"):
        name = prefix + "lang/" + lang + ".json"
        labels = json.loads(original.read(name))
        english = {
            "shape.zombierool.head": "Head",
            "gui.zombierool.texture_import_head": "Import head skin",
            "gui.zombierool.texture_kit.parent": "Up",
            "gui.zombierool.texture_kit.folder": "Destination folder",
            "gui.zombierool.texture_kit.folder_hint": "e.g. walls/brick",
            "gui.zombierool.texture_kit.new_folder": "New folder",
            "gui.zombierool.texture_kit.move": "Move here",
            "gui.zombierool.texture_kit.selected": "Selected: %s",
            "gui.zombierool.texture_kit.folder_created": "Folder created: /%s",
            "gui.zombierool.texture_kit.moved": "Texture moved: %s",
            "gui.zombierool.texture_kit.helper": "Drag a texture onto a folder (or Up). You can also select a texture, open its destination folder and click Move here. A typed path overrides the displayed folder; / means the root. Select a shape to receive the texture immediately.",
        }
        french = {
            "shape.zombierool.head": "Tête",
            "gui.zombierool.texture_import_head": "Importer un skin",
            "gui.zombierool.texture_kit.parent": "Retour",
            "gui.zombierool.texture_kit.folder": "Dossier de destination",
            "gui.zombierool.texture_kit.folder_hint": "ex. murs/brique",
            "gui.zombierool.texture_kit.new_folder": "Créer dossier",
            "gui.zombierool.texture_kit.move": "Déplacer ici",
            "gui.zombierool.texture_kit.selected": "Sélection : %s",
            "gui.zombierool.texture_kit.folder_created": "Dossier créé : /%s",
            "gui.zombierool.texture_kit.moved": "Texture déplacée : %s",
            "gui.zombierool.texture_kit.helper": "Glisse une texture sur un dossier (ou Retour). Tu peux aussi sélectionner une texture, ouvrir le dossier de destination et cliquer sur Déplacer ici. Un chemin saisi remplace le dossier affiché ; / désigne la racine. Choisis une forme pour recevoir la texture immédiatement.",
        }
        labels.update(english if lang == "en_us" else french)
        for slot in range(32):
            labels[f"block.zombierool.maptex_{slot}_head"] = ("Texture head " if lang == "en_us" else "Tête texturée ") + str(slot + 1)
        result[name] = encode(labels)
    for slot in range(32):
        stem = f"maptex_{slot}"
        model = json.loads(original.read(prefix + f"models/block/{stem}.json"))
        model.pop("parent", None)
        model["ambientocclusion"] = False
        model["elements"] = [{"from": [4, 0, 4], "to": [12, 8, 12], "faces": {face: {"texture": "#" + face, "uv": [0, 0, 16, 16]} for face in ("down", "up", "north", "south", "east", "west")}}]
        result[prefix + f"models/block/{stem}_head.json"] = encode(model)
        result[prefix + f"blockstates/{stem}_head.json"] = encode({"variants": {"facing=" + facing: {"model": "zombierool:block/" + stem + "_head", "y": rotation} for facing, rotation in zip(("north", "east", "south", "west"), (0, 90, 180, 270))}})
        result[prefix + f"models/item/{stem}_head.json"] = encode({"parent": "zombierool:block/" + stem + "_head"})
    state_name = prefix + "blockstates/glass_defense_door.json"
    original_variants = json.loads(original.read(state_name))["variants"]
    variants = {}
    for key, value in original_variants.items():
        for centered in (False, True):
            for hinge in ("left", "right"):
                for opened in (False, True):
                    entry = dict(value)
                    stem = value["model"].split(":block/")[1]
                    mirrored = (hinge == "right") != opened
                    suffix = ("_right" if mirrored else "") + ("_centered" if centered else "")
                    if suffix:
                        resource = prefix + "models/block/" + stem + suffix + ".json"
                        if resource not in result:
                            model = copy.deepcopy(json.loads(original.read(prefix + "models/block/" + stem + ".json")))
                            for element in model.get("elements", []):
                                if centered:
                                    element["from"][2] += 6.5
                                    element["to"][2] += 6.5
                                if mirrored:
                                    old_from, old_to = element["from"][0], element["to"][0]
                                    element["from"][0], element["to"][0] = 16 - old_to, 16 - old_from
                                    faces = element["faces"]
                                    east, west = faces.pop("east", None), faces.pop("west", None)
                                    if west is not None: faces["east"] = west
                                    if east is not None: faces["west"] = east
                                    for face in faces.values():
                                        uv = face.get("uv")
                                        if uv: uv[0], uv[2] = uv[2], uv[0]
                            result[resource] = encode(model)
                        entry["model"] += suffix
                    # These models occupy the north edge; vanilla facing north occupies
                    # the south edge. Open door templates also reverse their front UVs.
                    entry["y"] = (entry.get("y", 0) + 180 + ((-90 if hinge == "right" else 90) if opened else 0)) % 360
                    variants[key + f",hinge={hinge},open={str(opened).lower()},centered={str(centered).lower()}"] = entry
    result[state_name] = encode({"variants": variants})
    return result
