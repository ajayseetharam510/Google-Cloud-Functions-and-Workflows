package com.example;

import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;


import com.google.api.services.cloudresourcemanager.model.Binding;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.SetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.model.Policy;
import com.google.api.services.iam.v1.IamScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.io.PrintWriter;



// Add GCP Service Account to all projects with Project IAM Admin/Security Admin role
// Sample Input payloads
// {"action":"LIST POLICY", "projectId": "foo", "member": "user:mygoogleuser"}
// {"action":"ADD ROLE", "projectId": "foo", "member": "user:mygoogleuser", "role": "roles/browser"}
// {"action":"REMOVE ROLE", "projectId": "foo", "member": "user:mygoogleuser", "role": "roles/browser"}


public class Example implements HttpFunction {
  @Override
  public void service(HttpRequest request, HttpResponse response) throws Exception {


    // Reference: Google documentation https://cloud.google.com/iam/docs/quickstart-client-libraries

    Policy policy = null;
    System.out.println("Cloud Function HTTP Method "+ request.getMethod() + " for URL "+ request.getUri() + " with query " + request.getQuery().toString());
    Gson gson = new Gson();
    String action = "";
    String projectId = "";
    String member = "";
    String role = "";


    // Parse JSON request and check for "action" (mandatory), "projectId" (mandatory),"member","role" fields
    // "action" is a mandatory field and one of "LIST POLICY". "ADD ROLE", "REMOVE ROLE" for "member"

    try {
      JsonElement requestParsed = gson.fromJson(request.getReader(), JsonElement.class);
      JsonObject requestJson = null;

      if (requestParsed != null && requestParsed.isJsonObject()) {
        requestJson = requestParsed.getAsJsonObject();
      }

      if (requestJson != null && requestJson.has("action")) {
         action = requestJson.get("action").getAsString();
      }

      if (requestJson != null && requestJson.has("projectId")) {
        projectId = requestJson.get("projectId").getAsString();
      }

      if (requestJson != null && requestJson.has("member")) {
        member = requestJson.get("member").getAsString();
      }
      if (requestJson != null && requestJson.has("role")) {
        role = requestJson.get("role").getAsString();
      }
      System.out.println("action "+action + " projectId " + projectId + " member " + member + " role " + role);

    } catch (JsonParseException e) {

      java.util.logging.Logger logger =  java.util.logging.Logger.getLogger(this.getClass().getName());

      logger.severe("Error parsing JSON: " + e.getMessage());
    }


    CloudResourceManager service = null;
      try {
        service = createCloudResourceManagerService();
      } catch (IOException | GeneralSecurityException e) {
        System.out.println("Unable to initialize service: \n" + e.toString());
        var writer = new PrintWriter(response.getWriter());
        writer.printf("Unable to get policy for projectId %s", projectId);
      }

      try {
        GetIamPolicyRequest requestPolicy = new GetIamPolicyRequest();
        policy = service.projects().getIamPolicy(projectId, requestPolicy).execute();
        System.out.println("Policy retrieved: " + policy.toString());
        if (action.equalsIgnoreCase("LIST POLICY")) {
          var writer = new PrintWriter(response.getWriter());
          writer.printf("%s", policy.toString());
        }

      } catch (IOException e) {
        System.out.println("Unable to get policy: \n" + e.toString());
        var writer = new PrintWriter(response.getWriter());
        writer.printf("Error %s", e.toString());

      }


    if (action.equalsIgnoreCase("ADD ROLE")) {
        try {
          addBinding(service, projectId, member, role);
          GetIamPolicyRequest requestPolicy = new GetIamPolicyRequest();
          policy = service.projects().getIamPolicy(projectId, requestPolicy).execute();
          System.out.println("Policy retrieved: " + policy.toString());
          var writer = new PrintWriter(response.getWriter());
          writer.printf("%s", policy.toString()); // Client should doublecheck policy fulfillment

        }
        catch (Exception e) {
          System.out.println("Unable to add member to role: \n" + e.toString());
          var writer = new PrintWriter(response.getWriter());
          writer.printf("Error %s", e.toString());

        }

      }

    if (action.equalsIgnoreCase("REMOVE ROLE")) {
      try {
        removeMember(service, projectId, member, role);
        GetIamPolicyRequest requestPolicy = new GetIamPolicyRequest();
        policy = service.projects().getIamPolicy(projectId, requestPolicy).execute();
        System.out.println("Policy retrieved: " + policy.toString());
        var writer = new PrintWriter(response.getWriter());
        writer.printf("%s", policy.toString()); // Client should doublecheck policy fulfillment

      }
      catch (Exception e) {
        System.out.println("Unable to remove member for role: \n" + e.toString());
        var writer = new PrintWriter(response.getWriter());
        writer.printf("Error %s", e.toString());

      }

    }

  }

  public static CloudResourceManager createCloudResourceManagerService()
          throws IOException, GeneralSecurityException {
    // Use the Application Default Credentials strategy for authentication. For more info, see:
    // https://cloud.google.com/docs/authentication/production#finding_credentials_automatically
    GoogleCredentials credential =
            GoogleCredentials.getApplicationDefault()
                    .createScoped(Collections.singleton(IamScopes.CLOUD_PLATFORM));

    CloudResourceManager service =
            new CloudResourceManager.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    JacksonFactory.getDefaultInstance(),
                    new HttpCredentialsAdapter(credential))
                    .setApplicationName("service-accounts")
                    .build();
    return service;
  }

  public static void addBinding(
          CloudResourceManager crmService, String projectId, String member, String role) {

    // Gets the project's policy.
    Policy policy = getPolicy(crmService, projectId);

    // Finds binding in policy, if it exists
    Binding binding = null;
    for (Binding b : policy.getBindings()) {
      if (b.getRole().equals(role)) {
        binding = b;
        break;
      }
    }

    if (binding != null) {
      // If binding already exists, adds member to binding.
      binding.getMembers().add(member);
    } else {
      // If binding does not exist, adds binding to policy.
      binding = new Binding();
      binding.setRole(role);
      binding.setMembers(Collections.singletonList(member));
      policy.getBindings().add(binding);
    }

    // Sets the updated policy
    setPolicy(crmService, projectId, policy);
  }

  public static void removeMember(
          CloudResourceManager crmService, String projectId, String member, String role) {
    // Gets the project's policy.
    Policy policy = getPolicy(crmService, projectId);

    // Removes the member from the role.
    Binding binding = null;
    for (Binding b : policy.getBindings()) {
      if (b.getRole().equals(role)) {
        binding = b;
        break;
      }
    }
    if (binding.getMembers().contains(member)) {
      binding.getMembers().remove(member);
      if (binding.getMembers().isEmpty()) {
        policy.getBindings().remove(binding);
      }
    }

    // Sets the updated policy.
    setPolicy(crmService, projectId, policy);
  }

  public static Policy getPolicy(CloudResourceManager crmService, String projectId) {
    // Gets the project's policy by calling the
    // Cloud Resource Manager Projects API.
    Policy policy = null;
    try {
      GetIamPolicyRequest request = new GetIamPolicyRequest();
      policy = crmService.projects().getIamPolicy(projectId, request).execute();
    } catch (IOException e) {
      System.out.println("Unable to get policy: \n" + e.getMessage() + e.getStackTrace());
    }
    return policy;
  }

  private static void setPolicy(CloudResourceManager crmService, String projectId, Policy policy) {
    // Sets the project's policy by calling the
    // Cloud Resource Manager Projects API.
    try {
      SetIamPolicyRequest request = new SetIamPolicyRequest();
      request.setPolicy(policy);
      crmService.projects().setIamPolicy(projectId, request).execute();
    } catch (IOException e) {
      System.out.println("Unable to set policy: \n" + e.getMessage() + e.getStackTrace());
    }
  }
}


