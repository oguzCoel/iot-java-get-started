package ch.quickline.business;

import com.google.gson.Gson;
import com.microsoft.azure.iothub.*;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Hello world!
 *
 */
public class App {
    private static String connString = "HostName=IotT.azure-devices.net;DeviceId=myFirstJavaDevice;SharedAccessKey=QFh6SLy8Onr1Je4ALxCGwQ==";
    private static IotHubClientProtocol protocol = IotHubClientProtocol.AMQPS;
    private static String deviceId = "myFirstJavaDevice";
    private static DeviceClient client;

    private static class TelemetryDataPoint {
        public String deviceId;
        public double windSpeed;

        public String serialize() {
            Gson gson = new Gson();
            return gson.toJson(this);
        }
    }

    private static class EventCallback implements IotHubEventCallback
    {
        public void execute(IotHubStatusCode status, Object context) {
            System.out.println("IoT Hub responded to message with status: " + status.name());

            if (context != null) {
                synchronized (context) {
                    context.notify();
                }
            }
        }
    }

    private static class MessageSender implements Runnable {
        public volatile boolean stopThread = false;

        public void run()  {
            try {
                double avgWindSpeed = 10; // m/s
                Random rand = new Random();

                while (!stopThread) {
                    double currentWindSpeed = avgWindSpeed + rand.nextDouble() * 4 - 2;
                    TelemetryDataPoint telemetryDataPoint = new TelemetryDataPoint();
                    telemetryDataPoint.deviceId = deviceId;
                    telemetryDataPoint.windSpeed = currentWindSpeed;

                    String msgStr = telemetryDataPoint.serialize();
                    Message msg = new Message(msgStr);
                    System.out.println("Sending: " + msgStr);

                    Object lockobj = new Object();
                    EventCallback callback = new EventCallback();
                    client.sendEventAsync(msg, callback, lockobj);

                    synchronized (lockobj) {
                        lockobj.wait();
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e) {
                System.out.println("Finished.");
            }
        }
    }


    public static void main( String[] args ) throws IOException, URISyntaxException {
        client = new DeviceClient(connString, protocol);
        client.open();

        MessageSender sender = new MessageSender();

        ExecutorService executor = Executors.newFixedThreadPool(1);
        executor.execute(sender);

        System.out.println("Press ENTER to exit.");
        System.in.read();
        executor.shutdownNow();
        client.close();
    }
}
