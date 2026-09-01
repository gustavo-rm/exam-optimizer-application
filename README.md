# 🧠 Dynamic Study Planner API

> An optimization engine for study plans built with Artificial Intelligence (Genetic Algorithms) and founded on Learning Science Theories.

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Architecture](https://img.shields.io/badge/Architecture-DDD%20%7C%20Stateless-blueviolet?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)

## 📖 About the Project

The **Dynamic Study Planner** solves the chronic problem of static and generic ("one-size-fits-all") study plans used in preparation for high-performance exams (public contests, university entrance exams, certifications).

The system operates through a **Stateless RESTful API** that receives a "snapshot" of the student's current state (time availability and knowledge gaps) and the rules of the exam syllabus. In seconds, it processes a strategic optimization and a tactical daily schedule, generating a realistic and mathematically superior plan.

The objective function (Fitness) is not arbitrary: two of its three terms are derived from established
results in learning science, and the derivation, the weights and — importantly — the limits of each
approximation are documented in
[`docs/revisao-ag/05-fitness-function.md`](docs/revisao-ag/05-fitness-function.md).

## 🔬 Theoretical Foundation

Every fitness term is normalised to `[0,1]` and combined by a weighted sum whose weights sum to 1.

1. **Syllabus coverage weighted by exam value** — *(weight 0.50)*. Each subject contributes in
   proportion to what it is worth on the exam, multiplied by how much of it the plan can actually
   teach, modelled as an exponential approach to a mastery ceiling. The **knowledge gap
   (`knowledgeGaps`) declared by the student personalises that weighting: the larger the declared
   gap, the higher the subject's priority.** That is a monotonic triage rule — it is not attributed
   to any learning theory, and in particular it is *not* an implementation of Ausubel's meaningful
   learning (see below).
2. **The Forgetting Curve (Hermann Ebbinghaus)** — *(weight 0.30)*. Derived directly from
   `R = e^(−t/S)`: recall falls to `e⁻¹` after one stability interval, so a subject needs roughly
   `horizon / τ` sessions to stay above the forgetting threshold until exam day. The fitness scores
   the importance-weighted fraction of the syllabus that gets them.
   **This is a mean-field approximation, not spaced repetition proper** — the macro chromosome has no
   calendar, so the term knows *how many* sessions a subject gets, not *when*.
3. **Cognitive Load Theory (John Sweller)** — *(weight 0.20)*. `Subject.cognitiveLoad` is the
   intrinsic-load proxy; `CognitiveLoadCalculator` turns availability and psychological state into a
   sustainable daily budget, and the fitness penalises plans whose expected daily load exceeds it.
   **This bounds the *expected* daily load, not the load of a single learning episode**, which is
   what Sweller's construct is actually about.

### What this system does **not** do

**Ausubel's meaningful learning / prerequisite sequencing is not implemented.** The macro chromosome
is `Map<Subject, Integer>` — a count of days with no ordering — so precedence between topics is not
expressible, and the API does not collect prerequisite data in the first place. Earlier versions of
this README attributed Ausubel to the knowledge-gap multiplier; that attribution was incorrect and
has been removed. The reasoning, the two options considered and the decision are recorded in
[`docs/revisao-ag/06-decisao-ausubel.md`](docs/revisao-ag/06-decisao-ausubel.md).

## 🚀 Features

* **Evolutionary Optimization:** Uses a Genetic Algorithm built from scratch (with Tournament Selection, Creep Mutation, and Hybrid Crossover) to find the best allocation of effort over months of study.
* **Dynamic Scheduling:** Converts the macro plan into a tactical daily schedule, based on the exact availability of hours the student has per day of the week.
* **Self-Calibrating:** The system does not require the user to input technical metrics (such as mental effort hour limits). The `CognitiveLoadCalculator` class infers the daily endurance limit by crossing the difficulty of the syllabus with the student's confidence.
* **Resilient & Stateless Architecture:** Designed for the cloud. It keeps no state, meaning the student can update their gaps weekly and receive a 100% re-optimized plan ("re-planning from scratch"), ensuring total adaptability over time.

## 💻 Tech Stack

* **Language:** Java 21
* **Framework:** Spring Boot 3.5.x
* **Security & Validation:** Spring Security (stateless config), Spring Boot Validation (Jakarta Bean Validation API 3.1)
* **API Documentation:** Springdoc OpenAPI (Swagger UI)
* **Observability:** Spring Boot Actuator, Micrometer (Prometheus, Brave Tracing)
* **Logging:** SLF4J, Logback with Logstash Logback Encoder (Structured JSON logging)
* **Testing:** JUnit 5, Mockito, Spring Boot Test
* **Build Tool:** Maven

## 🏗️ Architecture

This project was built prioritizing a clean and extensible design:

* **Architectural Style:** Stateless REST API, Hexagonal Architecture elements (UseCases).
* **Domain-Driven Design (DDD):** Pure domain models isolated from framework rules. Entities and Value Objects handle business logic.
* **Design Patterns:**
  * **Strategy:** Allows composing and changing the behavior of the schedule generator and genetic operators.
  * **Factory:** Decouples object creation (e.g., GeneticAlgorithmFactory, StudyPlanFactory).
  * **Dependency Injection (IoC):** Extensive use of Spring containers to manage calculators and services.
* **Async Processing:** Heavy, CPU-bound Genetic Algorithm tasks are offloaded to a dedicated `ThreadPoolTaskExecutor` using Spring's `@Async` and `CompletableFuture`. A timeout mechanism ensures requests don't hang indefinitely.

## 📂 Project Structure

The directory organization reflects a clear separation of responsibilities:

```text
src/main/java/com/ia/project/dynamicstudyplanner/
├── api/                             # Controllers, DTOs, Mappers, Global Exception Handling (RFC 7807)
├── config/                          # Global Config (Async, Security, OpenAPI)
├── domain/                          # Pure Domain Models (Entities, Value Objects, Domain Exceptions)
├── ga/                              # Genetic Algorithm Engine (Factories, Strategies, Context, Individual, Population)
├── service/                         # Business Logic, Calculators, Scheduling Strategies
└── usecase/                         # Application Use Cases
```

## ✅ Requirements

* **Java:** Version 21
* **Maven:** 3.8+ (or use the provided Maven Wrapper `./mvnw`)

## ⚙️ Environment Variables

The application can be configured via `application.properties` or environment variables:

| Variable / Property | Default Value | Description |
|----------------------|---------------|-------------|
| `spring.profiles.active` | `dev` | Active Spring profile. |
| `server.forward-headers-strategy` | `framework` | Strategy for extracting client IPs from X-Forwarded-For headers. |
| `api.rate-limit.capacity` | `5` | Bucket4j Rate Limiting capacity. |
| `api.rate-limit.refill-tokens` | `5` | Tokens refilled per duration. |
| `api.rate-limit.refill-duration-minutes` | `1` | Refill duration in minutes. |
| `optimizer.thread-pool-size` | `8` | Size of the dedicated thread pool for CPU-bound tasks. |

## 🛠️ Installation & Running the Project

### Local Execution (Maven)

1. Clone the repository:
   ```bash
   git clone https://github.com/gustavo-rm/exam-optimizer-application.git
   cd DynamicStudyPlanner
   ```

2. Build the project:
   ```bash
   ./mvnw clean install
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

   Alternatively, you can run the generated `.jar` file:
   ```bash
   java -jar target/DynamicStudyPlanner-2.0.1.jar
   ```

The application will start on port `8080` by default.

## 📚 API Documentation

Once the application is running, you can access the Swagger UI to interact with the API:

* **Swagger UI:** `http://localhost:8080/swagger-ui.html`
* **OpenAPI Docs:** `http://localhost:8080/v3/api-docs`

### Main Endpoint

**`POST /api/v1/optimizer/generate`**

Generates an optimized study plan.

**Request Body (Example):**
Contains details about the exam, student profile, and Genetic Algorithm configuration.

**Response:**
Returns a detailed daily study schedule along with the genetic algorithm's optimization metadata.

**Error Handling:**
The API uses a centralized exception handling strategy via `@RestControllerAdvice`, returning standardized **RFC 7807 Problem Details** for all errors (e.g., `400 Bad Request` for validation failures, `408 Request Timeout` if the algorithm takes too long, `429 Too Many Requests` for rate limiting).

## 🗄️ Database

This application is **completely stateless** and does not use a database. It processes the input payload, runs the optimization algorithms in memory, and returns the result in real-time. This design choice maximizes horizontal scalability and simplifies deployment.

## 🧪 Testing

The project uses JUnit 5 and Mockito.

To run the tests:
```bash
./mvnw test
```

## 🔒 Security

* **Stateless:** The API is stateless and does not maintain sessions.
* **Public Access:** Currently configured to permit public access (`permitAll()`) to all `/api/v1/**` endpoints and Swagger UI, as it functions as an open optimization engine. CSRF is disabled.
* **Rate Limiting:** Protects against DoS attacks by limiting requests to computationally expensive endpoints using Bucket4j and Caffeine Cache. Returns a `429 Too Many Requests` response when exceeded.
* **Input Validation:** Strict `jakarta.validation` constraints (`@Max`, `@Min`, `@Size`, `@Valid`) protect against CPU and memory exhaustion via malicious payloads.

## ⚡ Performance and Scalability

* **Asynchronous Processing:** CPU-bound genetic algorithm calculations are executed on a dedicated thread pool (`@Async`), protecting the main Tomcat HTTP threads from exhaustion.
* **Fail-Fast:** Bounded queues on the task executor prevent memory exhaustion. Timeouts (`30 seconds`) ensure threads are not blocked indefinitely.
* **Statelessness:** The absence of a database or session state allows the application to be horizontally scaled indefinitely.
* **Caching:** High-frequency access patterns within the Genetic Algorithm (e.g., retrieving fittest individuals) are cached internally to avoid redundant computations.

## 📊 Logging and Monitoring

* **Observability:** Exposes `/actuator/health` and `/actuator/prometheus` endpoints for monitoring.
* **Metrics:** Uses Micrometer to track business and system metrics.
* **Distributed Tracing:** Micrometer Tracing (Brave bridge) is integrated. The `MdcTaskDecorator` ensures trace contexts (`traceId`, `spanId`) are propagated across asynchronous threads.
* **Structured Logging:** Configured to output JSON logs via Logback (`logstash-logback-encoder`), making it enterprise-ready for ingestion by ELK stacks or Datadog.

## 🔮 Future Improvements

* Containerize the application with Docker and provide a `docker-compose.yml`.
* Implement CI/CD pipelines (e.g., GitHub Actions) for automated testing and building.
* Add integration with an external monitoring stack (Grafana dashboards for Prometheus metrics).

## 🤝 Contributing

Contributions are welcome! If you would like to contribute:
1. Fork the repository.
2. Create a feature branch (`git checkout -b feature/AmazingFeature`).
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`).
4. Push to the branch (`git push origin feature/AmazingFeature`).
5. Open a Pull Request.

## 📄 License

Distributed under the MIT License. See `LICENSE` for more information.

## 👨‍💻 Author

Gustavo Malacarne (Software Engineer) - dynamic-study-planner
* Software Engineer focused on building scalable applications, backend architectures and AI-driven solutions. 
* Master’s degree in Computer Science with specialization in Artificial Intelligence, Machine Learning and software engineering practices. 
* Passionate about technology, research and developing innovative systems that combine performance, maintainability and intelligent decision-making.

## 🧹 Code Quality

The project prioritizes clean code and standard enterprise practices:
* **Linting and Formatting:** (Assuming IDE defaults, further configuration via Checkstyle or Spotless is recommended).
* **Static Analysis:** (Can be integrated via SonarQube or similar tools in future CI pipelines).
* **Architectural Standards:** Strict adherence to Domain-Driven Design principles with bounded contexts mapped to packages. Code smells and anti-patterns are actively refactored during reviews.

## 🚀 Deployment

Currently, the application runs via Maven wrapper locally. For production deployment:
1. Package the application: `./mvnw clean package`
2. Run the generated artifact: `java -jar target/DynamicStudyPlanner-2.0.1.jar`

(Future state: Dockerfile generation to deploy to container orchestrators like Kubernetes or AWS ECS).
