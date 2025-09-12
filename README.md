# VK Bot Application

A Quarkus-based VK bot that processes incoming messages and provides AI-powered responses. The project follows clean architecture principles with clear separation of concerns, ensuring maintainability and scalability.

## Features

- ✅ Clean architecture implementation with domain-driven design
- ✅ VK API integration
- ✅ AI-powered responses using LangChain4j
- ✅ Event-driven architecture with RabbitMQ
- ✅ Docker support for multiple deployment options (JVM, native)
- ✅ RESTful API endpoints for VK webhook integration

## Technologies Used

- **Quarkus** (Java/Kotlin) - Reactive, cloud-native framework
- **Kotlin** - Modern, concise programming language
- **LangChain4j** - AI integration framework for conversational AI
- **RabbitMQ** - Event-driven message brokering
- **Docker** - Containerization for consistent deployments
- **RESTEasy** - JAX-RS implementation for HTTP endpoints
- **Jackson** - JSON processing library
- **Quarkus DevServices** - Automatic service provisioning for development

## Prerequisites

- Java 21 (for building and running)
- Docker (optional, for containerized deployment)
- VK developer account and group

## Setup and Configuration

### 1. Create VK Group and Configure Callback

1. Create a VK group (if not already created)
2. Go to **Community Management → API**
3. Set up a callback server:
   - **URL**: `https://your-domain.com/vk/callback`
   - **Secret**: Choose a secure secret key
   - **Confirmation code**: Choose a confirmation code (e.g., `123456`)
4. Get the group's API token from the API section

### 2. Configure Application

You can configure the application using environment variables or by modifying `application.yml`:

#### Using Environment Variables

| Environment Variable | Description |
|----------------------|-------------|
| `VK_SECRET` | The secret key from VK callback setup |
| `VK_CONFIRMATION_CODE` | The confirmation code from VK callback setup |
| `VK_API_TOKEN` | Your VK group API token |

#### Using application.yml

```yaml
vk:
  secret: your_secret
  confirmation-code: 123456
  api:
    version: 5.131
    token: your_api_token
```

> **Note**: In production, ensure sensitive credentials are managed securely (e.g., via Kubernetes secrets, HashiCorp Vault, or environment variables)

## Building and Running

### Local Development (using Gradle)

```bash
./gradlew quarkusDev
```

This will start the application in development mode with hot reloading. The application will automatically start RabbitMQ using Quarkus DevServices.

### Docker Deployment

#### Build the Docker image

```bash
# For JVM mode (recommended for development)
docker build -f src/main/docker/Dockerfile.jvm -t vk-bot-jvm .

# For native mode (optimized for production)
docker build -f src/main/docker/Dockerfile.native -t vk-bot-native .
```

#### Run the container

```bash
docker run -p 8080:8080 \
  -e VK_SECRET=your_secret \
  -e VK_CONFIRMATION_CODE=123456 \
  -e VK_API_TOKEN=your_api_token \
  vk-bot-jvm
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/vk/callback` | POST | Handles incoming VK events. Requires `secret` field in request body for validation. |

### Example Request (from VK)
```json
{
  "type": "message_new",
  "object": {
    "message": {
      "text": "Привет!",
      "from_id": 123456,
      "peer_id": 789012,
      "conversation_message_id": 1
    }
  },
  "group_id": 123456,
  "secret": "your_secret"
}
```

### Example Response
```
ok
```

## Security

The application validates all incoming VK callback requests using the configured secret. Only requests with the correct secret will be processed. The confirmation code is used specifically for the initial VK group confirmation request.

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a new branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a pull request

## Additional Notes

- For development, RabbitMQ is automatically started via Quarkus DevServices. For production, configure RabbitMQ connection details in `application.yml`.
- The application uses Dockerfiles for different build modes (JVM, legacy jar, native, micro). Choose the appropriate one based on your deployment needs.
- Ensure that the VK group's callback URL is correctly configured to point to your application's `/vk/callback` endpoint.
- The AI service responds in Russian by default (configured in `UserAnswerAiService` interface).

> **Note**: The project follows clean architecture principles with clear separation between domain, ports, adapters, and use cases. This ensures maintainability and makes it easy to swap out components (e.g., different AI providers or messaging systems) without affecting the core business logic.
