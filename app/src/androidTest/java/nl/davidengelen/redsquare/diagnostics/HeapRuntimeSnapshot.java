package nl.davidengelen.redsquare.diagnostics;

/** Read-only standalone VM observation on a disposable emulator, never an app entry point. */
public final class HeapRuntimeSnapshot {
    public static void main(String[] arguments) {
        Runtime runtime = Runtime.getRuntime();
        System.out.println("{\"maxBytes\":" + runtime.maxMemory()
            + ",\"totalBytes\":" + runtime.totalMemory()
            + ",\"freeBytes\":" + runtime.freeMemory() + "}");
    }
}
