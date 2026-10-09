# TacZ compatibility

The default development dependency is TacZ 1.20.1 / 1.1.8-hotfix2. Override with `-Ptacz_version=1.1.7-hotfix` to exercise the earlier build (the corresponding JAR must be in dev-libs).

Official current release: https://www.curseforge.com/minecraft/mc-mods/timeless-and-classics-zero/files/9037989

173 API calls, fields and mixin selectors/invocations were checked against each of these binaries:
- 1.1.7-hotfix
- 1.1.7-hotfix2
- 1.1.8-release
- 1.1.8-hotfix
- 1.1.8-hotfix2

The real development client starts with 1.1.8-hotfix2, Oculus, Embeddium and the installed gunpacks. Minecraft decodes the new mono electric loop; the GPU state for blue arcs disables culling and depth writes. Wall purchases, Speed Cola and Pack-a-Punch retain their existing integration hooks; no hook signatures required changes for these binaries. Future TacZ versions need the same checks before they can be considered supported.

The official maps dependency catalogue has been updated to the 1.1.8-hotfix2 file and direct download URL. The map publisher uses its own GitHub CLI authentication, independent of mod release publishing.
