package org.apache.mesos.elasticsearch.performancetest;

import org.apache.log4j.Logger;
import org.apache.mesos.mini.MesosCluster;
import org.apache.mesos.mini.mesos.MesosClusterConfig;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import org.json.JSONObject;

/**
 * Main app to run Mesos Elasticsearch with Mini Mesos.
 */
@SuppressWarnings({"PMD.AvoidUsingHardCodedIP"})
public class Main {

    public static final Logger LOGGER = Logger.getLogger(Main.class);

    private static String slaveHttpAddress;

    private static HttpResponse<JsonNode> response;

    public static void main(String[] args) throws InterruptedException {
        MesosClusterConfig config = MesosClusterConfig.builder()
                .numberOfSlaves(3)
                .privateRegistryPort(15000) // Currently you have to choose an available port by yourself
                .slaveResources(new String[]{"ports(*):[9200-9200,9300-9300]", "ports(*):[9201-9201,9301-9301]", "ports(*):[9202-9202,9302-9302]"})
                .build();

        MesosCluster cluster = new MesosCluster(config);
        cluster.start();
        cluster.injectImage("mesos/elasticsearch-executor");

        LOGGER.info("Starting scheduler");

        ElasticsearchSchedulerContainer scheduler = new ElasticsearchSchedulerContainer(config.dockerClient, cluster.getMesosContainer().getIpAddress());

        scheduler.start();

        /** Get slave 1 ip address */
        String tasksEndPoint = "http://" + scheduler.getIpAddress() + ":8080/v1/tasks";
        LOGGER.debug("Fetching tasks on " + tasksEndPoint);

        try {
            TasksResponse tasksResponse = new TasksResponse(scheduler.getIpAddress());
            JSONObject taskObject = tasksResponse.getJson().getBody().getArray().getJSONObject(0);
            slaveHttpAddress = taskObject.getString("http_address");
        } catch (Exception e) {
            LOGGER.error("Failed to get slave IP. Error:" + e.getMessage());
        }

        DataPusherContainer pusher = new DataPusherContainer(config.dockerClient, slaveHttpAddress);
        pusher.start();
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                scheduler.remove();
                pusher.remove();
                cluster.stop();
            }
        });

        LOGGER.info("Scheduler started at http://" + scheduler.getIpAddress() + ":8080");

        LOGGER.info("Type CTRL-C to quit");
        while (true) {
            Thread.sleep(1000);
        }
    }

}