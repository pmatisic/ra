package org.ra.algoritam;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RicartAgrawala {
    private final int id;
    private final AtomicInteger brojZahtjeva;
    private final ConcurrentHashMap<Integer, Boolean> odgovori;
    private final ConcurrentLinkedQueue<Integer> zahtjeviRed;
    private volatile boolean ulazakUkritičniOdsjek;

    public RicartAgrawala(int id, int brojProcesa) {
        this.id = id;
        this.brojZahtjeva = new AtomicInteger();
        this.odgovori = new ConcurrentHashMap<>();
        this.zahtjeviRed = new ConcurrentLinkedQueue<>();
        this.ulazakUkritičniOdsjek = false;

        for (int i = 0; i < brojProcesa; i++) {
            if (i != id) {
                odgovori.put(i, false);
            }
        }
    }

    public void posaljiZahtjev(int procesId) {
        zahtjeviRed.add(procesId);
    }

    public void primiZahtjev(int procesId, int brojZahtjevaProcesa) {
        if ((brojZahtjevaProcesa < brojZahtjeva.get()) ||
                (brojZahtjevaProcesa == brojZahtjeva.get() && procesId < id)) {
            // Odgovori "DA"
            odgovori.put(procesId, true);
        } else {
            // Stavi zahtjev u red
            zahtjeviRed.add(procesId);
        }
    }

    public void primiOdgovor(int procesId) {
        odgovori.put(procesId, true);
    }

    public void uđiUKritičniOdsjek() {
        int broj = brojZahtjeva.incrementAndGet();
        for (Integer procesId : odgovori.keySet()) {
            posaljiZahtjev(procesId);
        }

        while (true) {
            boolean dozvola = true;
            for (Boolean odgovor : odgovori.values()) {
                if (!odgovor) {
                    dozvola = false;
                    break;
                }
            }
            if (dozvola) {
                ulazakUkritičniOdsjek = true;
                break;
            }
        }
    }

    public void izađiIzKritičnogOdsjeka() {
        ulazakUkritičniOdsjek = false;
        brojZahtjeva.set(Integer.MAX_VALUE);

        while (!zahtjeviRed.isEmpty()) {
            int procesId = zahtjeviRed.poll();
            primiZahtjev(procesId, brojZahtjeva.get());
        }
    }

    public boolean isUlazakUkritičniOdsjek() {
        return ulazakUkritičniOdsjek;
    }
}
