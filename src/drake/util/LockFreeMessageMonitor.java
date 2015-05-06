package drake.util;

import java.io.IOException;

import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;

public class LockFreeMessageMonitor implements LCMSubscriber {

  ConcurrentCopier<LCMMessageData> copier;
  
  public LockFreeMessageMonitor() {
    copier = new ConcurrentCopier<LCMMessageData>(new LCMMessageDataBuilder());
  }

  @Override
  public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins) {
    LCMMessageData data = copier.getCopyForWriting();
    int available = ins.available();
    if (data.byte_array == null || data.byte_array.length != available) { // TODO: is it OK to have a byte array that is too large?
      data.byte_array = new byte[available];
    }
    try {
      ins.readFully(data.byte_array);
    } catch (IOException e) {
      System.err.println("MultipleMessageMonitor exception on channel " + channel);
      e.printStackTrace();
    }
    copier.commit();
  }

  public byte[] getMessage() {
    LCMMessageData data = copier.getCopyForReading();
    if (data == null)
      return null; // could also return null if copy for reading has already been returned once (just add a private 'last_data_copied' field to compare against)
    else
      return data.byte_array;
  }

  private static class LCMMessageData {
    public byte[] byte_array;
  }

  private static class LCMMessageDataBuilder implements Builder<LCMMessageData> {

    @Override
    public LCMMessageData newInstance() {
      return new LCMMessageData();
    }
  }
}
