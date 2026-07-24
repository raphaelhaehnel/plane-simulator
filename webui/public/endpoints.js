/*
 * Declarative catalog of the simulator's API. THIS FILE IS THE EXTENSION POINT:
 * the UI (app.js) renders everything from it, so adding a new request or a new
 * field means editing only this file.
 *
 * Scenario types and formations are NOT listed here — they are fetched from the
 * simulator (GET /getTypes, GET /getFormations) when the page opens, so a new
 * type or formation added in the backend shows up in the UI with no change
 * here. buildEndpoints receives that metadata:
 *   types       [{ name: "PLANE", category: "GEOGRAPHIC" | "NON_GEOGRAPHIC" }, ...]
 *   formations  [{ name: "LINE", fields: [{ name, label }, ...] }, ...]
 *
 * Endpoint shape:
 *   id          unique key, also used as the nav label anchor
 *   title       human-readable name shown in the nav and form header
 *   method      "GET" | "POST"
 *   path        path on the simulator, e.g. "/start" (the UI calls it via /api/start)
 *   description one line shown under the form title
 *   fields      list of body fields (empty list = no request body)
 *
 * Field shape:
 *   name        JSON key in the request body; dot notation nests, e.g.
 *               "formation.type" becomes { "formation": { "type": ... } }
 *   label       text shown next to the input
 *   kind        "text" | "number" | "select" | "checkbox"
 *   options     for kind "select": list of allowed values
 *   value       pre-filled default (leave undefined for an empty input)
 *   required    if true, sending with the field blank shows an error
 *               (blank optional fields are simply omitted from the body)
 *   hint        small helper text under the input
 *   showIf      optional function(values) -> boolean; values holds every field
 *               of the form keyed by name (dot names included), so a field can
 *               appear only for certain types/formations. Hidden fields are
 *               never sent.
 */

/**
 * Optional pre-filled defaults, keyed by field name. A formation field with no
 * entry here just starts blank — a new backend formation still works without
 * touching this file, this map only makes the common ones convenient.
 */
const FIELD_DEFAULTS = {
  "formation.destLatRad": 0.43,
  "formation.destLonRad": 1.05,
  "formation.spacingMeters": 2000,
  "formation.radiusMeters": 6000
};

export function buildEndpoints({ types, formations }) {
  const categoryByType = Object.fromEntries(types.map((t) => [t.name, t.category]));
  const isGeo = (v) => categoryByType[v["type"]] === "GEOGRAPHIC";

  // One numeric input per formation field, visible only while its formation is selected.
  const formationFields = formations.flatMap((formation) =>
    formation.fields.map((field) => ({
      name: "formation." + field.name,
      label: field.label,
      kind: "number",
      value: FIELD_DEFAULTS["formation." + field.name],
      required: true,
      showIf: (v) => isGeo(v) && v["formation.type"] === formation.name
    }))
  );

  return [
    {
      id: "createScenario",
      title: "Create Scenario",
      method: "POST",
      path: "/createScenario",
      description: "Create a new simulation scenario (returns its id).",
      fields: [
        { name: "type", label: "Type", kind: "select", options: types.map((t) => t.name), value: types[0]?.name },
        { name: "topicName", label: "Topic name", kind: "text", required: true, hint: "Network topic the scenario publishes on" },
        { name: "amount", label: "Amount", kind: "number", value: 5, required: true, hint: "Number of simulated objects" },
        { name: "sendInterval", label: "Send interval (ms)", kind: "number", value: 500, required: true },
        { name: "originLatRad", label: "Origin latitude (rad)", kind: "number", value: 0.3575, required: true, showIf: isGeo },
        { name: "originLonRad", label: "Origin longitude (rad)", kind: "number", value: 0.9838, required: true, showIf: isGeo },
        { name: "speed", label: "Speed (m/s)", kind: "number", hint: "Optional, defaults to 230.0", showIf: isGeo },
        { name: "altitude", label: "Altitude (m)", kind: "number", hint: "Optional, defaults to 10000.0", showIf: isGeo },
        { name: "formation.type", label: "Formation", kind: "select", options: formations.map((f) => f.name), value: formations[0]?.name, showIf: isGeo },
        ...formationFields
      ]
    },
    {
      id: "getScenarios",
      title: "Get Scenarios",
      method: "GET",
      path: "/getScenarios",
      description: "List every scenario and the latest published state of its objects.",
      fields: []
    },
    {
      id: "start",
      title: "Start",
      method: "POST",
      path: "/start",
      description: "Start (or resume) a scenario by id.",
      fields: [{ name: "id", label: "Scenario id", kind: "text", required: true }]
    },
    {
      id: "pause",
      title: "Pause",
      method: "POST",
      path: "/pause",
      description: "Pause a running scenario by id (resumable with Start).",
      fields: [{ name: "id", label: "Scenario id", kind: "text", required: true }]
    },
    {
      id: "stopAll",
      title: "Stop All",
      method: "POST",
      path: "/stopAll",
      description: "Pause every running scenario at once.",
      fields: []
    },
    {
      id: "deleteScenario",
      title: "Delete Scenario",
      method: "POST",
      path: "/deleteScenario",
      description: "Pause and remove a scenario by id.",
      fields: [{ name: "id", label: "Scenario id", kind: "text", required: true }]
    }
  ];
}
