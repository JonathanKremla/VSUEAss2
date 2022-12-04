package dslab.util.datastructures;


import java.util.LinkedList;
import java.util.Queue;

/**
 * A Queue for the Producer-Consumer Problem
 */
public class DataQueue {
  private final Queue<Email> queue = new LinkedList<>();
  private final int maxSize;
  private final Object FULL = new Object();
  private final Object EMPTY = new Object();

  public DataQueue(int maxSize) {
    this.maxSize = maxSize;
  }

  public Email poll() {
    return queue.poll();
  }

  public Email peek() {
    return queue.peek();
  }

  public boolean add(Email email) {
    return queue.add(email);
  }

  public void waitOnFull() throws InterruptedException {
    synchronized (FULL) {
      FULL.wait();
    }
  }

  public void notifyAllForFull() {
    synchronized (FULL) {
      FULL.notifyAll();
    }
  }

  public void notifyAllForEmpty() {
    synchronized (EMPTY) {
      EMPTY.notifyAll();
    }
  }

  public void waitOnEmpty() throws InterruptedException {
    synchronized (EMPTY) {
      EMPTY.wait();
    }
  }

  public boolean isFull() {
    return queue.size() == maxSize;
  }

  public boolean isEmpty() {
    return queue.size() == 0;
  }
}
