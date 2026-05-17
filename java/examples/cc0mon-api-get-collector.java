///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17
//DEPS com.cc0mon:sdk:0.1.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.17.0

import com.cc0mon.sdk.Client;
import com.cc0mon.sdk.Errors;
import com.cc0mon.sdk.LoggingSetup;
import com.cc0mon.sdk.Models;
import com.cc0mon.sdk.Models.Collector;
import com.cc0mon.sdk.Models.CollectorItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;
import java.util.logging.Logger;

class GetCollector {
    public static void main(String[] args) {
        Logger log = LoggingSetup.configure("get-collector");
        String address = null;
        String energy = null, rarity = null;
        boolean ownedOnly = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--address": if (i + 1 < args.length) address = args[++i]; break;
                case "--energy": if (i + 1 < args.length) energy = args[++i]; break;
                case "--rarity": if (i + 1 < args.length) rarity = args[++i]; break;
                case "--owned-only": ownedOnly = true; break;
                case "-h": case "--help":
                    System.out.println("usage: cc0mon-api-get-collector.java --address 0x... [--owned-only] [--energy E] [--rarity R]");
                    System.out.println("Valid --energy values: " + String.join(", ", Models.ENERGIES));
                    System.out.println("Valid --rarity values: " + String.join(", ", Models.RARITIES));
                    System.exit(0);
            }
        }
        if (address == null) {
            System.err.println("usage: cc0mon-api-get-collector.java --address 0x... [--owned-only] [--energy E] [--rarity R]");
            System.exit(5);
        }
        boolean hasFilter = ownedOnly || energy != null || rarity != null;
        log.info("script start address=" + address + " owned_only=" + ownedOnly + " energy=" + energy + " rarity=" + rarity);

        try (Client client = new Client()) {
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            if (hasFilter) {
                List<CollectorItem> items = client.findCollectorItems(address, ownedOnly, energy, rarity);
                System.out.println(mapper.writeValueAsString(items));
                log.info("script exit code=0 matched=" + items.size());
            } else {
                Collector c = client.getCollector(address);
                System.out.println(mapper.writeValueAsString(c));
                log.info("script exit code=0 collected=" + c.collected() + " missing=" + c.missing() + " total_tokens_held=" + c.totalTokensHeld());
            }
        } catch (Errors.ValidationException e) { log.severe("validation: " + e.getMessage()); System.err.println("validation error: " + e.getMessage()); System.exit(5);
        } catch (Errors.NetworkException e)    { log.severe("network: " + e.getMessage());    System.err.println("network error: " + e.getMessage()); System.exit(2);
        } catch (Errors.ClientApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("client error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(3);
        } catch (Errors.ServerApiException e)  { log.severe("http " + e.getStatusCode());     System.err.println("server error HTTP " + e.getStatusCode() + ": " + e.getBody()); System.exit(4);
        } catch (Exception e) { log.severe("unexpected: " + e); System.err.println("unexpected: " + e); System.exit(1); }
    }
}
