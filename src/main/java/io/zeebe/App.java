package io.zeebe;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.client.api.worker.JobWorker;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class App
{
    public static void main(final String[] args)
    {
        final String gatewayAddress = System.getenv("ZEEBE_ADDRESS");
        System.out.println("****" + gatewayAddress);

        final ZeebeClient client =
                ZeebeClient.
                        newClientBuilder()
                        .brokerContactPoint("127.0.0.1:26500")
                        .usePlaintext()
                        .build();

//        final ZeebeClient client =
//                ZeebeClient.newClientBuilder()
//                        .gatewayAddress(gatewayAddress)
//                        .build();

        System.out.println("Connected");

        final DeploymentEvent deployment = client.newDeployCommand()
                .addResourceFromClasspath("order-process.bpmn")
                .send()
                .join();

        final int version = deployment.getWorkflows().get(0).getVersion();
        System.out.println("Workflow deployed. Version: " + version);

        final Map<String, Object> data = new HashMap<>();
        data.put("orderId", 31243);
        data.put("orderItems", Arrays.asList(435, 182, 376));

        final WorkflowInstanceEvent wfInstance = client.newCreateInstanceCommand()
                .bpmnProcessId("order-process")
                .latestVersion()
                .variables(data)
                .send()
                .join();

        final long workflowInstanceKey = wfInstance.getWorkflowInstanceKey();

        System.out.println("Workflow instance created. Key: " + workflowInstanceKey);


        final JobWorker jobWorker = client.newWorker()
                .jobType("payment-service")
                .handler((jobClient, job) -> {
                    final Map<String, Object> variables = job.getVariablesAsMap();
                    System.out.println("Process order: " + variables.get("orderId"));
                    double price = 46.50;
                    System.out.println("Collect money: $" + price);

                    final Map<String, Object> result = new HashMap<>();
                    result.put("totalPrice", price);

                    jobClient.newCompleteCommand(job.getKey())
                            .variables(result)
                            .send()
                            .join();
                })
                .fetchVariables("orderId")
                .open();


        final JobWorker fetcherService = client.newWorker()
                .jobType("fetcher-service")
                .handler((jobClient, job) -> {
                    final Map<String, Object> variables = job.getVariablesAsMap();
                    System.out.println("Process order: " + variables.get("orderId"));
                    System.out.println("Fetch items: " + variables.get("orderItems"));

                    jobClient.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .open();

        final JobWorker shippingService = client.newWorker()
                .jobType("shipping-service")
                .handler((jobClient, job) -> {
                    final Map<String, Object> variables = job.getVariablesAsMap();
                    System.out.println("Process order: " + variables.get("orderId"));
                    System.out.println("Fetch items: " + variables.get("orderItems"));

                    jobClient.newCompleteCommand(job.getKey())
                            .send()
                            .join();
                })
                .open();

//        jobWorker.close();
//
//        client.close();
//        System.out.println("Closed.");
    }
}