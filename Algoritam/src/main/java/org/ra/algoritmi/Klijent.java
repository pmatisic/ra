package org.ra.algoritmi;

import java.io.IOException;
import java.util.Scanner;

public class Klijent {
  private static Thread serverThread;

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

    while (!izlaz) {
      System.out.println("Unesi komandu (povezi, udi, izadi, kraj):");
      String komanda = scanner.nextLine();

      switch (komanda) {
        case "povezi":
          System.out.println("Unesi ID procesa za povezivanje:");
          int procesId = scanner.nextInt();
          scanner.nextLine();
          server.posaljiZahtjev(procesId, id);
          break;
        case "udi":
          server.udiUKriticniOdsjek();
          break;
        case "izadi":
          server.izadiIzKriticnogOdsjeka(id);
          break;
        case "kraj":
          izlaz = true;
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
