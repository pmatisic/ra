package org.ra.algoritmi;

import java.io.IOException;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
  private final int id;
  private final AtomicInteger brojZahtjeva;
  private final ConcurrentHashMap<Integer, Boolean> odgovori;
  private final ConcurrentLinkedQueue<Integer> zahtjeviRed;
  private volatile boolean ulazakUKriticniOdsjek;
  private volatile CountDownLatch dozvolaUlaska;
  private static final int PORT = 12345;
  private final int brojProcesa;

  public Server(int id, int brojProcesa) {
    this.id = id;
    this.brojProcesa = brojProcesa;
    this.brojZahtjeva = new AtomicInteger();
    this.odgovori = new ConcurrentHashMap<>();
    this.zahtjeviRed = new ConcurrentLinkedQueue<>();
    this.ulazakUKriticniOdsjek = false;
  }

  public void posaljiZahtjev(int procesId, int posiljateljId) {
    try (SocketChannel socket = poveziSe(procesId)) {
      ByteBuffer buffer = ByteBuffer.allocate(8);
      buffer.putInt(posiljateljId);
      buffer.putInt(brojZahtjeva.get());
      buffer.flip();
      socket.write(buffer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public SocketChannel poveziSe(int procesId) throws IOException {
    SocketChannel socket = SocketChannel.open();
    InetSocketAddress adresa = new InetSocketAddress("localhost", PORT + procesId * 2);
    while (true) {
      try {
        socket.connect(adresa);
        break;
      } catch (ConnectException e) {
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      } catch (AsynchronousCloseException e) {
        System.err.println(
            "AsynchronousCloseException se dogodio pri uspostavljanju veze s procesom " + procesId);
        throw e;
      }
    }
    return socket;
  }

  public void udiUKriticniOdsjek() {
    brojZahtjeva.incrementAndGet();
    zahtjeviRed.add(id);
    while (true) {
      ulazakUKriticniOdsjek = true;
      dozvolaUlaska = new CountDownLatch(1);
      for (int i = 0; i < brojProcesa; i++) {
        if (i == id) {
          continue;
        }
        try (SocketChannel socket = poveziSe(i)) {
          ByteBuffer buffer = ByteBuffer.allocate(8);
          buffer.putInt(id);
          buffer.putInt(brojZahtjeva.get());
          buffer.flip();
          socket.write(buffer);
        } catch (IOException e) {
          System.err.println("Povezivanje s procesom " + i + " nije uspjelo.");
          continue;
        }
      }
      try {
        dozvolaUlaska.await(100, TimeUnit.MILLISECONDS);
        break;
      } catch (InterruptedException e) {
        System.out.println("Proces " + id + " ceka na ulazak u kritični odsjek...");
      }
    }
  }

  public synchronized void izadiIzKriticnogOdsjeka(int id) {
    try {
      if (Thread.currentThread().isInterrupted()) {
        return;
      }
      ulazakUKriticniOdsjek = false;
      brojZahtjeva.incrementAndGet();
      dozvolaUlaska.countDown();
      Iterator<Integer> iterator = zahtjeviRed.iterator();
      if (iterator.hasNext()) {
        int procesId = iterator.next();
        posaljiOdgovor(procesId, true);
        iterator.remove();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void primiZahtjev(int procesId, int brojZahtjevaProcesa) {
    if ((brojZahtjevaProcesa < brojZahtjeva.get())
        || (brojZahtjevaProcesa == brojZahtjeva.get() && procesId < id)) {
      odgovori.put(procesId, true);
      posaljiOdgovor(procesId, true);
    } else {
      zahtjeviRed.add(procesId);
      System.out.println("Proces " + id + " dodao zahtjev procesa " + procesId + " u red.");
    }
  }

  public void prekini() {
    Thread.currentThread().interrupt();
  }

  public void posaljiOdgovor(int procesId, boolean odgovor) {
    try (SocketChannel socket = poveziSe(procesId)) {
      ByteBuffer buffer = ByteBuffer.allocate(5);
      buffer.putInt(id);
      buffer.put(odgovor ? (byte) 1 : (byte) 0);
      buffer.flip();
      socket.write(buffer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void primiOdgovor(int procesId, boolean odgovor) {
    if (odgovor) {
      odgovori.put(procesId, true);
      dozvolaUlaska.countDown();
    }
  }

  public void pokreniServer() throws IOException {
    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.configureBlocking(false);
    try {
      serverSocketChannel.socket().bind(new InetSocketAddress(PORT + id));
    } catch (BindException e) {
      System.err.println("Port " + (PORT + id)
          + " je već zauzet. Zatvorite sve pokrenute instance i pokrenite ih ponovno.");
      System.exit(1);
    }
    Selector selector = Selector.open();
    serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
    try {
      while (true) {
        selector.select();
        Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
        while (keys.hasNext()) {
          SelectionKey key = keys.next();
          keys.remove();
          if (!key.isValid()) {
            continue;
          }
          if (key.isAcceptable()) {
            ServerSocketChannel server = (ServerSocketChannel) key.channel();
            SocketChannel client = server.accept();
            client.configureBlocking(false);
            client.register(selector, SelectionKey.OP_READ);
          } else if (key.isReadable()) {
            SocketChannel client = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(13);
            int bytesRead = client.read(buffer);
            if (bytesRead == -1) {
              client.close();
            } else if (bytesRead > 0) {
              buffer.flip();
              while (buffer.remaining() >= 8) {
                int procesId = buffer.getInt();
                int brojZahtjevaProcesa = buffer.getInt();
                primiZahtjev(procesId, brojZahtjevaProcesa);
                System.out.println("Proces " + id + " primio zahtjev od procesa " + procesId);
                buffer.compact().flip();
              }
              buffer.compact();
              buffer.flip();
              while (buffer.remaining() >= 5) {
                int procesId = buffer.getInt();
                byte odgovorByte = buffer.get();
                primiOdgovor(procesId, odgovorByte == 1);
                System.out.println("Proces " + id + " primio odgovor od procesa " + procesId);
                buffer.compact().flip();
              }
            }
          }
        }
        if (ulazakUKriticniOdsjek) {
          System.out.println("Proces " + id + " je u kritičnom odsjeku");
          try {
            Thread.sleep(500);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
          System.out.println("Proces " + id + " izlazi iz kritičnog odsjeka");
          ulazakUKriticniOdsjek = false;
          dozvolaUlaska.countDown();
          for (Integer procesId : zahtjeviRed) {
            posaljiOdgovor(procesId, true);
          }
          zahtjeviRed.clear();
        }
      }
    } catch (IOException e) {
      System.err.println("Iznimka kod prijema zahtjeva ili slanja odgovora:");
      e.printStackTrace();
    } finally {
      serverSocketChannel.close();
      selector.close();
    }
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Pogrešan unos argumenata. Unesi: <trenutniProcesID> <brojProcesa>");
      System.exit(1);
    }
    int trenutniProces = Integer.parseInt(args[0]);
    int brojProcesa = Integer.parseInt(args[1]);
    Server server = new Server(trenutniProces, brojProcesa);
    try {
      server.pokreniServer();
    } catch (IOException e) {
      System.err.println("Pogreška pri pokretanju servera:");
      e.printStackTrace();
    }
  }
}
