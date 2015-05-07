package drake.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import lcm.lcm.LCM;

import org.junit.Test;

public class BlockingMessageMonitorTest {
  
  private static boolean DEBUG = false;

  @Test
  public void test() throws IOException {
    List<String> channels = new ArrayList<String>();
    Map<String, Long> publish_periods_millis = new HashMap<String, Long>();
    int num_channels = 5;
    for (int i = 0; i < num_channels; i++) {
      String channel = "TEST_CHANNEL_" + i;
      channels.add(channel);
      publish_periods_millis.put(channel, (i + 1) * 10l);
    }

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(num_channels);
    for (String channel : channels) {
      scheduler.scheduleAtFixedRate(new MessagePublisherTestTask(channel), 0, publish_periods_millis.get(channel), TimeUnit.MILLISECONDS);
    }

    BlockingMessageMonitor monitor = new BlockingMessageMonitor();
    for (String channel : channels) {
      LCM.getSingleton().subscribe(channel, monitor);
    }

    Map<String, Long> last_timestamps = new HashMap<String, Long>();
    for (String channel : channels) {
      last_timestamps.put(channel, -1l);
    }

    int num_polls = 2000;
    for (int i = 0; i < num_polls; i++) {
      Map<String, byte[]> byte_arrays = monitor.getMessages(1000l);
      if (byte_arrays != null) {
        if (DEBUG)
          System.out.println("num new messages: " + byte_arrays.size());
        for (String channel : channels) {
          byte[] byte_array = byte_arrays.get(channel);
          if (byte_array != null) {
            drake.lcmt_body_wrench_data msg = new drake.lcmt_body_wrench_data(byte_array);
            long last_timestamp = last_timestamps.get(channel);
            if (last_timestamp > 0) {
              assertTrue(msg.timestamp > last_timestamp);
              long difference_nanos = msg.timestamp - last_timestamp;
              double difference_millis = difference_nanos / 1e6;
              assertEquals(publish_periods_millis.get(channel), difference_millis, 10.0);
              if (DEBUG)
                System.out.println("timestamp difference: " + difference_millis + " ms");
            }
            last_timestamps.put(channel, msg.timestamp);
          }
        }
        if (DEBUG)
          System.out.println();
      }
    }
  }
  
  @Test
  public void testTimeout() {
    BlockingMessageMonitor monitor = new BlockingMessageMonitor();
    long timeout_millis = 1000l;
    long start_time_nanos = System.nanoTime();
    monitor.getMessages(timeout_millis);
    long elapsed_time_nanos = System.nanoTime() - start_time_nanos;
    long elapsed_time_millis = TimeUnit.MILLISECONDS.convert(elapsed_time_nanos, TimeUnit.NANOSECONDS);
    assertEquals((double) timeout_millis, (double) elapsed_time_millis, 20.0);
  }
}
