package planesim.core.server.api;

/** Wire shape of one scenario type advertised by {@code GET /getTypes}. */
public class ScenarioTypeDto {
    public String name;
    /** "GEOGRAPHIC" or "NON_GEOGRAPHIC" — tells a client whether origin/formation inputs apply. */
    public String category;
}
