package org.ra.algoritmi;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Klijent {
  private static Thread serverThread;
  private static int brojZahtjeva = 0;

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Pogrešan unos argumenata. Unesi: <ID procesa> <broj procesa>");
      System.exit(1);
    }

    int id = Integer.parseInt(args[0]);
    int brojProcesa = Integer.parseInt(args[1]);
    Server server = new Server(id, brojProcesa);

    serverThread = new Thread(() -> {
      try {
        server.pokreniServer();
      } catch (IOException e) {
        System.err.println("Pogreška pri pokretanju servera:");
        e.printStackTrace();
      }
    });
    serverThread.setDaemon(true);
    serverThread.start();

    Scanner scanner = new Scanner(System.in);
    boolean izlaz = false;
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    while (!izlaz) {
      System.out.println("Unesi komandu (povezi, udi, izadi, kraj):");
      String komanda = scanner.nextLine();
      switch (komanda) {

        case "povezi":
          System.out.println("Unesi ID serverskog procesa za povezivanje:");
          int procesId = scanner.nextInt();
          scanner.nextLine();
          brojZahtjeva++;
          server.posaljiZahtjev(procesId, brojZahtjeva);
          break;

        case "udi":
          System.out.println("Proces " + id + " pokušava ući u kritični odsjek.");
          executorService.submit(server::udiUKriticniOdsjek);
          break;

        case "izadi":
          System.out.println("Proces " + id + " pokušava izaći iz kritičnog odsjeka.");
          executorService.submit(server::izadiIzKriticnogOdsjeka);
          break;

        case "kraj":
          izlaz = true;
          executorService.shutdown();
          Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (serverThread != null) {
              serverThread.interrupt();
            }
          }));
          System.exit(0);
          break;

        default:
          System.out.println("Kriva komanda.");
          break;
      }
    }

    scanner.close();
  }
}
