# Salesforce

## Overview

The Salesforce source supports both `Full Refresh` and `Incremental` syncs. You can choose if this connector will copy only the new or updated data, or all rows in the tables and columns you set up for replication, every time a sync is run.

The Connector supports replicating both standard and custom Salesforce objects.

### Features

| Feature | Supported? |
| :--- | :--- |
| Full Refresh Sync | Yes |
| Incremental Sync | Yes |
| SSL connection | Yes |
| Namespaces | No |

#### Incremental Deletes Sync
This connector retrieves deleted records from Salesforce. For the streams which support it, a deleted record will be marked with the field `isDeleted=true` value.  

### Performance considerations

The connector is restricted by daily Salesforce rate limiting.
The connector uses as much rate limit as it can every day, then ends the sync early with success status and continues the sync from where it left the next time.
Note that, picking up from where it ends will work only for incremental sync.

## Getting Started (Airbyte Cloud)
#### Sandbox accounts

If you log in using at [https://login.salesforce.com](https://login.salesforce.com), then your account is not a sandbox. If you log in at [https://test.salesforce.com](https://test.salesforce.com) then it's a sandbox. 

If this is Greek to you, then you are likely not using a sandbox account.

### Requirements

* Salesforce Account
* Dedicated Salesforce user (optional)

**Note**: In order to tightly scope Airbyte's access to your data, we recommend creating a dedicated read-only Salesforce user that is used for Airbyte syncs. You can also further the user's (and therefore Airbyte's) access to only the data and streams you need Airbyte to replicate by creating a profile in Salesforce and assigning it to the user.

### Setup guide

1. Toggle whether your Salesforce account is a Sandbox account or a live account.  
2. Click `Authenticate your Salesforce account` to sign in with Salesforce and authorize your account.
3. Fill in the rest of the details.
4. You should be ready to sync data. 

## Getting started (Airbyte OSS) 
### Requirements
* Salesforce Account
* Salesforce OAuth credentials
* Dedicated Salesforce user (optional)

**Note**: In order to tightly scope Airbyte's access to your data, we recommend creating a dedicated read-only Salesforce user that is used for Airbyte syncs. You can also further the user's (and therefore Airbyte's) access to only the data and streams you need Airbyte to replicate by creating a profile in Salesforce and assigning it to the user.

### Setup guide

#### Sandbox accounts
If you log in using at [https://login.salesforce.com](https://login.salesforce.com), then your account is not a sandbox. If you log in at [https://test.salesforce.com](https://test.salesforce.com) then it's a sandbox. 

If this is Greek to you, then you are likely not using a sandbox account.
We recommend the following [walkthrough](https://medium.com/@bpmmendis94/obtain-access-refresh-tokens-from-salesforce-rest-api-a324fe4ccd9b) **while keeping in mind the edits we suggest below** for setting up a Salesforce app that can pull data from Salesforce and locating the credentials you need to provide to Airbyte.

Suggested edits:

1. If your salesforce URL does not take the form `X.salesforce.com`, use your actual Salesforce domain name. For example, if your Salesforce URL is `awesomecompany.force.com` then use that instead of `awesomecompany.salesforce.com`. 
2. When running a `curl` command, always run it with the `-L` option to follow any redirects.

## Streams

**Note**: The connector supports reading both standard streams and Custom Objects from Salesforce.

We fetch and handle all the possible & available streams dynamically based on:
- User Role & Permissions to read & fetch objects and their data
- Whether or not the stream has the queryable property set to true. Queryable streams are available to be fetched via the API. If you cannot see your object available via Airbyte, please ensure it is API-accessible to the user you used for authenticating into Airbyte  

**Note**: Using the BULK API is not possible to receive data from the following streams:

* AcceptedEventRelation
* AssetTokenEvent
* AttachedContentNote
* Attachment
* CaseStatus
* ContractStatus
* DeclinedEventRelation
* EventWhoRelation
* FieldSecurityClassification
* OrderStatus
* PartnerRole
* QuoteTemplateRichTextData
* RecentlyViewed
* ServiceAppointmentStatus
* SolutionStatus
* TaskPriority
* TaskStatus
* TaskWhoRelation
* UndecidedEventRelation

## Changelog

| Version | Date       | Pull Request | Subject                                                                                                                          |
|:--------|:-----------| :--- |:---------------------------------------------------------------------------------------------------------------------------------|
| 1.0.1 | 2022-02-27 | [10679](https://github.com/airbytehq/airbyte/pull/10679) | Reorganize input parameter order on the UI |
| 1.0.0 | 2022-02-27 | [10516](https://github.com/airbytehq/airbyte/pull/10516) | Speed up schema discovery by using parallelism |
| 0.1.23  | 2022-02-10 | [10141](https://github.com/airbytehq/airbyte/pull/10141) | Processing of failed jobs                                                                                                        |
| 0.1.22  | 2022-02-02 | [10012](https://github.com/airbytehq/airbyte/pull/10012) | Increase CSV field_size_limit                                                                                                    |
| 0.1.21  | 2022-01-28 | [9499](https://github.com/airbytehq/airbyte/pull/9499) | If a sync reaches daily rate limit it ends the sync early with success status. Read more in `Performance considerations` section |
| 0.1.20  | 2022-01-26 | [9757](https://github.com/airbytehq/airbyte/pull/9757) | Parse CSV with "unix" dialect                                                                                                    |
| 0.1.19  | 2022-01-25 | [8617](https://github.com/airbytehq/airbyte/pull/8617) | Update connector fields title/description                                                                                        |
| 0.1.18  | 2022-01-20 | [9478](https://github.com/airbytehq/airbyte/pull/9478) | Add available stream filtering by `queryable` flag                                                                               |
| 0.1.17  | 2022-01-19 | [9302](https://github.com/airbytehq/airbyte/pull/9302) | Deprecate API Type parameter                                                                                                     |
| 0.1.16  | 2022-01-18 | [9151](https://github.com/airbytehq/airbyte/pull/9151) | Fix pagination in REST API streams                                                                                               |
| 0.1.15  | 2022-01-11 | [9409](https://github.com/airbytehq/airbyte/pull/9409) | Correcting the presence of an extra `else` handler in the error handling                                                         |
| 0.1.14  | 2022-01-11 | [9386](https://github.com/airbytehq/airbyte/pull/9386) | Handling 400 error, while `sobject` doesn't support `query` or `queryAll` requests                                               |
| 0.1.13  | 2022-01-11 | [8797](https://github.com/airbytehq/airbyte/pull/8797) | Switched from authSpecification to advanced_auth in specefication                                                                |
| 0.1.12  | 2021-12-23 | [8871](https://github.com/airbytehq/airbyte/pull/8871) | Fix `examples` for new field in specification                                                                                    |
| 0.1.11  | 2021-12-23 | [8871](https://github.com/airbytehq/airbyte/pull/8871) | Add the ability to filter streams by user                                                                                        |
| 0.1.10  | 2021-12-23 | [9005](https://github.com/airbytehq/airbyte/pull/9005) | Handling 400 error when a stream is not queryable                                                                                |
| 0.1.9   | 2021-12-07 | [8405](https://github.com/airbytehq/airbyte/pull/8405) | Filter 'null' byte(s) in HTTP responses                                                                                          |
| 0.1.8   | 2021-11-30 | [8191](https://github.com/airbytehq/airbyte/pull/8191) | Make `start_date` optional and change its format to `YYYY-MM-DD`                                                                 |
| 0.1.7   | 2021-11-24 | [8206](https://github.com/airbytehq/airbyte/pull/8206) | Handling 400 error when trying to create a job for sync using Bulk API.                                                          |
| 0.1.6   | 2021-11-16 | [8009](https://github.com/airbytehq/airbyte/pull/8009) | Fix retring of BULK jobs                                                                                                         |
| 0.1.5   | 2021-11-15 | [7885](https://github.com/airbytehq/airbyte/pull/7885) | Add `Transform` for output records                                                                                               |
| 0.1.4   | 2021-11-09 | [7778](https://github.com/airbytehq/airbyte/pull/7778) | Fix types for `anyType` fields                                                                                                   |
| 0.1.3   | 2021-11-06 | [7592](https://github.com/airbytehq/airbyte/pull/7592) | Fix getting `anyType` fields using BULK API                                                                                      |
| 0.1.2   | 2021-09-30 | [6438](https://github.com/airbytehq/airbyte/pull/6438) | Annotate Oauth2 flow initialization parameters in connector specification                                                        |
| 0.1.1   | 2021-09-21 | [6209](https://github.com/airbytehq/airbyte/pull/6209) | Fix bug with pagination for BULK API                                                                                             |
| 0.1.0   | 2021-09-08 | [5619](https://github.com/airbytehq/airbyte/pull/5619) | Salesforce Aitbyte-Native Connector                                                                                              |
