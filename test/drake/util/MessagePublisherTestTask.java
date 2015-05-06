package drake.util;

import java.io.IOException;

import lcm.lcm.LCM;

class MessagePublisherTestTask implements Runnable {
  private final LCM lcm = new LCM();
  private final String channel;

  public MessagePublisherTestTask(String channel) throws IOException {
    this.channel = channel;
  }

  @Override
  public void run() {
    drake.lcmt_body_wrench_data msg = new drake.lcmt_body_wrench_data();
    msg.timestamp = System.nanoTime();
    msg.wrench = new double[6];
    for (int i = 0; i < msg.wrench.length; i++) {
      msg.wrench[i] = Math.random();
    }

    lcm.publish(channel, msg);
  }
}