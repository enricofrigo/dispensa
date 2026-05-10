# CRDT Sync Integration Test Scenarios

This document outlines the manual validation steps required to verify the new multi-transport CRDT synchronization infrastructure.

---

## 1. FRESH INSTALL (no prior data)
- [ ] App starts without crashes.
- [ ] Sync preferences are visible in **Settings**.
- [ ] "**Sync Now**" button is visible and clickable.
- [ ] `SyncWorker` is scheduled successfully (verify via logcat: `SYNC_WORKER`).
- [ ] `sync_changes` table is created in the database.
- [ ] No data loss or unexpected resets occur on application restart.

## 2. UPGRADE FROM 0.1.11 (Legacy version)
- [ ] Database migration from version 9 (or current) to 13 succeeds silently.
- [ ] Existing data in **Products**, **Categories**, and **Locations** is preserved.
- [ ] `SyncOutbox` legacy data is **NOT** migrated immediately (migration should only trigger on the first WebDAV sync).
- [ ] App starts normally post-upgrade.
- [ ] Existing sync preferences (if any) are preserved.

## 3. LOCAL NETWORK SYNC (mDNS + TCP)
- [ ] Two devices on the same WiFi discover each other via mDNS (verify via logcat: `LocalSyncTransport`).
- [ ] Manual "**Sync Now**" establishes a TCP connection between peers.
- [ ] Changes made on Device A (e.g., adding a product) appear on Device B after sync.
- [ ] No duplicate entries are created for the same data.
- [ ] Lamport clock increments correctly in the `sync_changes` table.

## 4. GOOGLE DRIVE SYNC (Solo Mode)
- [ ] Google Sign-In button appears in Settings (**Play flavor only**).
- [ ] OAuth flow completes successfully.
- [ ] Sync file (`.dispensa_sync_changes.json`) is uploaded to the hidden `appDataFolder` on Drive.
- [ ] Changes are synchronized bidirectionally between the device and the cloud.
- [ ] Automatic retry logic handles 429 (Rate Limit) errors correctly.

## 5. GOOGLE DRIVE HOUSEHOLD (Shared Mode)
- [ ] "**Create Household**" option/dialog appears in sync settings.
- [ ] Shared folder (e.g., "Dispensa MyHome") is created at the Drive root.
- [ ] Deep-link/QR code for the household is generated successfully.
- [ ] A second device joins the household via the deep-link.
- [ ] Changes from all member devices are merged correctly into the shared dataset.
- [ ] All member devices see the full, synchronized dataset.

## 6. MULTI-TRANSPORT ORCHESTRATION
- [ ] WebDAV, Local Network, and Google Drive transports all run within the same `SyncWorker` pass.
- [ ] No conflicts occur when multiple transports have pending changes.
- [ ] A failure in one transport (e.g., WebDAV offline) does **not** block others from completing.
- [ ] The final local database state is consistent across all active transports.

## 7. RETROCOMPATIBILITY & MIGRATION
- [ ] **Legacy Read**: A device running v1.0 (legacy WebDAV) can still read the new hybrid manifest (v1.1).
- [ ] **New Read**: A device running v1.1 can successfully read and process an old v1.0 manifest.
- [ ] **Outbox Migration**: The first successful WebDAV sync correctly converts `SyncOutbox` entries into `sync_changes`.
- [ ] No duplicate sync events are generated after the migration completes.

## 8. CONFLICT RESOLUTION (Lamport Clock LWW)
- [ ] **Scenario**: Device A deletes a product at `clock=10`. Device B updates the same product at `clock=8`.
- [ ] **Action**: Synchronize both devices.
- [ ] **Verification**: Device A's delete wins (higher clock value).
- [ ] **Result**: The product is deleted on Device B.
