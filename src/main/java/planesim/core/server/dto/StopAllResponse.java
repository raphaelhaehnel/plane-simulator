package planesim.core.server.dto;

import java.util.List;

/** POST /stopAll response body: the ids of every scenario that was actually running and got paused. */
public class StopAllResponse {
    public List<String> stoppedIds;

    public StopAllResponse(List<String> stoppedIds) {
        this.stoppedIds = stoppedIds;
    }
}
