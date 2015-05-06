package drake.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lcm.lcm.LCM;

import org.junit.Test;

public class LockFreeMessageMonitorTest {

  private static class LockFreeMessageReceiverTestTask implements Runnable {
    private final LockFreeMessageMonitor monitor = new LockFreeMessageMonitor();
    private long last_time_stamp = -1;
    private double difference_millis_check;
    private int messages_received = 0;

    public LockFreeMessageReceiverTestTask(String channel, double difference_millis_check) throws IOException {
      LCM.getSingleton().subscribe(channel, monitor);
      this.difference_millis_check = difference_millis_check;
    }

    @Override
    public void run() {
      byte[] byte_array = monitor.getMessage();
      if (byte_array != null) {
        drake.lcmt_body_wrench_data msg;
        try {
          msg = new drake.lcmt_body_wrench_data(byte_array);
          if (last_time_stamp > 0) {
            long difference_nanos = msg.timestamp - last_time_stamp;
            double difference_millis = difference_nanos / 1e6;
            // System.out.println("difference: " + difference_millis + " ms");
            assertTrue(msg.timestamp > last_time_stamp);
            if (!Double.isNaN(difference_millis_check) && messages_received > 0) {
              double tol_millis = 10;
              assertEquals(difference_millis, difference_millis_check, tol_millis);
            }
          }
          last_time_stamp = msg.timestamp;
          messages_received++;
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    public int getMessagesReceived() {
      return messages_received;
    }
  }

  @Test
  public void testPollFasterThanPublish() throws InterruptedException, IOException {
    long publish_period_millis = 100;
    long poll_period_millis = 1;
    long test_duration_millis = 5 * 1000;
    runTest(publish_period_millis, poll_period_millis, test_duration_millis);
  }

  @Test
  public void testPublishFasterThanPoll() throws InterruptedException, IOException {
    long publish_period_millis = 1;
    long poll_period_millis = 100;
    long test_duration_millis = 5 * 1000;
    runTest(publish_period_millis, poll_period_millis, test_duration_millis);
  }

  private void runTest(long publish_period_millis, long poll_period_millis, long test_duration_millis) throws IOException, InterruptedException {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    String channel = "TEST_CHANNEL";
    scheduler.scheduleAtFixedRate(new MessagePublisherTestTask(channel), 0, publish_period_millis, TimeUnit.MILLISECONDS);
    long num_iterations = test_duration_millis / poll_period_millis;
    double difference_millis_check = poll_period_millis > publish_period_millis ? poll_period_millis : Double.NaN;
    LockFreeMessageReceiverTestTask receiver_task = new LockFreeMessageReceiverTestTask(channel, difference_millis_check);
    for (int i = 0; i < num_iterations; i++) {
      receiver_task.run();
      Thread.sleep(poll_period_millis);
    }
    System.out.println("messages received: " + receiver_task.getMessagesReceived());
  }
}
