# URL Handling

Although our UI is a single-page application, we make extensive use of URLs to aid in usability. They allow bookmarking, back/forward navigation, and give some semantic meaning to the user's location within the application.

Other services must construct URLs that point to pages within our UI. For example, we send emails to users that may contain a URL for a particular workspace. Currently, these URLs are constructed by each service as needed, yielding the full suite of problems associated with duplicated code.

To avoid duplicating code, we need to engineer a solution that meets the following requirements:
 - The solution must be **language-agnostic**, since we can expect it to be consumed by any service regardless of the underlying implementation language.
 - It must handle URL **construction** and **parsing**, since these operations have to be symmetric and should therefore be defined in close proximity.
 - Ideally, it should not prevent a developer outside of the Broad from starting up the UI (the definitions should exist in an accessible location for any developer who clones the UI repo).

## Option 1: JSON-Based URL Definitions

The UI and any services needing to construct URLs do so from a JSON file following this example:

```json
{
  "billing": {
    "path": "billing"
  },
  "billingProject": {
    "path": "billing/$projectName",
    "regex": "billing/([^/]+)"
  },
  "methodRepo": {
    "path": "methods"
  },
  "policy": {
    "path": "policy"
  },
  "workspaceData": {
    "path": "workspaces/$namespace/$name/data",
    "regex": "workspaces/([^/]+)/([^/]+)/data"
  },
  "workspaceMethodConfig": {
    "path": "workspaces/$wsNamespace/$wsName/method-configs/$mcNamespace/$mcName",
    "regex": "workspaces/([^/]+)/([^/]+)/method-configs/([^/]+)/([^/]+)",
  },
  "workspaceSummary": {
    "path": "workspaces/$namespace/$name",
    "regex": "workspaces/([^/]+)/([^/]+)"
  }
}
```

Notes:
 - The `regex` key is omitted when it is the same as the `path` key.
 - Camel-case is used for language-agnostic identifiers.

### Location

This JSON file would exist within the UI repo. It may be consumed by other services by downloading it from http://ui-host.example.com/urls.json.
