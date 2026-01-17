# Backend Setup

## Environment Variables

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and fill in your actual API keys and credentials.

3. **Important**: Never commit `.env` to git (it's in `.gitignore`).

## Running the Backend

### Development Mode
```bash
./run-backend.sh
```

### Debug Mode (for IDE debugging)
```bash
./run-backend-debug.sh
```
Then attach your IDE debugger to `localhost:5005`.

### Running Tests
```bash
# Run all tests
./run-tests.sh

# Run a specific test class
./run-tests.sh ItemServiceImplTest
```

## Manual Maven Commands

If you prefer to run Maven directly:

```bash
# Development
mvn -s .m2/settings.xml spring-boot:run

# Debug mode
mvn -s .m2/settings.xml spring-boot:run \
    -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"

# Tests
mvn -s .m2/settings.xml test
```

## Environment Variables Reference

- `AUSPOST_API_KEY` - Required for AusPost API integration
- `POSTAGE_DATA_DIR` - Optional, defaults to `~/.postage-comparator`
<!-- Sendle integration is currently disabled. -->
