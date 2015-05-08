package drake.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lcm.lcm.LCM;

import org.junit.Test;

public class LockFreeMessageMonitorTest {

  private static class LockFreeMessageReceiverTestTask implements Runnable {
    private final LockFreeMessageMonitor monitor = new LockFreeMessageMonitor();
    private final Map<String, Long> last_timestamps = new HashMap<String, Long>();
    private int messages_received = 0;
    private final Map<String, Double> differences_millis_check;

    public LockFreeMessageReceiverTestTask(Iterable<String> channels, Map<String, Double> differences_millis_check) throws IOException {
      for (String channel : channels) {
        LCM.getSingleton().subscribe(channel, monitor);
        last_timestamps.put(channel, -1l);
      }
      this.differences_millis_check = differences_millis_check;
    }

    @Override
    public void run() {
      Map<String, byte[]> byte_arrays = monitor.getMessages();
      for (String channel : byte_arrays.keySet()) {
        byte[] byte_array = byte_arrays.get(channel);
        if (byte_array != null) {
          drake.lcmt_body_wrench_data msg;
          try {
            msg = new drake.lcmt_body_wrench_data(byte_array);
            long last_timestamp = last_timestamps.get(channel);
            if (last_timestamp > 0) {
              long difference_nanos = msg.timestamp - last_timestamp;
              double difference_millis = difference_nanos / 1e6;
              // System.out.println("difference: " + difference_millis + " ms");
              assertTrue(msg.timestamp > last_timestamp);
              double difference_millis_check = differences_millis_check.get(channel);
              if (!Double.isNaN(difference_millis_check) && messages_received > 0) {
                double tol_millis = 10;
                assertEquals(difference_millis, difference_millis_check, tol_millis);
              }
            }
            last_timestamp = msg.timestamp;
            messages_received++;
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    }

    public int getMessagesReceived() {
      return messages_received;
    }
  }

  @Test
  public void testPollFasterThanPublish() throws InterruptedException, IOException {
    Map<String, Long> publish_periods_millis = new HashMap<String, Long>();
    int num_channels = 5;
    for (int i = 0; i < num_channels; i++) {
      String channel = "TEST_CHANNEL_" + i;
      publish_periods_millis.put(channel, 100 + (i + 1) * 50l);
    }
    
    long poll_period_millis = 1;
    long test_duration_millis = 5 * 1000;
    runTest(publish_periods_millis, poll_period_millis, test_duration_millis);
  }

  @Test
  public void testPublishFasterThanPoll() throws InterruptedException, IOException {
    Map<String, Long> publish_periods_millis = new HashMap<String, Long>();
    int num_channels = 5;
    for (int i = 0; i < num_channels; i++) {
      String channel = "TEST_CHANNEL_" + i;
      publish_periods_millis.put(channel, (i + 1) * 1l);
    }
    long poll_period_millis = 100;
    long test_duration_millis = 5 * 1000;
    runTest(publish_periods_millis, poll_period_millis, test_duration_millis);
  }

  private void runTest(Map<String, Long> publish_periods_millis, long poll_period_millis, long test_duration_millis) throws IOException, InterruptedException {
    Set<String> channels = publish_periods_millis.keySet();


    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(channels.size());
    for (String channel : channels) {
      scheduler.scheduleAtFixedRate(new MessagePublisherTestTask(channel), 0, publish_periods_millis.get(channel), TimeUnit.MILLISECONDS);
    }

    long num_iterations = test_duration_millis / poll_period_millis;
    Map<String, Double> differences_millis_check = new HashMap<String, Double>();
    for (String channel : channels) {
      differences_millis_check.put(channel, poll_period_millis > publish_periods_millis.get(channel) ? poll_period_millis : Double.NaN);
    }
    LockFreeMessageReceiverTestTask receiver_task = new LockFreeMessageReceiverTestTask(channels, differences_millis_check);
    for (int i = 0; i < num_iterations; i++) {
      receiver_task.run();
      Thread.sleep(poll_period_millis);
    }
    System.out.println("messages received: " + receiver_task.getMessagesReceived());
  }
}
