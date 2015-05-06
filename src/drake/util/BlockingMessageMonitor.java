package drake.util;

import java.io.IOException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;

public class BlockingMessageMonitor implements LCMSubscriber {
  private boolean new_data_available = false;
  private byte[] byte_array;
  final Lock lock = new ReentrantLock();
  final Condition new_data = lock.newCondition();

  public BlockingMessageMonitor() {
  }

  @Override
  public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins) {
    lock.lock();
    try {
      int available = ins.available();
      // TODO: is it OK to have a byte array that is too large?
      if (byte_array == null || byte_array.length != available) {
        byte_array = new byte[available];
      }
      try {
        ins.readFully(byte_array);
      } catch (IOException e) {
        System.err.println("MultipleMessageMonitor exception on channel " + channel);
        e.printStackTrace();
      }

      new_data_available = true;
      new_data.signal();
    } finally {
      lock.unlock();
    }
  }

  public byte[] getMessage() {
    try {
      lock.lock();
      while (!new_data_available) {
        new_data.await();
      }
      new_data_available = false;
      return byte_array;
    } catch (InterruptedException e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    } finally {
      lock.unlock();
    }
    return null;
  }
}
