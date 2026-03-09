# Review Parser Service

## Описание
Spring Boot приложение — асинхронный парсер отзывов с сохранением в H2.

В проект добавлены:
- **Micrometer + Prometheus** метрики;
- **OpenTelemetry tracing** (экспорт в OTLP/Jaeger);
- оптимизации производительности (batch insert, индексы, уменьшение лишних аллокаций);
- шаблон **JMH**-бенчмарка для сравнения `for/stream/parallelStream`.

---

## Запуск

```bash
./mvnw clean spring-boot:run
```

API:
- `POST http://localhost:8080/parse` body:
  ```json
  { "url": "https://otzovik.com/reviews/film_draniki_2025/" }
  ```
- `GET http://localhost:8080/answer`

H2 console: `http://localhost:8080/h2-console`

---

## Шаг 1. Метрики Micrometer + Prometheus

### Что уже собирается
- время выполнения парсинга: `review_parser_parse_duration_seconds`;
- успешные парсинги: `review_parser_parse_success_total`;
- ошибочные парсинги: `review_parser_parse_error_total`;
- количество сохранённых записей в БД: `review_parser_db_records_saved_total`.

### Где смотреть
1. Запустите приложение.
2. Откройте endpoint:
   - `http://localhost:8080/actuator/prometheus`
   - `http://localhost:8080/actuator/metrics`

### Быстрый Prometheus локально
```yaml
# prometheus.yml
scrape_configs:
  - job_name: "reviewparser"
    metrics_path: "/actuator/prometheus"
    static_configs:
      - targets: ["host.docker.internal:8080"]
```

Запуск:
```bash
docker run --rm -p 9090:9090 -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml prom/prometheus
```

---

## Шаг 2. Профилирование (VisualVM/JFR) + thread/heap dumps + JMH

## 2.1 Java Flight Recorder (JFR)

Запуск приложения с JFR:
```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-XX:StartFlightRecording=filename=reviewparser.jfr,duration=120s,settings=profile"
```

Открытие файла `reviewparser.jfr` в JDK Mission Control:
- анализируйте `Method Profiling`, `Allocation`, `GC Pauses`, `Threads`.

## 2.2 VisualVM
1. Запустить `jvisualvm`.
2. Подключиться к процессу `reviewparser`.
3. Вкладки:
   - **Sampler/Profiler** — медленные методы, CPU hotspots;
   - **Monitor** — нагрузка CPU и частота GC;
   - **Threads** — состояния потоков и блокировки.

## 2.3 Thread dump
```bash
jcmd <PID> Thread.print > thread-dump.txt
```

Ищем:
- `BLOCKED` потоки;
- одинаковые stack trace (контеншен);
- долгие операции в пуле потоков.

## 2.4 Heap dump
```bash
jcmd <PID> GC.heap_dump heap.hprof
```

Открыть `heap.hprof` в VisualVM/Eclipse MAT и проверить:
- Dominator Tree;
- объекты с самым большим retained size;
- подозрительные коллекции.

## 2.5 JMH benchmark
В проекте есть `ReviewMappingBenchmark`.

Запуск:
```bash
./mvnw -Dtest=ReviewMappingBenchmark -DfailIfNoTests=false test
```

(Для полноценного JMH-раннера можно вынести benchmark в отдельный module или profile.)

---

## Шаг 3. Анализ памяти и GC

1. Включите GC-логи:
```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-Xlog:gc*:file=gc.log:time,uptime,level,tags"
```
2. Проверьте:
   - как часто срабатывает Young/Old GC;
   - среднюю паузу GC;
   - объёмы выделений при парсинге.
3. Сопоставьте пики в `gc.log` с JFR/VisualVM по времени.

---

## Шаг 4. Деградация производительности (что исправлено)

- **Отсутствие индексов**: добавлены индексы `sourceUrl` и `date` в таблице `Review`.
- **Частые аллокации**: замена stream-маппинга на `for` + pre-sized `ArrayList` в критическом участке парсинга.
- **Работа с БД**: включены `hibernate.jdbc.batch_size` и `order_inserts` для уменьшения количества round-trip.
- **Синхронизация/потоки**: сохранён отдельный пул потоков, добавлены наблюдения по этапам, чтобы быстрее находить узкие места по trace/span.

Пункт N+1 для текущей модели (одна сущность `Review` без связанных ленивых коллекций) в явном виде не проявляется. Если добавятся связи (`@OneToMany/@ManyToOne`) — проверяйте SQL-логи и используйте `join fetch`/batch-fetch.

---

## Шаг 5. OpenTelemetry + Jaeger

Трейсинг уже включён через Micrometer Tracing + OTLP exporter.

### Локальный Jaeger all-in-one
```bash
docker run --rm --name jaeger \
  -e COLLECTOR_OTLP_ENABLED=true \
  -p 16686:16686 -p 4317:4317 -p 4318:4318 \
  jaegertracing/all-in-one:1.57
```

После этого:
1. Запустите приложение.
2. Сделайте несколько `POST /parse`.
3. Откройте `http://localhost:16686`.
4. Выберите service `reviewparser` и смотрите диаграмму/спаны:
   - `review_parser.parse`
   - `review_parser.parse.download`
   - `review_parser.parse.transform`
   - `review_parser.parse.persist`

---

## Короткий план сдачи работы
1. Запустить парсер + снять метрики из `/actuator/prometheus`.
2. Снять профиль JFR/VisualVM под нагрузкой.
3. Собрать thread dump и heap dump, показать анализ.
4. Показать изменения по оптимизации (индексы, batch, аллокации).
5. Показать trace в Jaeger с разбиением по этапам.
