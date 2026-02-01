# RAPORT PROIECT – APLICAȚIE CLIENT–SERVER

## Clinică medicală – Programare, plată și audit

## 1. Analiza cerințelor

### 1.1 Tema

Proiectul urmărește implementarea unei aplicații **client–server** pentru gestionarea programărilor într-o clinică medicală (stațiune balneară) care oferă mai multe tipuri de tratamente în mai multe locații. Din cauza cererii ridicate, sistemul trebuie să permită rezervarea tratamentelor doar în limita capacităților disponibile și să solicite plata imediat după realizarea programării.

Aplicația nu oferă o interfață grafică pentru vizualizarea programărilor existente. Clientul trimite o cerere de programare pentru o anumită locație, tratament și oră, iar serverul răspunde cu **„programare reușită”** sau **„programare nereușită”**, în funcție de disponibilitate.

---

### 1.2 Obiective urmărite

- implementarea unei aplicații **client–server** cu comunicare **TCP**;
- folosirea execuției concurente pentru procesarea cererilor;
- utilizarea mecanismelor de tip **Future/Promise** și a unui **thread pool**;
- gestionarea rezervărilor temporare și a plăților;
- implementarea unui **audit periodic** pentru verificarea consistenței și a soldurilor;
- analiza comportamentului sistemului sub concurență ridicată.

---

### 1.3 Cerințe funcționale

1. **Programare (rezervare temporară)**
   - **Input**: nume client, CNP, locație, tip tratament, oră.
   - **Condiții**:
     - locația este deschisă între orele 10:00–18:00;
     - există capacitate disponibilă pentru tratamentul respectiv;
     - nu se depășește numărul maxim de pacienți `N(i,j)` pentru intervalul cerut.
   - **Efect**:
     - se creează o programare cu status **REZERVARE**;
     - se salvează un deadline de plată `T_plata`.

2. **Plată (confirmare)**
   - **Input**: id programare, CNP.
   - **Condiții**:
     - programarea există;
     - CNP-ul corespunde;
     - statusul este **REZERVARE**;
     - plata este realizată înainte de expirare.
   - **Efect**:
     - statusul devine **PLATITA**;
     - se înregistrează plata.

3. **Anulare / Expirare**
   - **Anulare explicită**:
     - se șterge plata;
     - se adaugă un refund (sumă negativă).
   - **Expirare automată**:
     - dacă `T_plata` este depășit, programarea devine **EXPIRATA**.

4. **Audit periodic**
   - rulează la fiecare **5s** sau **10s**;
   - identifică rezervările neplătite;
   - verifică suprapunerile și depășirile de capacitate;
   - calculează soldul financiar per locație.

5. **Simulare clienți**
   - mai mulți clienți concurenți trimit cereri la interval fix;
   - o parte dintre rezervări sunt plătite, restul sunt anulate sau expiră.

---

## 2. Proiectare și arhitectură

### 2.1 Structura aplicației

Aplicația este organizată pe straturi, pentru separarea clară a responsabilităților:

- **Model**  
  Entități de domeniu: `Reservation`, `Payment`, `Status`, `VerificationReport`.

- **Repository**  
  Componenta `Storage` se ocupă de persistența datelor în fișiere text:
  - `planificari.txt`
  - `plati.txt`
  - `refunds.txt`
  - `events.txt`
  - `verificari_5s.txt` / `verificari_10s.txt`

- **Service**  
  `ClinicService` conține logica de business:
  - creare programări;
  - plată;
  - anulare;
  - expirare automată;
  - audit periodic.

- **Server**  
  `ServerMain` și `ClientConnection` implementează serverul TCP și procesarea cererilor.

- **Client**  
  `ClientMain` și `ClientWorker` generează trafic concurent pentru testare.

---

### 2.2 Protocol de comunicare TCP

Comunicarea se face prin linii de text:

- `BOOK|nume|cnp|locatie|tratament|HH:MM`  
  Răspuns: `BOOK_OK|id|cost|deadline` sau `BOOK_FAIL|motiv`

- `PAY|id|cnp`  
  Răspuns: `PAY_OK|id` sau `PAY_FAIL|motiv`

- `CANCEL|id|cnp`  
  Răspuns: `CANCEL_OK|id|refund` sau `CANCEL_FAIL|motiv`

---

## 3. Decizii de sincronizare

### 3.1 Gestionarea concurenței

Pentru a asigura consistența datelor, aplicația folosește un **ReadWriteLock**:

- **WRITE lock**:
  - programare;
  - plată;
  - anulare (ștergere plată + refund + schimbare status).
- **READ lock**:
  - audit periodic.

Astfel, auditul nu poate rula în timpul unei anulări incomplete, dar poate rula între o rezervare și plata corespunzătoare, conform cerinței.

---

### 3.2 Corectitudine sub concurență

- mai multe cereri **BOOK** pot fi procesate concurent;
- modificările asupra structurii comune sunt protejate de lock;
- nu apar race condition-uri între anulare și audit.

---

## 4. Folosirea futures și thread pool

### 4.1 Thread pool

Serverul folosește un `ExecutorService` cu un număr fix de thread-uri (**p = 10**).  
Acest lucru limitează numărul maxim de cereri procesate simultan și previne supraîncărcarea sistemului.

---

### 4.2 Future pentru fiecare cerere

Pentru fiecare cerere **BOOK**, serverul creează un `CompletableFuture`:

```java
CompletableFuture
    .supplyAsync(() -> service.book(...), workerPool)
    .thenAccept(out::println);
```

CompletableFuture este o implementare a interfeței **Future** și permite execuție asincronă non-blocantă.  
Thread-ul de rețea rămâne liber, iar procesarea cererii se face în **worker pool**, ceea ce îmbunătățește scalabilitatea serverului.

---

## 5. Testare și rulare

### 5.1 Parametri de test

- **p** = 10 thread-uri server
- **număr clienți** = 10
- **număr locații** = 5
- **număr tratamente** = 5
- **durată rulare server** = 180 secunde
- **auditIntervalSec** = 5s și 10s

---

### 5.2 Rezultate – audit la 5 secunde

- auditul rulează frecvent;
- rezervările neplătite sunt detectate rapid;
- se generează mai mult I/O;
- soldurile sunt actualizate mai des.

---

### 5.3 Rezultate – audit la 10 secunde

- mai puține runde de audit;
- anulările și expirările apar mai „grupat”;
- overhead de I/O mai mic.

---

### 5.4 Observații comparative

- audit mai des → reacție mai rapidă, dar cost de procesare mai mare;
- audit mai rar → performanță mai bună, dar informații mai puțin granulare.

---

## 6. Concluzii privind performanța

### 6.1 Throughput

În ambele rulări, aplicația a obținut un throughput stabil și foarte apropiat ca valoare:

- **Audit 5s:** ~**7.39 cereri/secundă**
- **Audit 10s:** ~**7.42 cereri/secundă**

Diferența dintre cele două configurații este nesemnificativă, ceea ce indică faptul că
frecvența auditului (5s vs 10s) **nu influențează semnificativ capacitatea de procesare**
a serverului în configurația testată (10 clienți, p = 10 thread-uri).

---

### 6.2 Timpi medii de răspuns

Pentru majoritatea cererilor, latențele sunt foarte reduse:

- **BOOK**
    - p50 ≈ 0.7 ms
    - p95 ≈ 1.5–2.1 ms
    - latență medie ≈ 1.38–1.41 ms

- **PAY**
    - p50 ≈ 0.7–0.8 ms
    - p95 ≈ 1.4–1.7 ms
    - latență medie ≈ 0.88–0.97 ms

- **CANCEL**
    - p50 ≈ 0.68–0.83 ms
    - p95 ≈ 1.94–2.10 ms
    - latență medie ≈ 1.03–1.19 ms

Aceste valori arată că, în condiții normale, serverul răspunde în **sub 2 ms**
pentru majoritatea cererilor, confirmând eficiența utilizării
**thread pool-ului** și a execuției asincrone prin **CompletableFuture**.

---

### 6.3 Tail latency și variații

Deși latențele medii sunt mici, pentru operația **BOOK** apare un fenomen de
**tail latency** pronunțat:

- p99 ≈ **39–43 ms**
- max ≈ **46–48 ms**

Acest comportament indică faptul că, rar, unele cereri BOOK sunt întârziate semnificativ,
probabil din cauza:
- competiției pe lock-uri,
- cozii din thread pool,
- rulării auditului,
- sau planificării thread-urilor de către sistemul de operare.

Operația **PAY** este mult mai stabilă din acest punct de vedere, având:
- p99 între **2.49 ms și 5.70 ms**,
- valori maxime sub **13 ms**.

---

### 6.4 Impactul intervalului de audit

Comparând rulările cu audit la 5s și la 10s, se observă că:

- throughput-ul rămâne practic identic;
- auditul la 10s tinde să producă ușor mai puține variații pentru PAY;
- diferențele de latență sunt minore și nu afectează comportamentul general al sistemului.

Astfel, alegerea intervalului de audit reprezintă un compromis între
**granularitatea informațiilor** și **overhead-ul de procesare**, fără impact major
asupra performanței globale în acest scenariu.

---

### 6.5 Concluzie finală

Aplicația demonstrează un comportament performant și stabil sub concurență:
- throughput constant (~7.4 cereri/secundă);
- latențe medii foarte mici (sub 2 ms);
- utilizare eficientă a execuției asincrone cu **Future** și **thread pool**.

Prezența tail latency pentru BOOK este normală într-un sistem concurent și
reprezintă un punct potențial de optimizare, fără a compromite corectitudinea
sau stabilitatea aplicației.

