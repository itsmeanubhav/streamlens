# StreamLens

**Understand Java Stream API execution, one step at a time.**

StreamLens is an interactive Java Stream API visualizer that helps developers understand how data flows through a stream pipeline. It analyses Java source code, identifies stream operations, executes the pipeline and presents intermediate results alongside explanations of each operation.

The goal is to make Java Stream API behaviour easier to understand, debug and learn.

## Features

- **Pipeline Analysis:** Identifies Java Stream API pipelines and their operations.
- **Step-by-Step Execution:** Displays how data changes as it passes through intermediate operations.
- **Intermediate Results:** Shows the values produced at different stages of a pipeline.
- **Multiple Pipelines:** Analyses independent stream pipelines within a Java program.
- **Operation Explanations:** Provides descriptions of intermediate and terminal operations.
- **AI-Assisted Explanations:** Optional Gemini integration for additional natural-language explanations.
- **Interactive Interface:** Explore pipeline execution and individual operation results through a web interface.

## Supported Operations

StreamLens supports commonly used Java Stream API operations, including:

| Category           | Operations                                              |
| ------------------ | ------------------------------------------------------- |
| Filtering          | `filter()`                                              |
| Transformation     | `map()`, `flatMap()`                                    |
| Sorting            | `sorted()`                                              |
| Duplicate handling | `distinct()`                                            |
| Limiting           | `limit()`, `skip()`                                     |
| Inspection         | `peek()`                                                |
| Collection         | `collect()`                                             |
| Grouping           | `groupingBy()`, `partitioningBy()`                      |
| Aggregation        | `reduce()`, `count()`, `sum()`, `average()`             |
| Matching           | `anyMatch()`, `allMatch()`, `noneMatch()`               |
| Finding            | `findFirst()`, `findAny()`                              |
| Comparison         | `min()`, `max()`                                        |
| Primitive streams  | `mapToInt()`, `mapToLong()`, `mapToDouble()`, `boxed()` |

Support depends on the operation and pipeline structure. Additional operations and Java constructs can be introduced progressively.

## Technology Stack

**Backend**

- Java 17
- Spring Boot 3
- JavaParser
- Maven
- Java Stream API

**Frontend**

- HTML
- CSS
- JavaScript

**Additional Integration**

- Gemini API for optional enhanced explanations
- Dynamic Java compilation and subprocess execution

## Architecture

StreamLens follows a backend-driven architecture.

1. Theupported operations and constructs an execution representation.
2. Java source is compiled an frontend submits Java source code to the Spring Boot application.
3. JavaParser analyses the source and identifies stream pipelines.
4. The application extracts sd executed in a separate process.
5. Intermediate execution results are collected and associated with pipeline operations.
6. The analysis results are returned to the frontend for visualisation.

The application serves its frontend directly through Spring Boot, so a separate frontend build or hosting service is not required.

## Getting Started

### Prerequisites

- JDK 17 or later (a full JDK is required).
- Maven 3.8 or later, or the included Maven Wrapper.
- Git.

A full JDK is necessary because StreamLens uses `javac` and `java` to compile and execute generated Java source code.

### Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/streamlens.git
cd streamlens
```

### Build the Application

Using Maven Wrapper:

**Windows**

```bash
mvnw.cmd clean package
```

**Linux / macOS**

```bash
./mvnw clean package
```

Alternatively:

```bash
mvn clean package
```

### Run the Application

```bash
mvn spring-boot:run
```

Once the application starts, open:

[http://localhost:8080](http://localhost:8080)

The frontend is served directly by Spring Boot.

## Optional AI Integration

StreamLens can use the Gemini API to provide additional natural-language explanations.

To enable this integration:

1. Obtain an API key from [Google AI Studio](https://aistudio.google.com/apikey).
2. Configure the `GEMINI_API_KEY` environment variable.

**Windows PowerShell**

```powershell
$env:GEMINI_API_KEY="your-api-key"
.\mvnw.cmd spring-boot:run
```

**Linux / macOS**

```bash
export GEMINI_API_KEY="your-api-key"
./mvnw spring-boot:run
```

The core pipeline analysis and execution functionality is designed to work independently of the optional AI integration.

Never commit API keys or other credentials to the repository.

## API

### Analyse Java Code

**Endpoint**

```http
POST /api/analyze
```

**Content-Type**

```http
application/json
```

**Example Request**

```json
{
  "code": "List<Integer> numbers = Arrays.asList(10, 20, 30, 40); List<Integer> result = numbers.stream().filter(n -> n > 20).map(n -> n * 2).collect(Collectors.toList());"
}
```

The response contains the extracted pipeline information, operations, execution results and available explanations.

## Deployment

StreamLens can be deployed as a Docker-based Spring Boot web application.

The deployment requires a full JDK because Java compilation takes place at runtime.

For a Render deployment:

1. Push the project to a GitHub repository.
2. Create a new Web Service in [Render](https://render.com).
3. Connect the GitHub repository.
4. Select Docker as the runtime.
5. Configure the appropriate environment variables.
6. Deploy the application.

The application can serve both its frontend and backend through a single public URL.

For initial deployments, Render's free hosting option can be used subject to its resource limits and availability.

## Execution and Security Considerations

StreamLens compiles and executes Java source code supplied through its analysis interface.

Execution currently uses a separate operating-system process with execution timeouts and resource controls. Source validation and restrictions on unsupported Java functionality provide additional safeguards.

These measures should not be considered equivalent to a hardened multi-tenant sandbox. Untrusted public execution requires additional operating-system or container-level isolation, resource enforcement and security testing.

## Project Structure

```text
streamlens/
│
├── src/
│   └── main/
│       ├── java/
│       │   └── com/streamlens/
│       │       ├── controller/
│       │       ├── service/
│       │       ├── model/
│       │       └── ...
│       │
│       └── resources/
│           ├── static/
│           │   └── index.html
│           └── application.properties
│
├── pom.xml
├── Dockerfile
├── .gitignore
└── README.md
```

## Future Enhancements

- Expanded Java language and Stream API support.
- Improved pipeline visualisation.
- Detailed execution statistics.
- Interactive operation-level explanations.
- Support for custom Java classes and complex data structures.
- Additional examples and interview practice problems.
- Enhanced execution isolation and security controls.

---

**StreamLens — Explore. Execute. Understand.**
