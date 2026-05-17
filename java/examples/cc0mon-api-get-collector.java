///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models.Collector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.logging.Logger;

class GetCollector {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-collector");
        String address = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--address".equals(args[i])) address = args[i + 1];
        }
        if (address == null) { System.err.println("usage: cc0mon-api-get-collector.java --address 0x..."); System.exit(5); }
        log.info("script start address=" + address);

        try (Client client = new Client()) {
            Collector c = client.getCollector(address);
            System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(c));
            log.info("script exit code=0 progress_keys=" + c.progress().size() + " items=" + c.items().size());
        } catch (Errors.ValidationException e) { log.severe("validation: " + e.getMessage()); System.err.println("validation error: " + e.getMessage()); System.exit(5);
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
