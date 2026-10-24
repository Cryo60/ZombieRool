# Extension des buildables

Les recettes de carte restent indépendantes des presets. Les composants acceptent tout objet enregistré, y compris ceux des addons, avec leur NBT. Les identifiants de pièces sont libres et limités à 64 caractères. Les établis acceptent les recettes existantes ; aucune recette de bouclier ou de turbine n’est imposée à une carte.

Un datapack peut ajouter un preset dans `data/<namespace>/buildable_presets/<nom>.json`. Après `/reload`, il apparaît dans la bibliothèque de l’éditeur, avec pagination. Le créateur doit cliquer dessus pour créer sa propre recette modifiable. Exemple :

```json
{
  "name": "Custom Generator",
  "mode": "item",
  "ticks": 40,
  "limit": 0,
  "reward": {"item": "minecraft:clock", "count": 1, "nbt": "{CustomModelData:42}"},
  "parts": [
    {"id": "example:coil", "item": "minecraft:copper_ingot", "count": 1},
    {"id": "example:frame", "item": "minecraft:iron_ingot", "count": 1}
  ]
}
```

Les objets `minecraft:*` de cet exemple peuvent être remplacés par les identifiants d’un addon. Les modes sont `item`, `block`, `deploy` et `event` ; `block` et `deploy` demandent un objet bloc. Le mode `event` permet de traiter le résultat dans Lua. Les composants restent des emplacements choisis par le map maker.

Un addon Java peut appeler `BuildablePresets.register(ResourceLocation, CompoundTag)` après l’enregistrement des objets, avec `name`, `mode`, `ticks`, `limit`, `reward` et `presetPieces`. Chaque entrée `presetPieces` contient `part` et `visual` (ItemStack sérialisé). Le registre copie les données. Les datapacks prennent priorité sur le preset Java portant le même identifiant.

Pour un composant ayant un modèle 3D vertical, ajouter son objet au tag cumulatif `zombierool:upright_components` : fichier `data/zombierool/tags/items/upright_components.json`, `replace: false`, avec les objets de l’addon dans `values`. Le rendu emploie le contexte `NONE`, sans réduction d’objet au sol. Les composants natifs se couchent à plat sur une face horizontale et restent verticaux sur un mur ; les réglages NBT et fournisseurs Java permettent de remplacer cette pose.

Les objets des tags `zombierool:buildable_components` et `zombierool:upright_components` sont également inclus dans la catégorie créative **Composants**, quel que soit leur namespace. Le premier tag permet d’ajouter des composants plats sans modifier leur orientation.

La pose peut être réglée par objet dans son NBT, conservé par les supports :

```snbt
{zr_component_pose:{scale:1.2f,y:0.15f,pitch:0f,yaw:90f,roll:0f,context:"none"}}
```

`x`, `y`, `z` sont des décalages en blocs ; les rotations sont en degrés. Un addon peut aussi enregistrer un fournisseur dans `BuildableVisuals.registerPose(itemId, (stack, face) -> ComponentPose)`, sur serveur et client, pour garder la sélection et le rendu cohérents. Les réglages NBT s’appliquent après le fournisseur.

Pour la navigation, ajouter les blocs de support d’un addon au tag cumulatif `zombierool:allowed_blocks`, en plus de leur collision vide et de leur type de chemin traversable. Les supports, decoys et markers fournis par ZombieRool sont déjà inclus. Les limites de navigation restent respectées.

Lua expose `defineBuildable`, `bindBuildablePart`, `bindBuildableItemPart`, `bindBuildableDestination`, `removeBuildable` et les consultations de progression. Événements : `OnBuildablePartPickedUp`, `OnBuildablePartDropped`, `OnBuildablePartInstalled`, `OnBuildableCompleted`, `OnBuildableClaimed`, `OnBuildablePlaced`. Les récompenses bouclier et turbine déclenchent également `OnBuildableClaimed`.

Les blocs techniques sans collision remplaçables temporairement par un équipement déployé peuvent être ajoutés au tag cumulatif `zombierool:equipment_replaceable`. Le Path Block natif y figure déjà. Le bloc original est sérialisé avec l’appareil et restauré à sa récupération ou destruction.
