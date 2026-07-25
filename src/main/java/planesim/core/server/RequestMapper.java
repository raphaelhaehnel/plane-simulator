package planesim.core.server;

import planesim.core.engine.GeoScenarioConfig;
import planesim.core.engine.NonGeoScenarioConfig;
import planesim.core.engine.ScenarioConfig;
import planesim.core.formation.CircleFormation;
import planesim.core.formation.FormationSpec;
import planesim.core.formation.LineFormation;
import planesim.core.formation.OrbitFormation;
import planesim.core.formation.ScatterFormation;
import planesim.core.formation.WedgeFormation;
import planesim.core.scenario.GeoLiveState;
import planesim.core.scenario.NonGeoLiveState;
import planesim.core.scenario.Scenario;
import planesim.core.scenario.ScenarioType;
import planesim.core.server.api.CreateScenarioRequest;
import planesim.core.server.api.FormationDescriptorDto;
import planesim.core.server.api.FormationDto;
import planesim.core.server.api.FormationFieldDto;
import planesim.core.server.api.GeoStateDto;
import planesim.core.server.api.NonGeoStateDto;
import planesim.core.server.api.ScenarioDto;
import planesim.core.server.api.ScenarioIdRequest;
import planesim.core.server.api.ScenarioTypeDto;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Converts between the HTTP-facing DTOs and the internal domain model. Validation here is limited
 * to what the domain types can't check themselves (missing/absent fields, unknown formation type);
 * range checks that {@link GeoScenarioConfig}/{@link NonGeoScenarioConfig}/{@link LineFormation}/
 * {@link CircleFormation} already enforce in their compact constructors (e.g. objectCount &gt; 0,
 * spacing/radius &gt;= 0) are left to those constructors rather than duplicated here.
 */
public final class RequestMapper {

    private static final double DEFAULT_SPEED_MPS = 230.0;
    private static final double DEFAULT_ALTITUDE_M = 10000.0;

    private RequestMapper() {
    }

    public static ScenarioType toScenarioType(String raw) {
        if (raw == null) {
            throw new BadRequestException("type is required");
        }
        try {
            return ScenarioType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unsupported scenario type: " + raw + " (must be one of " + typeNames() + ")");
        }
    }

    /** Returns {@code req.topicName}, or throws {@link BadRequestException} if it's missing/blank. */
    public static String toTopicName(CreateScenarioRequest req) {
        if (req.topicName == null || req.topicName.isBlank()) {
            throw new BadRequestException("topicName is required");
        }
        return req.topicName;
    }

    /** Dispatches on {@code type}'s category to build the matching {@link ScenarioConfig} kind. */
    public static ScenarioConfig toScenarioConfig(ScenarioType type, CreateScenarioRequest req) {
        return switch (type.category()) {
            case GEOGRAPHIC -> toGeoScenarioConfig(req);
            case NON_GEOGRAPHIC -> toNonGeoScenarioConfig(req);
        };
    }

    /** Returns {@code request.id}, or throws {@link BadRequestException} if it's missing/blank. */
    public static String requireScenarioId(ScenarioIdRequest request) {
        if (request.id == null || request.id.isBlank()) {
            throw new BadRequestException("id is required");
        }
        return request.id;
    }

    private static GeoScenarioConfig toGeoScenarioConfig(CreateScenarioRequest req) {
        if (req.originLatRad == null || req.originLonRad == null) {
            throw new BadRequestException("originLatRad and originLonRad are required");
        }
        if (req.formation == null || req.formation.type == null) {
            throw new BadRequestException("formation.type is required (one of " + FormationCatalog.names() + ")");
        }
        FormationSpec formationSpec = FormationCatalog.parse(req.formation);
        double speed = req.speed != null ? req.speed : DEFAULT_SPEED_MPS;
        double altitude = req.altitude != null ? req.altitude : DEFAULT_ALTITUDE_M;
        return new GeoScenarioConfig(req.originLatRad, req.originLonRad, req.amount, speed, altitude,
                req.sendInterval, formationSpec);
    }

    private static NonGeoScenarioConfig toNonGeoScenarioConfig(CreateScenarioRequest req) {
        return new NonGeoScenarioConfig(req.amount, req.sendInterval);
    }

    public static ScenarioDto toDto(Scenario scenario) {
        ScenarioConfig config = scenario.config();
        ScenarioDto dto = new ScenarioDto();
        dto.id = scenario.id();
        dto.type = scenario.type().name();
        dto.topicName = scenario.topicName();
        dto.status = scenario.status().name();
        dto.amount = config.objectCount();
        dto.sendInterval = config.publishIntervalMs();

        if (config instanceof GeoScenarioConfig geo) {
            dto.originLatRad = geo.originLatRad();
            dto.originLonRad = geo.originLonRad();
            dto.speed = geo.speedMps();
            dto.altitude = geo.altitudeMeters();
            dto.formation = toFormationDto(geo.formation());
            dto.geoObjects = scenario.liveGeoSnapshot().stream().map(RequestMapper::toGeoDto).toList();
        } else if (config instanceof NonGeoScenarioConfig) {
            dto.nonGeoObjects = scenario.liveNonGeoSnapshot().stream().map(RequestMapper::toNonGeoDto).toList();
        }
        return dto;
    }

    private static FormationDto toFormationDto(FormationSpec spec) {
        FormationDto dto = new FormationDto();
        if (spec instanceof LineFormation line) {
            dto.type = "LINE";
            dto.destLatRad = line.destLatRad();
            dto.destLonRad = line.destLonRad();
            dto.spacingMeters = line.spacingMeters();
        } else if (spec instanceof CircleFormation circle) {
            dto.type = "CIRCLE";
            dto.radiusMeters = circle.radiusMeters();
        } else if (spec instanceof OrbitFormation orbit) {
            dto.type = "ORBIT";
            dto.radiusMeters = orbit.radiusMeters();
        } else if (spec instanceof WedgeFormation wedge) {
            dto.type = "WEDGE";
            dto.destLatRad = wedge.destLatRad();
            dto.destLonRad = wedge.destLonRad();
            dto.spacingMeters = wedge.spacingMeters();
            dto.apexAngleRad = wedge.apexAngleRad();
        } else if (spec instanceof ScatterFormation scatter) {
            dto.type = "SCATTER";
            dto.radiusMeters = scatter.radiusMeters();
        }
        return dto;
    }

    private static GeoStateDto toGeoDto(GeoLiveState state) {
        GeoStateDto dto = new GeoStateDto();
        dto.index = state.index();
        dto.latRad = state.latRad();
        dto.lonRad = state.lonRad();
        dto.headingDeg = state.headingDeg();
        return dto;
    }

    public static ScenarioTypeDto toTypeDto(ScenarioType type) {
        ScenarioTypeDto dto = new ScenarioTypeDto();
        dto.name = type.name();
        dto.category = type.category().name();
        return dto;
    }

    static FormationDescriptorDto toFormationDescriptorDto(FormationCatalog.Descriptor descriptor) {
        FormationDescriptorDto dto = new FormationDescriptorDto();
        dto.name = descriptor.name();
        dto.fields = descriptor.fields().stream().map(field -> {
            FormationFieldDto fieldDto = new FormationFieldDto();
            fieldDto.name = field.name();
            fieldDto.label = field.label();
            return fieldDto;
        }).toList();
        return dto;
    }

    /** Comma-separated scenario type names, for error messages that enumerate the valid values. */
    private static String typeNames() {
        return Arrays.stream(ScenarioType.values()).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static NonGeoStateDto toNonGeoDto(NonGeoLiveState state) {
        NonGeoStateDto dto = new NonGeoStateDto();
        dto.index = state.index();
        dto.fields = state.fields();
        return dto;
    }
}
