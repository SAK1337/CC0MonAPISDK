///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models.OwnerInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.logging.Logger;

class GetOwner {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-owner");
        Integer id = parseId(args);
        if (id == null) { System.err.println("usage: cc0mon-api-get-owner.java --id <1..10000>"); System.exit(5); }
        log.info("script start id=" + id);
        try (Client client = new Client()) {
            OwnerInfo info = client.getOwner(id);
            System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(info));
            log.info("script exit code=0 owner=" + info.owner());
        } catch (Errors.ValidationException e) { log.severe("validation: " + e.getMessage()); System.err.println("validation error: " + e.getMessage()); System.exit(5);
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }

    static Integer parseId(String[] args) {
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--id".equals(args[i])) try { return Integer.parseInt(args[i + 1]); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }
}
