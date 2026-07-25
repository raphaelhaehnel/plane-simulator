package planesim.core.server;

import planesim.core.formation.CircleFormation;
import planesim.core.formation.FormationSpec;
import planesim.core.formation.LineFormation;
import planesim.core.formation.OrbitFormation;
import planesim.core.formation.ScatterFormation;
import planesim.core.formation.WedgeFormation;
import planesim.core.server.api.FormationDto;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single registry of formation kinds known to the HTTP API. It is read by {@code GET
 * /getFormations} (which advertises each kind and its required fields, so a client can build its
 * input form without hardcoding them), by {@link RequestMapper}'s formation parsing (request →
 * {@link FormationSpec}), and by {@link RequestMapper}'s formation echoing ({@link FormationSpec} →
 * DTO on {@code GET /getScenarios}), so all three can never drift apart. Each {@link Descriptor}
 * carries <em>both</em> directions of the wire round-trip — a {@code parser} and a {@code
 * serializer} — so the whole mapping for one formation lives in one place.
 *
 * <p>Adding a new formation: add the record in {@code planesim.core.formation}, a branch in
 * {@code FormationPlanner}, its fields on {@link FormationDto}, and one {@link Descriptor} here
 * (name + fields + parser + serializer) — no client change and no separate echo branch needed.
 */
final class FormationCatalog {

    /** One required numeric field of a formation, as advertised to clients. */
    record Field(String name, String label) {
    }

    /**
     * One formation kind: its wire name, the concrete {@link FormationSpec} type it maps to, its
     * required fields, and both directions of its wire mapping ({@code parser} = DTO → spec,
     * {@code serializer} = spec → DTO).
     */
    record Descriptor(String name, Class<? extends FormationSpec> specType, List<Field> fields,
                      Function<FormationDto, FormationSpec> parser,
                      Function<FormationSpec, FormationDto> serializer) {
    }

    static final List<Descriptor> ALL = List.of(
            new Descriptor("LINE", LineFormation.class,
                    List.of(new Field("destLatRad", "Destination latitude (rad)"),
                            new Field("destLonRad", "Destination longitude (rad)"),
                            new Field("spacingMeters", "Spacing (m)")),
                    FormationCatalog::parseLine, FormationCatalog::serializeLine),
            new Descriptor("CIRCLE", CircleFormation.class,
                    List.of(new Field("radiusMeters", "Radius (m)")),
                    FormationCatalog::parseCircle, FormationCatalog::serializeCircle),
            new Descriptor("ORBIT", OrbitFormation.class,
                    List.of(new Field("radiusMeters", "Radius (m)")),
                    FormationCatalog::parseOrbit, FormationCatalog::serializeOrbit),
            new Descriptor("WEDGE", WedgeFormation.class,
                    List.of(new Field("destLatRad", "Destination latitude (rad)"),
                            new Field("destLonRad", "Destination longitude (rad)"),
                            new Field("spacingMeters", "Spacing (m)"),
                            new Field("apexAngleRad", "Apex angle (rad)")),
                    FormationCatalog::parseWedge, FormationCatalog::serializeWedge),
            new Descriptor("SCATTER", ScatterFormation.class,
                    List.of(new Field("radiusMeters", "Radius (m)")),
                    FormationCatalog::parseScatter, FormationCatalog::serializeScatter));

    private FormationCatalog() {
    }

    static FormationSpec parse(FormationDto dto) {
        String name = dto.type.toUpperCase(Locale.ROOT);
        return ALL.stream()
                .filter(descriptor -> descriptor.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new BadRequestException(
                        "formation.type must be one of " + names() + ", got: " + dto.type))
                .parser().apply(dto);
    }

    /** Serializes a {@link FormationSpec} back to its wire DTO, for {@code GET /getScenarios}. */
    static FormationDto toDto(FormationSpec spec) {
        return ALL.stream()
                .filter(descriptor -> descriptor.specType().isInstance(spec))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No FormationCatalog descriptor for formation type " + spec.getClass().getName()))
                .serializer().apply(spec);
    }

    /** Comma-separated formation names, for error messages that enumerate the valid values. */
    static String names() {
        return ALL.stream().map(Descriptor::name).collect(Collectors.joining(", "));
    }

    private static FormationSpec parseLine(FormationDto dto) {
        if (dto.destLatRad == null || dto.destLonRad == null || dto.spacingMeters == null) {
            throw new BadRequestException("LINE formation requires destLatRad, destLonRad, spacingMeters");
        }
        return new LineFormation(dto.destLatRad, dto.destLonRad, dto.spacingMeters);
    }

    private static FormationDto serializeLine(FormationSpec spec) {
        LineFormation line = (LineFormation) spec;
        FormationDto dto = new FormationDto();
        dto.type = "LINE";
        dto.destLatRad = line.destLatRad();
        dto.destLonRad = line.destLonRad();
        dto.spacingMeters = line.spacingMeters();
        return dto;
    }

    private static FormationSpec parseCircle(FormationDto dto) {
        if (dto.radiusMeters == null) {
            throw new BadRequestException("CIRCLE formation requires radiusMeters");
        }
        return new CircleFormation(dto.radiusMeters);
    }

    private static FormationDto serializeCircle(FormationSpec spec) {
        CircleFormation circle = (CircleFormation) spec;
        FormationDto dto = new FormationDto();
        dto.type = "CIRCLE";
        dto.radiusMeters = circle.radiusMeters();
        return dto;
    }

    private static FormationSpec parseOrbit(FormationDto dto) {
        if (dto.radiusMeters == null) {
            throw new BadRequestException("ORBIT formation requires radiusMeters");
        }
        return new OrbitFormation(dto.radiusMeters);
    }

    private static FormationDto serializeOrbit(FormationSpec spec) {
        OrbitFormation orbit = (OrbitFormation) spec;
        FormationDto dto = new FormationDto();
        dto.type = "ORBIT";
        dto.radiusMeters = orbit.radiusMeters();
        return dto;
    }

    private static FormationSpec parseWedge(FormationDto dto) {
        if (dto.destLatRad == null || dto.destLonRad == null || dto.spacingMeters == null
                || dto.apexAngleRad == null) {
            throw new BadRequestException("WEDGE formation requires destLatRad, destLonRad, spacingMeters, apexAngleRad");
        }
        return new WedgeFormation(dto.destLatRad, dto.destLonRad, dto.spacingMeters, dto.apexAngleRad);
    }

    private static FormationDto serializeWedge(FormationSpec spec) {
        WedgeFormation wedge = (WedgeFormation) spec;
        FormationDto dto = new FormationDto();
        dto.type = "WEDGE";
        dto.destLatRad = wedge.destLatRad();
        dto.destLonRad = wedge.destLonRad();
        dto.spacingMeters = wedge.spacingMeters();
        dto.apexAngleRad = wedge.apexAngleRad();
        return dto;
    }

    private static FormationSpec parseScatter(FormationDto dto) {
        if (dto.radiusMeters == null) {
            throw new BadRequestException("SCATTER formation requires radiusMeters");
        }
        return new ScatterFormation(dto.radiusMeters);
    }

    private static FormationDto serializeScatter(FormationSpec spec) {
        ScatterFormation scatter = (ScatterFormation) spec;
        FormationDto dto = new FormationDto();
        dto.type = "SCATTER";
        dto.radiusMeters = scatter.radiusMeters();
        return dto;
    }
}
