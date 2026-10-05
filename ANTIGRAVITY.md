# 📦 Dispensa - Project & Architecture Context for Antigravity

## 🎯 Panoramica del Progetto
**Dispensa** (`eu.frigo.dispensa`) è un'applicazione Android nativa per la gestione della dispensa domestica, il tracciamento delle scadenze alimentari per ridurre gli sprechi, la gestione di liste della spesa e la sincronizzazione decentralizzata multi-dispositivo (WebDAV, Google Drive, SAF locale).

---

## ☕ Ambiente Java & JDK Locale
- **JDK per compilazione ed esecuzione Gradle:** OpenJDK 23
- **Percorso JDK:** `/Users/alicevangelista/Library/Java/JavaVirtualMachines/openjdk-23.0.1` (o puntato tramite `JAVA_HOME=/Users/alicevangelista/Library/Java/JavaVirtualMachines/openjdk-23.0.1/Contents/Home`)
- **Target di compilazione bytecode:** Java 21 (`sourceCompatibility = 21`, `targetCompatibility = 21`, `jvmTarget = 21`)

---

## 🏗️ Stack Tecnologico & Requisiti

- **Linguaggi:** Java (predominante) + Kotlin
- **Android SDK:** `minSdk = 27`, `targetSdk = 36`, `compileSdk = 36`
- **Build System:** Gradle (Kotlin DSL `build.gradle.kts`) con Version Catalog (`gradle/libs.versions.toml`)
- **Architettura UI/App:** MVVM (ViewModel + LiveData / RxJava3), ViewBinding, Material Components 3, Android Navigation Component
- **Database & Persistenza:** Android Room (con migrazioni schemi SQLite esportati in `dbcore/schemas`)
- **Background Tasks:** Android WorkManager (`androidx.work`)
- **Networking:** Retrofit 2 + Gson, OkHttp (integrazione API OpenFoodFacts)
- **Scansione Barcode / OCR:**
  - Flavor `play`: Google Play Services ML Kit Barcode Scanning + Text Recognition
  - Flavor `fdroid`: ZXing Android Embedded (100% open-source)

---

## 🧩 Struttura Modulare

Il progetto adotta un'architettura multi-modulo pulita e disaccoppiata:

```text
dispensa/
├── app/          # Livello UI, Activity, Fragment, ViewModel, Adapter, Worker di notifica
├── dbcore/       # Room Database, Entità, DAO, Repository, Migrazioni, Backup e Cache OFF
├── sync-core/    # Motore di sincronizzazione astratto, modelli eventi/manifest, pairing crittografico
├── sync-webdav/  # Provider di sync per server WebDAV (Nextcloud, ownCloud, standard WebDAV)
├── sync-local/   # Provider di sync per cartelle locali / SAF (Storage Access Framework)
└── sync-gdrive/  # Provider di sync per Google Drive (esclusivo per flavor 'play')
```

### Dettaglio Moduli:
1. **`:app`**:
   - Gestione schermate (`MainActivity`, `DispensaManagerActivity`, `ShoppingListActivity`, `SharedDispenseActivity`, `ManageDevicesActivity`, `SettingsActivity`).
   - Schedulazione notifiche scadenze con `ExpiryCheckWorkerScheduler` / `ExpiryCheckWorker`.
   - UI personalizzata per la configurazione dei vari sync provider.

2. **`:dbcore`**:
   - **Entità principali:** `Dispensa`, `Product`, `StorageLocation`, `ShoppingItem`, `CategoryDefinition`, `ProductCategoryLink`, `SyncOutbox`, `JoinedPantryConfig`, `OpenFoodFactCacheEntity`.
   - **Repository:** `Repository.java` coordina l'accesso ai DAO e funge da ponte verso l'outbox di sincronizzazione.
   - **Cache OpenFoodFacts:** `OpenFoodFactCacheManager` per minimizzare le chiamate di rete esterne.
   - **Backup:** `BackupManager` e `PreMigrationBackupHelper` per import/export JSON e sicurezza dei dati.

3. **`:sync-core`**:
   - **Architettura Sync:** Motore decentralizzato basato su eventi (`PantryEvent`), manifest (`PantryManifest`) e snapshot (`PantrySnapshot`).
   - **Pairing & Sicurezza:** `CryptoEngine` e `ShareLinkCodec` per condivisione sicura di dispense tramite URL o QR Code (`zxing-core`).
   - **Coordination:** `SyncCoordinator`, `FolderSyncEngine`, `SyncScheduler`, gestione lifecycle e bus eventi `SyncBus`.

4. **Provider di Sincronizzazione (`:sync-webdav`, `:sync-local`, `:sync-gdrive`)**:
   - Implementano l'interfaccia `SharedFolderStore` / `SyncProvider`.
   - Disaccoppiati tramite `SyncProviderLoader` per mantenere modulare l'inclusione delle dipendenze.

---

## 🏷️ Product Flavors & Build Variants

Il progetto definisce la dimensione di flavor `store`:

| Flavor | Descrizione | Dipendenze Esclusive |
| :--- | :--- | :--- |
| **`play`** | Distribuito su Google Play Store | `:sync-gdrive`, Google Play Services Auth, ML Kit Barcode, ML Kit Text Recognition |
| **`fdroid`** | Distribuito su F-Droid (FOSS) | ZXing Android Embedded (senza dipendenze proprietarie Google) |

---

## 🛠️ Comandi Gradle Principali

Per eseguire i comandi specificando la JDK corretta se necessario:
```bash
JAVA_HOME=/Users/alicevangelista/Library/Java/JavaVirtualMachines/openjdk-23.0.1/Contents/Home ./gradlew <task>
```

### Build & Assemble
- Build Debug Play Store: `./gradlew assemblePlayDebug`
- Build Debug F-Droid: `./gradlew assembleFdroidDebug`
- Build Release Play Store: `./gradlew assemblePlayRelease`
- Build Release F-Droid: `./gradlew assembleFdroidRelease`

### Test & Lint
- Unit Test modulo App: `./gradlew :app:testPlayDebugUnitTest`
- Unit Test DB Core: `./gradlew :dbcore:test`
- Unit Test Sync Core: `./gradlew :sync-core:test`
- Esegui tutti i test: `./gradlew test`
- Lint check: `./gradlew lint`

---

## 🌐 Localizzazione & Risorse
L'app supporta attualmente 3 lingue con risorse stringhe in:
- `app/src/main/res/values/strings.xml` (Default: Inglese)
- `app/src/main/res/values-it/strings.xml` (Italiano)
- `app/src/main/res/values-de/strings.xml` (Tedesco)

---

## 📝 Linee Guida di Sviluppo per Agenti AI

1. **Modifiche al Database:**
   - Qualsiasi modifica alle entità in `dbcore` richiede l'aggiornamento della versione del database in `AppDatabase.java` e la creazione della relativa migrazione `Migration`.
   - Mantenere abilitato l'export dello schema Room (`room.schemaLocation`) per verificare la retrocompatibilità.
2. **Disaccoppiamento Sync:**
   - Non introdurre dipendenze dirette tra `:sync-core` e le librerie proprietarie di Google.
   - Il modulo `:sync-gdrive` deve restare collegato solo al flavor `play`.
3. **Gestione Threading & Concorrenza:**
   - Le operazioni su database e sync devono essere sempre asincrone (tramite RxJava3 `Schedulers.io()`, Executor o Coroutine se in Kotlin).
4. **Retrocompatibilità UI:**
   - Supportare temi Light e Dark (`values` vs `values-night`).
   - Utilizzare ViewBinding per interagire con i layout XML.
