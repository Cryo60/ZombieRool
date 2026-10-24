# Creator tools and rewards

## Creator home and built-in camo settings
Run `tools/camo-studio/start.cmd`, or open the ZombieRool Creator desktop shortcut. The local home now contains the reward-code generator only. The former camo editor pages and its save endpoint are disabled; old editor sources and reference models remain development material outside the JAR.

All 99 camo appearance profiles are represented directly by `CamoAppearance.Profile` defaults and `TacZCamoOverlay.patternScale`. Migration checked every former setting for exact equivalence. There is no appearance JSON, compressed appearance file, or per-player appearance preferences. Native 2D weapons use the same defaults, with reduced tiling density for their flat silhouettes.

The 101 native sprite paint masks are packed losslessly as one-bit data with gzip under `META-INF/zombierool/visuals/sprite-masks.bin.gz`. The binary public verification key is under `META-INF/zombierool/rewards/public-key.der`. These resources load from the mod classpath rather than resource packs. This is compact storage and organization, not secrecy. No private signing key is distributed.

## Script Studio in Map Config > Scripting
The Scripting category is separate from match controls. It links directly to Guided rules, Code editor, API documentation and Live console.

Guided mode offers supported events, compatible conditions, several actions and an optional delay in seconds. Events include game start, wave start, zombie kill, block interaction and power activation. Actions include chat messages, channels, atmosphere pressurization, player points/weapons/voice, swimming, ending Outnumbered and global sound. Choices that need a player are available only on player events. Preview the generated Lua, then insert it into the script. The inserted rule preserves an earlier handler for the same event and remains normal editable Lua. Inserting does not save or execute it. Delayed repeated triggers reuse the rule's timer ID, replacing its previous pending timer.

Code mode retains indentation, mouse text selection, undo/redo, find/replace, Ctrl+S save, F5 check and Ctrl+Space API completion. F1 opens documentation for the latest API call before the cursor. API Help has named parameters, searchable topics and examples for common map tasks; an example can be inserted into Code. A syntax error with a line number moves the code cursor to that line. Run executes the buffer immediately; Reload resets runtime variables/timers and loads saved scripts. Save validates without executing. Files are stored in the current world's `zr_scripts` directory, with backups under `.history`. Concurrent external modifications are rejected instead of overwritten. Operator permission is required.

The guided catalog is a starting set of map operations. Code and the API reference provide access to the larger Lua API. More complex quest logic remains editable Lua. Lua callbacks have an instruction/time budget to interrupt accidental Lua loops. Java API calls are trusted map code; this is not an untrusted-script sandbox.

Voice hooks:
```lua
function OnVoiceLineRequested(uuid, event)
    -- false suppresses this event; nil keeps the usual voice selection.
    if event == "level_start" then return "zombierool:player_us_level_start" end
end
function OnVoiceLinePlayed(uuid, event, sound)
    print(uuid, event, sound)
end
-- ZombieroolAPI:playVoice(uuid, "namespace:sound")
-- ZombieroolAPI:playVoiceEvent(uuid, "level_start")
-- ZombieroolAPI:setPlayerVoicePreset(uuid, "preset_id")
```
Sound IDs must exist in an installed resource pack. Playback retains cooldown and recursion protection.

## Shared career
Profiles are compressed `.dat` files under `%USERPROFILE%/.zombierool/career_<Minecraft UUID>.dat`. Instances on the same computer and Windows account share the profile for the same Minecraft account. Different computers do not sync automatically. Legacy profiles migrate automatically and are retained. Atomic writes, a backup and a process lock protect writes; concurrent progression is merged. The format is storage, not anti-cheat encryption. Gameplay XP/ZRF rewards use a 2x multiplier; fixed redeem rewards keep their stated amounts.

## Signed reward codes: no hosting needed
The creator private key is `%USERPROFILE%/.zombierool/creator/reward-private.pem`. Back up this file privately; never publish it or include it in the JAR/Gist. The JAR contains only the public verification key. Do not run `init` again: a key already exists for this project. All future releases must keep that public key to accept existing codes.

The desktop folder “ZombieRool Creator” opens a graphical generator: select multiple camos, ZRF, XP, optional target UUID, optional dates and number of distinct codes. Copy the output or download JSON. It calls the same local Node signer; the private key never goes into the browser or JAR.

From the repository, with Node installed:
```powershell
node tools/reward-codes.mjs issue --camos camo_grotog,camo_sponsor_animated --target MINECRAFT_UUID --name "Sponsor" --out sponsor-codes.json
node tools/reward-codes.mjs issue --camos camo_halloween_animated,camo_horror_eye_animated --zrf 1000 --from 2026-10-25T00:00:00Z --until 2026-11-02T00:00:00Z --name "Halloween" --out halloween-event.json
```
Use exact catalog IDs. `--xp`, `--skins`, `--count` also work. Each generated code has a random unique ID and may contain several rewards. `--count` generates that many distinct codes; it is NOT a global redemption limit. The output file is never overwritten automatically. Send the `code` string privately to a sponsor. Binding to `--target` prevents use by another account in an unmodified client. Without a target, every player who obtains the code can claim it once in their local profile.

## Public seasonal events through GitHub Gist
Publish only public event JSON (the generated array of `name` / `code` objects). Copy the Gist's raw HTTPS URL into Career > Barracks > Events, then Refresh. Online requests must be enabled to fetch it; manually entered signed codes work offline. Only currently valid signed events are shown. No GitHub token or private key belongs in the mod. A secret/unlisted Gist is not a suitable secret store: anyone with its URL can read it.

A Gist publishes a list, not a redemption ledger. Removing an event hides it from the list but does not invalidate a code already copied. Offline expiration uses the computer clock, and local save files/client code remain modifiable. Offline codes cannot reliably enforce a global maximum use count, server-side revocation, or prevent local cheating. Those require an optional central HTTPS service with authenticated Minecraft accounts, a persistent database, atomic claims and server-controlled time. No such service is deployed, no subscription is enabled, and no payment is required for this offline/Gist implementation.

## Camos aléatoires et recherche

Le générateur permet d'ajouter un nombre de camos aléatoires par code et de choisir leur rareté. Le tirage a lieu à la génération, séparément pour chaque code, sans doublon avec les camos précis du même code. Un code partagé donne donc le même contenu à ses destinataires. Le tirage classique exclut les exclusifs, prestiges et maîtrises ; ces catégories restent sélectionnables explicitement. Le résultat de chaque tirage est affiché avant transmission. La recherche filtre effectivement la liste, sans distinction d'accents, par nom, identifiant ou rareté.


## Launch Halloween gift

The external reward generator includes a prepared Halloween + Horror Eye + 2,500 ZRF template. Enter the actual release date and select the template to calculate exactly 30 days of validity, including a November release. No date is assumed, and no campaign code is issued automatically. Generate and publish the signed event in the public Gist when the release date is confirmed. The template is recorded in tools/camo-studio/campaigns.json, outside the mod.

Opening an existing Lua file switches to Code and displays its source. Hidden Code widgets do not intercept Guided clicks. API documentation uses the game language (English by default, French guide for French).


## Editing and copying Lua

Code supports click-to-position, mouse drag selection and the standard text clipboard shortcuts. The Copy and Cut buttons use the selection, or the current whole line when nothing is selected; Paste replaces the selection at the cursor. Undo restores edits. Cursor position is retained when switching tabs. API help is read-only but selectable: Copy example copies only Lua, Copy selection copies the highlighted documentation, and Insert example inserts Lua at the retained code cursor. Search for a specific API method first. Plain text rendering keeps cursor and selection aligned, including wrapped and scrolled lines.


Bundled appearance, packed sprite masks and the binary public verification key live under META-INF/zombierool, outside the resource-pack asset namespace. This is organization and compact storage, not encryption. The public key is intentionally distributable and cannot create signatures. Existing issued vouchers retain the same key and remain valid. The creator private key is never moved or regenerated.


The Camo Studio is retired. Camo appearance settings now come directly from CamoAppearance.Profile and TacZCamoOverlay.patternScale in code, with all 99 former JSON profiles checked for exact equivalence. No appearance JSON or compressed appearance file is shipped. The Creator home keeps the reward-code generator; old studio pages and its save endpoint are disabled. Packed sprite masks and the public verification key remain bundled under META-INF/zombierool.
