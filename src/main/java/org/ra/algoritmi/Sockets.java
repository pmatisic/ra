package org.ra.algoritmi;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Sockets {
    private static final int PORT = 12345;

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length != 2) {
            System.err.println("Koristi: java Sockets <brojProcesa> <trenutniProcesID>");
            System.exit(1);
        }

        int brojProcesa = Integer.parseInt(args[0]);
        int trenutniProcesID = Integer.parseInt(args[1]);
        RicartAgrawala proces = new RicartAgrawala(trenutniProcesID, brojProcesa);

        List<String> adrese = new ArrayList<>();
        for (int i = 0; i < brojProcesa; i++) {
            adrese.add("localhost"); // Pretpostavljamo da svi procesi rade na istom računalu
        }

        ExecutorService executor = Executors.newFixedThreadPool(brojProcesa);

        // Slušanje zahtjeva od drugih procesa
        AsynchronousServerSocketChannel serverSocket = AsynchronousServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(PORT + trenutniProcesID));
        serverSocket.accept(null, new CompletionHandler<AsynchronousSocketChannel, Void>() {
            @Override
            public void completed(AsynchronousSocketChannel socket, Void attachment) {
                serverSocket.accept(null, this);

                ByteBuffer buffer = ByteBuffer.allocate(8);
                socket.read(buffer, null, new CompletionHandler<Integer, Void>() {
                    @Override
                    public void completed(Integer bytesRead, Void attachment) {
                        buffer.flip();
                        int procesId = buffer.getInt();
                        int brojZahtjevaProcesa = buffer.getInt();
                        proces.primiZahtjev(procesId, brojZahtjevaProcesa);

                        buffer.clear();
                        buffer.putInt(trenutniProcesID);
                        buffer.put(proces.getOdgovor(procesId) ? (byte) 1 : (byte) 0);
                        buffer.flip();

                        socket.write(buffer, null, new CompletionHandler<Integer, Void>() {
                            @Override
                            public void completed(Integer bytesWritten, Void attachment) {
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void failed(Throwable exc, Void attachment) {
                                exc.printStackTrace();
                            }
                        });
                    }

                    @Override
                    public void failed(Throwable exc, Void attachment) {
                        exc.printStackTrace();
                    }
                });
            }

            @Override
            public void failed(Throwable exc, Void attachment) {
                exc.printStackTrace();
            }
        });

        // Komunikacija s drugim procesima
        proces.udiUKriticniOdsjek();
        int broj = proces.getBrojZahtjeva();

        AtomicInteger brojOdgovora = new AtomicInteger();
        for (int i = 0; i < brojProcesa; i++) {
            if (i != trenutniProcesID) {
                int finalI = i;
                executor.submit(() -> {
                    try (AsynchronousSocketChannel socket = AsynchronousSocketChannel.open()) {
                        socket.connect(new InetSocketAddress(adrese.get(finalI), PORT + finalI), null,
                                new CompletionHandler<Void, Void>() {
                                    @Override
                                    public void completed(Void result, Void attachment) {
                                        ByteBuffer buffer = ByteBuffer.allocate(8);
                                        buffer.putInt(trenutniProcesID);
                                        buffer.putInt(broj);
                                        buffer.flip();
                                        socket.write(buffer, null, new CompletionHandler<Integer, Void>() {
                                            @Override
                                            public void completed(Integer bytesWritten, Void attachment) {
                                                buffer.clear();
                                                socket.read(buffer, null, new CompletionHandler<Integer, Void>() {
                                                    @Override
                                                    public void completed(Integer bytesRead, Void attachment) {
                                                        buffer.flip();
                                                        int procesId = buffer.getInt();
                                                        boolean odgovor = buffer.get() == 1;
                                                        proces.primiOdgovor(procesId, odgovor);
                                                        brojOdgovora.incrementAndGet();
                                                    }

                                                    @Override
                                                    public void failed(Throwable exc, Void attachment) {
                                                        exc.printStackTrace();
                                                    }
                                                });
                                            }

                                            @Override
                                            public void failed(Throwable exc, Void attachment) {
                                                exc.printStackTrace();
                                            }
                                        });
                                    }

                                    @Override
                                    public void failed(Throwable exc, Void attachment) {
                                        exc.printStackTrace();
                                    }
                                });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        // Čekanje na sve odgovore
        while (brojOdgovora.get() < brojProcesa - 1) {
            try {
// Čekanje na sve odgovore
                proces.getDozvolaUlaska().await();

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        // Ulazak u kritični odsjek
        System.out.println("Proces " + trenutniProcesID + " je ušao u kritični odsjek.");

        // Rad u kritičnom odsjeku (simulacija)
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Izlazak iz kritičnog odsjeka
        System.out.println("Proces " + trenutniProcesID + " izlazi iz kritičnog odsjeka.");
        proces.izadiIzKriticnogOdsjeka();

        // Zatvaranje izvršitelja i oslobađanje resursa
        executor.shutdown();
    }
}