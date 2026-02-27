package org.hyperledger.identus.notifications.api

/** Re-exports from event.notification for the notifications bounded context API.
  *
  * These type aliases establish the public API surface for the Notifications bounded context. Consumers should depend on
  * notifications-api rather than eventNotification directly. In a future phase, the actual types will be moved here and
  * the aliases reversed.
  */

// Service trait
type EventNotificationService = org.hyperledger.identus.event.notification.EventNotificationService

// Event types
type Event[A] = org.hyperledger.identus.event.notification.Event[A]
val Event = org.hyperledger.identus.event.notification.Event

type EventProducer[A] = org.hyperledger.identus.event.notification.EventProducer[A]
type EventConsumer[A] = org.hyperledger.identus.event.notification.EventConsumer[A]

// Config
type EventNotificationConfig = org.hyperledger.identus.event.notification.EventNotificationConfig
val EventNotificationConfig = org.hyperledger.identus.event.notification.EventNotificationConfig

// Error types
type EventNotificationServiceError = org.hyperledger.identus.event.notification.EventNotificationServiceError
val EventNotificationServiceError = org.hyperledger.identus.event.notification.EventNotificationServiceError
