/*
 * Copyright 2026 The Collect One Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openforis.collectone.v2.server.app

import com.squareup.wire.Instant
import java.io.File
import java.net.URI
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import openforis.collectone.v1.userdata.UserDataSeed
import org.openforis.collectone.v2.server.auth.Authenticator
import org.openforis.collectone.v2.server.auth.EmailPasslist
import org.openforis.collectone.v2.server.data.InMemoryUserDataStore
import org.openforis.collectone.v2.server.data.SpannerConnection
import org.openforis.collectone.v2.server.data.SpannerOperationRecorder
import org.openforis.collectone.v2.server.data.SpannerSchema
import org.openforis.collectone.v2.server.data.SpannerSettings
import org.openforis.collectone.v2.server.data.SpannerUserDataStore
import org.openforis.collectone.v2.server.data.StagedSeedOverlayUserDataStore
import org.openforis.collectone.v2.server.data.UserDataStore
import org.openforis.collectone.v2.server.data.userDataSeedJsonAdapter
import org.openforis.collectone.v2.server.debug.SpannerDebug
import org.openforis.collectone.v2.server.geo.EarthEngineTileClient
import org.openforis.collectone.v2.server.geo.EarthEngineValueClient
import org.openforis.collectone.v2.server.geo.HttpAgStackRegistryClient
import org.openforis.collectone.v2.server.geo.HttpWhispClient
import org.openforis.collectone.v2.server.geo.ImageryAnalytics
import org.openforis.collectone.v2.server.messaging.BrevoHttpEmailSender
import org.openforis.collectone.v2.server.messaging.DebouncedSurveyTopicNotifier
import org.openforis.collectone.v2.server.messaging.EmailInviteNotifier
import org.openforis.collectone.v2.server.messaging.EmailSender
import org.openforis.collectone.v2.server.messaging.FcmHttpV1TopicSender
import org.openforis.collectone.v2.server.messaging.InviteNotifier
import org.openforis.collectone.v2.server.messaging.LoggingEmailSender
import org.openforis.collectone.v2.server.messaging.MessageLogRecorder
import org.openforis.collectone.v2.server.messaging.NoopSurveyTopicNotifier
import org.openforis.collectone.v2.server.messaging.SurveyTopicNotifier
import org.openforis.collectone.v2.server.service.AgStackEntityRegistrar
import org.openforis.collectone.v2.server.service.EntityEnricher
import org.openforis.collectone.v2.server.service.GeoIdRegistrar
import org.openforis.collectone.v2.server.service.NoopEntityEnricher
import org.openforis.collectone.v2.server.service.NoopGeoIdRegistrar
import org.openforis.collectone.v2.server.service.UserDataBackend
import org.openforis.collectone.v2.server.service.WhispEntityEnricher
import org.slf4j.LoggerFactory

/** Classpath location of the sample seed generated from the app's test fixtures. */
const val SAMPLE_SEED_RESOURCE = "/seed/user_data_seed.json"

/** How long a trashed survey stays restorable before the daily purge job removes its data. */
const val SURVEY_TRASH_RETENTION_DAYS = 30L

private val logger = LoggerFactory.getLogger("org.openforis.collectone.v2.server.userdata.Main")

/**
 * Starts the Collect One backend server.
 *
 * Environment variables (with fallback to `local.properties` for email settings):
 * - `PORT`: port to listen on (default 8080; set by Cloud Run).
 * - `SPANNER_EMULATOR_HOST`, `SPANNER_PROJECT_ID`, `SPANNER_INSTANCE_ID`, `SPANNER_DATABASE_ID`
 *   (required: `SPANNER_EMULATOR_HOST` or `SPANNER_INSTANCE_ID`): the Cloud Spanner database that
 *   stores the data ([SpannerSettings.fromEnv]). The service applies the schema at startup and
 *   loads the seed into an empty database; on a loaded one, it sets the seed's survey areas on
 *   stored sample surveys that have none ([UserDataBackend.backfillSeedSurveyAreas]). With the
 *   debug UI, the Spanner debug web UI at `/debug/spanner` browses it and records its operations.
 * - `SEED_AREA_BACKFILL_ENABLED`: `false` skips the startup survey-area backfill (default `true`).
 *   Staged revisions set it so they don't write to the shared live database before release.
 * - `USER_DATA_SEED`: path to a `UserDataSeed` in proto JSON to load into an empty database (and on
 *   `POST /debug/reset`) instead of the bundled sample seed.
 * - `AUTH_MODE`: `firebase` requires a Firebase Authentication ID token for project
 *   `FIREBASE_PROJECT_ID` (default `collect-one-dev`); `dev` (default) verifies the token when a
 *   call sends one (signed-in users are administrators there), and otherwise trusts `x-dev-user-*`
 *   headers or serves the call as the sample user.
 * - `ADMIN_EMAILS`: comma-separated verified emails of administrators in `firebase` mode.
 * - `DEBUG_UI_ENABLED`: when `true` (default in `dev` mode only), serves the unauthenticated debug
 *   web UIs (`/`, `/debug`, `/debug/rpcs`, `/debug/messages`, `/debug/spanner`) and records RPCs
 *   and Spanner operations for them. Never enable it on a service that stores real data.
 * - `DEBUG_RESET_ENABLED`: when `true`, serves the unauthenticated `POST /debug/reset`, which
 *   replaces all data with the seed ([debugResetEnabled]). Defaults to `true` only for local `dev`
 *   runs: `false` in `firebase` mode and on Cloud Run (where `K_SERVICE` is set).
 * - `PASSLIST` (or `--passlist=<list>` CLI argument): comma-separated passlist of emails and domain
 *   patterns (`*@domain`, `*.domain`, or exact email) allowed to sign in.
 * - `FCM_ENABLED`: when `true` (default in `firebase` mode when Application Default Credentials are
 *   available), publishes `survey_<id>_def` and `survey_<id>_entities` topic tickles to FCM for
 *   project `FIREBASE_PROJECT_ID`.
 * - `EMAIL_MODE`: `brevo` to send transactional invitation emails via the Brevo HTTP API, or `log`
 *   to record emails in-memory only (`/debug/messages`). Defaults to `brevo` when `BREVO_API_KEY`
 *   is configured, and `log` otherwise.
 * - `BREVO_API_KEY`: Brevo transactional email HTTP API key (`xkeysib-...`).
 * - `EMAIL_FROM`: sender email address (default `noreply@example.org`).
 * - `EMAIL_FROM_NAME`: sender display name (default `Collect One`).
 * - `PUBLIC_WEB_URL`: base URL of the Collect One web console used in invitation deep links
 *   (default `https://collectone.example.org`).
 * - `WHISP_API_KEY`: Open Foris Whisp API key (`X-API-KEY`) for asynchronous geospatial entity
 *   enrichment (`WHISP_API_URL` and `WHISP_PREFIX` override the default endpoint and `whisp_`
 *   property prefix).
 * - `RPC_PARALLELISM`: how many RPC handlers run at once in this instance ([rpcParallelism]).
 *
 * With `DEBUG_RESET_ENABLED`, `POST /debug/reset` restores the initial data (see
 * [UserDataServer.create]).
 */
fun main(args: Array<String>) {
  val config = ServerConfig.from(System::getenv, loadLocalProperties())
  val seed = loadSeed(config.seedPath)
  val settings =
    requireNotNull(SpannerSettings.fromEnv()) {
      "The Collect One server stores its data in Cloud Spanner: set SPANNER_EMULATOR_HOST (e.g. " +
        "localhost:9010, after starting scripts/spanner-emulator.sh) or SPANNER_INSTANCE_ID"
    }
  val topicNotifier = topicNotifier(config)
  val messageLogRecorder = MessageLogRecorder()
  val inviteNotifier: InviteNotifier =
    EmailInviteNotifier(
      emailSender = emailSender(config.email),
      messageLogRecorder = messageLogRecorder,
      publicWebUrl = config.email.publicWebUrl,
      fromEmail = config.email.from,
      fromName = config.email.fromName,
    )
  val recorder = if (config.debugUiEnabled) SpannerOperationRecorder() else null
  val connection =
    SpannerConnection.open(settings, recorder) { admin -> SpannerSchema.apply(admin, settings) }
  val spannerDebug = recorder?.let { SpannerDebug(connection, it) }
  val store = runBlocking {
    spannerStore(connection, seed, syncSeedMutations = config.seedAreaBackfillEnabled)
  }
  val geoIdRegistrar = geoIdRegistrar(store, config.agStack, topicNotifier)
  val earthEngineTileClient = EarthEngineTileClient.fromEnvironment()
  val entityEnricher = entityEnricher(store, config.whisp, topicNotifier)
  val backend =
    UserDataBackend(
      store,
      topicNotifier = topicNotifier,
      inviteNotifier = inviteNotifier,
      earthEngineTileClient = earthEngineTileClient,
      imageryAnalytics =
        ImageryAnalytics(EarthEngineValueClient.fromEnvironment(), earthEngineTileClient),
      entityEnricher = entityEnricher,
      geoIdRegistrar = geoIdRegistrar,
      defaultMobileClientVersionPolicy = config.mobileClientVersion,
    )
  runBlocking { backend.refreshMobileClientVersionPolicy() }
  // Sample surveys loaded before the seed gave them survey areas get them now; surveys with an
  // area are left alone, so this is a no-op on later starts. Staged revisions share the live
  // database, so they set SEED_AREA_BACKFILL_ENABLED=false to leave it untouched until release.
  if (config.seedAreaBackfillEnabled) {
    runBlocking { backend.backfillSeedSurveyAreas(seed) }
      .takeIf { it.isNotEmpty() }
      ?.let { logger.info("Backfilled the survey area of sample surveys {}", it) }
  }
  val purgeScheduler = scheduleTrashPurge(backend)
  val server =
    UserDataServer.create(
      backend,
      config.port,
      debugUiEnabled = config.debugUiEnabled,
      messageLogRecorder = messageLogRecorder,
      spannerDebug = spannerDebug,
      authenticator = authenticator(config, parsePasslistArg(args)),
      dataResetEnabled = config.debugResetEnabled,
      rpcParallelism = config.rpcParallelism,
      publicWebUrl = config.email.publicWebUrl,
    )
  Runtime.getRuntime()
    .addShutdownHook(
      Thread {
        purgeScheduler.shutdownNow()
        geoIdRegistrar.close()
        topicNotifier.close()
        inviteNotifier.close()
        entityEnricher.close()
        server.stop().join()
        connection.close()
      }
    )
  server.start().join()
  logger.info(
    "Collect One server listening on port {} ({} surveys, {} organizations)",
    server.activeLocalPort(),
    seed.surveys.size,
    seed.organizations.size,
  )
  logger.info("Spanner: {}", settings.description)
  logger.info("RPC parallelism: {}", config.rpcParallelism)
  logger.info(
    "Auth: {}; debug UI: {}; debug reset: {}",
    config.authMode,
    if (config.debugUiEnabled) "enabled" else "disabled",
    if (config.debugResetEnabled) "enabled" else "disabled",
  )
  logger.info(
    "Email: {} (from: {} <{}>, web: {})",
    config.email.mode,
    config.email.fromName,
    config.email.from,
    config.email.publicWebUrl,
  )
  logger.info(
    "Whisp enrichment: {}",
    if (config.whisp.enabled) "enabled (${config.whisp.apiUrl}, prefix=${config.whisp.prefix})"
    else "disabled (WHISP_API_KEY unset)",
  )
  server.blockUntilShutdown()
}

private fun entityEnricher(
  store: UserDataStore,
  whisp: WhispConfig,
  topicNotifier: SurveyTopicNotifier,
): EntityEnricher =
  if (whisp.enabled) {
    WhispEntityEnricher(
      store = store,
      whispClient = HttpWhispClient(apiKey = whisp.apiKey, endpoint = URI.create(whisp.apiUrl)),
      topicNotifier = topicNotifier,
      prefix = whisp.prefix,
    )
  } else {
    NoopEntityEnricher
  }

/**
 * FCM topic tickles when [ServerConfig.fcmEnabled], or none. Without Application Default
 * Credentials the FCM sender can't be created, and the server runs without tickles (clients still
 * sync on their own schedule).
 */
private fun topicNotifier(config: ServerConfig): SurveyTopicNotifier =
  if (config.fcmEnabled) {
    runCatching { DebouncedSurveyTopicNotifier(FcmHttpV1TopicSender(config.firebaseProjectId)) }
      .getOrElse { NoopSurveyTopicNotifier }
  } else {
    NoopSurveyTopicNotifier
  }

/**
 * [GeoIdRegistrar] calling the external AgStack Asset Registry when [AgStackConfig.registryUrl] is
 * configured, or [NoopGeoIdRegistrar] when unset.
 */
private fun geoIdRegistrar(
  store: UserDataStore,
  agStack: AgStackConfig,
  topicNotifier: SurveyTopicNotifier,
): GeoIdRegistrar =
  if (agStack.registryUrl.isBlank()) {
    NoopGeoIdRegistrar
  } else {
    AgStackEntityRegistrar(
      store = store,
      registryClient =
        HttpAgStackRegistryClient(
          baseUrl = URI.create(agStack.registryUrl),
          apiKey = agStack.apiKey,
          authToken = agStack.authToken,
        ),
      topicNotifier = topicNotifier,
    )
  }

/** The sender of [email]'s mode; `brevo` without an API key falls back to logging the emails. */
private fun emailSender(email: EmailConfig): EmailSender =
  when (email.mode) {
    "brevo" -> {
      if (email.brevoApiKey.isBlank()) {
        logger.warn(
          "EMAIL_MODE=brevo but BREVO_API_KEY is unset; falling back to LoggingEmailSender"
        )
        LoggingEmailSender
      } else {
        BrevoHttpEmailSender(
          apiKey = email.brevoApiKey,
          defaultFromEmail = email.from,
          defaultFromName = email.fromName,
        )
      }
    }
    "log" -> LoggingEmailSender
    else -> error("Unknown EMAIL_MODE \"${email.mode}\" (expected brevo or log)")
  }

private fun authenticator(config: ServerConfig, passlist: EmailPasslist): Authenticator =
  when (config.authMode) {
    "dev" ->
      Authenticator.dev(
        firebase = Authenticator.firebase(config.firebaseProjectId, passlist = passlist),
        passlist = passlist,
      )
    "firebase" ->
      Authenticator.firebase(
        config.firebaseProjectId,
        adminEmails = config.adminEmails,
        passlist = passlist,
      )
    else -> error("Unknown AUTH_MODE \"${config.authMode}\" (expected dev or firebase)")
  }

/**
 * Purges surveys trashed more than [SURVEY_TRASH_RETENTION_DAYS] ago, an hour after startup and
 * then daily. The cutoff comes from the store's clock (its watermark), not this machine's, so it
 * agrees with the trash times the store stamped. A failed run is retried the next day.
 */
private fun scheduleTrashPurge(backend: UserDataBackend): ScheduledExecutorService =
  Executors.newSingleThreadScheduledExecutor { r ->
      Thread(r, "survey-trash-purge").apply { isDaemon = true }
    }
    .apply {
      scheduleAtFixedRate(
        {
          runCatching {
            runBlocking {
              val cutoff =
                Instant.ofEpochSecond(
                  backend.currentWatermark().epochSecond -
                    TimeUnit.DAYS.toSeconds(SURVEY_TRASH_RETENTION_DAYS),
                  0,
                )
              backend.purgeTrashedSurveys(cutoff)
            }
          }
        },
        1,
        24,
        TimeUnit.HOURS,
      )
    }

private fun loadLocalProperties(): Properties =
  Properties().apply {
    listOf(File("../local.properties"), File("local.properties"))
      .filter { it.isFile }
      .forEach { file -> runCatching { file.inputStream().use(::load) } }
  }

/**
 * A store on the database in [connection] (whose schema [SpannerSchema.apply] already applied),
 * loading [seed] if the database was never loaded when [syncSeedMutations] is `true`. When
 * [syncSeedMutations] is `false` (staged `--no-traffic` revisions sharing a live database), no
 * startup Spanner writes run and missing seed surveys are served via
 * [StagedSeedOverlayUserDataStore].
 */
suspend fun spannerStore(
  connection: SpannerConnection,
  seed: UserDataSeed,
  syncSeedMutations: Boolean = true,
): UserDataStore {
  val store = SpannerUserDataStore(connection.databaseClient, seed)
  if (store.dataEpoch == SpannerUserDataStore.NO_EPOCH) {
    if (syncSeedMutations) {
      store.reset()
    } else {
      return InMemoryUserDataStore(seed)
    }
  } else if (syncSeedMutations) {
    store.syncGlobalLibraryFromSeed()
    store.syncMissingSurveysFromSeed()
  }
  return if (syncSeedMutations) {
    store
  } else {
    StagedSeedOverlayUserDataStore.create(primary = store, seed = seed)
  }
}

/** Loads the seed at [path], or the bundled sample seed when [path] is null or blank. */
fun loadSeed(path: String?): UserDataSeed {
  val json =
    if (!path.isNullOrBlank()) {
      File(path).readText()
    } else {
      UserDataBackend::class.java.getResourceAsStream(SAMPLE_SEED_RESOURCE)?.use {
        it.readBytes().decodeToString()
      } ?: return UserDataSeed()
    }
  return requireNotNull(userDataSeedJsonAdapter.fromJson(json)) { "Empty user data seed" }
}

/**
 * Whether to serve `POST /debug/reset`, which anyone who can reach the service may call to replace
 * all data with the seed: `DEBUG_RESET_ENABLED` from [env] when it's `true` or `false`, otherwise
 * `true` only for a local `dev` run.
 *
 * Cloud Run sets `K_SERVICE` on every service, so a deployed service never gets the endpoint by
 * default, even in `dev` mode: a demo deployment has to opt in, and a forgotten setting fails safe.
 * Local servers, tests, and end-to-end scripts that reset between runs keep it without setup.
 */
internal fun debugResetEnabled(env: (String) -> String?, authMode: String): Boolean =
  env("DEBUG_RESET_ENABLED")?.trim()?.lowercase()?.toBooleanStrictOrNull()
    ?: (authMode == "dev" && env("K_SERVICE").isNullOrBlank())

/**
 * How many RPC handlers may run at once in this instance: `RPC_PARALLELISM` from [env] when it's a
 * whole number from 1 to [MAX_RPC_PARALLELISM], otherwise [DEFAULT_RPC_PARALLELISM], so a bad value
 * falls back to the safe default rather than failing startup.
 */
internal fun rpcParallelism(env: (String) -> String?): Int =
  env("RPC_PARALLELISM")?.trim()?.toIntOrNull()?.takeIf { it in 1..MAX_RPC_PARALLELISM }
    ?: DEFAULT_RPC_PARALLELISM

/**
 * Extracts `--passlist=<list>` or `--passlist <list>` CLI arguments combined with `PASSLIST` from
 * the environment.
 */
internal fun parsePasslistArg(
  args: Array<String>,
  envPasslist: String? = System.getenv("PASSLIST"),
): EmailPasslist {
  val rawValues = mutableListOf<String>()
  if (!envPasslist.isNullOrBlank()) rawValues += envPasslist
  var i = 0
  while (i < args.size) {
    val arg = args[i]
    when {
      arg.startsWith("--passlist=") -> rawValues += arg.removePrefix("--passlist=")
      arg == "--passlist" && i + 1 < args.size -> {
        rawValues += args[i + 1]
        i++
      }
    }
    i++
  }
  return EmailPasslist.parse(rawValues.joinToString(","))
}

/**
 * Whether the startup survey-area backfill runs: on unless `SEED_AREA_BACKFILL_ENABLED` is `false`.
 */
internal fun isSeedAreaBackfillEnabled(env: (String) -> String?): Boolean =
  env("SEED_AREA_BACKFILL_ENABLED")?.trim()?.lowercase()?.toBooleanStrictOrNull() ?: true
