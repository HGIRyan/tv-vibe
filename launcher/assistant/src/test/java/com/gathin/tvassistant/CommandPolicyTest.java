package com.gathin.tvassistant;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class CommandPolicyTest {
    private static final AppCatalog CATALOG = new AppCatalog(java.util.List.of(
        new AppCatalog.App("OPEN_HULU", "Hulu", "com.hulu.livingroomplus", java.util.List.of()),
        new AppCatalog.App("OPEN_APP:com.example.newapp", "New app", "com.example.newapp", java.util.List.of("New"))));
    private JSONObject answer(String action, double confidence, double probability) throws Exception {
        return new JSONObject().put("answers", new JSONArray().put(new JSONObject()
            .put("type", "choice").put("name", "tv_action").put("choice", action).put("confidence", confidence)
            .put("probabilities", new JSONArray().put(new JSONObject().put("value", action).put("probability", probability)))));
    }
    @Test public void transcriptMustConfirmWakeAndKeepFastCommand() {
        assertTrue(CommandPolicy.hasWake("hey tv pause"));
        assertEquals("pause", CommandPolicy.commandAfterWake("Hey TV, pause."));
        assertEquals("resume", CommandPolicy.commandAfterWake("Advertisement. Hey T. V., resume!"));
        assertEquals("", CommandPolicy.commandAfterWake("pause"));
        assertEquals("", CommandPolicy.commandAfterWake("Hey TV"));
        assertFalse(CommandPolicy.hasWake("they tv pause"));
        assertFalse(CommandPolicy.hasWake("hey tvshow"));
    }
    @Test public void latestWakeSeparatesPrerollFromCommand() {
        assertEquals("open hulu", CommandPolicy.commandAfterWake("Hey TV pause. Hey TV open Hulu"));
    }
    @Test public void confidentAllowedActionIsAccepted() throws Exception {
        assertEquals(CommandPolicy.Action.PAUSE, CommandPolicy.parseDecision(answer("PAUSE", .97, .98), CATALOG).action);
    }
    @Test public void uncertainUnknownAndMalformedAnswersNeverExecute() throws Exception {
        assertEquals(CommandPolicy.Action.NO_ACTION, CommandPolicy.parseDecision(answer("PAUSE", .6, .99), CATALOG).action);
        assertEquals(CommandPolicy.Action.NO_ACTION, CommandPolicy.parseDecision(answer("PLAY", .99, .4), CATALOG).action);
        assertEquals(CommandPolicy.Action.NO_ACTION, CommandPolicy.parseDecision(answer("SHELL_COMMAND", .99, .99), CATALOG).action);
        assertEquals(CommandPolicy.Action.NO_ACTION, CommandPolicy.parseDecision(new JSONObject(), CATALOG).action);
        assertEquals(CommandPolicy.Action.NO_ACTION, CommandPolicy.parseDecision(answer("PAUSE", 1.1, .99), CATALOG).action);
    }
    @Test public void newlyDiscoveredAppsAreAcceptedOnlyWhileInCatalog() throws Exception {
        String id = "OPEN_APP:com.example.newapp";
        CommandPolicy.Selection selected = CommandPolicy.parseDecision(answer(id, .99, .99), CATALOG);
        assertEquals(CommandPolicy.Action.OPEN_APP, selected.action);
        assertEquals("com.example.newapp", selected.app.packageName);
        assertEquals(id, selected.value());
        assertEquals(CommandPolicy.Action.NO_ACTION,
            CommandPolicy.parseDecision(answer(id, .99, .99), new AppCatalog(java.util.List.of())).action);
        assertEquals(CommandPolicy.Action.NO_ACTION,
            CommandPolicy.parseDecision(answer("OPEN_APP", .99, .99), CATALOG).action);
    }
    @Test public void refusalNeverExecutes() throws Exception {
        JSONObject response = new JSONObject().put("answers", new JSONArray()
            .put(new JSONObject().put("type", "refusal").put("name", "tv_action")));
        assertEquals(CommandPolicy.Action.NO_ACTION, CommandPolicy.parseDecision(response, CATALOG).action);
    }
    @Test public void requestEnumeratesOnlyKnownActionsAndResumeSharesPlay() throws Exception {
        JSONObject request = CommandPolicy.decisionRequest("resume", CATALOG);
        assertEquals("gpt-6-luna", request.getString("model"));
        JSONArray choices = request.getJSONArray("questions").getJSONObject(0).getJSONArray("choices");
        assertEquals(CommandPolicy.Action.values().length - 1 + CATALOG.apps.size(), choices.length());
        for (int i = 0; i < choices.length(); i++)
            assertTrue(CATALOG.byId(choices.getJSONObject(i).getString("value")) != null
                || CommandPolicy.Action.valueOf(choices.getJSONObject(i).getString("value")) != CommandPolicy.Action.OPEN_APP);
    }
}
