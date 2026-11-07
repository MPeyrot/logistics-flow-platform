# Spring Batch 6 sans base dans ingestion-service

Recherche du ticket #77 (spec #76, import CSV). Mesuré le 2026-11-07 sur un projet jetable (Spring Boot 4.1.2, `spring-boot-starter-batch` et `-batch-test`, Java 21, **sans** DataSource), hors du dépôt. Version résolue : **Spring Batch 6.0.6** (`spring-boot-batch` 4.1.2). Chaque réponse porte la sortie observée ; les noms de classes sont ceux des jars. Les pages de la documentation officielle n'ont pas été ouvertes : les références sont les Javadoc des classes citées (https://docs.spring.io/spring-batch/reference/).

## Index
- Synthèse et décisions pour #78
- 1. Dépôt de jobs sans base
- 2. Exécutions simultanées et identité d'instance
- 3. Lancement synchrone
- 4. Lecture du CSV (en-tête, BOM, CRLF, lignes vides, encodage)
- 5. Numéro de ligne, skip et collecte des erreurs
- 6. Écrivain qui échoue au deuxième chunk
- API de Spring Batch 6 utile

## Synthèse et décisions pour #78
- Rien à déclarer : ni `@EnableBatchProcessing`, ni DataSource. Poser `spring.batch.job.enabled=false`.
- Le dépôt sans base ne protège **pas** des exécutions simultanées : un `Semaphore(1)` (`tryAcquire()`, sinon 429 + `Retry-After`) suffit et est obligatoire. Mettre aussi un paramètre identifiant unique (UUID) à chaque lancement.
- Lancement synchrone natif : `JobOperator.start(job, params)` rend le `JobExecution` terminé. Construire le `Job` par requête (le fichier y est lié), sans bean `Job`.
- **Lire et décoder tout le fichier avant le job** (plafond 2 Mo : le garder en mémoire ne coûte rien) : décodage UTF-8 strict, BOM retiré, comptage des lignes, en-tête vérifié. Un décodage strict dans le lecteur échoue après des chunks déjà écrits (19 800 items écrits sur 20 000 dans la mesure), donc pas « avant toute publication ».
- Skip : politique **ciblée** (erreurs de parsing et de validation seulement), pas « tout sauter » ; la validation va dans le processeur, l'écrivain (Kafka) ne rejette rien item par item.
- Lignes vides : le `LineMapper` rend une sentinelle, le processeur la filtre (`null`).

## 1. Dépôt de jobs sans base
- Bean `jobRepository` = `org.springframework.batch.core.repository.support.ResourcelessJobRepository` (sortie : `JobRepository bean jobRepository -> …ResourcelessJobRepository`). Aucun bean `DataSource` (0), aucun `PlatformTransactionManager`.
- `JobOperator` (bean `jobOperator`, un proxy sur `TaskExecutorJobOperator`) et `JobLauncher` sont le même bean. Pas de `@EnableBatchProcessing` : l'autoconfiguration `BatchAutoConfiguration` du starter suffit.
- `spring.batch.job.enabled` vaut `true` par défaut : un bean `Job` est lancé au démarrage (`runs at startup = 1`, par `JobLauncherApplicationRunner`). Avec `spring.batch.job.enabled=false` : 0 lancement. L'import sera lancé par requête : poser la propriété à `false` (et ne déclarer aucun bean `Job`). Non vérifié : plusieurs beans `Job` sans `spring.batch.job.name` font échouer l'exécuteur de démarrage, d'après la documentation de Spring Boot.
- `ResourcelessJobRepository` ne garde en mémoire qu'**un seul** `JobInstance` et **un seul** `JobExecution` (champs `jobInstance`, `jobExecution` : le dernier lancé, vu avec `javap -p`). Tout le point 2 vient de là.

## 2. Exécutions simultanées et identité d'instance
Verdict : **le dépôt ne tient pas la concurrence ; ne pas s'y fier.**
- Deux lancements, paramètres différents, deux threads, les deux dans l'écrivain en même temps : les deux finissent `COMPLETED`, chacun avec ses items (A : 5, B : 7, sans mélange). Mais l'état rapporté se corrompt : sur 80 imports de 8 threads (paramètre unique), **1 exécution a rapporté deux `StepExecution`**, dont une obsolète en `STARTED` (celle d'un autre import). Les compteurs de la bonne étape restaient justes, l'objet ne se lit plus de façon fiable.
- Mêmes paramètres, simultanés : le second lancement lève `JobExecutionAlreadyRunningException`. Mais ce contrôle ne regarde que la dernière exécution : A tourne, B (autres paramètres) se lance et se termine, puis un doublon de A est lancé : **il passe** (`COMPLETED`). Aucune garantie contre deux imports du même fichier.
- Mêmes paramètres, après la fin : `JobInstanceAlreadyCompleteException` (« A job instance already exists and is complete for identifying parameters… ») **seulement si c'est le dernier lancé** : séquence X, Y, X → les trois passent. Sans aucun paramètre : deuxième lancement refusé de même.
- Un paramètre **non identifiant** (`addString("run", "1", false)`) ne change pas l'identité : deuxième lancement refusé (`JobInstanceAlreadyComplete`). Un paramètre **identifiant** unique (`addString("run", UUID.randomUUID().toString())`) permet de relancer le même fichier à volonté.
- Mécanisme suffisant pour « un seul import à la fois par instance » : un `java.util.concurrent.Semaphore(1)` autour de `start()` (`tryAcquire()` sans attente, échec = 429 + `Retry-After`, `release()` dans un `finally`). Un import à la fois évite la corruption ci-dessus. Pas de partage entre instances : sans effet avec `max-instances=1` sur Cloud Run, et la spec accepte ce périmètre.

## 3. Lancement synchrone
- `JobOperator.start(Job, JobParameters)` (méthode `default` de `JobOperator`, qui étend `JobLauncher`) s'exécute **dans le thread de l'appelant** : écrivain sur `main`, appelant `main`, `start()` dure 608 ms pour 2 chunks de 300 ms. Aucun `TaskExecutor` à retirer : l'autoconfiguration n'en pose pas d'asynchrone.
- Au retour : `getStatus()` = `COMPLETED`, `isRunning()` = false. Compteurs sur `jobExecution.getStepExecutions()` : `getReadCount()`, `getWriteCount()`, `getFilterCount()`, `getSkipCount()` (`getReadSkipCount()`, `getProcessSkipCount()`, `getWriteSkipCount()`), `getCommitCount()`, `getRollbackCount()`. Exemple : `read=4 write=4 skip=0 commit=2 rollback=0`.
- `start` lève des exceptions **contrôlées** (`JobInstanceAlreadyCompleteException`, `JobExecutionAlreadyRunningException`, `InvalidJobParametersException`, `JobRestartException`).

## 4. Lecture du CSV
Lecteur utilisé : `new FlatFileItemReader<Row>(resource, lineMapper)` + `setLinesToSkip(1)` + `setSkippedLinesCallback(header -> …)`. Le callback reçoit la ligne d'en-tête brute et fixe les noms du `DelimitedLineTokenizer` (`tok.setNames(cols)`) ; le `LineMapper` `(line, n) -> …` lit par nom. Ressource : `ByteArrayResource` (un `InputStreamResource` sur le flux multipart marcherait aussi, mais ne se lit qu'une fois).
- **Ordre libre des colonnes** : `type,occurredAt,site,shipmentReference` → item `{type=…, occurredAt=…, site=…, shipmentReference=REF1}`, écrit.
- **Colonne inconnue ou manquante** : une exception levée dans le callback (appelé à l'ouverture, `open()`) fait échouer l'étape **avant toute lecture** : `FAILED`, `read=0 write=0`, `ItemStreamException: Failed to initialize the reader` dont la cause est l'exception levée (`IllegalArgumentException: colonne inconnue: bogus`). Lue par l'appelant via `getAllFailureExceptions()`.
- **Fichier vide (0 octet)** : `COMPLETED`, `read=0`, **le callback n'est jamais appelé**, donc aucune erreur de colonnes. En-tête seul : `COMPLETED`, `read=0`. À détecter à part (drapeau « en-tête vu » mis dans le callback, ou contrôle avant le job).
- **BOM UTF-8** : le lecteur ne le retire **pas** ; le callback doit retirer `﻿` en tête de l'en-tête (fait dans la mesure : 2 items lus). Plus simple si le fichier est décodé avant.
- **CRLF** : sans effet (`C1`, `C2` lus proprement). Dernière ligne sans saut de ligne : lue.
- **Guillemets** : `"R,1"` et `"S ""x"""` donnent `R,1` et `S "x"` (tokenizer par défaut).
- **Champ entre guillemets sur plusieurs lignes** : **non géré par défaut** (le `\n` interne coupe l'enregistrement : deux lignes, deux erreurs de parsing). À refuser, ou `setRecordSeparatorPolicy(new DefaultRecordSeparatorPolicy())` (non essayé).
- **Lignes vides** (`""` ou `"   "`) : transmises au `LineMapper` (`3:[]`, `5:[   ]`), qui échoue en `FlatFileParseException`. Avec une politique de skip : deux erreurs rapportées ; sans : le job échoue (`FatalStepExecutionException: Unable to process chunk`). Pour les ignorer : le `LineMapper` rend une sentinelle (`Row(n, Map.of())` si `line.isBlank()`) et le processeur renvoie `null` → `filter=1`, `COMPLETED` (mesuré : `read=3 write=2 filter=1`). Ne pas renvoyer `null` du `LineMapper` : le lecteur y verrait la fin du fichier.
- **Encodage** : le décodage par défaut remplace silencieusement un octet invalide par U+FFFD (Latin-1 `é` → `R�1`, `COMPLETED`). Un décodage strict (`setBufferedReaderFactory((res, enc) -> new BufferedReader(new InputStreamReader(res.getInputStream(), UTF_8.newDecoder().onMalformedInput(REPORT).onUnmappableCharacter(REPORT))))`) fait échouer l'étape (`NonTransientFlatFileException: Unable to read from resource`), mais **au moment de la lecture**, tampon par tampon : sur 20 000 lignes (809 Ko) avec un octet invalide à la fin, le job a écrit 19 800 items (198 commits) avant d'échouer ; sur un petit fichier l'échec tombe à l'ouverture (`read=0`). Donc : décoder le fichier entier (`REPORT`) **avant** le job et passer le texte décodé au lecteur.

## 5. Numéro de ligne, skip et collecte des erreurs
- `LineMapper.mapLine(line, lineNumber)` reçoit le numéro de **ligne physique**, en-tête = 1, lignes vides comprises (premier item : 2 ; une ligne vide en 3, l'item suivant en 4). Pour qu'un rejet du **processeur** le connaisse, mettre le numéro dans l'item (`record Row(int line, Map<String,String> values)`).
- Une erreur de parsing porte son numéro : `FlatFileParseException.getLineNumber()` et `getInput()` (ex. `Parsing error at line: 5 … input=[R3,PICKED_UP,S1]`).
- Un rejet du processeur lève une exception de la politique (`RowRejected(line, message)`) : l'item est sauté, `SkipListener.onSkipInProcess(item, t)` reçoit l'item donc son numéro. Mesure sur 7 lignes : lignes 2 et 6 écrites ; erreurs collectées : lecture lignes 3 (vide) et 5 (3 colonnes sur 4), traitement lignes 4 et 7. Compteurs : `skip=4 (r2/p2/w0) write=2`, `COMPLETED`.
- Construction qui marche en 6 : `new StepBuilder("importStep", repo).<Row,Row>chunk(2).reader(r).processor(p).writer(w).faultTolerant().skipPolicy(policy).skipListener(listener).build()`. `chunk(int)` sans gestionnaire de transactions est le nouveau `ChunkOrientedStepBuilder` (aucun `PlatformTransactionManager` requis). `SkipListener` : `onSkipInRead(Throwable)`, `onSkipInProcess(T, Throwable)`, `onSkipInWrite(S, Throwable)`.
- **« Tout sauter » : à éviter.** `skipPolicy((t, n) -> true)` saute aussi les exceptions de l'**écrivain**. Mesure (écrivain qui échoue sur l'item de la ligne 4, chunks de 2) : `COMPLETED`, `skip=1 (w1) rollback=2`, appels de l'écrivain de tailles `[2, 2, 1, 1, 2]` : le chunk fautif est **rejoué item par item**. Pour un écrivain qui publie sur Kafka, un item déjà publié dans le chunk raté le serait deux fois (le rollback n'annule pas Kafka ; l'`eventId` déterministe et le consommateur idempotent le rattrapent, mais autant l'éviter). Une panne Kafka serait aussi sautée en silence. Politique ciblée recommandée : `t instanceof FlatFileParseException || t instanceof IncorrectTokenCountException || t instanceof RowRejected`.

## 6. Écrivain qui échoue au deuxième chunk
Chunks de 2, 6 lignes, l'écrivain lève `IllegalStateException` à son 2e appel :
- État : `FAILED`, `exit=FAILED`, étape `FAILED`, `write=2` (premier chunk seulement, lignes 2 et 3 réellement écrites), `read=4` (le chunk fautif est déjà lu), `commit=1 rollback=1`. L'écrivain n'est plus appelé.
- Exception : `jobExecution.getAllFailureExceptions()` contient `FatalStepExecutionException: Unable to process chunk` dont la **cause** est l'`IllegalStateException`. `getFailureExceptions()` rend `[]` (ne pas l'utiliser). Avec `faultTolerant()` + politique ciblée, la cause directe est un `RetryException` (« Retry policy for operation 'Retryable write operation' exhausted ») : remonter la chaîne des causes jusqu'à la racine pour retrouver l'exception d'origine.
- Rien n'est annulé en aval : les items du premier chunk restent écrits (publiés, pour Kafka). Un import interrompu est donc partiel ; le rapport dit « N lignes publiées avant l'échec » (`writeCount`), et l'`eventId` déterministe permet de réimporter sans doublon.

## API de Spring Batch 6 utile
- Paquets : `org.springframework.batch.core.job.{Job,JobExecution,builder.JobBuilder,parameters.{JobParameters,JobParametersBuilder}}`, `…core.step.{StepExecution,builder.StepBuilder}`, `…core.launch.JobOperator`, `…core.listener.SkipListener`, `…core.step.skip.SkipPolicy`, `…core.BatchStatus` ; infrastructure : `org.springframework.batch.infrastructure.item.{Chunk,ItemWriter,ItemProcessor,file.FlatFileItemReader,file.FlatFileParseException,file.transform.DelimitedLineTokenizer}`.
- `new JobBuilder("importJob", repo).start(step).build()`, `JobRepository` injecté.
- Le projet d'essai était dans le répertoire temporaire de la session ; seule cette note est dans le dépôt.
