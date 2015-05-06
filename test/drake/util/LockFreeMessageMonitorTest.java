package drake.util;

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
    
    public LockFreeMessageReceiverTestTask(String channel) throws IOException {
      LCM.getSingleton().subscribe(channel, monitor);
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
            assertTrue(msg.timestamp >= last_time_stamp);
            // assertEquals(difference_millis, period, 20.0);
            System.out.println("difference: " + difference_millis + " ms");
          }
          last_time_stamp = msg.timestamp;
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  @Test
  public void test() throws InterruptedException, IOException {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    int publish_period_millis = 1;
    int poll_period_millis = 30;
    String channel = "TEST_CHANNEL";
    scheduler.scheduleAtFixedRate(new MessagePublisherTestTask(channel), 0, publish_period_millis, TimeUnit.MILLISECONDS);
    scheduler.scheduleAtFixedRate(new LockFreeMessageReceiverTestTask(channel), 0, poll_period_millis, TimeUnit.MILLISECONDS);

    long test_duration_millis = 5 * 1000;
    Thread.sleep(test_duration_millis);
  }
}
