package org.apache.mesos.elasticsearch.systemtest;

import com.jayway.awaitility.Awaitility;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.log4j.Logger;
import org.apache.mesos.elasticsearch.common.elasticsearch.ElasticsearchParser;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Get Array of tasks from the API
 */
public class ESTasks {
    private static final Logger LOGGER = Logger.getLogger(ESTasks.class);
    private final String tasksEndPoint;

    public ESTasks(Configuration config, String schedulerIpAddress) {
        tasksEndPoint = "http://" + schedulerIpAddress + ":" + config.getSchedulerGuiPort() + "/v1/tasks";
    }

    public HttpResponse<JsonNode> getResponse() throws UnirestException {
        return Unirest.get(tasksEndPoint).asJson();
    }

    public List<JSONObject> getTasks() {
        List<JSONObject> tasks = new ArrayList<>();
        LOGGER.debug("Fetching tasks on " + tasksEndPoint);
        final AtomicReference<HttpResponse<JsonNode>> response = new AtomicReference<>();
        Awaitility.await().atMost(30, TimeUnit.SECONDS).pollInterval(1, TimeUnit.SECONDS).until(() -> { // This can take some time, somtimes.
            try {
                response.set(Unirest.get(tasksEndPoint).asJson());
                return true;
            } catch (UnirestException e) {
                LOGGER.debug(e);
                return false;
            }
        });
        for (int i = 0; i < response.get().getBody().getArray().length(); i++) {
            JSONObject jsonObject = response.get().getBody().getArray().getJSONObject(i);
            tasks.add(jsonObject);
        }
        return tasks;
    }

    // TODO (pnw): I shouldn't have to prepend http everywhere. Add here instead.
    public List<String> getEsHttpAddressList() {
        return getTasks().stream().map(ElasticsearchParser::parseHttpAddress).collect(Collectors.toList());
    }

    public void waitForGreen(Integer numNodes) {
        LOGGER.debug("Wating for green and " + numNodes + " nodes.");
        Awaitility.await().atMost(5, TimeUnit.MINUTES).pollInterval(1, TimeUnit.SECONDS).until(() -> { // This can take some time, somtimes.
            try {
                List<String> esAddresses = getEsHttpAddressList();
                // This may throw a JSONException if we call before the JSON has been generated. Hence, catch exception.
                final JSONObject body = Unirest.get("http://" + esAddresses.get(0) + "/_cluster/health").asJson().getBody().getObject();
                final boolean numberOfNodes = body.getInt("number_of_nodes") == numNodes;
                final boolean green = body.getString("status").equals("green");
                final boolean initializingShards = body.getInt("initializing_shards") == 0;
                final boolean unassignedShards = body.getInt("unassigned_shards") == 0;
                LOGGER.debug(green + " and " + numberOfNodes + " and " + initializingShards + " and " + unassignedShards + ": " + body);
                return green && numberOfNodes && initializingShards && unassignedShards;
            } catch (Exception e) {
                LOGGER.debug(e);
                return false;
            }
        });
    }

    public Integer getDocumentCount(String httpAddress) throws UnirestException {
        JSONArray responseElements = Unirest.get("http://" + httpAddress + "/_count").asJson().getBody().getArray();
        LOGGER.debug(responseElements);
        return responseElements.getJSONObject(0).getInt("count");
    }

    public void waitForCorrectDocumentCount(Integer docCount) throws UnirestException {
        List<String> esAddresses = getEsHttpAddressList();
        Awaitility.await().atMost(1, TimeUnit.MINUTES).pollDelay(2, TimeUnit.SECONDS).until(() -> {
            for (String httpAddress : esAddresses) {
                try {
                    Integer count = getDocumentCount(httpAddress);
                    if (docCount != 0 && (count == 0 || count % docCount != 0)) { // This allows for repeated testing. Only run if docCount != 0.
                        return false;
                    }
                } catch (Exception e) {
                    LOGGER.error("Unirest exception:" + e.getMessage());
                    return false;
                }
            }
            return true;
        });
    }
}
