-- Copyright 2026 The Collect One Authors.
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Cloud Spanner schema of the user data service: the first stage of
-- docs/technical/backend/spanner-schema.md. Records are stored as PROTO columns
-- (encoded by the service with Wire); columns the database filters, sorts, or
-- indexes on are STORED generated columns. CommitTs is the commit timestamp of
-- a row's last write and overrides the proto's commit_timestamp on read.
--
-- Statements end with ";" on its own line end. Applied by SpannerSchema.

CREATE PROTO BUNDLE (
  groundplatform.v2.data.AuditInfo,
  groundplatform.v2.data.AuditRecord,
  groundplatform.v2.data.EntityRecord,
  groundplatform.v2.data.ImpactEvent,
  groundplatform.v2.data.ImpactEventType,
  groundplatform.v2.data.PlotLock,
  groundplatform.v2.data.ReviewStatus,
  groundplatform.v2.data.SubmissionRecord,
  groundplatform.v2.forms.DataType,
  groundplatform.v2.library.ConceptDef,
  groundplatform.v2.library.OrganizationLibraryRecord,
  groundplatform.v2.survey.AclEntry,
  groundplatform.v2.survey.CachedProfile,
  groundplatform.v2.survey.MembershipStatus,
  groundplatform.v2.survey.Organization,
  groundplatform.v2.survey.OrganizationMember,
  groundplatform.v2.survey.OrganizationRole,
  groundplatform.v2.survey.PeerDataVisibility,
  groundplatform.v2.survey.Role,
  groundplatform.v2.survey.SharingPolicy,
  groundplatform.v2.survey.SurveyAcl,
  groundplatform.v2.survey.SurveyDef,
  groundplatform.v2.survey.SurveyState
);

-- Deployment-wide metadata. DataEpoch rotates when the data is reset.
CREATE TABLE ServiceMetadata (
  Singleton BOOL NOT NULL,
  DataEpoch STRING(36) NOT NULL,
) PRIMARY KEY (Singleton);

-- Signed-in users. AccessEpoch increases when the user's ACL entries or memberships change;
-- survey overview and organization list tokens carry it.
CREATE TABLE Users (
  UserId STRING(128) NOT NULL,
  EmailNormalized STRING(320),
  EmailVerified BOOL NOT NULL,
  Profile groundplatform.v2.survey.CachedProfile,
  AccessEpoch INT64 NOT NULL,
  CreateTime TIMESTAMP NOT NULL,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (UserId);

CREATE NULL_FILTERED INDEX UsersByEmail ON Users(EmailNormalized);

CREATE TABLE Organizations (
  OrganizationId UUID NOT NULL,
  Record groundplatform.v2.survey.Organization NOT NULL,
  Name STRING(MAX) AS (Record.name) STORED,
  Listed BOOL AS (Record.listed) STORED,
  IsDeleted BOOL AS (Record.is_deleted) STORED,
  SearchTokens TOKENLIST AS (TOKENIZE_SUBSTRING(CONCAT(IFNULL(Record.name, ''), ' ', IFNULL(Record.description, '')))) HIDDEN,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (OrganizationId);

-- SearchOrganizations: case-insensitive substring search on name and description.
CREATE SEARCH INDEX OrganizationsSearch ON Organizations(SearchTokens) STORING (Listed, IsDeleted);

CREATE TABLE OrganizationMembers (
  OrganizationId UUID NOT NULL,
  EmailNormalized STRING(320) NOT NULL,
  Record groundplatform.v2.survey.OrganizationMember NOT NULL,
  UserId STRING(128) AS (NULLIF(Record.user_id, '')) STORED,
  Role groundplatform.v2.survey.OrganizationRole AS (Record.role) STORED,
  Status groundplatform.v2.survey.MembershipStatus AS (Record.status) STORED,
  InviteToken STRING(64) AS (NULLIF(Record.invite_token, '')) STORED,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (OrganizationId, EmailNormalized),
  INTERLEAVE IN PARENT Organizations ON DELETE CASCADE;

CREATE NULL_FILTERED INDEX MembersByUser ON OrganizationMembers(UserId) STORING (Status, Role);

CREATE INDEX MembersByEmail ON OrganizationMembers(EmailNormalized);

CREATE UNIQUE NULL_FILTERED INDEX MembersByInvite ON OrganizationMembers(InviteToken);

CREATE TABLE OrganizationLibraries (
  OrganizationId UUID NOT NULL,
  Record groundplatform.v2.library.OrganizationLibraryRecord NOT NULL,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (OrganizationId),
  INTERLEAVE IN PARENT Organizations ON DELETE CASCADE;

-- Concepts fanned out from each library's bundle in the same transaction, for SearchConcepts.
-- Position is the concept's index in its bundle, so resolved order matches the bundle order.
CREATE TABLE LibraryConcepts (
  OrganizationId UUID NOT NULL,
  ConceptId STRING(256) NOT NULL,
  Concept groundplatform.v2.library.ConceptDef NOT NULL,
  Position INT64 NOT NULL,
  DataType groundplatform.v2.forms.DataType AS (Concept.data_type) STORED,
  SearchText STRING(MAX) NOT NULL,
  SearchTokens TOKENLIST AS (TOKENIZE_SUBSTRING(SearchText)) HIDDEN,
) PRIMARY KEY (OrganizationId, ConceptId),
  INTERLEAVE IN PARENT OrganizationLibraries ON DELETE CASCADE;

CREATE SEARCH INDEX LibraryConceptsSearch ON LibraryConcepts(SearchTokens) STORING (DataType);

CREATE TABLE Surveys (
  SurveyId UUID NOT NULL,
  SavedDef groundplatform.v2.survey.SurveyDef NOT NULL,
  AclRules groundplatform.v2.survey.SurveyAcl,
  -- When AclRules last changed; sharing changes don't restamp the survey definition.
  AclCommitTs TIMESTAMP OPTIONS (allow_commit_timestamp = true),
  OrganizationId UUID AS (CAST(NULLIF(SavedDef.organization_id, '') AS UUID)) STORED,
  OwnerId STRING(MAX) AS (SavedDef.owner_id) STORED,
  OwnerEmail STRING(320) AS (LOWER(AclRules.owner_id)) STORED,
  State groundplatform.v2.survey.SurveyState AS (SavedDef.state) STORED,
  SharingPolicy groundplatform.v2.survey.SharingPolicy AS (AclRules.sharing_policy) STORED,
  PeerDataVisibility groundplatform.v2.survey.PeerDataVisibility AS (AclRules.peer_data_visibility) STORED,
  PurgedAt TIMESTAMP OPTIONS (allow_commit_timestamp = true),
  SearchTokens TOKENLIST AS (TOKENIZE_SUBSTRING(CONCAT(IFNULL(SavedDef.title, ''), ' ', IFNULL(SavedDef.description, '')))) HIDDEN,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (SurveyId);

-- SearchPublicSurveys: case-insensitive substring search on title and description.
CREATE SEARCH INDEX SurveysSearch ON Surveys(SearchTokens) STORING (SharingPolicy, State);

CREATE NULL_FILTERED INDEX SurveysByOrganization ON Surveys(OrganizationId, CommitTs)
  STORING (SharingPolicy, State, OwnerId);

-- A caller's own surveys, for survey lists and the per-owner survey quota.
CREATE INDEX SurveysByOwner ON Surveys(OwnerId);

CREATE NULL_FILTERED INDEX SurveysByOwnerEmail ON Surveys(OwnerEmail);

CREATE TABLE SurveyWorkingCopies (
  SurveyId UUID NOT NULL,
  Def groundplatform.v2.survey.SurveyDef NOT NULL,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (SurveyId),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

-- Immutable snapshot appended on save_working_copy; serves GetSurvey(saved_version).
CREATE TABLE SurveyRevisions (
  SurveyId UUID NOT NULL,
  RevisionNumber INT64 NOT NULL,
  Def groundplatform.v2.survey.SurveyDef NOT NULL,
  Version STRING(64) AS (Def.version) STORED,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (SurveyId, RevisionNumber DESC),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

CREATE TABLE SurveyAclEntries (
  SurveyId UUID NOT NULL,
  EmailNormalized STRING(320) NOT NULL,
  Record groundplatform.v2.survey.AclEntry NOT NULL,
  UserId STRING(128) AS (NULLIF(Record.user_id, '')) STORED,
  Role groundplatform.v2.survey.Role AS (Record.role) STORED,
  InviteToken STRING(64) AS (NULLIF(Record.invite_token, '')) STORED,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (SurveyId, EmailNormalized),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

CREATE NULL_FILTERED INDEX AclByUser ON SurveyAclEntries(UserId) STORING (Role);

CREATE INDEX AclByEmail ON SurveyAclEntries(EmailNormalized);

CREATE UNIQUE NULL_FILTERED INDEX AclByInvite ON SurveyAclEntries(InviteToken);

CREATE TABLE Entities (
  SurveyId UUID NOT NULL,
  EntityId UUID NOT NULL,
  Record groundplatform.v2.data.EntityRecord NOT NULL,
  DatasetId STRING(128) AS (Record.dataset_id) STORED,
  IsDeleted BOOL AS (Record.is_deleted) STORED,
  CreatedBy STRING(128) AS (Record.audit_info.created_by) STORED,
  Label STRING(MAX) AS (Record.label) STORED,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
  DeletedAt TIMESTAMP OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (SurveyId, EntityId),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

CREATE INDEX EntitiesDelta ON Entities(SurveyId, CommitTs, EntityId), INTERLEAVE IN Surveys;

CREATE TABLE PlotLocks (
  SurveyId UUID NOT NULL,
  EntityId UUID NOT NULL,
  Lock groundplatform.v2.data.PlotLock NOT NULL,
) PRIMARY KEY (SurveyId, EntityId),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

CREATE TABLE Submissions (
  SurveyId UUID NOT NULL,
  SubmissionId UUID NOT NULL,
  Record groundplatform.v2.data.SubmissionRecord NOT NULL,
  FormId STRING(128) AS (Record.form_id) STORED,
  IsDeleted BOOL AS (Record.is_deleted) STORED,
  CreatedBy STRING(128) AS (Record.audit_info.created_by) STORED,
  ReviewStatus groundplatform.v2.data.ReviewStatus AS (Record.review_status) STORED,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
  DeletedAt TIMESTAMP OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (SurveyId, SubmissionId),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

CREATE INDEX SubmissionsDelta ON Submissions(SurveyId, CommitTs, SubmissionId), INTERLEAVE IN Surveys;

CREATE TABLE AuditRecords (
  SurveyId UUID NOT NULL,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
  AuditId UUID NOT NULL,
  Record groundplatform.v2.data.AuditRecord NOT NULL,
  TargetId STRING(64) AS (Record.target_id) STORED,
) PRIMARY KEY (SurveyId, CommitTs, AuditId),
  INTERLEAVE IN PARENT Surveys ON DELETE CASCADE;

CREATE TABLE ImpactEvents (
  EventId UUID NOT NULL,
  Record groundplatform.v2.data.ImpactEvent NOT NULL,
  SurveyId STRING(36) AS (NULLIF(Record.survey_id, '')) STORED,
  OrganizationId STRING(36) AS (NULLIF(Record.organization_id, '')) STORED,
  Type groundplatform.v2.data.ImpactEventType AS (Record.type) STORED,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (EventId);

CREATE NULL_FILTERED INDEX ImpactBySurvey ON ImpactEvents(SurveyId, CommitTs, EventId);

-- Replay detection: the highest applied client_seq per client and scope.
CREATE TABLE ClientMutationWatermarks (
  ClientId STRING(64) NOT NULL,
  Scope STRING(128) NOT NULL,
  MaxClientSeq INT64 NOT NULL,
  CommitTs TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp = true),
) PRIMARY KEY (ClientId, Scope),
  ROW DELETION POLICY (OLDER_THAN(CommitTs, INTERVAL 365 DAY));
