#!/bin/bash

if [ "$#" -ne 2 ]; then
    echo "Korištenje: ./pokreniServer.sh <broj_procesa> <ID_procesa>"
    exit 1
fi

broj_procesa="$1"
ID_procesa="$2"

# JAR datoteka koja sadrži aplikaciju
jar_file="target/ra-1.0.0.jar"

# Main klasa za server
server_class="org.ra.algoritmi.Server"

# Provjera postoji li JAR datoteka
if [ ! -f "$jar_file" ]; then
    echo "JAR datoteka ne postoji. Provjerite putanju ili izgradite projekt uz pomoć naredbe <make>."
    exit 1
fi

# Pokreni server s odgovarajućim ID-om i brojem procesa
java -cp "$jar_file" "$server_class" $ID_procesa $broj_procesa
