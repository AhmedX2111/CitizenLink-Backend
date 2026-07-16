# Request Flow

These diagrams describe how an HTTP request moves through the Spring Boot backend. `CorsConfig` allows requests from the Angular development client at `http://localhost:4200`.

## Layered request flow

```mermaid
flowchart LR
    Client["Angular client on http://localhost:4200"]
    Cors["CORS handling<br/>CorsConfig.java<br/>Allows localhost:4200 and configured HTTP methods"]
    Dispatcher["Spring MVC dispatcher and route matching<br/>Selects the controller from request mappings"]
    Binding["Request binding and validation<br/>Deserializes DTOs and applies Jakarta validation"]
    Controller["REST controller<br/>Receives request DTOs and returns ResponseEntity"]
    Service["Service implementation<br/>Applies business rules, workflows, and mapping"]
    Repository["Spring Data JPA repository"]
    Database[("PostgreSQL")]
    Handler["GlobalExceptionHandler<br/>Maps validation, business, data,<br/>and unexpected exceptions to ErrorResponse"]

    Client -->|"HTTP request"| Cors
    Cors -->|"Forward allowed request"| Dispatcher
    Dispatcher -->|"Bind request parameters or body"| Binding
    Binding -->|"Invoke matched endpoint"| Controller
    Controller -->|"Invokes business operation"| Service
    Service -->|"Reads or writes entities"| Repository
    Repository -->|"Executes SQL through JPA and Hibernate"| Database
    Database -->|"Returns query or mutation result"| Repository
    Repository -->|"Returns entities or projections"| Service
    Service -->|"Returns response data"| Controller
    Controller -->|"Return response body and status"| Dispatcher
    Dispatcher -->|"HTTP response"| Client

    Binding -.->|"Propagates validation exceptions"| Handler
    Controller -.->|"Propagates controller exceptions"| Handler
    Service -.->|"Propagates business exceptions"| Handler
    Repository -.->|"Propagates data-access exceptions"| Handler
    Handler -->|"Returns structured ErrorResponse"| Dispatcher
```

## Successful request sequence

```mermaid
sequenceDiagram
    participant Client as "Angular client on :4200"
    participant Cors as "CorsConfig"
    participant Dispatcher as "Spring MVC dispatcher"
    participant Controller as "REST controller"
    participant Service as "Service implementation"
    participant Repository as "Spring Data JPA repository"
    participant Database as "PostgreSQL"

    Client->>Cors: "Send HTTP request"
    Cors->>Dispatcher: "Forward allowed request"
    Dispatcher->>Controller: "Bind input and invoke matched endpoint"
    Controller->>Service: "Call business operation"
    Service->>Repository: "Read or persist entities"
    Repository->>Database: "Execute SQL through JPA and Hibernate"
    Database-->>Repository: "Return rows or mutation result"
    Repository-->>Service: "Return entities or projections"
    Service-->>Controller: "Return mapped response data"
    Controller-->>Dispatcher: "Return ResponseEntity"
    Dispatcher-->>Client: "Send HTTP status and response body"
```

## Exception response flow

`GlobalExceptionHandler` converts exceptions propagated from request processing into the shared `ErrorResponse` envelope.

```mermaid
flowchart TD
    Failure["Exception reaches GlobalExceptionHandler"]
    Kind{"Exception type"}
    Validation["MethodArgumentNotValidException<br/>400 VALIDATION_ERROR"]
    Missing["ResourceNotFoundException<br/>404 NOT_FOUND"]
    Conflict["IllegalTransitionException<br/>DataIntegrityViolationException<br/>DuplicateResourceException<br/>409 conflict response"]
    Argument["IllegalArgumentException<br/>400 BAD_REQUEST"]
    Unexpected["Any other Exception<br/>500 INTERNAL_ERROR"]
    Envelope["Build ErrorResponse<br/>code, message, and optional field details"]
    Client["Return JSON error response to the client"]

    Failure -->|"Select matching @ExceptionHandler method"| Kind
    Kind -->|"Validation failure"| Validation
    Kind -->|"Resource missing"| Missing
    Kind -->|"Workflow or data conflict"| Conflict
    Kind -->|"Invalid business argument"| Argument
    Kind -->|"Unhandled failure"| Unexpected
    Validation -->|"Create response body"| Envelope
    Missing -->|"Create response body"| Envelope
    Conflict -->|"Create response body"| Envelope
    Argument -->|"Create response body"| Envelope
    Unexpected -->|"Create response body"| Envelope
    Envelope -->|"Send mapped HTTP status"| Client
```
