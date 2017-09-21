- [ ] **Submitter**: Include the JIRA issue number in the PR description
- [ ] **Submitter**: Check documentation and code comments. Add explanatory PR comments if helpful.
- [ ] **Submitter**: If you changed a URL that is used elsewhere (e.g. in an email), comment about where it is used and ensure the dependent code is updated.
- [ ] **Submitter**: JIRA ticket checks:
  * Acceptance criteria exists and is met
  * Note any changes to implementation from the description
  * To Demo flag is set
  * Release Summary is filled out, if applicable
  * Add notes on how to QA
- [ ] **Submitter**: Update RC_XXX release ticket with any config or environment changes necessary
- [ ] **Submitter**: Update FISMA documentation if changes to:
  * Authentication
  * Authorization
  * Encryption
  * Audit trails
- [ ] **Submitter**: If you're adding new libraries, sign us up to security updates for them
- [ ] **Submitter**: If you're adding new automated UI tests, review the test plan with QA
* Review cycle:
  * LR reviews
  * Rest of team may comment on PR at will
  * **LR assigns to submitter** for feedback fixes
  * Submitter rebases to develop again if necessary
  * Submitter makes further commits. DO NOT SQUASH
  * Submitter updates documentation as needed
  * Submitter **reassigns to LR** for further feedback
- [ ] **TL** sign off
- [ ] **LR** sign off\
- [ ] **Product Owner** sign off
- [ ] **Submitter**: Verify all tests go green, including CI tests and automated UI tests.
- [ ] **Submitter**: Squash commits and merge to develop. If adding test code, merge application code and test code at the same time.
- [ ] **Submitter**: Delete branch after merge
- [ ] **Submitter**: **Test this change works on dev environment after deployment**. YOU own getting it fixed if dev isn't working for ANY reason!
- [ ] **Submitter**: Mark JIRA issue as resolved once this checklist is completed
