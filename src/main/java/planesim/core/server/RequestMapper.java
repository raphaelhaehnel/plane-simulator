package planesim.core.server;

import planesim.core.engine.GeoScenarioConfig;
import planesim.core.engine.NonGeoScenarioConfig;
import planesim.core.engine.ScenarioConfig;
import planesim.core.formation.CircleFormation;
import planesim.core.formation.FormationSpec;
import planesim.core.formation.LineFormation;
import planesim.core.scenario.GeoLiveState;
import planesim.core.scenario.NonGeoLiveState;
import planesim.core.scenario.Scenario;
import planesim.core.scenario.ScenarioType;
import planesim.core.server.dto.CreateScenarioRequest;
import planesim.core.server.dto.FormationDto;
import planesim.core.server.dto.GeoStateDto;
import planesim.core.server.dto.NonGeoStateDto;
import planesim.core.server.dto.ScenarioDto;
import planesim.core.server.dto.ScenarioIdRequest;

import java.util.Locale;

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
            throw new BadRequestException("Unsupported scenario type: " + raw + " (must be PLANE, RADAR, or WEATHER)");
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
            throw new BadRequestException("formation.type is required (LINE or CIRCLE)");
        }
        FormationSpec formationSpec = toFormationSpec(req.formation);
        double speed = req.speed != null ? req.speed : DEFAULT_SPEED_MPS;
        double altitude = req.altitude != null ? req.altitude : DEFAULT_ALTITUDE_M;
        return new GeoScenarioConfig(req.originLatRad, req.originLonRad, req.amount, speed, altitude,
                req.sendInterval, formationSpec);
    }

    private static NonGeoScenarioConfig toNonGeoScenarioConfig(CreateScenarioRequest req) {
        return new NonGeoScenarioConfig(req.amount, req.sendInterval);
    }

    private static FormationSpec toFormationSpec(FormationDto dto) {
        return switch (dto.type.toUpperCase(Locale.ROOT)) {
            case "LINE" -> {
                if (dto.destLatRad == null || dto.destLonRad == null || dto.spacingMeters == null) {
                    throw new BadRequestException("LINE formation requires destLatRad, destLonRad, spacingMeters");
                }
                yield new LineFormation(dto.destLatRad, dto.destLonRad, dto.spacingMeters);
            }
            case "CIRCLE" -> {
                if (dto.radiusMeters == null) {
                    throw new BadRequestException("CIRCLE formation requires radiusMeters");
                }
                yield new CircleFormation(dto.radiusMeters);
            }
            default -> throw new BadRequestException("formation.type must be LINE or CIRCLE, got: " + dto.type);
        };
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

    private static NonGeoStateDto toNonGeoDto(NonGeoLiveState state) {
        NonGeoStateDto dto = new NonGeoStateDto();
        dto.index = state.index();
        dto.fields = state.fields();
        return dto;
    }
}
