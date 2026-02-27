package org.hyperledger.identus.server

/** Re-exports from agent.server.jobs for the api-server background jobs module.
  *
  * These type aliases establish the public surface for background job orchestration, grouped by bounded context. In a
  * future phase, the actual implementations will be moved here.
  *
  * Bounded context groupings:
  *   - Connections: ConnectBackgroundJobs
  *   - Credentials: IssueBackgroundJobs, PresentBackgroundJobs, StatusListJobs
  *   - DID/Wallet: DIDStateSyncBackgroundJobs
  *   - Shared infrastructure: BackgroundJobsHelper, BackgroundJobError
  */
package object jobs {
  // Connections context
  val ConnectBackgroundJobs = org.hyperledger.identus.agent.server.jobs.ConnectBackgroundJobs

  // Credentials context
  val IssueBackgroundJobs = org.hyperledger.identus.agent.server.jobs.IssueBackgroundJobs
  val PresentBackgroundJobs = org.hyperledger.identus.agent.server.jobs.PresentBackgroundJobs
  val StatusListJobs = org.hyperledger.identus.agent.server.jobs.StatusListJobs

  // DID/Wallet context
  val DIDStateSyncBackgroundJobs = org.hyperledger.identus.agent.server.jobs.DIDStateSyncBackgroundJobs

  // Shared job infrastructure
  type BackgroundJobsHelper = org.hyperledger.identus.agent.server.jobs.BackgroundJobsHelper
  type BackgroundJobError = org.hyperledger.identus.agent.server.jobs.BackgroundJobError
}
