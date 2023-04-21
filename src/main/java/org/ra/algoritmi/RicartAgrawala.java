package org.ra.algoritmi;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;

public class RicartAgrawala {
    private final int id;
    private final AtomicInteger brojZahtjeva;
    private final ConcurrentHashMap<Integer, Boolean> odgovori;
    private final ConcurrentLinkedQueue<Integer> zahtjeviRed;
    private volatile boolean ulazakUkritičniOdsjek;
    private volatile CountDownLatch dozvolaUlaska;

    public boolean getOdgovor(int procesId) {
        return odgovori.get(procesId);
    }

    public int getBrojZahtjeva() {
        return brojZahtjeva.get();
    }

    public CountDownLatch getDozvolaUlaska() {
        return dozvolaUlaska;
    }

    public RicartAgrawala(int id, int brojProcesa) {
        this.id = id;
        this.brojZahtjeva = new AtomicInteger();
        this.odgovori = new ConcurrentHashMap<>();
        this.zahtjeviRed = new ConcurrentLinkedQueue<>();
        this.ulazakUkritičniOdsjek = false;
        this.dozvolaUlaska = new CountDownLatch(brojProcesa - 1);

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

    public void primiOdgovor(int procesId, boolean odgovor) {
        if (odgovor) {
            odgovori.put(procesId, true);
            dozvolaUlaska.countDown();
        }
    }

    public void udiUKriticniOdsjek() throws InterruptedException {
        int broj = brojZahtjeva.incrementAndGet();
        for (Integer procesId : odgovori.keySet()) {
            posaljiZahtjev(procesId);
        }

        dozvolaUlaska.await();
        ulazakUkritičniOdsjek = true;
    }

    public void izadiIzKriticnogOdsjeka() {
        ulazakUkritičniOdsjek = false;
        brojZahtjeva.set(Integer.MAX_VALUE);

        while (!zahtjeviRed.isEmpty()) {
            int procesId = zahtjeviRed.poll();
            primiZahtjev(procesId, brojZahtjeva.get());
        }

        // Resetiraj CountDownLatch za sljedeći ulazak u kritični odsjek
        resetirajDozvolaUlaska();
    }

    private void resetirajDozvolaUlaska() {
        dozvolaUlaska = new CountDownLatch(odgovori.size());
    }
    public boolean isUlazakUkritičniOdsjek() {
        return ulazakUkritičniOdsjek;
    }
}
