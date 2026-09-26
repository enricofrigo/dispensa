package eu.frigo.dispensa.sync.local.store;

import android.content.Context;
import androidx.documentfile.provider.DocumentFile;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class SafFolderStoreContractTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private SafFolderStore folderStore;

    @Before
    public void setup() throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        File rootDir = temporaryFolder.newFolder("saf_shared_sync");
        DocumentFile rootDoc = DocumentFile.fromFile(rootDir);
        folderStore = new SafFolderStore(context, rootDoc);
    }

    @Test
    public void testFolderCreationAndExistence() {
        folderStore.ensureFolder("pantry-saf-test/events/").blockingAwait();
        Assert.assertTrue(folderStore.exists("pantry-saf-test/events/").blockingGet());
    }

    @Test
    public void testAtomicWriteAndRead() {
        byte[] data = "{\"version\": 2, \"pantryName\": \"Cucina SAF\"}".getBytes(StandardCharsets.UTF_8);
        folderStore.writeAtomic("pantry-saf-test/manifest.json", data, null).blockingAwait();

        Assert.assertTrue(folderStore.exists("pantry-saf-test/manifest.json").blockingGet());
        byte[] read = folderStore.read("pantry-saf-test/manifest.json").blockingGet();
        Assert.assertArrayEquals(data, read);
    }

    @Test
    public void testListFilesReturnsFilesInDirectory() {
        folderStore.writeAtomic("pantry-saf-test/events/ev_1.json", "e1".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        folderStore.writeAtomic("pantry-saf-test/events/ev_2.json", "e2".getBytes(StandardCharsets.UTF_8), null).blockingAwait();

        List<String> files = folderStore.listFiles("pantry-saf-test/events/").blockingGet();
        Assert.assertEquals(2, files.size());
        Assert.assertTrue(files.contains("ev_1.json"));
        Assert.assertTrue(files.contains("ev_2.json"));
    }

    @Test
    public void testDeleteFileAndFolder() {
        folderStore.writeAtomic("pantry-saf-test/snapshots/snap_1.json", "s1".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        Assert.assertTrue(folderStore.exists("pantry-saf-test/snapshots/snap_1.json").blockingGet());

        folderStore.deleteFile("pantry-saf-test/snapshots/snap_1.json").blockingAwait();
        Assert.assertFalse(folderStore.exists("pantry-saf-test/snapshots/snap_1.json").blockingGet());
    }
}
