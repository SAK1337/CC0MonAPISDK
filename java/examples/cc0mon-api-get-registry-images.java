///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models.SpeciesImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.logging.Logger;

class GetRegistryImages {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-registry-images");
        Integer limit = null;
        for (int i = 0; i + 1 < args.length; i++) {
            if ("--limit".equals(args[i])) try { limit = Integer.parseInt(args[i + 1]); } catch (NumberFormatException e) {}
        }
        log.info("script start limit=" + limit);

        try (Client client = new Client()) {
            List<SpeciesImage> images = client.getRegistryImages();
            List<SpeciesImage> shown = limit == null ? images : images.subList(0, Math.min(limit, images.size()));
            System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(shown));
            log.info("script exit code=0 total=" + images.size() + " shown=" + shown.size());
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
