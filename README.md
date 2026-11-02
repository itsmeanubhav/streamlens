# StreamLens

StreamLens is a small project I've been working on to make Java Stream API easier to understand.

The idea is simple: write a Java Stream pipeline, run it, and see what happens at each step. Instead of looking only at the final result, you can follow how elements move through operations like `filter()`, `map()`, `sorted()` and `collect()`.

It also provides explanations for individual operations, along with the intermediate results generated during execution.

## What it does

- Takes Java code containing Stream API pipelines as input.
- Identifies stream operations in the code.
- Compiles and executes the pipeline.
- Shows the intermediate results at different stages.
- Explains what each operation is doing.
- Supports multiple independent pipelines.
- Can use Gemini for additional explanations (optional).

## Supported operations

Some of the operations currently supported are:

| Category | Operations |
|---|---|
| Filtering | `filter()` |
| Transformation | `map()`, `flatMap()` |
| Sorting | `sorted()` |
| Duplicate removal | `distinct()` |
| Limiting | `limit()`, `skip()` |
| Inspection | `peek()` |
| Collection | `collect()` |
| Grouping | `groupingBy()`, `partitioningBy()` |
| Aggregation | `reduce()`, `count()`, `sum()`, `average()` |
| Matching | `anyMatch()`, `allMatch()`, `noneMatch()` |
| Finding | `findFirst()`, `findAny()` |
| Comparison | `min()`, `max()` |
| Primitive streams | `mapToInt()`, `mapToLong()`, `mapToDouble()`, `boxed()` |

Support for some operations depends on the pipeline structure and input code.

## Tech stack

- Java 17
- Spring Boot 3
- JavaParser
- Java Stream API
- Maven
- HTML, CSS and JavaScript
- Gemini API (optional)

## How it works

The frontend sends the Java code to the Spring Boot backend.

JavaParser is used to analyse the source code and identify the Stream API pipelines. The application then extracts the operations and prepares the code for execution.

The generated Java code is compiled using `javac` and executed in a separate JVM process. StreamLens captures the results at different stages and sends them back to the frontend.

The frontend displays the pipeline, its operations and the corresponding results.

The frontend is part of the Spring Boot application itself, so there is no separate frontend server or build process.

## Running locally

### Requirements

- JDK 17 or later (a full JDK is required because the application uses `javac`).
- Maven 3.8+ or Maven Wrapper.
- Git.

### Clone the project

```bash
git clone https://github.com/YOUR_USERNAME/streamlens.git
cd streamlens
```

### Build

On Windows:

```bash
mvnw.cmd clean package
```

On Linux/macOS:

```bash
./mvnw clean package
```

Or use Maven directly:

```bash
mvn clean package
```

### Start the application

```bash
mvn spring-boot:run
```

Open the application in your browser:

http://localhost:8080

## Gemini configuration (optional)

Gemini can be enabled to provide additional explanations for the analysed pipelines.

Get an API key from [Google AI Studio](https://aistudio.google.com/apikey).

On Windows PowerShell:

```powershell
$env:GEMINI_API_KEY="your-api-key"
.\mvnw.cmd spring-boot:run
```

On Linux/macOS:

```bash
export GEMINI_API_KEY="your-api-key"
./mvnw spring-boot:run
```

The application can still perform its core analysis without a Gemini API key.

Please don't commit API keys to the repository.

## API

The main analysis endpoint is:

```http
POST /api/analyze
```

Example request:

```json
{
  "code": "List<Integer> numbers = Arrays.asList(10, 20, 30, 40); List<Integer> result = numbers.stream().filter(n -> n > 20).map(n -> n * 2).collect(Collectors.toList());"
}
```

The response includes the extracted pipelines, operations, execution results and explanations.

## Deployment

The application can be deployed as a single Spring Boot web service.

I've been using Render for the initial deployment. The application needs a full JDK at runtime because it compiles Java code dynamically.

For a Docker deployment, use a JDK-based image rather than a JRE-only image.

Render's free service is useful for an initial deployment, although it can sleep after a period of inactivity.

## A note about execution

StreamLens compiles and executes Java code submitted through the application. It currently uses a separate operating-system process for execution, along with timeout and resource controls.

This is an area that needs further hardening before unrestricted public use. Process-level execution and source-code validation alone should not be considered a complete security sandbox.

## What's next

There are a few things I'd like to improve as the project develops:

- Better support for complex Java code and custom objects.
- More Stream API operations.
- Improved pipeline visualisation.
- Better handling of intermediate results.
- More examples for common Java interview problems.
- Improved execution isolation and security.

---

I'm continuing to improve StreamLens as I work on it. Suggestions and feedback are welcome.
