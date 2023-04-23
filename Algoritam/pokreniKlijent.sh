#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Koristite: ./pokreniKlijent.sh <broj_procesa> <ID_klijenta>"
    exit 1
fi

broj_procesa="$1"
ID_klijenta="$2"

# JAR datoteka koja sadrži aplikaciju
jar_file="target/ra-1.0.0.jar"

# Main klasa za klijent
client_class="org.ra.algoritmi.Klijent"

# Provjera postoji li JAR datoteka
if [ ! -f "$jar_file" ]; then
    echo "JAR datoteka ne postoji. Provjerite putanju ili izgradite projekt koristeći naredbu <make>."
    exit 1
fi

# Pokreni klijenta s odgovarajućim ID-om i brojem procesa
java -cp "$jar_file" "$client_class" $ID_klijenta $broj_procesa
