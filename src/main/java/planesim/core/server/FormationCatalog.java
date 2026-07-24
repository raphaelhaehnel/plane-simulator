package planesim.core.server;

import planesim.core.formation.CircleFormation;
import planesim.core.formation.FormationSpec;
import planesim.core.formation.LineFormation;
import planesim.core.formation.OrbitFormation;
import planesim.core.server.api.FormationDto;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The single registry of formation kinds known to the HTTP API. Both {@code GET /getFormations}
 * (which advertises each kind and its required fields, so a client can build its input form
 * without hardcoding them) and {@link RequestMapper}'s formation parsing read from here, so the
 * advertised catalog and the accepted requests can never drift apart. Adding a new formation:
 * add the record in {@code planesim.core.formation}, a branch in {@code FormationPlanner}, its
 * fields on {@link FormationDto}, and one {@link Descriptor} here — no client change needed.
 */
final class FormationCatalog {

    /** One required numeric field of a formation, as advertised to clients. */
    record Field(String name, String label) {
    }

    /** One formation kind: its wire name, required fields, and how to parse its DTO. */
    record Descriptor(String name, List<Field> fields, Function<FormationDto, FormationSpec> parser) {
    }

    static final List<Descriptor> ALL = List.of(
            new Descriptor("LINE",
                    List.of(new Field("destLatRad", "Destination latitude (rad)"),
                            new Field("destLonRad", "Destination longitude (rad)"),
                            new Field("spacingMeters", "Spacing (m)")),
                    FormationCatalog::parseLine),
            new Descriptor("CIRCLE",
                    List.of(new Field("radiusMeters", "Radius (m)")),
                    FormationCatalog::parseCircle),
            new Descriptor("ORBIT",
                    List.of(new Field("radiusMeters", "Radius (m)")),
                    FormationCatalog::parseOrbit));

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

    private static FormationSpec parseCircle(FormationDto dto) {
        if (dto.radiusMeters == null) {
            throw new BadRequestException("CIRCLE formation requires radiusMeters");
        }
        return new CircleFormation(dto.radiusMeters);
    }

    private static FormationSpec parseOrbit(FormationDto dto) {
        if (dto.radiusMeters == null) {
            throw new BadRequestException("ORBIT formation requires radiusMeters");
        }
        return new OrbitFormation(dto.radiusMeters);
    }
}
