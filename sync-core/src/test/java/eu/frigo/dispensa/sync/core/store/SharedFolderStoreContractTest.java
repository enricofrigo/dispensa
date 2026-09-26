package eu.frigo.dispensa.sync.core.store;

import org.junit.Assert;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class SharedFolderStoreContractTest {

    protected SharedFolderStore createStore() {
        return new InMemorySharedFolderStore();
    }

    @Test
    public void testFolderCreationAndExistence() {
        SharedFolderStore store = createStore();
        store.ensureFolder("pantry-test/events/").blockingAwait();
        Assert.assertTrue(store.exists("pantry-test/events/").blockingGet());
    }

    @Test
    public void testAtomicWriteAndRead() {
        SharedFolderStore store = createStore();
        byte[] data = "{\"version\": 2, \"pantryName\": \"Cucina\"}".getBytes(StandardCharsets.UTF_8);
        store.writeAtomic("pantry-test/manifest.json", data, null).blockingAwait();

        Assert.assertTrue(store.exists("pantry-test/manifest.json").blockingGet());
        byte[] read = store.read("pantry-test/manifest.json").blockingGet();
        Assert.assertArrayEquals(data, read);
    }

    @Test
    public void testListFilesReturnsOnlyDirectFilesInDirectory() {
        SharedFolderStore store = createStore();
        store.writeAtomic("pantry-test/events/ev_1.json", "e1".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        store.writeAtomic("pantry-test/events/ev_2.json", "e2".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        store.writeAtomic("pantry-test/manifest.json", "m".getBytes(StandardCharsets.UTF_8), null).blockingAwait();

        List<String> files = store.listFiles("pantry-test/events/").blockingGet();
        Assert.assertEquals(2, files.size());
        Assert.assertTrue(files.contains("ev_1.json"));
        Assert.assertTrue(files.contains("ev_2.json"));
    }

    @Test
    public void testDeleteFileAndFolder() {
        SharedFolderStore store = createStore();
        store.writeAtomic("pantry-test/snapshots/snap_1.json", "s1".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        Assert.assertTrue(store.exists("pantry-test/snapshots/snap_1.json").blockingGet());

        store.deleteFile("pantry-test/snapshots/snap_1.json").blockingAwait();
        Assert.assertFalse(store.exists("pantry-test/snapshots/snap_1.json").blockingGet());

        store.writeAtomic("pantry-test/events/ev_1.json", "e1".getBytes(StandardCharsets.UTF_8), null).blockingAwait();
        store.deleteFolder("pantry-test/").blockingAwait();
        Assert.assertFalse(store.exists("pantry-test/events/ev_1.json").blockingGet());
    }
}
