---
name: ktor-layered-api
description: "Architecture and conventions for Kotlin + Ktor REST APIs: Routes → Controller → Repository → DAO/Service layers, Koin, Exposed + Flyway on PostgreSQL, Ktor Client (CIO) for outside APIs, S3 object storage, Firebase App Check protection without login, Failure-based errors via StatusPages, an ErrorLogger, and JUnit + MockK unit tests. Use when creating or reviewing endpoints, layers, services, config or tests in a Ktor project."
---

# Ktor layered API

A small-overhead architecture for Ktor APIs. Project-specific rules (database schema, validation rules, business logic) live in the project's `CLAUDE.md`. When they conflict, `CLAUDE.md` wins.

## 1. Stack

| Concern | Choice | Why |
|---|---|---|
| Server | Ktor 3 + Netty engine, `application.yaml` | Default and most used engine |
| Language/build | Kotlin, Gradle Kotlin DSL, version catalogs (`ktorLibs` + `libs`) | |
| JSON | kotlinx.serialization via `ContentNegotiation` | Native to Ktor |
| DI | Koin (`koin-ktor`) | |
| Database | PostgreSQL + Exposed (**JDBC**) + HikariCP + Flyway | Flyway and Hikari need JDBC; SQL migrations are explicit |
| Outside APIs | Ktor Client, **CIO** engine | Native, no extra deps. Use OkHttp only if HTTP/2 or OkHttp interceptors are needed |
| Object storage | AWS SDK for Kotlin (`aws.sdk.kotlin:s3`) | Works with any S3 API (AWS, Cloudflare R2, RustFS, ...) |
| Errors | `StatusPages` + `Failure` classes | One place maps errors to HTTP |
| Logging | Logback (SLF4J) behind an `ErrorLogger` interface | Monitoring tool can be swapped |
| Tests | JUnit 5/6 + MockK + kotlinx-coroutines-test | |
| Local services | Docker Compose (PostgreSQL, S3-compatible storage) | |

Don't add layers, libraries or patterns (use cases, mappers frameworks, CQRS, generic base classes) that this skill doesn't list, unless the user asks.

## 2. Package layout

```
<root package>/
├── Application.kt              main + the single Ktor module
├── core/                       everything used across themes
│   ├── config/                 AppConfig (typed application.yaml)
│   ├── di/                     Koin modules, one per layer
│   ├── plugins/                one file per Ktor plugin setup (Serialization, StatusPages, Cors, ...)
│   ├── data/
│   │   ├── database/           AppDatabase (pool + migrations + Exposed), tables/, entities/, daos/
│   │   └── repositories/       repository interfaces + implementations
│   ├── services/               clients of outside systems, one sub-package per provider
│   │   └── <provider>/         <Provider>Service.kt, models/ (provider DTOs)
│   ├── logging/                ErrorLogger + implementations
│   ├── models/                 domain models shared by layers, ErrorResponse, failures/
│   └── utils/
└── routes/
    └── <theme>/                one package per theme (e.g. books)
        ├── <Theme>Routes.kt    presentation
        ├── <Theme>Controller.kt
        └── models/             request/response DTOs of this theme
src/main/resources/
├── application.yaml
├── logback.xml
└── db/migration/               V1__<description>.sql, V2__...
src/test/kotlin/<root package>/ mirrors main (routes/<theme>/, core/data/repositories/)
```

## 3. Layers

A request flows **Routes → Controller → Repository → (DAO | Service)**. Each layer:
- only calls the layer directly below it,
- depends on the **interface** of that layer, never on the implementation (dependency inversion, so tests can mock it),
- throws `Failure`s for expected errors and never catches an exception just to answer HTTP.

Name pairs `XController` / `XControllerImpl`, `XRepository` / `XRepositoryImpl`, `XDao` / `XDaoImpl`, `XService` / `XServiceImpl`.

**The interface and its implementation always live in separate files** (`XDao.kt` and `XDaoImpl.kt`, in the same package). Helpers used only by the implementation, like row mappers, go in the implementation's file. The snippets below show both together only to keep them short.

### 3.1 Routes (presentation)

Only HTTP: paths, path/query parameters, reading the body, choosing the success status, responding. No validation rules, no business logic, no `try/catch`.

```kotlin
fun Route.booksRoutes() {
    val booksController by inject<BooksController>()

    route("/books") {
        get {
            call.respond(HttpStatusCode.OK, booksController.getBooks())
        }
        get("/{id}") {
            val id = call.parameters["id"] ?: throw ValidationFailure("id is required.")
            call.respond(HttpStatusCode.OK, booksController.getBook(id))
        }
        post {
            val request = call.receive<CreateBookRequest>()
            call.respond(HttpStatusCode.Created, booksController.createBook(request))
        }
    }
}
```

Register every theme in `core/plugins/Routing.kt`:

```kotlin
fun Application.configureRouting() {
    routing {
        booksRoutes()
    }
}
```

### 3.2 Controller

The logic behind an endpoint: validates the request DTO, applies the business rules, calls the repository and maps the result to a response DTO. Knows nothing about Ktor's `call`.

```kotlin
interface BooksController {
    suspend fun getBook(id: String): BookResponse
    suspend fun createBook(request: CreateBookRequest): BookResponse
}

class BooksControllerImpl(
    private val repository: AppRepository,
) : BooksController {

    override suspend fun createBook(request: CreateBookRequest): BookResponse {
        if (request.title.isBlank()) throw ValidationFailure("title must not be blank.")
        val book = repository.createBook(title = request.title, author = request.author)
        return book.toResponse()
    }
    // ...
}
```

- Validate, don't fix: reject bad input with `ValidationFailure` (400) instead of trimming or changing it silently.
- Keep validation limits (max lengths, sizes) as named constants in one place.

### 3.3 Repository

Decides **where data comes from**: database (DAOs), outside services, or both. Combines them, and turns low-level exceptions into `Failure`s (logging them first with `ErrorLogger`). Works with entities and domain models, never with DTOs.

```kotlin
interface AppRepository {
    suspend fun createBook(title: String, author: String?): BookEntity
    suspend fun getWeather(latitude: Double, longitude: Double): Weather
}

class AppRepositoryImpl(
    private val booksDao: BooksDao,
    private val weatherService: WeatherService,
    private val errorLogger: ErrorLogger,
) : AppRepository {

    override suspend fun createBook(title: String, author: String?): BookEntity {
        try {
            return booksDao.insert(title = title, author = author)
        } catch (failure: Failure) {
            throw failure
        } catch (exception: Exception) {
            errorLogger.logNonFatal(exception, "AppRepository.createBook")
            throw DatabaseErrorFailure(exception.message)
        }
    }

    override suspend fun getWeather(latitude: Double, longitude: Double): Weather =
        weatherService.getWeather(latitude, longitude) // the service already throws Failures
}
```

Use one repository per project unless the project's `CLAUDE.md` says otherwise. Split only when it grows hard to read.

### 3.4 Database (tables, entities, DAOs)

- **Schema = Flyway SQL migrations** in `resources/db/migration` (`V1__create_books.sql`). Never edit a migration that already ran: add a new one. Don't use `SchemaUtils.create` in production code.
- **Audit columns and soft delete on every table**: `created_at`, `updated_at`, `is_deleted` (default `false`), `deleted_at`. Rows are never removed: deleting is `UPDATE ... SET is_deleted = true`. A `BEFORE UPDATE` trigger sets `updated_at`, and sets `deleted_at` when `is_deleted` changes, so manual edits stay consistent and code never writes these columns. Add `CHECK (is_deleted = (deleted_at IS NOT NULL))`. Every read filters `is_deleted = false`, and unique constraints become partial unique indexes `... WHERE NOT is_deleted`, so a deleted row doesn't block a new one.

  ```sql
  CREATE FUNCTION set_audit_columns() RETURNS trigger AS $$
  BEGIN
      NEW.updated_at := now();
      IF NEW.is_deleted AND NOT OLD.is_deleted THEN
          NEW.deleted_at := now();
      ELSIF NOT NEW.is_deleted THEN
          NEW.deleted_at := NULL;
      END IF;
      RETURN NEW;
  END;
  $$ LANGUAGE plpgsql;

  CREATE TRIGGER books_set_audit_columns
      BEFORE UPDATE ON books FOR EACH ROW EXECUTE FUNCTION set_audit_columns();
  ```

  Create the function once (first migration), then one trigger per table.
- **Deleting never cascades** between independent records (deleting a child never deletes its parent, deleting a parent never deletes its children) unless the project's `CLAUDE.md` says so.
- **Tables** (`tables/`): Exposed `Table` objects that mirror the SQL. Keep column names identical to the migration.
- **Entities** (`entities/`): plain data classes for one row (the DAO's return type).
- **DAOs** (`daos/`): one per table or aggregate. Only queries, no business rules.

```kotlin
object BooksTable : Table("books") {
    val id = uuid("id")              // kotlin.uuid.Uuid (use javaUUID() for java.util.UUID)
    val title = text("title")
    val author = text("author").nullable()
    val isDeleted = bool("is_deleted")  // set to true to delete; the trigger fills deleted_at/updated_at
    override val primaryKey = PrimaryKey(id)
}

data class BookEntity(val id: Uuid, val title: String, val author: String?)

interface BooksDao {
    suspend fun insert(title: String, author: String?): BookEntity
    suspend fun findById(id: Uuid): BookEntity?
}

class BooksDaoImpl(
    private val appDatabase: AppDatabase,
) : BooksDao {

    override suspend fun findById(id: Uuid): BookEntity? = withContext(Dispatchers.IO) {
        suspendTransaction(db = appDatabase.database) {
            BooksTable.selectAll()
                .where { (BooksTable.id eq id) and (BooksTable.isDeleted eq false) }
                .map { it.toBookEntity() }
                .singleOrNull()
        }
    }
    // ...
}

private fun ResultRow.toBookEntity() = BookEntity(
    id = this[BooksTable.id],
    title = this[BooksTable.title],
    author = this[BooksTable.author],
)
```

- Imports in Exposed 1.x: `org.jetbrains.exposed.v1.core.*` (Table, operators), `org.jetbrains.exposed.v1.jdbc.*` (`selectAll`, `insert`, `Database`), `org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction`.
- `uuid()` columns use `kotlin.uuid.Uuid`. Depending on the Kotlin version this needs `@OptIn(ExperimentalUuidApi::class)`.
- JDBC blocks a thread: always run `suspendTransaction` inside `withContext(Dispatchers.IO)`.
- Work that must be atomic across tables (insert A, then B that references A) goes in **one DAO method with one transaction**, not in the repository calling two DAOs.
- Compare nullable columns with `isNotDistinctFrom`, not `eq`, when `null` must match `null`. For a nullable value use `column isNotDistinctFrom QueryParameter(value, column.columnType)`.
- "Insert if missing, then select" (`insertIgnore` + `select`) must run at `READ COMMITTED` (`suspendTransaction(db = ..., transactionIsolation = Connection.TRANSACTION_READ_COMMITTED)`). At `REPEATABLE READ`, `ON CONFLICT DO NOTHING` fails with a serialization error when a concurrent request just inserted the same row.
- Prefer database constraints (unique, check, foreign keys) for invariants. They also protect against concurrent requests.

`AppDatabase` (in `core/data/database/`) builds the HikariCP pool (`isAutoCommit = false`, since Exposed manages transactions), runs `Flyway.configure().dataSource(ds).load().migrate()`, then `Database.connect(ds)`. It's connected from `core/plugins/Database.kt` and closed on `ApplicationStopped`.

### 3.5 Services (outside systems)

Anything outside the API: third-party HTTP APIs, object storage, email, and so on. A service:
- sets up its client (base URL, headers, timeouts, credentials from config),
- exposes suspend methods that return **domain models** (`core/models`), never the provider's DTOs,
- respects the provider's limits (rate limit, User-Agent, attribution),
- converts every provider error into a specific `Failure` (e.g. `WeatherApiErrorFailure`, 502).

#### HTTP API with Ktor Client

One `HttpClient` per provider, created in the service package and provided by Koin, closed when Koin stops.

```kotlin
// core/services/weather/WeatherHttpClient.kt
fun createWeatherHttpClient(config: WeatherConfig): HttpClient = HttpClient(CIO) {
    expectSuccess = true // non-2xx responses throw ResponseException
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true }) // providers add fields at any time
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
    }
    defaultRequest {
        url(config.baseUrl + "/") // request paths are then relative: get("forecast")
        header(HttpHeaders.UserAgent, config.userAgent)
    }
}
```

```kotlin
// core/services/weather/models/WeatherForecastResponse.kt: the provider's JSON, only the fields we use
@Serializable
data class WeatherForecastResponse(
    @SerialName("temperature_2m") val temperature: Double,
)

// core/services/weather/WeatherService.kt
interface WeatherService {
    suspend fun getWeather(latitude: Double, longitude: Double): Weather
}

class WeatherServiceImpl(
    private val httpClient: HttpClient,
    private val errorLogger: ErrorLogger,
) : WeatherService {

    override suspend fun getWeather(latitude: Double, longitude: Double): Weather {
        try {
            val response: WeatherForecastResponse = httpClient.get("forecast") {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
            }.body()
            return Weather(temperatureCelsius = response.temperature)
        } catch (exception: CancellationException) {
            throw exception // never swallow coroutine cancellation
        } catch (exception: Exception) {
            // ResponseException (4xx/5xx), HttpRequestTimeoutException, IOException, serialization errors
            errorLogger.logNonFatal(exception, "WeatherService.getWeather")
            throw WeatherApiErrorFailure(exception.message)
        }
    }
}
```

**Rate-limited providers** (e.g. "max 1 request per second"): serialize calls with a `Mutex` in the service, which is a Koin `single` so the limit holds for the whole API:

```kotlin
private val mutex = Mutex()
private var lastRequestMark = TimeSource.Monotonic.markNow() - MIN_INTERVAL

private suspend fun <T> throttled(block: suspend () -> T): T = mutex.withLock {
    val elapsed = lastRequestMark.elapsedNow()
    if (elapsed < MIN_INTERVAL) delay(MIN_INTERVAL - elapsed)
    try {
        block()
    } finally {
        lastRequestMark = TimeSource.Monotonic.markNow()
    }
}

companion object {
    private val MIN_INTERVAL = 1.seconds
}
```

This only works with a single API instance. With several instances, use a shared limiter or cache.

#### Object storage (S3 API)

```kotlin
fun createS3Client(config: StorageConfig): S3Client = S3Client {
    region = config.region                              // "auto" for Cloudflare R2
    endpointUrl = Url.parse(config.endpoint)
    forcePathStyle = config.forcePathStyle             // true for RustFS/MinIO-style local servers
    credentialsProvider = StaticCredentialsProvider(
        Credentials(accessKeyId = config.accessKey, secretAccessKey = config.secretKey)
    )
    // The SDK's default checksums are an AWS feature that S3-compatible servers don't all support
    requestChecksumCalculation = RequestHttpChecksumConfig.WHEN_REQUIRED
    responseChecksumValidation = ResponseHttpChecksumConfig.WHEN_REQUIRED
}
```

The storage service uploads with `putObject` (bucket, key, `ByteStream.fromBytes(bytes)`, `contentType`) and returns the **object key**. Store the key in the database, not the URL, and build the public URL when reading (`publicBaseUrl + "/" + key`). If the database write fails after an upload, delete the object (or accept orphans and clean them up later), and say which in `CLAUDE.md`. If the key comes from a client-sent id, check that the id is free before uploading, otherwise the upload overwrites the existing object.

## 4. Dependency injection (Koin)

- Install Koin first in `Application.module()`, since every other plugin resolves from it.
- One Koin module per layer in `core/di/`: `configModule`, `loggingModule`, `databaseModule`, `daosModule`, `servicesModule`, `repositoriesModule`, `controllersModule`.
- Always bind to the interface:

```kotlin
val daosModule = module {
    single<BooksDao> { BooksDaoImpl(appDatabase = get()) }
}
val servicesModule = module {
    single(named("weather")) { createWeatherHttpClient(get()) } onClose { it?.close() }
    single<WeatherService> { WeatherServiceImpl(httpClient = get(named("weather")), errorLogger = get()) }
}
val repositoriesModule = module {
    single<AppRepository> { AppRepositoryImpl(get(), get(), get()) }
}
val controllersModule = module {
    single<BooksController> { BooksControllerImpl(repository = get()) }
}
```

- Resolve in routes and plugins with `by inject<T>()` (`org.koin.ktor.ext.inject`). Everything else receives its dependencies through the constructor. Never call `get()`/`inject()` inside controllers, repositories, DAOs or services.

## 5. Configuration

- All config in `application.yaml` as `key: "$ENV_VAR:default"`. Defaults target the local Docker Compose services, so local runs need no env vars.
- Read once into typed data classes (`AppConfig.kt`, `fun ApplicationConfig.toAppConfig()`), and provide each config class through Koin. No class reads `environment.config` directly.
- List every production variable in `.env.example`. `.env` is git-ignored. Never commit secrets.

## 6. Errors

```kotlin
abstract class Failure(
    originalExceptionMessage: String?,  // for logs only
    val statusCode: HttpStatusCode,
    val messageToClient: String,        // safe to show: no stack traces, no user data
) : Exception(originalExceptionMessage) {
    val errorCode: String get() = this::class.simpleName ?: "Failure"
}

class ValidationFailure(messageToClient: String) :
    Failure(messageToClient, HttpStatusCode.BadRequest, messageToClient)
```

- One class per meaningful error in `core/models/failures/` (`ValidationFailure` 400, `NotFoundFailure` 404, `ConflictFailure` 409, `<Provider>ApiErrorFailure` 502, `DatabaseErrorFailure` 500, `UnknownErrorFailure` 500).
- `core/plugins/StatusPages.kt` is the only place that answers errors. It responds `ErrorResponse(error = failure.errorCode, message = failure.messageToClient)` for `Failure`, 400 for Ktor's `BadRequestException` (malformed body), and logs + 500 for any other `Throwable`. It also handles 404 for unknown paths. Clients detect a specific failure by `error`, not by status alone: an unknown path also answers 404.
- `messageToClient` never contains what the user sent: name the field and the rule, not the value.
- Always rethrow `CancellationException`.

## 7. Protecting an API without user login

Any secret shipped in a client (API key in an APK, in a web bundle, or generated at install and registered with an open endpoint) can be extracted or recreated, so it doesn't prove that a request comes from your app. Use platform attestation instead, plus limits:

- **Firebase App Check**: Play Integrity (Android), App Attest / DeviceCheck (iOS), reCAPTCHA (web). The client sends the token in `X-Firebase-AppCheck`.
- Check it **locally** with Ktor's JWT authentication. No call to Google per request:

```kotlin
val jwkProvider = JwkProviderBuilder(URI("https://firebaseappcheck.googleapis.com/v1/jwks").toURL())
    .cached(10, 6, TimeUnit.HOURS)
    .rateLimited(10, 1, TimeUnit.MINUTES)
    .build()

install(Authentication) {
    jwt("app-check") {
        authHeader { call -> call.request.headers["X-Firebase-AppCheck"]?.let { HttpAuthHeader.Single("Bearer", it) } }
        verifier(jwkProvider, issuer = "https://firebaseappcheck.googleapis.com/$projectNumber") {
            withAudience("projects/$projectNumber")
        }
        validate { credential -> if (credential.payload.subject in allowedAppIds) JWTPrincipal(credential.payload) else null }
        challenge { _, _ -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse("InvalidAppCheckToken", "...")) }
    }
}

routing {
    authenticate("app-check") {
        booksRoutes()
    }
}
```

- Dependencies: `ktorLibs.server.auth` and `ktorLibs.server.auth.jwt` (brings `com.auth0:jwks-rsa`). Add the header to CORS `allowHeader`.
- Add per-IP limits with Ktor's `RateLimit` plugin, and keep request size limits and validation. Attestation raises the cost of abuse, it doesn't make it impossible.
- Local development uses App Check debug tokens. They are real signed tokens, so the API needs no special mode.

## 8. Logging

```kotlin
interface ErrorLogger {
    fun logNonFatal(throwable: Throwable, operation: String)
    fun logClientError(errorCode: String, message: String, operation: String)
}
```

- Every handled exception that is not a client error goes to `logNonFatal` once, where it is converted into a `Failure` (repository/service), or in `StatusPages` for unexpected ones. `StatusPages` doesn't log 5xx `Failure`s again: that entry would be a duplicate without the original stack trace.
- Every 4xx goes to `logClientError` at warn level, without a stack trace, to observe expected errors: `Failure`s, malformed requests and unknown paths in `StatusPages`, rejected tokens in the authentication `challenge`. Its `operation` is the method and path (`"POST /books"`), never the query string. For malformed requests, log a fixed text: Ktor's exception message can quote the body.
- `operation` = `"ClassName.methodName"`. Never put personal data (emails, names, locations, tokens) in `operation`, log messages or `messageToClient`.
- Start with a Logback implementation. A monitoring tool (Sentry, etc.) is a new implementation + one Koin binding.
- `CallLogging` logs method, path and status, never request or response bodies.

## 9. Unit tests

What to test: **controllers** (mock the repository) and **repositories** (mock DAOs, services, `ErrorLogger`). Route tests with `testApplication` are optional. DAOs and services are only tested if the project's `CLAUDE.md` asks for it.

### 9.1 Class setup
- Never start Koin or the Ktor application in unit tests. Build the class under test by hand.
- Mocks are `private lateinit var`, created with strict `mockk()` (never `relaxed = true`) in `@BeforeEach`, together with the class under test.
- Mock behavior goes inside each test, not in `setup`.
- Add `@AfterEach` with `unmockkAll()` only when using `mockkObject`/`mockkStatic`.
- Classes that receive a `CoroutineDispatcher` get a test dispatcher from `runTest`'s scheduler (`StandardTestDispatcher(testScheduler)`). Never mock a dispatcher.

```kotlin
class BooksControllerImplTest {

    private lateinit var repository: AppRepository
    private lateinit var controller: BooksController

    @BeforeEach
    fun setup() {
        repository = mockk()
        controller = BooksControllerImpl(repository = repository)
    }

    @Test
    fun `if title is blank should throw ValidationFailure and not call the repository`() = runTest {
        // Given
        val fakeRequest = CreateBookRequest(title = "  ", author = "Fake Author")

        // When / Then
        assertThrows<ValidationFailure> {
            controller.createBook(fakeRequest)
        }
        coVerify(exactly = 0) { repository.createBook(any(), any()) }
    }

    @Test
    fun `if request is valid should create the book and return it`() = runTest {
        // Given
        val fakeBook = BookEntity(id = Uuid.random(), title = "Fake Title", author = null)
        coEvery { repository.createBook("Fake Title", null) } returns fakeBook

        // When
        val response = controller.createBook(CreateBookRequest(title = "Fake Title", author = null))

        // Then
        assertEquals(fakeBook.id.toString(), response.id)
        coVerify(exactly = 1) { repository.createBook("Fake Title", null) }
    }
}
```

### 9.2 Test rules
- Name: `` `if <condition> should <expected behavior>` ``, in English, with backticks.
- Body: `// Given`, `// When`, `// Then` comments (`// When / Then` with `assertThrows`).
- Coroutines: `runTest` (`kotlinx.coroutines.test.runTest`).
- Mocking: `every` for regular functions, `coEvery` for suspend, `just runs` for `Unit`, `slot`/`capture` to inspect arguments.
- Verification: `verify`/`coVerify` always with `exactly = N`. Use `exactly = 0` to state what must not be called.
- Assertions from `org.junit.jupiter.api.Assertions` (`assertEquals`, `assertTrue`, `assertNull`, `assertInstanceOf`) and `org.junit.jupiter.api.assertThrows`. No `try/catch` in tests, no Kotlin `assert()`.
- Fake data: `fake` prefix, values clearly fake (`fake.user@example.com`, `"fake-id"`).
- Cover: happy path, each validation rule, null and empty values, boundaries, and each error path (DAO/service throws → repository logs once and throws the right `Failure`).

## 10. Checklist: adding an endpoint

1. Migration (`V<n>__...sql`) if the schema changes, then table + entity + DAO method (interface and impl).
2. Service method if it needs an outside system.
3. Repository method (interface and impl), with error conversion and logging.
4. Request/response DTOs in `routes/<theme>/models/` (`@Serializable`). Never serialize entities directly.
5. Controller method (interface and impl) with validation.
6. Route in `<Theme>Routes.kt`, registered in `core/plugins/Routing.kt` inside the right `authenticate` block.
7. Koin bindings for anything new.
8. New `Failure` classes if a new error case appears.
9. New config in `application.yaml` + `AppConfig` + `.env.example`.
10. Unit tests for the controller and repository methods.
11. Update the project's `CLAUDE.md` if a rule or decision changed.
