# Report Problem Protocol 1.0 & 2.0

This Protocol is part of Aries (RFC 0035).
Describes how to report errors and warnings in a powerful, interoperable way.

- Version 1.0 - see [https://github.com/hyperledger/aries-rfcs/tree/main/features/0035-report-problem]
- Version 2.0:
  - see [https://identity.foundation/didcomm-messaging/spec/#problem-reports]
  - see [https://didcomm.org/report-problem/2.0/]

NOTE: In this context never reference to `Error` or `Warning`. Always reference as `Problem`.

TODO: Support [l10n](https://github.com/hyperledger/aries-rfcs/blob/main/features/0043-l10n/README.md) in the Future.

## PIURI

- Version 1.0:
  - `https://didcomm.org/report-problem/1.0`
- Version 2.0:
  - `https://didcomm.org/report-problem/2.0/problem-report`

## Notes

The protocol is one-way, a simple one-step notification protocol:

## Roles

- `notifier` - Who sends notification.
- `notified` - Who receive notification.
