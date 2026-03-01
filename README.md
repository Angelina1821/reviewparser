# Review Parser Service

## Описание
Проект представляет собой Spring Boot приложение — парсер отзывов с веб-страниц.  
Сервис собирает отзывы (автор, текст, рейтинг, дата) и сохраняет их в встроенную базу H2.

## Как запустить проект

### 1. Убедитесь, что установлены:
- Java 17+
- Maven

### 2. Соберите проект
В корневой папке проекта выполните:

```bash
mvn clean install
```
### 3. Запустите приложение

Запустите Spring Boot приложение через:

```bash
mvn spring-boot:run
```

или через IDE (класс ReviewparserApplication).

### 4. Использование API
🔹 Запуск парсинга (POST запрос)

URL:

http://localhost:8080/parse

Body (JSON):

{
  "url": "https://otzovik.com/reviews/film_draniki_2025/"
}

После запроса парсинг запускается асинхронно.

🔹 Получение результатов (GET запрос)

URL:

http://localhost:8080/answer

Ответ вернет список сохранённых отзывов.

### 5. H2 база данных (по желанию)

Консоль H2 доступна по адресу:

http://localhost:8080/h2-console

JDBC URL:

jdbc:h2:mem:reviewsdb