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

### Running the Item Sync

There are two ways to run the item synchronization:

#### Option 1: Using the ItemSyncUtil

Run the `ItemSyncUtil` class directly:

```bash
./mvnw compile exec:java -Dexec.mainClass="com.tellenn.artifacts.utils.ItemSyncUtil"
```

This will start the application with the `item-sync` profile active, which triggers the sync process.

#### Option 2: Enabling the Profile in application.properties

1. Uncomment the following line in `application.properties`:

```properties
spring.profiles.active=item-sync
```

2. Start the application normally:

```bash
./mvnw spring-boot:run
```

### How It Works

The sync process:

1. Connects to MongoDB
2. Empties the `items` collection
3. Fetches all items from the Artifacts API, handling pagination automatically
4. Stores all items in the MongoDB database

The `ItemSyncService` class provides the `syncAllItems()` method that can also be called programmatically from your code.

## Database Client

The project includes a client-like interface for querying the MongoDB database, similar to how the API clients work.

### Using the DatabaseClient

The `DatabaseClient` class provides methods to query items from the database:

```kotlin
// Inject the DatabaseClient
@Autowired
private lateinit var databaseClient: DatabaseClient

// Get all items (paginated)
val allItems = databaseClient.getItems(page = 1, size = 10)

// Search items by name
val searchResult = databaseClient.getItems(name = "sword")

// Filter items by type
val weaponItems = databaseClient.getItems(type = "weapon")

// Get item details by code
val itemDetails = databaseClient.getItemDetails("ITEM_CODE")
```

### Filtering Options

The `getItems()` method supports the following filters:

- `name`: Filter by item name (case-insensitive, partial match)
- `type`: Filter by item type (exact match)
- `rarity`: Filter by item rarity (exact match)
- `level`: Filter by item level (exact match)
- `equippable`: Filter by whether the item is equippable
- `usable`: Filter by whether the item is usable
- `stackable`: Filter by whether the item is stackable
- `slot`: Filter by equipment slot

### Pagination

Both the API client and database client support pagination:

- `page`: The page number (starting from 1)
- `size`: The number of items per page

### Running the Example

To run the DatabaseClient example:

1. First, make sure you've synced items to the database using one of the item sync methods.

2. Add the following line to `application.properties`:

```properties
spring.profiles.active=db-client-example
```

3. Start the application:

```bash
./mvnw spring-boot:run
```

The example will query items from the database and log the results.

## Development

### Building the Project

```bash
./mvnw clean install
```

### Running with Docker

The application can be containerized using Docker. The project includes a Dockerfile and docker-compose.yml for easy deployment.

#### Building the Docker Image

```bash
docker build --platform linux/arm64/v8 -t tellenn/tellenn-artifacts-client:latest .
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

#### Test Structure

The test suite includes:

- **MongoTestConfiguration**: Sets up the MongoDB Testcontainer and configures Spring Data MongoDB to use it
- **ItemRepositoryTest**: Tests the MongoDB repository operations (CRUD operations, custom queries)
- **DatabaseClientTest**: Tests the client interface for querying the database
- **ItemSyncServiceTest**: Tests the synchronization service that fetches items from the API and stores them in MongoDB

#### Running the Tests

To run the tests, you need:

1. Docker installed and running on your machine
2. JDK 17 or higher

Then run:

```bash
./mvnw test
```

The tests will automatically:
1. Download the MongoDB Docker image (if not already present)
2. Start a MongoDB container
3. Run the tests against the container
4. Stop and remove the container when done
