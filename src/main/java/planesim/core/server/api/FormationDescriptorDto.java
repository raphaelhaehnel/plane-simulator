package planesim.core.server.api;

import java.util.List;

/** Wire shape of one formation kind advertised by {@code GET /getFormations}. */
public class FormationDescriptorDto {
    public String name;
    public List<FormationFieldDto> fields;
}
