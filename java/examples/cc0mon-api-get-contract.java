///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models.Contract;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.logging.Logger;

class GetContract {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-contract");
        log.info("script start");
        try (Client client = new Client()) {
            Contract c = client.getContract();
            System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(c));
            log.info("script exit code=0 address=" + c.address());
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.RateLimitException e)  { log.severe("rate limited http " + e.getStatusCode()); System.err.println("rate limited HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
