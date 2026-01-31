## Client-Server Clinica (TCP, Java, Futures, ThreadPool)

### Cerinte bifate
- TCP client-server
- Thread pool max p (default p=10)
- Pentru fiecare BOOK se foloseste future (CompletableFuture)
- Timeout plata (T_plata)
- Verificare periodica 5s / 10s (log in fisier)
- Anulare atomica (ReadWriteLock -> verificarea nu prinde anulare partiala)
- Server shutdown dupa 3 minute + notificare clienti

### Rulare
1) Server verificare 5s:
   ./gradlew runServer5
   (Windows: gradlew.bat runServer5)

2) Server verificare 10s:
   ./gradlew runServer10

3) Clienti (10 clienti):
   ./gradlew runClients

### Output
Se scrie in directorul `output/`:
- planificari.txt
- plati.txt
- refunds.txt
- events.txt
- verificari_5s.txt / verificari_10s.txt
