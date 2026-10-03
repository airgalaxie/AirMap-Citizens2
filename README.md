# AirMap-Citizens2

## English

Shows NPCs from Citizens as markers on the web map – position and name are updated continuously.
Runs on AirMap and on Dynmap, both provide the same marker API.

### Changes from the original

- Marker icons included, one per NPC group, switchable in the config
- NPCs in unloaded chunks keep their marker at the last known position
- Citizens' internal helper NPCs (sit trait, animations, hologram lines) are not shown
- Marker layer renamed from `Citizens2` to `AirMap-Citizens2`, the old layer can be deleted after the update
- Build ported to Gradle with all versions managed centrally
- Migrated to the current Dynmap Core API and a modern Spigot API
- Legacy Maven version kept on branch `legacy`, active development on `main`

Build with `./gradlew build`.

Thanks to mikeprimm for the original plugin.

Code is licensed under the Apache License 2.0, see `LICENSE` and `NOTICE`.

---

## Deutsch

Zeigt NPCs aus Citizens als Marker auf der Web-Karte – Position und Name werden laufend aktualisiert.
Läuft auf AirMap und auf Dynmap, beide bieten dieselbe Marker-API.

### Änderungen gegenüber dem Original

- Marker-Icons mitgeliefert, eines je NPC-Gruppe, in der Config umschaltbar
- NPCs in nicht geladenen Chunks behalten ihren Marker an der zuletzt bekannten Position
- Interne Hilfs-NPCs von Citizens (Sitz-Trait, Animationen, Holo-gramm-Zeilen) werden nicht angezeigt
- Marker-Ebene von `Citizens2` in `AirMap-Citizens2` umbenannt, die alte Ebene kann nach dem Update gelöscht werden
- Build auf Gradle umgestellt, alle Versionsangaben zentral verwaltet
- Auf die aktuelle Dynmap-Core-API und moderne Spigot-API migriert
- Alte Maven-Version bleibt als Branch `legacy` erhalten, aktuelle Entwicklung läuft auf `main`

Build mit `./gradlew build`.

Dank an mikeprimm für das ursprüngliche Plugin.

Der Code steht unter der Apache License 2.0, siehe `LICENSE` und `NOTICE`.