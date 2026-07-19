package planesim.server;

import planesim.core.SimulationConfig;
import planesim.formation.CircleFormation;
import planesim.formation.FormationSpec;
import planesim.formation.LineFormation;
import planesim.scenario.ObjectLiveState;
import planesim.scenario.Scenario;
import planesim.scenario.ScenarioType;
import planesim.server.dto.CreateScenarioRequest;
import planesim.server.dto.FormationDto;
import planesim.server.dto.ObjectStateDto;
import planesim.server.dto.ScenarioDto;

import java.util.Locale;

/**
 * Converts between the HTTP-facing DTOs and the internal domain model. Validation here is limited
 * to what the domain types can't check themselves (missing/absent fields, unknown formation type);
 * range checks that {@link SimulationConfig}/{@link LineFormation}/{@link CircleFormation} already
 * enforce in their compact constructors (e.g. objectCount &gt; 0, spacing/radius &gt;= 0) are left to
 * those constructors rather than duplicated here.
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
            throw new BadRequestException("Unsupported scenario type: " + raw + " (must be PLANE or RADAR)");
        }
    }

    public static SimulationConfig toSimulationConfig(CreateScenarioRequest req) {
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
        SimulationConfig c = scenario.config();
        ScenarioDto dto = new ScenarioDto();
        dto.id = scenario.id();
        dto.type = scenario.type().name();
        dto.status = scenario.status().name();
        dto.amount = c.objectCount();
        dto.originLatRad = c.originLatRad();
        dto.originLonRad = c.originLonRad();
        dto.speed = c.speedMps();
        dto.altitude = c.altitudeMeters();
        dto.sendInterval = c.publishIntervalMs();
        dto.formation = toFormationDto(c.formation());
        dto.objects = scenario.liveSnapshot().stream().map(RequestMapper::toObjectDto).toList();
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

    private static ObjectStateDto toObjectDto(ObjectLiveState state) {
        ObjectStateDto dto = new ObjectStateDto();
        dto.index = state.index();
        dto.latRad = state.latRad();
        dto.lonRad = state.lonRad();
        dto.headingDeg = state.headingDeg();
        return dto;
    }
}
