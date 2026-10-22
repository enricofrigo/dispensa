---
name: release
description: "Automatizza la creazione di una nuova release di Dispensa (F-Droid e Play): genera i changelog Fastlane in it-IT e en-US, aggiorna gli screenshot UI con Robolectric Native (FastlaneScreenshotsTest), fa bump versione/tag/push e aggiorna eu.frigo.dispensa.yml su fdroiddata (con modalità --dry-run preventiva)."
---

# Dispensa Release Workflow Skill

Usa questa skill quando l'utente chiede di preparare, testare (dry-run) o pubblicare una nuova release di **Dispensa**.

## Requisiti da `GEMINI.md`
- **JDK**: `JAVA_HOME=/Users/alicevangelista/Library/Java/JavaVirtualMachines/openjdk-23.0.1/Contents/Home` (OpenJDK 23, configurato automaticamente in `scripts/release.sh`).
- **Flavors**: `fdroid` (FOSS, senza `:sync-gdrive`) e `play` (con `:sync-gdrive` e ML Kit).
- **Localizzazione**: `en` (`values`), `it` (`values-it`), `de` (`values-de`).

## Flusso Operativo Obbligatorio

### 1. Analisi modifiche dall'ultimo tag
1. Individua l'ultimo tag e la versione corrente in `app/build.gradle.kts`:
   ```bash
   git describe --tags --abbrev=0
   ```
2. Controlla i commit e le modifiche UI dall'ultimo tag:
   ```bash
   git log $(git describe --tags --abbrev=0)..HEAD --oneline
   git diff --stat $(git describe --tags --abbrev=0)..HEAD -- app/src/main/res/layout/ app/src/main/java/eu/frigo/dispensa/activity/ app/src/main/java/eu/frigo/dispensa/ui/
   ```
3. Se sono state introdotte nuove schermate (`Activity` o `Fragment`) o modifiche visive importanti:
   - Aggiorna [FastlaneScreenshotsTest.java](file:///Users/alicevangelista/AndroidStudioProjects/dispensa/app/src/test/java/eu/frigo/dispensa/screenshots/FastlaneScreenshotsTest.java) per includere la cattura della nuova UI in `it-IT` e `en-US`.

### 2. Scrittura Changelog Fastlane (`it-IT` e `en-US`)
1. Calcola il nuovo `versionCode` (`CURRENT_VERSION_CODE + 1`).
2. Scrivi i due file changelog sintetici (stile elenco puntato `• ...`, **massimo 500 byte** ciascuno):
   - `fastlane/metadata/android/it-IT/changelogs/<nuovoVersionCode>.txt` (in italiano)
   - `fastlane/metadata/android/en-US/changelogs/<nuovoVersionCode>.txt` (in inglese)

### 3. Esecuzione in modalità `--dry-run` (Sempre prima del rilascio reale!)
Esegui lo script in modalità dry-run:
```bash
./scripts/release.sh --dry-run <nuova_versione>
```
*(Usa `--skip-screenshots` se non ci sono modifiche UI e l'utente non richiede di rigenerare gli screenshot, `--flavor both` per verificare sia `fdroid` che `play`, oppure `--revert-dry-run` se vuoi ripristinare subito i file dopo aver mostrato i diff).*

Mostra all'utente:
- Gli screenshot generati in `fastlane/metadata/android/{it-IT,en-US}/images/phoneScreenshots/`
- I testi dei changelog `it-IT` e `en-US`
- Il `git diff` di `app/build.gradle.kts` e di `/Users/alicevangelista/AndroidStudioProjects/fdroiddata/metadata/eu.frigo.dispensa.yml`

### 4. Rilascio Reale (Solo dopo conferma esplicita dell'utente)
Quando l'utente approva l'esito del dry-run, esegui:
```bash
./scripts/release.sh --yes <nuova_versione>
```
Questo effettuerà commit, tag e push su `dispensa` e creerà il branch con commit e push su `fdroiddata`.
