package drake.util;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lcm.lcm.LCM;

import org.junit.Test;

public class BlockingMessageMonitorTest {

  @Test
  public void test() throws IOException {
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    int publish_period_millis = 100;
    String channel = "TEST_CHANNEL";
    scheduler.scheduleAtFixedRate(new MessagePublisherTestTask(channel), 0, publish_period_millis, TimeUnit.MILLISECONDS);

    BlockingMessageMonitor monitor = new BlockingMessageMonitor();
    LCM.getSingleton().subscribe(channel, monitor);
    long last_time_stamp = -1;
    int num_messages = 20;
    for (int i = 0; i < num_messages; i++) {
      byte[] byte_array = monitor.getMessage();
      if (byte_array != null) {
        drake.lcmt_body_wrench_data msg;
        try {
          msg = new drake.lcmt_body_wrench_data(byte_array);
          if (last_time_stamp > 0) {
            long difference_nanos = msg.timestamp - last_time_stamp;
            double difference_millis = difference_nanos / 1e6;
            assertTrue(msg.timestamp >= last_time_stamp);
            assertEquals(publish_period_millis, difference_millis, 20.0);
            System.out.println("difference: " + difference_millis + " ms");
          }
          last_time_stamp = msg.timestamp;
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }
}
