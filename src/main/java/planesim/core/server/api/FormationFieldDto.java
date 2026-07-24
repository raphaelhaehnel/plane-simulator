package planesim.core.server.api;

/** Wire shape of one required formation field advertised by {@code GET /getFormations}. */
public class FormationFieldDto {
    public String name;
    /** Human-readable caption a client can show next to the input for this field. */
    public String label;
}
