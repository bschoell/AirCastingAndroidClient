package pl.llp.aircasting.sensor.hxm;

import pl.llp.aircasting.event.sensor.SensorEvent;
import pl.llp.aircasting.model.ExternalSensorDescriptor;
import pl.llp.aircasting.sensor.AbstractSensor;
import pl.llp.aircasting.sensor.Status;
import pl.llp.aircasting.util.Constants;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.google.common.eventbus.EventBus;
import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ConnectListenerImpl;
import zephyr.android.HxMBT.ConnectedEvent;
import zephyr.android.HxMBT.ZephyrPacketArgs;
import zephyr.android.HxMBT.ZephyrPacketEvent;
import zephyr.android.HxMBT.ZephyrPacketListener;
import zephyr.android.HxMBT.ZephyrProtocol;

import java.util.concurrent.atomic.AtomicBoolean;

public class HXMHeartBeatMonitor extends AbstractSensor
{
  Status status = Status.NOT_YET_STARTED;

  AtomicBoolean active = new AtomicBoolean(false);
  private BTClient client;
  public HxMConnectionListener listener;

  public HXMHeartBeatMonitor(ExternalSensorDescriptor descriptor, EventBus eventBus, BluetoothAdapter adapter)
  {
    super(descriptor, eventBus, adapter);
  }

  @Override
  public synchronized void start()
  {
    active.set(true);
    status = Status.STARTED;
    new Thread(new Runnable()
    {
      @Override
      public void run()
      {
        while (active.get())
        {
          try
          {
            listener = new HxMConnectionListener(messageHandler);
            client = connect();
            if (client != null)
            {
              client.addConnectedEventListener(listener);
              client.start();
            }
          }
          catch (InterruptedException e)
          {
            status = Status.DID_NOT_CONNECT;
            Log.e(Constants.TAG, "Failed to establish adapter connection", e);
            return;
          }
        }
      }
    }).start();
  }

  private BTClient connect() throws InterruptedException
  {
    BTClient obtainedClient = null;
    while (obtainedClient == null && active.get())
    {
      try
      {
        synchronized (adapter)
        {
          if (adapter.isDiscovering())
          {
            adapter.cancelDiscovery();
          }
        }
        obtainedClient = new BTClient(adapter, descriptor.getAddress());
      }
      catch (Exception e)
      {
        status = Status.DID_NOT_CONNECT;
        Log.e(Constants.TAG, "Couldn't connect to device [" + descriptor.getName() + ", " + descriptor
            .getAddress() + " ]", e);
        Thread.sleep(Constants.THREE_SECONDS);
      }
    }
    return obtainedClient;
  }

  public void stop()
  {
    active.set(false);
    status = Status.STOPPED;
    if (client != null && client.IsConnected())
    {
      client.removeConnectedEventListener(listener);
      client.Close();
    }
  }

  final Handler messageHandler = new Handler()
  {
    public void handleMessage(Message msg)
    {

    }
  };

  class HxMConnectionListener extends ConnectListenerImpl
  {
    private int HR_SPD_DIST_PACKET = 0x26;

    private HRSpeedDistPacketInfo HRSpeedDistPacket = new HRSpeedDistPacketInfo();

    public HxMConnectionListener(Handler handler)
    {
      super(handler, null);
    }

    public void Connected(ConnectedEvent<BTClient> eventArgs)
    {
      ZephyrProtocol _protocol = new ZephyrProtocol(eventArgs.getSource().getComms());
      _protocol.addZephyrPacketEventListener(new ZephyrPacketListener()
      {
        public void ReceivedPacket(ZephyrPacketEvent eventArgs)
        {
          ZephyrPacketArgs msg = eventArgs.getPacket();
          if (HR_SPD_DIST_PACKET == msg.getMsgID())
          {
            byte[] messageBytes = msg.getBytes();
            int heartRate = HRSpeedDistPacket.GetHeartRate(messageBytes);
            eventBus.post(EventMaker.event("Zephyr", "HxM", heartRate));
          }
        }
      });
    }
  }
}

class EventMaker
{
  static SensorEvent event(String packageName, String sensorName, double value)
  {
    return new SensorEvent(packageName, sensorName, "Heart Rate", "hz", "Hz", "HRT", 0, 10, 20, 30, 40, value);
  }

}