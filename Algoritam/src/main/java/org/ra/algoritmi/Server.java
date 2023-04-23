package org.ra.algoritmi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {
  private final int id;
  private final AtomicInteger brojZahtjeva;
  private final ConcurrentLinkedQueue<Integer> zahtjeviRed;
  private volatile CountDownLatch dozvolaUlaska;
  private static final int PORT = 12345;
  private final int brojProcesa;

  public Server(int id, int brojProcesa) {
    this.id = id;
    this.brojProcesa = brojProcesa;
    this.brojZahtjeva = new AtomicInteger();
    this.zahtjeviRed = new ConcurrentLinkedQueue<>();
  }

  public void pokreniServer() throws IOException {
    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
    serverSocketChannel.configureBlocking(false);
    serverSocketChannel.socket().bind(new InetSocketAddress(PORT + id));
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
            ByteBuffer buffer = ByteBuffer.allocate(14);
            int bytesRead = client.read(buffer);
            if (bytesRead == -1) {
              client.close();
            } else if (bytesRead > 0) {
              buffer.flip();
              while (buffer.hasRemaining()) {
                byte messageType = buffer.get();
                System.out.println("Vrsta poruke koja je primljena: " + messageType);
                if (messageType == 0 && buffer.remaining() >= 8) {
                  int procesId = buffer.getInt();
                  int brojZahtjevaProcesa = buffer.getInt();
                  primiZahtjev(messageType, procesId, brojZahtjevaProcesa);
                  System.out.println("Proces " + id + " primio zahtjev od procesa " + procesId);
                } else if (messageType == 1 && buffer.remaining() >= 5) {
                  int procesId = buffer.getInt();
                  byte odgovorByte = buffer.get();
                  System.out.println("Proces ID: " + procesId);
                  System.out.println("Odgovor: " + (odgovorByte == 1));
                  primiOdgovor(messageType, procesId, odgovorByte == 1);
                  System.out.println("Proces " + id + " primio odgovor od procesa " + procesId);
                } else {
                  break;
                }
              }
            }
          }
        }
      }
    } catch (IOException e) {
      System.err.println("Iznimka kod prijema zahtjeva ili slanja od odgovora:");
      e.printStackTrace();
    } finally {
      serverSocketChannel.close();
      selector.close();
    }
  }

  public SocketChannel poveziSe(int procesId) throws IOException {
    SocketChannel socket = SocketChannel.open();
    InetSocketAddress adresa = new InetSocketAddress("localhost", PORT);
    System.out.println("Pokušavam se povezati na adresu: " + adresa.toString());
    while (true) {
      try {
        socket.connect(adresa);
        break;
      } catch (IOException e) {
        System.err.println("Povezivanje nije uspjelo, pokušavam ponovno...");
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ex) {
          ex.printStackTrace();
        }
      }
    }
    return socket;
  }

  public void posaljiZahtjev(int procesId, int brojZahtjeva) {
    try (SocketChannel socket = poveziSe(procesId)) {
      ByteBuffer buffer = ByteBuffer.allocate(9);
      buffer.put((byte) 0);
      buffer.putInt(id);
      buffer.putInt(brojZahtjeva);
      buffer.flip();
      socket.write(buffer);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void primiZahtjev(int messageType, int procesId, int brojZahtjevaProcesa) {
    if (messageType == 0) {
      if ((brojZahtjevaProcesa < brojZahtjeva.get())
          || (brojZahtjevaProcesa == brojZahtjeva.get() && procesId < id)) {
        posaljiOdgovor(procesId, true);
      } else {
        zahtjeviRed.add(procesId);
        System.out.println("Proces " + id + " je dodao zahtjev procesa " + procesId + " u red.");
      }
    }
  }

  public void udiUKriticniOdsjek() {
    int trenutniBrojZahtjeva = brojZahtjeva.incrementAndGet();
    zahtjeviRed.add(id);
    while (true) {
      dozvolaUlaska = new CountDownLatch(1);
      for (int i = 0; i < brojProcesa; i++) {
        if (i == id) {
          continue;
        }
        posaljiZahtjev(i, trenutniBrojZahtjeva);
      }
      try {
        dozvolaUlaska.await(100, TimeUnit.MILLISECONDS);
        System.out.println("Proces " + id + " je ušao u kritični odsjek.");
        break;
      } catch (InterruptedException e) {
        System.out.println("Proces " + id + " čeka na ulazak u kritični odsjek...");
      }
    }
  }

  public void izadiIzKriticnogOdsjeka() {
    System.out.println("Izlazak iz kritičnog odsjeka je započeo.");
    try {
      if (Thread.currentThread().isInterrupted()) {
        System.out.println("Dretva je prekinuta.");
        return;
      }
      brojZahtjeva.incrementAndGet();
      System.out.println("Broj zahtjeva je inkrementiran.");
      if (dozvolaUlaska == null) {
        dozvolaUlaska = new CountDownLatch(1);
      }
      dozvolaUlaska.countDown();
      System.out.println("Dozvola za ulazak je istekla.");
      Iterator<Integer> iterator = zahtjeviRed.iterator();
      if (iterator.hasNext()) {
        int procesId = iterator.next();
        System.out.println("Proces ID za sljedeći zahtjev: " + procesId);
        System.out.println("Pokušavam poslati odgovor procesu " + procesId);
        posaljiOdgovor(procesId, true);
        System.out.println("Proces " + id + " izašao je iz kritičnog odsjeka.");
        iterator.remove();
      } else {
        System.out.println("Nema sljedećih zahtjeva u redu.");
      }
      for (Integer procesId : zahtjeviRed) {
        posaljiOdgovor(procesId, true);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void posaljiOdgovor(int procesId, boolean odgovor) {
    try (SocketChannel socket = poveziSe(procesId)) {
      System.out.println("Povezan sa procesom " + procesId);
      ByteBuffer buffer = ByteBuffer.allocate(6);
      buffer.put((byte) 1);
      buffer.putInt(id);
      buffer.put(odgovor ? (byte) 1 : (byte) 0);
      buffer.flip();
      System.out.println("Šaljem odgovor...");
      System.out.println("Proces ID: " + procesId);
      System.out.println("Odgovor: " + odgovor);
      socket.write(buffer);
      socket.shutdownOutput();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void primiOdgovor(int messageType, int procesId, boolean odgovor) {
    if (messageType == 1) {
      if (odgovor) {
        if (dozvolaUlaska == null) {
          dozvolaUlaska = new CountDownLatch(1);
        }
        dozvolaUlaska.countDown();
      }
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
