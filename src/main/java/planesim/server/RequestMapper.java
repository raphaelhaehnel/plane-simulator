package planesim.server;

import planesim.core.ScenarioConfig;
import planesim.core.SimulationConfig;
import planesim.core.ValueSimulationConfig;
import planesim.formation.CircleFormation;
import planesim.formation.FormationSpec;
import planesim.formation.LineFormation;
import planesim.scenario.GeoLiveState;
import planesim.scenario.NonGeoLiveState;
import planesim.scenario.Scenario;
import planesim.scenario.ScenarioType;
import planesim.server.dto.CreateScenarioRequest;
import planesim.server.dto.FormationDto;
import planesim.server.dto.GeoStateDto;
import planesim.server.dto.NonGeoStateDto;
import planesim.server.dto.ScenarioDto;

import java.util.Locale;

/**
 * Converts between the HTTP-facing DTOs and the internal domain model. Validation here is limited
 * to what the domain types can't check themselves (missing/absent fields, unknown formation type);
 * range checks that {@link SimulationConfig}/{@link ValueSimulationConfig}/{@link LineFormation}/
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

    /** Dispatches on {@code type} to build the matching {@link ScenarioConfig} kind: geographic for PLANE/RADAR, non-geographic for WEATHER. */
    public static ScenarioConfig toScenarioConfig(ScenarioType type, CreateScenarioRequest req) {
        return switch (type) {
            case PLANE, RADAR -> toSimulationConfig(req);
            case WEATHER -> toValueSimulationConfig(req);
        };
    }

    private static SimulationConfig toSimulationConfig(CreateScenarioRequest req) {
        if (req.originLatRad == null || req.originLonRad == null) {
            throw new BadRequestException("originLatRad and originLonRad are required");
        }
        if (req.formation == null || req.formation.type == null) {
            throw new BadRequestException("formation.type is required (LINE or CIRCLE)");
        }
        FormationSpec formationSpec = toFormationSpec(req.formation);
        double speed = req.speed != null ? req.speed : DEFAULT_SPEED_MPS;
        double altitude = req.altitude != null ? req.altitude : DEFAULT_ALTITUDE_M;
        return new SimulationConfig(req.originLatRad, req.originLonRad, req.amount, speed, altitude,
                req.sendInterval, formationSpec);
    }

    private static ValueSimulationConfig toValueSimulationConfig(CreateScenarioRequest req) {
        return new ValueSimulationConfig(req.amount, req.sendInterval);
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
        dto.status = scenario.status().name();
        dto.amount = config.objectCount();
        dto.sendInterval = config.publishIntervalMs();

        if (config instanceof SimulationConfig geo) {
            dto.originLatRad = geo.originLatRad();
            dto.originLonRad = geo.originLonRad();
            dto.speed = geo.speedMps();
            dto.altitude = geo.altitudeMeters();
            dto.formation = toFormationDto(geo.formation());
            dto.geoObjects = scenario.liveGeoSnapshot().stream().map(RequestMapper::toGeoDto).toList();
        } else if (config instanceof ValueSimulationConfig) {
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
