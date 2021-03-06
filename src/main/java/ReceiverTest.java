import com.microsoft.azure.eventhubs.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReceiverTest {

    public static void main(String[] args)
            throws EventHubException, ExecutionException, InterruptedException, IOException {
        System.out.println("ReceiverTest");

        //Endpoint=sb://salla1.servicebus.windows.net/;SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=iNAHAuk8DgLhGCNMplyumXqnDPfQcYZcdEN81mOO4NE=

       // String connStr = "HostName=iothubsalla.azure-devices.net;DeviceId=a1;SharedAccessKey=7HV7/0wFCsQiRjAc/cJnIfXpTXZNe2irAsu1smsu4Jo=";
        final ConnectionStringBuilder connStr = new ConnectionStringBuilder()
                .setNamespaceName("salla1") // to target National clouds - use .setEndpoint(URI)
                .setEventHubName("salla1")
                .setSasKeyName("RootManageSharedAccessKey")
                .setSasKey("iNAHAuk8DgLhGCNMplyumXqnDPfQcYZcdEN81mOO4NE=");

        final ExecutorService executorService = Executors.newSingleThreadExecutor();
        final EventHubClient ehClient = EventHubClient.createSync(connStr.toString(), executorService);

        final EventHubRuntimeInformation eventHubInfo = ehClient.getRuntimeInformation().get();
        final String partitionId = eventHubInfo.getPartitionIds()[0]; // get first partition's id

        final PartitionReceiver receiver = ehClient.createEpochReceiverSync(
                EventHubClient.DEFAULT_CONSUMER_GROUP_NAME,
                partitionId,
                EventPosition.fromEnqueuedTime(Instant.EPOCH),
                2345);

        System.out.println("date-time receiver created...");

        try {
            int receivedCount = 0;
            while (receivedCount++ < 100) {
                receiver.receive(100)
                        .thenAcceptAsync(receivedEvents -> {
                            int batchSize = 0;
                            if (receivedEvents != null) {
                                for (EventData receivedEvent : receivedEvents) {
                                    System.out.print(String.format("Offset: %s, SeqNo: %s, EnqueueTime: %s",
                                            receivedEvent.getSystemProperties().getOffset(),
                                            receivedEvent.getSystemProperties().getSequenceNumber(),
                                            receivedEvent.getSystemProperties().getEnqueuedTime()));

                                    if (receivedEvent.getBytes() != null)
                                        System.out.println(String.format("| Message Payload: %s", new String(receivedEvent.getBytes(), Charset.defaultCharset())));
                                    batchSize++;
                                }
                            }

                            System.out.println(String.format("ReceivedBatch Size: %s", batchSize));
                        }, executorService).get();
            }
        } finally {
            // cleaning up receivers is paramount;
            // Quota limitation on maximum number of concurrent receivers per consumergroup per partition is 5
            receiver.close()
                    .thenComposeAsync(aVoid -> ehClient.close(), executorService)
                    .whenCompleteAsync((t, u) -> {
                        if (u != null) {
                            // wire-up this error to diagnostics infrastructure
                            System.out.println(String.format("closing failed with error: %s", u.toString()));
                        }
                    }, executorService).get();

            executorService.shutdown();
        }

    }

}
