# Tellenn Artifacts Client

This is a client library for interacting with the Artifacts MMO API.

## Features

- API clients for various endpoints
- MongoDB integration for data persistence
- Item synchronization utility

## MongoDB Item Synchronization

The project includes functionality to synchronize all items from the Artifacts API to a local MongoDB database.

### Prerequisites

- MongoDB server running on localhost:27017 (or configure the connection in `application.properties`)
- Java 11 or higher

### Configuration

MongoDB connection settings can be configured in `application.properties`:

```properties
spring.data.mongodb.host=localhost
spring.data.mongodb.port=27017
spring.data.mongodb.database=artifacts
```
## Development

### Building the Project

```bash
./mvnw clean install
```

### Running with Docker

The application can be containerized using Docker. The project includes a Dockerfile and docker-compose.yml for easy deployment.

#### Building the Docker Image

```bash
docker build llenn/tellenn-artifacts-client:latest .
```

#### Running with Docker Compose

The docker-compose.yml file sets up both the application and a MongoDB instance:

```bash
docker-compose up
```

This will:
1. Build the application image if it doesn't exist
2. Start a MongoDB container
3. Start the application container connected to MongoDB
4. Map port 8080 for the application and 27017 for MongoDB

#### Environment Variables

The following environment variables can be configured:

- `SPRING_DATA_MONGODB_URI`: MongoDB connection URI (default: mongodb://mongo:27017/tellenn-artifacts)

You can override these in the docker-compose.yml file or by setting environment variables.

### Running Tests

```bash
./mvnw test
```

### Testing with Testcontainers

The project uses Testcontainers to test database interactions with a real MongoDB instance running in a Docker container. This approach provides several benefits:

1. Tests run against a real MongoDB instance, not an in-memory mock
2. Each test runs in isolation with a clean database state
3. No need to set up a separate MongoDB instance for testing
4. Tests are more reliable and closer to production behavior
