///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models;
import com.cc0mon.sdk.Models.Species;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.logging.Logger;

class GetRegistry {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-registry");
        Integer limit = null;
        String energy = null, rarity = null, nameContains = null;
        boolean wantHelp = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--limit": if (i + 1 < args.length) try { limit = Integer.parseInt(args[++i]); } catch (NumberFormatException e) {} break;
                case "--energy": if (i + 1 < args.length) energy = args[++i]; break;
                case "--rarity": if (i + 1 < args.length) rarity = args[++i]; break;
                case "--name-contains": if (i + 1 < args.length) nameContains = args[++i]; break;
                case "-h": case "--help": wantHelp = true; break;
            }
        }
        if (wantHelp) {
            System.out.println("usage: cc0mon-api-get-registry.java [--limit N] [--energy E] [--rarity R] [--name-contains S]");
            System.out.println("Valid --energy values: " + String.join(", ", Models.ENERGIES));
            System.out.println("Valid --rarity values: " + String.join(", ", Models.RARITIES));
            System.exit(0);
        }
        log.info("script start limit=" + limit + " energy=" + energy + " rarity=" + rarity + " name_contains=" + nameContains);

        try (Client client = new Client()) {
            List<Species> species = client.findSpecies(energy, rarity, nameContains);
            List<Species> shown = limit == null ? species : species.subList(0, Math.min(limit, species.size()));
            System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(shown));
            log.info("script exit code=0 matched=" + species.size() + " shown=" + shown.size());
        } catch (Errors.ValidationException e) { log.severe("validation: " + e.getMessage()); System.err.println("validation error: " + e.getMessage()); System.exit(5);
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
