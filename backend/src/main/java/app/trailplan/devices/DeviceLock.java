package app.trailplan.devices;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * One device conversation at a time: MTP allows only one program to use a device, and a sync plan
 * must stay valid while it is carried out.
 */
@Component
public class DeviceLock {

  private final ReentrantLock lock = new ReentrantLock();

  /**
   * Runs the task, unless another device task is running.
   *
   * @throws UsbException if one is
   */
  public <T> T run(Supplier<T> task) {
    if (!lock.tryLock()) {
      throw UsbException.running();
    }
    try {
      return task.get();
    } finally {
      lock.unlock();
    }
  }
}
