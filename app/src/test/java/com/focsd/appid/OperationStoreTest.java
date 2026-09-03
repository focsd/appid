package com.focsd.appid;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OperationStoreTest {
    @Test
    public void removeStreamedOutputRemovesOnlyDeliveredOccurrences() {
        String complete = "download one\nReading package lists...\nReading package lists...\ndone\n";
        String streamed = "download one\nReading package lists...\n";

        assertEquals(
                "Reading package lists...\ndone\n",
                OperationStore.removeStreamedOutput(complete, streamed));
    }

    @Test
    public void removeStreamedOutputPreservesUnstreamedTranscript() {
        assertEquals(
                "first\nsecond\n",
                OperationStore.removeStreamedOutput("first\nsecond\n", "unrelated\n"));
    }
}
