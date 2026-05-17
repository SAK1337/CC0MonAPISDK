# cc0mon SDK — Java

A typed Java SDK for the cc0mon.com NFT API, plus 10 JBang single-file example scripts.

- **JDK:** 17 LTS or newer (uses `record`, text blocks, `switch` expressions)
- **HTTP:** `java.net.http.HttpClient` (zero external HTTP dependency)
- **JSON:** Jackson `jackson-databind` 2.17+
- **Models:** `record` types with a `Map<String,Object> raw` escape hatch
- **Retries:** on by default (429 + 5xx, exponential backoff, `Retry-After` honored)

## Install

Prerequisites:

- JDK 17+ on `PATH`
- Maven 3.9+
- [JBang](https://www.jbang.dev/download/) (for example scripts)

```powershell
cd java
mvn install -DskipTests
```

This compiles `com.cc0mon:sdk:0.1.0` and installs it to your local `~/.m2/repository`. JBang then resolves it via the `//DEPS` directive in each example.

## Hello world

```java
import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Models.Token;

class Demo {
    public static void main(String[] args) {
        try (Client c = new Client()) {
            Token token = c.getToken(1);
            System.out.println(token.name() + " owned by " + token.owner());
        }
    }
}
```

## Example scripts (JBang)

Each example is a self-contained `.java` file that JBang runs directly. The `//DEPS` directive at the top pulls the SDK + Jackson from your local Maven cache:

```powershell
jbang examples\cc0mon-api-get-token.java --id 1
jbang examples\cc0mon-api-get-image-png.java --id 42 --out .\my-mon.png
jbang examples\cc0mon-api-get-collector.java --address 0x0000000000000000000000000000000000000000
```

| Script | Args |
|--------|------|
| `cc0mon-api-get-token.java` | `--id <n>` |
| `cc0mon-api-get-metadata.java` | `--id <n>` |
| `cc0mon-api-get-traits.java` | `--id <n>` |
| `cc0mon-api-get-image-svg.java` | `--id <n> [--out path]` |
| `cc0mon-api-get-image-png.java` | `--id <n> [--out path]` |
| `cc0mon-api-get-owner.java` | `--id <n>` |
| `cc0mon-api-get-contract.java` | (none) |
| `cc0mon-api-get-registry.java` | `[--limit n]` |
| `cc0mon-api-get-registry-images.java` | `[--limit n]` |
| `cc0mon-api-get-collector.java` | `--address 0x…` |

### Why JBang?

Standard `javac` requires public class names to match the filename — but `cc0mon-api-get-token` is not a valid Java identifier. JBang sidesteps this by allowing **package-private** classes (e.g., `class GetToken`) inside hyphenated filenames, and it auto-detects the main method.

If you'd rather not install JBang, you can still use the SDK directly in a normal Maven project — it's just a regular library.

## Logging

Every script writes append-only to `./cc0mon-api-<action>.log` in the current working directory (resolved from `System.getProperty("user.dir")`). Default level is `INFO`; set the env var `CC0MON_LOG_LEVEL=DEBUG` for request/response detail.

```powershell
$env:CC0MON_LOG_LEVEL = "DEBUG"
jbang examples\cc0mon-api-get-token.java --id 1
Get-Content .\cc0mon-api-get-token.log -Tail 10
```

## Exit codes

| Code | Meaning |
|------|---------|
| `0` | Success |
| `2` | Network failure |
| `3` | HTTP 4xx (after retries) |
| `4` | HTTP 5xx (after retries) |
| `5` | Argument/input validation |
| `1` | Unexpected error |

## Search & filtering

Three scripts accept filter flags; the SDK also exposes `findSpecies()` and `findCollectorItems()`. Filters are AND-combined, validated against the 16 energies × 4 rarities in `Models.ENERGIES` / `Models.RARITIES`, and applied client-side.

```powershell
jbang examples\cc0mon-api-get-registry.java --energy Fire --rarity Common
jbang examples\cc0mon-api-get-registry-images.java --name-contains drill --has-image
jbang examples\cc0mon-api-get-collector.java --address 0xB07952A55bF9c45C268F37C3631823Df50ac721a --owned-only --energy Fire
```

```java
import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Models;
import com.cc0mon.sdk.Models.Species;

try (Client c = new Client()) {
    List<Species> fireCommon = c.findSpecies("Fire", "Common", null);
    fireCommon.forEach(s -> System.out.println(s.name()));
    System.out.println("valid energies: " + Models.ENERGIES);
}
```

Invalid energy/rarity throws `Errors.ValidationException` (script exit `5`) with a message listing valid values.

## Customizing the client

```java
import java.time.Duration;
import com.cc0mon.sdk.Client;

try (Client c = new Client("https://api.cc0mon.com", Duration.ofSeconds(10), 0, "my-app/1.0")) {
    c.getToken(1);
}
```

Constructor parameters: `baseUrl`, `timeout`, `retries` (pass 0 to disable), `userAgent` (or `null` for the default).

## Integration tests

```powershell
$env:CC0MON_RUN_INTEGRATION = "1"
mvn verify
```

`ClientIT.java` (one test per endpoint) is wired through `maven-failsafe-plugin`. The env-var gate prevents accidental runs.

## Error handling

```java
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.Client;

try (Client c = new Client()) {
    var token = c.getToken(99999);
} catch (Errors.ValidationException e) {
    System.err.println("bad input: " + e.getMessage());
} catch (Errors.RateLimitException e) {
    System.err.println("rate limited; retry after " + e.getRetryAfterSeconds() + "s");
} catch (Errors.ApiException e) {
    System.err.printf("API said %d: %s%n", e.getStatusCode(), e.getBody());
} catch (Errors.NetworkException e) {
    System.err.println("transport problem: " + e.getMessage());
}
```
