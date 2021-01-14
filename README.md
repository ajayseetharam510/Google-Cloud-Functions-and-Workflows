# Google-Cloud-Functions-and-Workflows
Example of using Google Cloud Functions to manage Google Cloud Permissions

Reference: Google documentation https://cloud.google.com/iam/docs/quickstart-client-libraries

Sample Input payloads
// {"action":"LIST POLICY", "projectId": "foo", "member": "user:mygoogleuser"}
// {"action":"ADD ROLE", "projectId": "foo", "member": "user:mygoogleuser", "role": "roles/browser"}
// {"action":"REMOVE ROLE", "projectId": "foo", "member": "user:mygoogleuser", "role": "roles/browser"}

Deploy with 
gcloud functions deploy function1 --entry-point com.example.Example --runtime java11 --trigger-http

