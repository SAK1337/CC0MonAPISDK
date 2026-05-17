///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

class GetImageSvg {
    public static void main(String[] args) throws Exception {
        Logger log = LoggingSetup.configure("get-image-svg");
        Integer id = null;
        Path out = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--id".equals(args[i])) try { id = Integer.parseInt(args[i + 1]); } catch (NumberFormatException e) {}
            if ("--out".equals(args[i])) out = Paths.get(args[i + 1]);
        }
        if (id == null) { System.err.println("usage: cc0mon-api-get-image-svg.java --id <1..10000> [--out path]"); System.exit(5); }
        if (out == null) out = Paths.get(System.getProperty("user.dir"), "cc0mon-" + id + ".svg");
        log.info("script start id=" + id + " out=" + out);

        try (Client client = new Client()) {
            byte[] bytes = client.getImageSvg(id);
            Files.write(out, bytes);
            System.out.println(out.toAbsolutePath());
            log.info("script exit code=0 bytes=" + bytes.length + " path=" + out);
        } catch (Errors.ValidationException e) { log.severe("validation: " + e.getMessage()); System.err.println("validation error: " + e.getMessage()); System.exit(5);
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.RateLimitException e)  { log.severe("rate limited http " + e.getStatusCode()); System.err.println("rate limited HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
