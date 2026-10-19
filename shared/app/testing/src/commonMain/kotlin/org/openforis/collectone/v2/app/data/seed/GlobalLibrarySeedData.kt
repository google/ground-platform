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
package org.openforis.collectone.v2.app.data.seed

import org.openforis.collectone.v2.app.domain.model.CodeListItem
import org.openforis.collectone.v2.app.domain.model.ConceptAggregation
import org.openforis.collectone.v2.app.domain.model.ConceptDataType
import org.openforis.collectone.v2.app.domain.model.ConceptLink
import org.openforis.collectone.v2.app.domain.model.ExportProfile
import org.openforis.collectone.v2.app.domain.model.FormTemplate
import org.openforis.collectone.v2.app.domain.model.ImpactPillar
import org.openforis.collectone.v2.app.domain.model.LibraryConcept
import org.openforis.collectone.v2.app.domain.model.LibraryStatus
import org.openforis.collectone.v2.app.domain.model.LocalizedText
import org.openforis.collectone.v2.app.domain.model.Organization
import org.openforis.collectone.v2.app.domain.model.OrganizationLibrary
import org.openforis.collectone.v2.app.domain.model.PrivacyClass
import org.openforis.collectone.v2.app.domain.model.PurposePack
import org.openforis.collectone.v2.app.domain.model.editor.EditorChoice
import org.openforis.collectone.v2.app.domain.model.editor.EditorForm
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestion
import org.openforis.collectone.v2.app.domain.model.editor.EditorQuestionType
import org.openforis.collectone.v2.app.domain.model.editor.GeometryCapture

/**
 * The global library (v0) that bootstraps the `"All users"` organization's library.
 *
 * The canonical copy is the seed files `shared/assets/library/<vocabulary>.textproto` (one
 * `groundplatform.v2.library.LibraryBundle` per vocabulary). Reading resources isn't practical on
 * every target (wasmJs loads them asynchronously), so this is a Kotlin copy;
 * `GlobalLibrarySeedTest` (jvmTest) parses the seed files and fails if they differ. Run it with
 * `-PregenerateLibrarySeed` to rewrite them from this file.
 *
 * Template questions carry their concept links, which the seed files store as `ground:concept` bind
 * attributes.
 */
object GlobalLibrarySeedData {
  private const val GLOBAL = Organization.ALL_USERS_ID

  /** Vocabularies in seed-file order. */
  val VOCABULARIES: List<String> = listOf("core", "eudr", "ferm", "pame", "iplc", "lulc", "timber")

  private const val EUDR_REGULATION = "https://eur-lex.europa.eu/eli/reg/2023/1115/oj"

  private const val GOAL_DEFORESTATION_FREE = "deforestation_free_supply_chains"
  private const val GOAL_RESTORATION = "ecosystem_restoration"
  private const val GOAL_PROTECTED_AREAS = "effective_protected_areas"
  private const val GOAL_IPLC_RIGHTS = "iplc_rights_and_tenure"
  private const val GOAL_LEGAL_TIMBER = "legal_timber"
  private const val GOAL_FOREST_CARBON = "forest_carbon_and_conservation"
  private const val GOAL_SMALLHOLDER_LIVELIHOODS = "smallholder_livelihoods"
  private const val GOAL_OPEN_EARTH_DATA = "open_earth_data"

  /** The whole global library, owned by `"All users"`. */
  fun library(): OrganizationLibrary {
    val bundles = VOCABULARIES.map(::vocabulary)
    return OrganizationLibrary(
      organizationId = GLOBAL,
      concepts = bundles.flatMap { it.concepts },
      formTemplates = bundles.flatMap { it.formTemplates },
      purposePacks = bundles.flatMap { it.purposePacks },
      exportProfiles = bundles.flatMap { it.exportProfiles },
    )
  }

  /** The entries of one seed file (`<vocabulary>.textproto`). */
  fun vocabulary(name: String): OrganizationLibrary =
    when (name) {
      "core" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = coreConcepts(),
          formTemplates = listOf(producerProfileTemplate()),
          purposePacks = listOf(producerRegistrationPack()),
        )
      "eudr" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = eudrConcepts(),
          formTemplates = listOf(eudrPlotRegistrationTemplate()),
          purposePacks = listOf(eudrDueDiligencePack()),
          exportProfiles = listOf(eudrGeoJsonProfile()),
        )
      "ferm" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = fermConcepts(),
          formTemplates = listOf(fermMonitoringWaveTemplate()),
          purposePacks = listOf(fermRestorationPack()),
        )
      "pame" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = pameConcepts(),
          formTemplates = listOf(protectedAreaPatrolTemplate()),
          purposePacks = listOf(protectedAreaManagementPack()),
        )
      "iplc" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = iplcConcepts(),
          formTemplates = listOf(communityLandMappingTemplate()),
          purposePacks = listOf(iplcLandMappingPack()),
        )
      "lulc" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = lulcConcepts(),
          formTemplates = listOf(lulcGroundTruthTemplate(), reddPlusForestAssessmentTemplate()),
          purposePacks = listOf(lulcTrainingDataPack(), reddPlusTfffPack()),
        )
      "timber" ->
        OrganizationLibrary(
          GLOBAL,
          concepts = timberConcepts(),
          formTemplates = listOf(timberHarvestFellingTemplate()),
          purposePacks = listOf(timberTraceabilityPack()),
        )
      else -> throw IllegalArgumentException("Unknown vocabulary $name")
    }

  // --- Helpers --------------------------------------------------------------------------------

  /** Text in English, French, Spanish, Vietnamese, and Swahili (blank translations are skipped). */
  private fun text(
    en: String,
    fr: String = "",
    es: String = "",
    vi: String = "",
    sw: String = "",
  ): LocalizedText = LocalizedText.of("en" to en, "fr" to fr, "es" to es, "vi" to vi, "sw" to sw)

  private fun keyword(language: String, word: String) = LocalizedText(mapOf(language to word))

  private fun keywords(vararg pairs: Pair<String, String>) = pairs.map {
    keyword(it.first, it.second)
  }

  private fun item(code: String, label: LocalizedText, vararg ids: Pair<String, String>) =
    CodeListItem(code, label, ids.toMap())

  private fun yesNo(): List<CodeListItem> =
    listOf(
      item("yes", text("Yes", "Oui", "Sí", "Có", "Ndiyo")),
      item("no", text("No", "Non", "No", "Không", "Hapana")),
    )

  private fun concept(
    id: String,
    label: LocalizedText,
    dataType: ConceptDataType,
    description: LocalizedText,
    aggregation: ConceptAggregation,
    privacyClass: PrivacyClass,
    unit: String = "",
    keywords: List<LocalizedText> = emptyList(),
    codeList: List<CodeListItem> = emptyList(),
    goals: List<String> = emptyList(),
    pillar: ImpactPillar? = null,
    references: List<String> = emptyList(),
  ) =
    LibraryConcept(
      id = id,
      organizationId = GLOBAL,
      label = label,
      dataType = dataType,
      description = description,
      keywords = keywords,
      unit = unit,
      codeList = codeList,
      aggregation = aggregation,
      privacyClass = privacyClass,
      goals = goals,
      pillar = pillar,
      references = references,
      status = LibraryStatus.STABLE,
    )

  /** A template question named [name] (also its key) that copies [concept]'s choices, if any. */
  private fun question(
    concept: LibraryConcept,
    name: String,
    type: EditorQuestionType,
    required: Boolean = false,
    hint: String = "",
    capture: GeometryCapture = GeometryCapture.GPS_ONLY,
  ) =
    EditorQuestion(
      key = name,
      name = name,
      type = type,
      label = concept.label.text,
      hint = hint,
      required = required,
      choices =
        concept.codeList.map {
          EditorChoice(value = it.code, label = it.label.text, code = it.code)
        },
      decimalPlaces =
        if (concept.dataType == ConceptDataType.DECIMAL) EditorQuestion.DEFAULT_DECIMAL_PLACES
        else 0,
      capture = capture,
    )

  private fun template(
    id: String,
    title: LocalizedText,
    description: LocalizedText,
    questions: List<Pair<EditorQuestion, LibraryConcept>>,
  ) =
    FormTemplate(
      id = id,
      organizationId = GLOBAL,
      title = title,
      description = description,
      form =
        EditorForm(
          formId = id,
          title = title.text,
          questions = questions.map { (q, c) -> q.copy(conceptLink = ConceptLink.to(c)) },
        ),
      status = LibraryStatus.STABLE,
    )

  private fun List<LibraryConcept>.byId(id: String): LibraryConcept = first { it.id == id }

  // --- core -----------------------------------------------------------------------------------

  private fun coreConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "core.area_ha",
        text(
          "Plot / parcel area (ha)",
          "Superficie (ha)",
          "Superficie (ha)",
          "Diện tích (ha)",
          "Eneo (ha)",
        ),
        ConceptDataType.DECIMAL,
        text("Area of the plot or site in hectares."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "har",
        keywords =
          keywords(
            "en" to "area",
            "en" to "size",
            "en" to "hectares",
            "fr" to "surface",
            "es" to "área",
            "es" to "hectáreas",
            "vi" to "héc ta",
            "sw" to "ukubwa",
          ),
      ),
      concept(
        "core.geoid",
        text("GeoID", "GeoID", "GeoID", "GeoID", "GeoID"),
        ConceptDataType.TEXT,
        text("Unique identifier of a field boundary in a public geospatial asset registry."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "field id", "en" to "plot id", "en" to "asset registry"),
      ),
      concept(
        "core.producer_id",
        text(
          "Producer ID",
          "Identifiant du producteur",
          "ID del productor",
          "Mã người sản xuất",
          "Kitambulisho cha mzalishaji",
        ),
        ConceptDataType.TEXT,
        text("Identifier of the farmer, producer, or supplier, unique within the organization."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        keywords =
          keywords("en" to "farmer id", "en" to "member number", "es" to "código del productor"),
      ),
      concept(
        "core.producer_name",
        text(
          "Producer name",
          "Nom du producteur",
          "Nombre del productor",
          "Tên người sản xuất",
          "Jina la mzalishaji",
        ),
        ConceptDataType.TEXT,
        text("Full name of the farmer, producer, or supplier."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        keywords =
          keywords(
            "en" to "farmer",
            "en" to "grower",
            "fr" to "agriculteur",
            "es" to "agricultor",
            "vi" to "nông dân",
            "sw" to "mkulima",
          ),
      ),
      concept(
        "core.country",
        text("Country", "Pays", "País", "Quốc gia", "Nchi"),
        ConceptDataType.TEXT,
        text("Country as an ISO 3166-1 alpha-2 code (for example KE)."),
        ConceptAggregation.COUNT_DISTINCT_FEATURES,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "nation", "fr" to "nation", "es" to "nación"),
      ),
      concept(
        "core.admin_area",
        text(
          "Administrative area",
          "Division administrative",
          "Área administrativa",
          "Đơn vị hành chính",
          "Eneo la utawala",
        ),
        ConceptDataType.TEXT,
        text("Province, county, district, or other administrative area."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords =
          keywords(
            "en" to "province",
            "en" to "county",
            "en" to "district",
            "fr" to "région",
            "es" to "provincia",
            "es" to "municipio",
            "vi" to "tỉnh",
            "vi" to "huyện",
            "sw" to "kaunti",
            "sw" to "wilaya",
          ),
      ),
      concept(
        "core.producer_gender",
        text(
          "Producer gender",
          "Genre du producteur",
          "Género del productor",
          "Giới tính người sản xuất",
          "Jinsia ya mzalishaji",
        ),
        ConceptDataType.SELECT_ONE,
        text("Gender of the producer, as they describe it."),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "sex", "fr" to "sexe", "es" to "sexo"),
        codeList =
          listOf(
            item("female", text("Female", "Femme", "Mujer", "Nữ", "Mwanamke")),
            item("male", text("Male", "Homme", "Hombre", "Nam", "Mwanamume")),
            item("other", text("Other", "Autre", "Otro", "Khác", "Nyingine")),
            item(
              "prefer_not_to_say",
              text(
                "Prefer not to say",
                "Préfère ne pas répondre",
                "Prefiere no responder",
                "Không muốn trả lời",
                "Hapendi kujibu",
              ),
            ),
          ),
      ),
      concept(
        "core.household_size",
        text(
          "Household size",
          "Taille du ménage",
          "Tamaño del hogar",
          "Số người trong hộ",
          "Idadi ya watu wa kaya",
        ),
        ConceptDataType.INTEGER,
        text("Number of people living in the producer's household."),
        ConceptAggregation.MEAN,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "family size", "es" to "familia", "fr" to "famille"),
      ),
      concept(
        "core.collection_date",
        text(
          "Collection date",
          "Date de collecte",
          "Fecha de recolección",
          "Ngày thu thập",
          "Tarehe ya ukusanyaji",
        ),
        ConceptDataType.DATE,
        text("Date the data was collected in the field."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "visit date", "en" to "survey date"),
      ),
      concept(
        "core.site_photo",
        text("Site photo", "Photo du site", "Foto del sitio", "Ảnh hiện trường", "Picha ya eneo"),
        ConceptDataType.MEDIA,
        text("Photo of the plot or site."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "picture", "en" to "image", "es" to "fotografía"),
      ),
      concept(
        "core.data_consent",
        text(
          "Consent to data collection",
          "Consentement à la collecte de données",
          "Consentimiento para la recolección de datos",
          "Đồng ý thu thập dữ liệu",
          "Ridhaa ya ukusanyaji wa data",
        ),
        ConceptDataType.SELECT_ONE,
        text("Whether the person agreed to have their data collected and stored."),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "permission", "en" to "agreement", "es" to "permiso"),
        codeList = yesNo(),
      ),
      concept(
        "core.cooperative_name",
        text(
          "Cooperative or producer group",
          "Coopérative ou groupement de producteurs",
          "Cooperativa o grupo de productores",
          "Hợp tác xã hoặc nhóm nông hộ",
          "Chama cha ushirika au kikundi cha wazalishaji",
        ),
        ConceptDataType.TEXT,
        text("Name of the cooperative, farmer society, or producer organization."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords =
          keywords(
            "en" to "cooperative",
            "en" to "farmer group",
            "en" to "association",
            "fr" to "coopérative",
            "es" to "cooperativa",
          ),
        goals = listOf(GOAL_SMALLHOLDER_LIVELIHOODS),
        pillar = ImpactPillar.ADAPTATION,
      ),
      concept(
        "core.certification_status",
        text(
          "Certification status",
          "Statut de certification",
          "Estado de certificación",
          "Tình trạng chứng nhận",
          "Hali ya uthibitisho",
        ),
        ConceptDataType.SELECT_ONE,
        text("Whether the producer or plot is certified under a sustainability standard."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "certified", "en" to "audit status", "es" to "certificación"),
        codeList =
          listOf(
            item(
              "certified",
              text("Certified", "Certifié", "Certificado", "Đã chứng nhận", "Imethibitishwa"),
            ),
            item(
              "in_transition",
              text(
                "In transition",
                "En conversion",
                "En transición",
                "Đang chuyển đổi",
                "Katika mpito",
              ),
            ),
            item(
              "conventional",
              text(
                "Conventional / uncertified",
                "Conventionnel / non certifié",
                "Convencional / no certificado",
                "Thông thường / chưa chứng nhận",
                "Kawaida / haijathibitishwa",
              ),
            ),
          ),
        goals = listOf(GOAL_SMALLHOLDER_LIVELIHOODS),
        pillar = ImpactPillar.ADAPTATION,
      ),
      concept(
        "core.annual_yield_kg",
        text(
          "Estimated annual yield (kg)",
          "Rendement annuel estimé (kg)",
          "Rendimiento anual estimado (kg)",
          "Sản lượng hàng năm ước tính (kg)",
          "Mavuno ya mwaka yanayokadiriwa (kg)",
        ),
        ConceptDataType.DECIMAL,
        text("Expected or harvested annual production volume from the farm or plot, in kilograms."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "kg",
        keywords =
          keywords("en" to "harvest volume", "en" to "production", "es" to "producción anual"),
        goals = listOf(GOAL_SMALLHOLDER_LIVELIHOODS),
        pillar = ImpactPillar.ADAPTATION,
      ),
    )

  private fun producerProfileTemplate(): FormTemplate {
    val c = coreConcepts()
    return template(
      id = "producer_profile",
      title =
        text(
          "Producer & farm registration",
          "Enregistrement du producteur et de l'exploitation",
          "Registro de productor y finca",
          "Đăng ký nông hộ và trang trại",
          "Usajili wa mzalishaji na shamba",
        ),
      description =
        text(
          "Registers a smallholder farmer with consent, cooperative membership, household details, certification status, and annual production."
        ),
      questions =
        listOf(
          question(
            c.byId("core.data_consent"),
            "data_consent",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to c.byId("core.data_consent"),
          question(c.byId("core.producer_name"), "producer_name", EditorQuestionType.TEXT, true) to
            c.byId("core.producer_name"),
          question(
            c.byId("core.producer_id"),
            "producer_id",
            EditorQuestionType.TEXT,
            required = true,
            hint = "Cooperative member number or producer ID.",
          ) to c.byId("core.producer_id"),
          question(
            c.byId("core.cooperative_name"),
            "cooperative_name",
            EditorQuestionType.TEXT,
            hint = "Cooperative, farmer society, or producer group.",
          ) to c.byId("core.cooperative_name"),
          question(
            c.byId("core.producer_gender"),
            "producer_gender",
            EditorQuestionType.SELECT_ONE,
          ) to c.byId("core.producer_gender"),
          question(c.byId("core.household_size"), "household_size", EditorQuestionType.NUMBER) to
            c.byId("core.household_size"),
          question(c.byId("core.country"), "country", EditorQuestionType.TEXT) to
            c.byId("core.country"),
          question(c.byId("core.admin_area"), "admin_area", EditorQuestionType.TEXT) to
            c.byId("core.admin_area"),
          question(
            c.byId("core.certification_status"),
            "certification_status",
            EditorQuestionType.SELECT_ONE,
          ) to c.byId("core.certification_status"),
          question(
            c.byId("core.annual_yield_kg"),
            "annual_yield_kg",
            EditorQuestionType.NUMBER,
            hint = "Expected annual harvest in kilograms.",
          ) to c.byId("core.annual_yield_kg"),
        ),
    )
  }

  private fun producerRegistrationPack() =
    PurposePack(
      id = "producer_registration",
      organizationId = GLOBAL,
      title =
        text(
          "Producer registration & market access",
          "Enregistrement des producteurs et accès aux marchés",
          "Registro de productores y acceso a mercados",
          "Đăng ký nông hộ và tiếp cận thị trường",
          "Usajili wa wazalishaji na upatikanaji wa masoko",
        ),
      description =
        text(
          "Register smallholder farmers, cooperatives, household demographics, and harvest volumes to support cooperative services and certified market access."
        ),
      icon = "person_add",
      formTemplateIds = listOf("producer_profile"),
      programIds = listOf("rainforest_alliance", "fairtrade", "agstack", "organic"),
      goals = listOf(GOAL_SMALLHOLDER_LIVELIHOODS),
      pillar = ImpactPillar.ADAPTATION,
      status = LibraryStatus.STABLE,
    )

  // --- eudr -----------------------------------------------------------------------------------

  private fun eudrConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "eudr.commodity",
        text("Commodity", "Produit de base", "Producto básico", "Mặt hàng", "Bidhaa"),
        ConceptDataType.SELECT_ONE,
        text("Relevant agricultural or forest risk commodity produced on the plot."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords =
          keywords(
            "en" to "crop",
            "en" to "product",
            "fr" to "culture",
            "fr" to "produit",
            "es" to "cultivo",
            "es" to "producto",
            "vi" to "cây trồng",
            "vi" to "nông sản",
            "sw" to "zao",
          ),
        codeList =
          listOf(
            item(
              "cattle",
              text("Cattle", "Bovins", "Ganado bovino", "Gia súc", "Ng'ombe"),
              "hs" to "0102",
            ),
            item("cocoa", text("Cocoa", "Cacao", "Cacao", "Ca cao", "Kakao"), "hs" to "1801"),
            item("coffee", text("Coffee", "Café", "Café", "Cà phê", "Kahawa"), "hs" to "0901"),
            item(
              "oil_palm",
              text("Oil palm", "Palmier à huile", "Palma aceitera", "Cọ dầu", "Mchikichi"),
            ),
            item(
              "rubber",
              text("Rubber", "Caoutchouc", "Caucho", "Cao su", "Mpira"),
              "hs" to "4001",
            ),
            item("soya", text("Soya", "Soja", "Soja", "Đậu tương", "Soya"), "hs" to "1201"),
            item("wood", text("Wood", "Bois", "Madera", "Gỗ", "Mbao"), "hs" to "44"),
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.production_place",
        text(
          "Production place",
          "Lieu de production",
          "Lugar de producción",
          "Nơi sản xuất",
          "Mahali pa uzalishaji",
        ),
        ConceptDataType.POLYGON,
        text("Boundary of the plot of land where the commodity was produced."),
        ConceptAggregation.COUNT_DISTINCT_FEATURES,
        PrivacyClass.ORG_ONLY,
        keywords =
          keywords(
            "en" to "plot",
            "en" to "parcel",
            "en" to "farm",
            "en" to "geolocation",
            "fr" to "parcelle",
            "es" to "parcela",
            "es" to "finca",
            "vi" to "lô đất",
            "vi" to "thửa đất",
            "sw" to "shamba",
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.harvest_date_range",
        text(
          "Harvest date range",
          "Période de récolte",
          "Período de cosecha",
          "Thời gian thu hoạch",
          "Kipindi cha mavuno",
        ),
        ConceptDataType.TEXT,
        text("Date or time range of production, as an ISO 8601 interval (2026-01-01/2026-03-31)."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "harvest", "en" to "production date", "es" to "cosecha"),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.plot_over_4ha",
        text(
          "Plot larger than 4 ha",
          "Parcelle de plus de 4 ha",
          "Parcela de más de 4 ha",
          "Lô đất lớn hơn 4 ha",
          "Shamba kubwa kuliko ha 4",
        ),
        ConceptDataType.SELECT_ONE,
        text("Whether the plot is larger than 4 hectares, so its boundary must be a polygon."),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList = yesNo(),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.quantity_kg",
        text("Quantity (kg)", "Quantité (kg)", "Cantidad (kg)", "Khối lượng (kg)", "Kiasi (kg)"),
        ConceptDataType.DECIMAL,
        text("Net mass of the commodity delivered, in kilograms."),
        ConceptAggregation.SUM,
        PrivacyClass.ORG_ONLY,
        unit = "kg",
        keywords = keywords("en" to "weight", "en" to "volume", "es" to "peso", "fr" to "poids"),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.deforestation_free",
        text(
          "Deforestation-free",
          "Zéro déforestation",
          "Libre de deforestación",
          "Không gây mất rừng",
          "Bila ukataji wa misitu",
        ),
        ConceptDataType.SELECT_ONE,
        text("Whether the plot has been free of deforestation since 31 December 2020."),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          yesNo() +
            item("unknown", text("Unknown", "Inconnu", "Desconocido", "Không rõ", "Haijulikani")),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.legal_land_use",
        text(
          "Legal land-use status",
          "Statut légal d'utilisation des terres",
          "Estado legal de uso de la tierra",
          "Tình trạng sử dụng đất hợp pháp",
          "Hali halali ya matumizi ya ardhi",
        ),
        ConceptDataType.SELECT_ONE,
        text(
          "Evidence that the plot is cultivated in accordance with relevant local land-use and tenure legislation."
        ),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "legality", "en" to "land title", "es" to "legalidad"),
        codeList =
          listOf(
            item(
              "verified_legal",
              text(
                "Verified legal title or use right",
                "Titre ou droit d'usage légal vérifié",
                "Título o derecho de uso legal verificado",
                "Có giấy chứng nhận quyền sử dụng đất hợp pháp",
                "Hati au haki halali iliyothibitishwa",
              ),
            ),
            item(
              "customary_recognized",
              text(
                "Recognized customary or cooperative right",
                "Droit coutumier ou coopératif reconnu",
                "Derecho consuetudinario o cooperativo reconocido",
                "Quyền canh tác truyền thống hoặc hợp tác xã được công nhận",
                "Haki ya kimila au ya ushirika inayotambuliwa",
              ),
            ),
            item(
              "pending_verification",
              text(
                "Pending verification",
                "Vérification en attente",
                "Verificación pendiente",
                "Đang chờ xác minh",
                "Inasubiri uthibitisho",
              ),
            ),
            item(
              "non_compliant",
              text(
                "No legal land-use evidence",
                "Aucune preuve de légalité",
                "Sin evidencia legal de uso",
                "Không có bằng chứng sử dụng đất hợp pháp",
                "Hakuna ushahidi wa uhalali",
              ),
            ),
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.scientific_name",
        text(
          "Scientific species name",
          "Nom scientifique de l'espèce",
          "Nombre científico de la especie",
          "Tên khoa học của loài",
          "Jina la kisayansi la spishi",
        ),
        ConceptDataType.TEXT,
        text(
          "Botanical or scientific name of the crop or tree species produced on the plot (required for wood products)."
        ),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords =
          keywords("en" to "botanical name", "en" to "species", "es" to "nombre científico"),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.supplier_name",
        text(
          "Supplier name",
          "Nom du fournisseur",
          "Nombre del proveedor",
          "Tên nhà cung cấp",
          "Jina la msambazaji",
        ),
        ConceptDataType.TEXT,
        text("Name of the business or person that supplied the commodity."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "vendor", "en" to "trader", "es" to "proveedor"),
        references = listOf(EUDR_REGULATION),
      ),
      concept(
        "eudr.plot_area_ha",
        text(
          "Production plot area (ha)",
          "Surface de la parcelle de production (ha)",
          "Área de la parcela de producción (ha)",
          "Diện tích lô đất sản xuất (ha)",
          "Eneo la shamba la uzalishaji (ha)",
        ),
        ConceptDataType.DECIMAL,
        text("Area of the agricultural or forest commodity production plot in hectares."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "har",
        keywords =
          keywords(
            "en" to "plot area",
            "en" to "hectares",
            "es" to "hectáreas",
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
        references = listOf(EUDR_REGULATION),
      ),
    )

  private fun eudrPlotRegistrationTemplate(): FormTemplate {
    val e = eudrConcepts()
    val c = coreConcepts()
    return template(
      id = "eudr_plot_registration",
      title =
        text(
          "Deforestation compliance plot check",
          "Vérification de parcelle zéro déforestation",
          "Verificación de parcela libre de deforestación",
          "Kiểm tra lô đất tuân thủ không gây mất rừng",
          "Ukaguzi wa shamba la uzingatiaji wa kutokata misitu",
        ),
      description =
        text(
          "Records plot geolocation, commodity, producer ID, harvest period, deforestation-free status after 31 December 2020, and legal land-use evidence."
        ),
      questions =
        listOf(
          question(e.byId("eudr.commodity"), "commodity", EditorQuestionType.SELECT_ONE, true) to
            e.byId("eudr.commodity"),
          question(
            e.byId("eudr.production_place"),
            "production_place",
            EditorQuestionType.POLYGON,
            required = true,
            hint = "Walk or draw the plot boundary (polygons required for plots over 4 ha).",
            capture = GeometryCapture.GPS_OR_MAP,
          ) to e.byId("eudr.production_place"),
          question(c.byId("core.area_ha"), "area_ha", EditorQuestionType.NUMBER) to
            c.byId("core.area_ha"),
          question(e.byId("eudr.plot_over_4ha"), "plot_over_4ha", EditorQuestionType.SELECT_ONE) to
            e.byId("eudr.plot_over_4ha"),
          question(c.byId("core.producer_id"), "producer_id", EditorQuestionType.TEXT, true) to
            c.byId("core.producer_id"),
          question(
            e.byId("eudr.harvest_date_range"),
            "harvest_date_range",
            EditorQuestionType.TEXT,
            hint = "For example 2026-01-01/2026-03-31.",
          ) to e.byId("eudr.harvest_date_range"),
          question(
            e.byId("eudr.deforestation_free"),
            "deforestation_free",
            EditorQuestionType.SELECT_ONE,
            required = true,
          ) to e.byId("eudr.deforestation_free"),
          question(
            e.byId("eudr.legal_land_use"),
            "legal_land_use",
            EditorQuestionType.SELECT_ONE,
          ) to e.byId("eudr.legal_land_use"),
        ),
    )
  }

  private fun eudrDueDiligencePack() =
    PurposePack(
      id = "eudr_due_diligence",
      organizationId = GLOBAL,
      title =
        text(
          "Deforestation compliance",
          "Conformité zéro déforestation",
          "Cumplimiento de cero deforestación",
          "Tuân thủ không gây mất rừng",
          "Uzingatiaji wa kutokata misitu",
        ),
      description =
        text(
          "Map production plots, verify zero-deforestation status after the cutoff date, and record legality and supply-chain traceability data."
        ),
      icon = "verified",
      formTemplateIds = listOf("eudr_plot_registration"),
      exportProfileIds = listOf("eudr_geojson"),
      programIds = listOf("eudr", "uk_frc", "rainforest_alliance", "fairtrade"),
      goals = listOf(GOAL_DEFORESTATION_FREE),
      pillar = ImpactPillar.MITIGATION,
      status = LibraryStatus.STABLE,
    )

  private fun eudrGeoJsonProfile() =
    ExportProfile(
      id = "eudr_geojson",
      organizationId = GLOBAL,
      title = text("EUDR GeoJSON"),
      format = "geojson",
      fieldConcepts =
        linkedMapOf(
          "production_place" to "eudr.production_place",
          "commodity" to "eudr.commodity",
          "area_ha" to "core.area_ha",
          "producer_name" to "core.producer_name",
          "country" to "core.country",
        ),
      status = LibraryStatus.STABLE,
    )

  // --- ferm -----------------------------------------------------------------------------------

  private fun fermConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "ferm.intervention_type",
        text(
          "Restoration intervention",
          "Type d'intervention",
          "Tipo de intervención",
          "Loại hình can thiệp phục hồi",
          "Aina ya uingiliaji wa urejeshaji",
        ),
        ConceptDataType.SELECT_ONE,
        text("Main restoration approach used on the site."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "approach", "en" to "method", "en" to "restoration type"),
        codeList =
          listOf(
            item("natural_regeneration", text("Natural regeneration")),
            item("assisted_natural_regeneration", text("Assisted natural regeneration")),
            item("tree_planting", text("Tree planting")),
            item("agroforestry", text("Agroforestry")),
            item("mangrove_restoration", text("Mangrove restoration")),
            item("hydrological_rehabilitation", text("Hydrological or tidal rehabilitation")),
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_RESTORATION),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "ferm.area_under_restoration_ha",
        text(
          "Area under restoration (ha)",
          "Superficie en restauration (ha)",
          "Área en restauración (ha)",
          "Diện tích phục hồi (ha)",
          "Eneo linalorejeshwa (ha)",
        ),
        ConceptDataType.DECIMAL,
        text("Area where restoration activities are underway, in hectares."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "har",
        keywords = keywords("en" to "restored area", "en" to "hectares restored"),
        goals = listOf(GOAL_RESTORATION),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "ferm.start_date",
        text(
          "Restoration start date",
          "Date de début",
          "Fecha de inicio",
          "Ngày bắt đầu phục hồi",
          "Tarehe ya kuanza urejeshaji",
        ),
        ConceptDataType.DATE,
        text("Date restoration activities started on the site."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        goals = listOf(GOAL_RESTORATION),
      ),
      concept(
        "ferm.trees_planted",
        text(
          "Trees planted",
          "Arbres plantés",
          "Árboles plantados",
          "Số cây đã trồng",
          "Miti iliyopandwa",
        ),
        ConceptDataType.INTEGER,
        text("Number of trees, mangrove propagules, or seedlings planted."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "seedlings", "es" to "plántulas", "fr" to "plants"),
        goals = listOf(GOAL_RESTORATION),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "ferm.trees_surviving",
        text(
          "Trees surviving",
          "Arbres survivants",
          "Árboles sobrevivientes",
          "Số cây sống sót",
          "Miti inayoendelea kuishi",
        ),
        ConceptDataType.INTEGER,
        text("Number of planted trees or seedlings still alive at the monitoring visit."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "survival", "en" to "alive", "es" to "supervivencia"),
        goals = listOf(GOAL_RESTORATION),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "ferm.survival_rate_pct",
        text(
          "Survival rate (%)",
          "Taux de survie (%)",
          "Tasa de supervivencia (%)",
          "Tỷ lệ sống (%)",
          "Kiwango cha kuishi (%)",
        ),
        ConceptDataType.DECIMAL,
        text("Share of planted trees still alive, as a percentage."),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "%",
        goals = listOf(GOAL_RESTORATION),
      ),
      concept(
        "ferm.ecosystem_type",
        text(
          "Ecosystem type",
          "Type d'écosystème",
          "Tipo de ecosistema",
          "Loại hệ sinh thái",
          "Aina ya mfumo ikolojia",
        ),
        ConceptDataType.SELECT_ONE,
        text(
          "Broad ecosystem biome being restored, aligned with the IUCN Global Ecosystem Typology."
        ),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("forest", text("Forest")),
            item("mangrove", text("Mangrove")),
            item("grassland", text("Grassland or savanna")),
            item("wetland", text("Wetland")),
            item("cropland", text("Cropland")),
            item("coastal_marine", text("Coastal or marine")),
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_RESTORATION),
      ),
      concept(
        "ferm.restoration_objective",
        text(
          "Restoration objective",
          "Objectif de restauration",
          "Objetivo de restauración",
          "Mục tiêu phục hồi",
          "Lengo la urejeshaji",
        ),
        ConceptDataType.SELECT_MULTIPLE,
        text("Primary ecological and social objectives of the restoration initiative."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("biodiversity_recovery", text("Biodiversity recovery")),
            item("climate_mitigation", text("Carbon sequestration & climate mitigation")),
            item("watershed_soil", text("Watershed & soil protection")),
            item("coastal_resilience", text("Coastal & storm protection")),
            item("community_livelihoods", text("Community livelihoods & agroforestry")),
          ),
        goals = listOf(GOAL_RESTORATION),
        pillar = ImpactPillar.ADAPTATION,
      ),
      concept(
        "ferm.species_planted",
        text(
          "Planted species",
          "Espèces plantées",
          "Especies plantadas",
          "Loài cây trồng",
          "Spishi zilizopandwa",
        ),
        ConceptDataType.TEXT,
        text("Scientific or common names of native or agroforestry species planted on the site."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "tree species", "en" to "native species"),
        goals = listOf(GOAL_RESTORATION),
      ),
    )

  private fun fermMonitoringWaveTemplate(): FormTemplate {
    val f = fermConcepts()
    val c = coreConcepts()
    return template(
      id = "ferm_monitoring_wave",
      title =
        text(
          "Ecosystem restoration site check",
          "Suivi de site de restauration",
          "Monitoreo de sitio de restauración",
          "Kiểm tra hiện trường phục hồi hệ sinh thái",
          "Ukaguzi wa eneo la urejeshaji wa mfumo ikolojia",
        ),
      description =
        text(
          "Baseline or repeat field visit recording ecosystem type, restoration intervention, area under restoration, planted species, and seedling survival."
        ),
      questions =
        listOf(
          question(
            f.byId("ferm.ecosystem_type"),
            "ecosystem_type",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to f.byId("ferm.ecosystem_type"),
          question(
            f.byId("ferm.intervention_type"),
            "intervention_type",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to f.byId("ferm.intervention_type"),
          question(
            f.byId("ferm.restoration_objective"),
            "restoration_objective",
            EditorQuestionType.SELECT_MULTIPLE,
          ) to f.byId("ferm.restoration_objective"),
          question(
            f.byId("ferm.area_under_restoration_ha"),
            "area_under_restoration_ha",
            EditorQuestionType.NUMBER,
            required = true,
          ) to f.byId("ferm.area_under_restoration_ha"),
          question(f.byId("ferm.start_date"), "start_date", EditorQuestionType.DATE) to
            f.byId("ferm.start_date"),
          question(
            f.byId("ferm.species_planted"),
            "species_planted",
            EditorQuestionType.TEXT,
            hint = "Dominant native or agroforestry species planted.",
          ) to f.byId("ferm.species_planted"),
          question(f.byId("ferm.trees_planted"), "trees_planted", EditorQuestionType.NUMBER) to
            f.byId("ferm.trees_planted"),
          question(f.byId("ferm.trees_surviving"), "trees_surviving", EditorQuestionType.NUMBER) to
            f.byId("ferm.trees_surviving"),
          question(
            f.byId("ferm.survival_rate_pct"),
            "survival_rate_pct",
            EditorQuestionType.NUMBER,
            hint = "Percentage of planted seedlings surviving (0–100%).",
          ) to f.byId("ferm.survival_rate_pct"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun fermRestorationPack() =
    PurposePack(
      id = "ferm_restoration",
      organizationId = GLOBAL,
      title =
        text(
          "Ecosystem restoration",
          "Restauration des écosystèmes",
          "Restauración de ecosistemas",
          "Phục hồi hệ sinh thái",
          "Urejeshaji wa mifumo ikolojia",
        ),
      description =
        text(
          "Track degraded terrestrial, inland water, mangrove, and coastal sites under restoration, intervention activities, planted species, and seedling survival."
        ),
      icon = "forest",
      formTemplateIds = listOf("ferm_monitoring_wave"),
      programIds = listOf("ferm", "gbf_target_2", "bonn_challenge"),
      goals = listOf(GOAL_RESTORATION),
      pillar = ImpactPillar.MITIGATION,
      status = LibraryStatus.STABLE,
    )

  // --- pame -----------------------------------------------------------------------------------

  private fun pameConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "pame.threat_type",
        text(
          "Threat type",
          "Type de menace",
          "Tipo de amenaza",
          "Loại mối đe dọa",
          "Aina ya tishio",
        ),
        ConceptDataType.SELECT_MULTIPLE,
        text(
          "Direct pressures or threats observed at the site, aligned with the IUCN-CMP Direct Threats Classification."
        ),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "pressure", "en" to "disturbance", "es" to "amenaza"),
        codeList =
          listOf(
            item("logging", text("Logging")),
            item("poaching", text("Poaching")),
            item("fire", text("Fire")),
            item("encroachment", text("Encroachment")),
            item("mining", text("Mining")),
            item("grazing", text("Grazing")),
            item("invasive_species", text("Invasive species")),
            item("pollution", text("Pollution")),
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.threat_severity",
        text(
          "Threat severity",
          "Gravité de la menace",
          "Severidad de la amenaza",
          "Mức độ nghiêm trọng",
          "Ukali wa tishio",
        ),
        ConceptDataType.SELECT_ONE,
        text("Intensity and scope of damage caused by the observed threat."),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("low", text("Low")),
            item("medium", text("Medium")),
            item("high", text("High")),
          ),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.management_zone",
        text(
          "Management zone",
          "Zone de gestion",
          "Zona de manejo",
          "Phân khu quản lý",
          "Kanda ya usimamizi",
        ),
        ConceptDataType.SELECT_ONE,
        text("Protected area or conservation zone where the observation was recorded."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("core_strict", text("Strict protection / core zone")),
            item("buffer_zone", text("Buffer zone")),
            item("ecological_restoration", text("Ecological restoration zone")),
            item("sustainable_use", text("Community / sustainable use zone")),
            item("wildlife_corridor", text("Wildlife corridor / OECM")),
          ),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.management_action",
        text(
          "Management response",
          "Mesure de gestion prise",
          "Acción de manejo tomada",
          "Biện pháp xử lý",
          "Hatua ya usimamizi iliyochukuliwa",
        ),
        ConceptDataType.SELECT_ONE,
        text("Immediate management or enforcement action taken by the field team."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.ORG_ONLY,
        codeList =
          listOf(
            item("observation_logged", text("Observation logged only")),
            item("snare_gear_removed", text("Snare or illegal gear removed")),
            item("warning_issued", text("Community warning or sensitization")),
            item("incident_escalated", text("Escalated to enforcement team")),
            item("fire_suppressed", text("Fire controlled or suppressed")),
            item("boundary_repaired", text("Boundary marker or fence repaired")),
          ),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.patrol_effort_km",
        text(
          "Patrol effort (km)",
          "Effort de patrouille (km)",
          "Esfuerzo de patrullaje (km)",
          "Quãng đường tuần tra (km)",
          "Umbali wa doria (km)",
        ),
        ConceptDataType.DECIMAL,
        text("Distance covered by the patrol, in kilometers."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "km",
        keywords = keywords("en" to "distance", "en" to "ranger patrol"),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.patrol_date",
        text(
          "Patrol date",
          "Date de patrouille",
          "Fecha de patrullaje",
          "Ngày tuần tra",
          "Tarehe ya doria",
        ),
        ConceptDataType.DATE,
        text("Date of the patrol."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        goals = listOf(GOAL_PROTECTED_AREAS),
      ),
      concept(
        "pame.ranger_count",
        text(
          "Rangers on patrol",
          "Écogardes en patrouille",
          "Guardaparques en patrulla",
          "Số kiểm lâm tuần tra",
          "Askari wa wanyamapori kwenye doria",
        ),
        ConceptDataType.INTEGER,
        text("Number of rangers taking part in the patrol."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "staff", "en" to "team size"),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.species_observed",
        text(
          "Species observed",
          "Espèces observées",
          "Especies observadas",
          "Loài quan sát được",
          "Spishi zilizoonekana",
        ),
        ConceptDataType.TEXT,
        text("Scientific or common name of wildlife observed."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "wildlife", "en" to "sighting"),
        goals = listOf(GOAL_PROTECTED_AREAS),
      ),
    )

  private fun protectedAreaPatrolTemplate(): FormTemplate {
    val p = pameConcepts()
    val c = coreConcepts()
    return template(
      id = "protected_area_patrol",
      title =
        text(
          "Protected area patrol & threat check",
          "Patrouille et évaluation des menaces",
          "Patrullaje y evaluación de amenazas",
          "Tuần tra và đánh giá mối đe dọa khu bảo tồn",
          "Doria na ukaguzi wa vitisho katika eneo lililohifadhiwa",
        ),
      description =
        text(
          "Logs ranger patrol effort, management zone, IUCN direct threat categories, threat severity, wildlife observations, and management response."
        ),
      questions =
        listOf(
          question(p.byId("pame.patrol_date"), "patrol_date", EditorQuestionType.DATE, true) to
            p.byId("pame.patrol_date"),
          question(
            p.byId("pame.management_zone"),
            "management_zone",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to p.byId("pame.management_zone"),
          question(p.byId("pame.ranger_count"), "ranger_count", EditorQuestionType.NUMBER) to
            p.byId("pame.ranger_count"),
          question(
            p.byId("pame.patrol_effort_km"),
            "patrol_effort_km",
            EditorQuestionType.NUMBER,
            hint = "Distance patrolled in kilometers.",
          ) to p.byId("pame.patrol_effort_km"),
          question(
            p.byId("pame.threat_type"),
            "threat_type",
            EditorQuestionType.SELECT_MULTIPLE,
            true,
          ) to p.byId("pame.threat_type"),
          question(
            p.byId("pame.threat_severity"),
            "threat_severity",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to p.byId("pame.threat_severity"),
          question(
            p.byId("pame.management_action"),
            "management_action",
            EditorQuestionType.SELECT_ONE,
          ) to p.byId("pame.management_action"),
          question(
            p.byId("pame.species_observed"),
            "species_observed",
            EditorQuestionType.TEXT,
            hint = "Key wildlife or indicator species observed.",
          ) to p.byId("pame.species_observed"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun protectedAreaManagementPack() =
    PurposePack(
      id = "protected_area_management",
      organizationId = GLOBAL,
      title =
        text(
          "Protected area management",
          "Gestion des aires protégées",
          "Gestión de áreas protegidas",
          "Quản lý khu bảo tồn",
          "Usimamizi wa maeneo yaliyohifadhiwa",
        ),
      description =
        text(
          "Log ranger patrols, verify deforestation or fire alerts, classify direct threats and severity, and record wildlife observations and management actions."
        ),
      icon = "shield",
      formTemplateIds = listOf("protected_area_patrol"),
      programIds = listOf("gd_pame", "mett", "imet", "smart", "gbf_target_3"),
      goals = listOf(GOAL_PROTECTED_AREAS),
      pillar = ImpactPillar.PROTECTION,
      status = LibraryStatus.STABLE,
    )

  // --- iplc -----------------------------------------------------------------------------------

  private fun iplcConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "iplc.community_type",
        text(
          "Community type",
          "Type de communauté",
          "Tipo de comunidad",
          "Loại hình cộng đồng",
          "Aina ya jamii",
        ),
        ConceptDataType.SELECT_ONE,
        text("Whether the community identifies as Indigenous Peoples or a local community."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.ORG_ONLY,
        codeList =
          listOf(
            item("indigenous_peoples", text("Indigenous Peoples")),
            item("local_community", text("Local community")),
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "iplc.community_name",
        text(
          "Community name",
          "Nom de la communauté",
          "Nombre de la comunidad",
          "Tên cộng đồng",
          "Jina la jamii",
        ),
        ConceptDataType.TEXT,
        text("Name of the community, as the community uses it."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        goals = listOf(GOAL_IPLC_RIGHTS),
      ),
      concept(
        "iplc.tenure_type",
        text(
          "Land tenure type",
          "Type de régime foncier",
          "Tipo de tenencia de la tierra",
          "Hình thức quyền sử dụng đất",
          "Aina ya umiliki wa ardhi",
        ),
        ConceptDataType.SELECT_ONE,
        text("How rights to the land are held, aligned with FAO VGGT tenure categories."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "land rights", "en" to "ownership"),
        codeList =
          listOf(
            item("customary", text("Customary")),
            item("communal", text("Communal")),
            item("private", text("Private")),
            item("public", text("Public")),
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "iplc.legal_recognition",
        text(
          "Legal recognition status",
          "Statut de reconnaissance juridique",
          "Estado de reconocimiento legal",
          "Tình trạng công nhận pháp lý",
          "Hali ya utambuzi wa kisheria",
        ),
        ConceptDataType.SELECT_ONE,
        text("Statutory recognition status of the community's land claim, aligned with LandMark."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.ORG_ONLY,
        codeList =
          listOf(
            item("formally_titled", text("Formally titled or gazetted")),
            item("customary_unrecognized", text("Held under customary law (not yet titled)")),
            item("in_progress", text("Claim or registration in progress")),
            item("contested", text("Contested or overlapping claim")),
          ),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "iplc.governance_institution",
        text(
          "Community governance institution",
          "Institution de gouvernance communautaire",
          "Institución de gobernanza comunitaria",
          "Thiết chế quản trị cộng đồng",
          "Taasisi ya utawala wa jamii",
        ),
        ConceptDataType.TEXT,
        text(
          "Traditional council, elders' assembly, or community organization governing the territory."
        ),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        goals = listOf(GOAL_IPLC_RIGHTS),
      ),
      concept(
        "iplc.resource_use",
        text(
          "Customary resource use",
          "Usages coutumiers des ressources",
          "Uso consuetudinario de recursos",
          "Sử dụng tài nguyên theo phong tục",
          "Matumizi ya asili ya rasilimali",
        ),
        ConceptDataType.SELECT_MULTIPLE,
        text("Traditional livelihood, stewardship, and cultural uses of the mapped territory."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.ORG_ONLY,
        codeList =
          listOf(
            item("agroforestry_crops", text("Traditional agroforestry & rotational crops")),
            item("ntfp_harvesting", text("Non-timber forest products (NTFP) & medicinal plants")),
            item("pastoralism_grazing", text("Seasonal pastoralism & grazing")),
            item("artisanal_fishing", text("Artisanal fishing & wetland stewardship")),
            item("sacred_cultural_site", text("Sacred grove or cultural heritage site")),
            item("community_conservation", text("Community-conserved forest or wildlife corridor")),
          ),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "iplc.fpic_consent",
        text(
          "Free, prior and informed consent",
          "Consentement libre, informé et préalable (CLIP)",
          "Consentimiento libre, previo e informado (CLPI)",
          "Sự đồng thuận tự nguyện, trước và được thông tin đầy đủ (FPIC)",
          "Ridhaa huru, ya awali na yenye taarifa (FPIC)",
        ),
        ConceptDataType.SELECT_ONE,
        text("Status of the community's free, prior and informed consent (FPIC)."),
        ConceptAggregation.SHARE_BY_CODE,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "fpic", "en" to "consent"),
        codeList =
          listOf(
            item("given", text("Given")),
            item("withheld", text("Withheld")),
            item("pending", text("Pending")),
          ),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "iplc.territory_boundary",
        text(
          "Territory boundary",
          "Limite du territoire",
          "Límite del territorio",
          "Ranh giới lãnh thổ cộng đồng",
          "Mpaka wa eneo la jamii",
        ),
        ConceptDataType.POLYGON,
        text("Boundary of the community's territory, mapped with the community."),
        ConceptAggregation.COUNT_DISTINCT_FEATURES,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "territory", "en" to "community land"),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
    )

  private fun communityLandMappingTemplate(): FormTemplate {
    val i = iplcConcepts()
    val c = coreConcepts()
    return template(
      id = "community_land_mapping",
      title =
        text(
          "Community land & resource mapping",
          "Cartographie des terres et ressources communautaires",
          "Mapeo de tierras y recursos comunitarios",
          "Lập bản đồ đất đai và tài nguyên cộng đồng",
          "Uchoraji ramani wa ardhi na rasilimali za jamii",
        ),
      description =
        text(
          "Participatory mapping form capturing FPIC consent, community identity, customary governance, tenure recognition, resource use, and territory boundaries."
        ),
      questions =
        listOf(
          question(
            i.byId("iplc.fpic_consent"),
            "fpic_consent",
            EditorQuestionType.SELECT_ONE,
            required = true,
            hint = "Confirm Free, Prior and Informed Consent before recording community data.",
          ) to i.byId("iplc.fpic_consent"),
          question(
            i.byId("iplc.community_name"),
            "community_name",
            EditorQuestionType.TEXT,
            true,
          ) to i.byId("iplc.community_name"),
          question(
            i.byId("iplc.community_type"),
            "community_type",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to i.byId("iplc.community_type"),
          question(
            i.byId("iplc.governance_institution"),
            "governance_institution",
            EditorQuestionType.TEXT,
            hint = "Traditional council, elders' assembly, or community association.",
          ) to i.byId("iplc.governance_institution"),
          question(
            i.byId("iplc.tenure_type"),
            "tenure_type",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to i.byId("iplc.tenure_type"),
          question(
            i.byId("iplc.legal_recognition"),
            "legal_recognition",
            EditorQuestionType.SELECT_ONE,
          ) to i.byId("iplc.legal_recognition"),
          question(
            i.byId("iplc.resource_use"),
            "resource_use",
            EditorQuestionType.SELECT_MULTIPLE,
          ) to i.byId("iplc.resource_use"),
          question(
            i.byId("iplc.territory_boundary"),
            "territory_boundary",
            EditorQuestionType.POLYGON,
            required = true,
            hint = "Walk or trace the customary territory boundary with community representatives.",
            capture = GeometryCapture.GPS_OR_MAP,
          ) to i.byId("iplc.territory_boundary"),
          question(c.byId("core.area_ha"), "area_ha", EditorQuestionType.NUMBER) to
            c.byId("core.area_ha"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun iplcLandMappingPack() =
    PurposePack(
      id = "iplc_land_mapping",
      organizationId = GLOBAL,
      title =
        text(
          "Community land & resource mapping",
          "Cartographie des terres et ressources communautaires",
          "Mapeo de tierras y recursos comunitarios",
          "Lập bản đồ đất đai và tài nguyên cộng đồng",
          "Uchoraji ramani wa ardhi na rasilimali za jamii",
        ),
      description =
        text(
          "Document customary and Indigenous territories, Free, Prior and Informed Consent (FPIC), tenure governance, and traditional resource stewardship."
        ),
      icon = "groups",
      formTemplateIds = listOf("community_land_mapping"),
      programIds = listOf("landmark", "icca", "vggt"),
      goals = listOf(GOAL_IPLC_RIGHTS),
      pillar = ImpactPillar.PROTECTION,
      status = LibraryStatus.STABLE,
    )

  // --- lulc -----------------------------------------------------------------------------------

  private fun lulcConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "lulc.land_cover_class",
        text(
          "Land cover class",
          "Classe de couverture terrestre",
          "Clase de cobertura terrestre",
          "Lớp độ che phủ đất",
          "Aina ya ufunikaji wa ardhi",
        ),
        ConceptDataType.SELECT_ONE,
        text("Land cover category, following the FAO Global Forest Resources Assessment."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "land cover", "en" to "vegetation", "es" to "cobertura"),
        codeList =
          listOf(
            item("forest", text("Forest")),
            item("other_wooded_land", text("Other wooded land")),
            item("other_land_with_tree_cover", text("Other land with tree cover")),
            item("other_land", text("Other land")),
            item("inland_water", text("Inland water bodies")),
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE, GOAL_OPEN_EARTH_DATA),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.canopy_cover_pct",
        text(
          "Canopy cover (%)",
          "Couverture de la canopée (%)",
          "Cobertura de dosel (%)",
          "Độ tàn che (%)",
          "Ufunikaji wa matawi ya miti (%)",
        ),
        ConceptDataType.DECIMAL,
        text("Share of the ground covered by tree crowns, as a percentage."),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "%",
        keywords = keywords("en" to "tree cover", "en" to "crown cover"),
        goals = listOf(GOAL_FOREST_CARBON, GOAL_OPEN_EARTH_DATA),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.tree_height_m",
        text(
          "Tree height (m)",
          "Hauteur des arbres (m)",
          "Altura de los árboles (m)",
          "Chiều cao cây (m)",
          "Urefu wa miti (m)",
        ),
        ConceptDataType.DECIMAL,
        text("Height of the dominant trees, in meters."),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "m",
        goals = listOf(GOAL_FOREST_CARBON),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.land_use",
        text(
          "Land use",
          "Utilisation des terres",
          "Uso de la tierra",
          "Sử dụng đất",
          "Matumizi ya ardhi",
        ),
        ConceptDataType.SELECT_ONE,
        text("Main socio-economic or ecological use of the land."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("cropland", text("Cropland")),
            item("agroforestry", text("Agroforestry / shaded tree crop")),
            item("grazing", text("Grazing")),
            item("forestry", text("Forestry")),
            item("settlement", text("Settlement")),
            item("conservation", text("Conservation")),
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_OPEN_EARTH_DATA),
      ),
      concept(
        "lulc.crop_type",
        text(
          "Primary crop or tree commodity",
          "Culture ou essence principale",
          "Cultivo o especie arbórea principal",
          "Cây trồng chính",
          "Zao kuu au mti",
        ),
        ConceptDataType.SELECT_ONE,
        text(
          "Dominant perennial or annual crop observed on the plot, used to train and validate commodity land-cover models."
        ),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "commodity crop", "en" to "tree crop"),
        codeList =
          listOf(
            item("cocoa", text("Cocoa")),
            item("coffee", text("Coffee")),
            item("oil_palm", text("Oil palm")),
            item("rubber", text("Rubber")),
            item("cashew", text("Cashew")),
            item("tea", text("Tea")),
            item("annual_crops", text("Annual food crops")),
            item("none_natural", text("None (natural vegetation)")),
            item("other", text("Other crop")),
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE, GOAL_OPEN_EARTH_DATA),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.shade_tree_tier",
        text(
          "Shade canopy structure",
          "Structure de la canopée d'ombrage",
          "Estructura del dosel de sombra",
          "Cấu trúc tầng cây che bóng",
          "Muundo wa miti ya kivuli",
        ),
        ConceptDataType.SELECT_ONE,
        text(
          "Upper-stratum shade tree structure over perennial crops, distinguishing shaded agroforestry from natural forest and monocultures."
        ),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "shade trees", "en" to "agroforestry canopy"),
        codeList =
          listOf(
            item("multi_strata_native", text("Multi-strata with native remnant trees")),
            item("planted_shade", text("Planted shade or fruit trees")),
            item("unshaded_monoculture", text("Unshaded monoculture")),
            item("not_applicable", text("Not applicable (non-cropland)")),
          ),
        goals = listOf(GOAL_DEFORESTATION_FREE, GOAL_OPEN_EARTH_DATA),
        pillar = ImpactPillar.ADAPTATION,
      ),
      concept(
        "lulc.forest_stratum",
        text(
          "Forest structure & stratum",
          "Strate et structure forestière",
          "Estrato y estructura forestal",
          "Trạng thái và cấu trúc rừng",
          "Tabaka na muundo wa msitu",
        ),
        ConceptDataType.SELECT_ONE,
        text("Forest condition and successional stage for REDD+ and tropical forest monitoring."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("primary_intact", text("Primary intact forest")),
            item("secondary_regenerating", text("Secondary or regenerating forest")),
            item("degraded_forest", text("Degraded natural forest")),
            item("mangrove_peatland", text("Mangrove or peat swamp forest")),
            item("planted_forest", text("Planted forest or woodlot")),
            item("non_forest", text("Non-forest")),
          ),
        goals = listOf(GOAL_FOREST_CARBON, GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.basal_area_m2_ha",
        text(
          "Basal area (m²/ha)",
          "Surface terrière (m²/ha)",
          "Área basal (m²/ha)",
          "Tiết diện ngang (m²/ha)",
          "Eneo la shina (m²/ha)",
        ),
        ConceptDataType.DECIMAL,
        text(
          "Cross-sectional stem area at breast height per hectare, used to estimate stand biomass and carbon stock."
        ),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "m2/har",
        keywords = keywords("en" to "stand density", "en" to "biomass", "en" to "carbon stock"),
        goals = listOf(GOAL_FOREST_CARBON),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.disturbance_driver",
        text(
          "Observed disturbance or degradation",
          "Perturbation ou dégradation observée",
          "Perturbación o degradación observada",
          "Tác nhân gây suy thoái rừng",
          "Uharibifu au usumbufu ulioonekana",
        ),
        ConceptDataType.SELECT_MULTIPLE,
        text("Signs of canopy degradation or disturbance recorded in the sample plot."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("none", text("No disturbance observed")),
            item("selective_logging", text("Selective logging or timber extraction")),
            item("fire_burn", text("Fire or burn scars")),
            item("agricultural_clearing", text("Small-scale clearing or shifting cultivation")),
            item("grazing_browsing", text("Livestock grazing")),
            item("wind_drought_pest", text("Storm, drought, or pest dieback")),
          ),
        goals = listOf(GOAL_FOREST_CARBON, GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.observation_date",
        text(
          "Observation date",
          "Date d'observation",
          "Fecha de observación",
          "Ngày quan sát",
          "Tarehe ya uchunguzi",
        ),
        ConceptDataType.DATE,
        text("Date the land cover or sample plot was observed."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
      ),
    )

  private fun lulcGroundTruthTemplate(): FormTemplate {
    val l = lulcConcepts()
    val c = coreConcepts()
    return template(
      id = "lulc_ground_truth",
      title =
        text(
          "Land cover & crop reference observation",
          "Observation de référence de la couverture terrestre",
          "Observación de referencia de cobertura y cultivos",
          "Quan sát thực địa độ che phủ đất và cây trồng",
          "Uchunguzi wa marejeleo ya ufunikaji wa ardhi na mazao",
        ),
      description =
        text(
          "Visual or field plot classification of land cover class, land use, perennial crop type, shade canopy structure, and crown cover percentage."
        ),
      questions =
        listOf(
          question(
            l.byId("lulc.land_cover_class"),
            "land_cover_class",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to l.byId("lulc.land_cover_class"),
          question(l.byId("lulc.land_use"), "land_use", EditorQuestionType.SELECT_ONE, true) to
            l.byId("lulc.land_use"),
          question(l.byId("lulc.crop_type"), "crop_type", EditorQuestionType.SELECT_ONE) to
            l.byId("lulc.crop_type"),
          question(
            l.byId("lulc.shade_tree_tier"),
            "shade_tree_tier",
            EditorQuestionType.SELECT_ONE,
          ) to l.byId("lulc.shade_tree_tier"),
          question(
            l.byId("lulc.canopy_cover_pct"),
            "canopy_cover_pct",
            EditorQuestionType.NUMBER,
          ) to l.byId("lulc.canopy_cover_pct"),
          question(
            l.byId("lulc.observation_date"),
            "observation_date",
            EditorQuestionType.DATE,
          ) to l.byId("lulc.observation_date"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun reddPlusForestAssessmentTemplate(): FormTemplate {
    val l = lulcConcepts()
    val c = coreConcepts()
    return template(
      id = "redd_plus_forest_assessment",
      title =
        text(
          "Forest inventory & disturbance plot",
          "Parcelle d'inventaire forestier et de perturbation",
          "Parcela de inventario y perturbación forestal",
          "Ô tiêu chuẩn kiểm kê và giám sát suy thoái rừng",
          "Ploti ya hesabu ya msitu na uharibifu",
        ),
      description =
        text(
          "Permanent sample plot or visual verification form capturing forest stratum, canopy cover, tree height, stand basal area, and degradation drivers."
        ),
      questions =
        listOf(
          question(
            l.byId("lulc.land_cover_class"),
            "land_cover_class",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to l.byId("lulc.land_cover_class"),
          question(
            l.byId("lulc.forest_stratum"),
            "forest_stratum",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to l.byId("lulc.forest_stratum"),
          question(
            l.byId("lulc.canopy_cover_pct"),
            "canopy_cover_pct",
            EditorQuestionType.NUMBER,
            true,
          ) to l.byId("lulc.canopy_cover_pct"),
          question(l.byId("lulc.tree_height_m"), "tree_height_m", EditorQuestionType.NUMBER) to
            l.byId("lulc.tree_height_m"),
          question(
            l.byId("lulc.basal_area_m2_ha"),
            "basal_area_m2_ha",
            EditorQuestionType.NUMBER,
            hint = "Stand basal area in m²/ha.",
          ) to l.byId("lulc.basal_area_m2_ha"),
          question(
            l.byId("lulc.disturbance_driver"),
            "disturbance_driver",
            EditorQuestionType.SELECT_MULTIPLE,
          ) to l.byId("lulc.disturbance_driver"),
          question(
            l.byId("lulc.observation_date"),
            "observation_date",
            EditorQuestionType.DATE,
          ) to l.byId("lulc.observation_date"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun lulcTrainingDataPack() =
    PurposePack(
      id = "lulc_training_data",
      organizationId = GLOBAL,
      title =
        text(
          "Land cover & crop mapping",
          "Cartographie de la couverture terrestre et des cultures",
          "Mapeo de cobertura terrestre y cultivos",
          "Lập bản đồ độ che phủ đất và cây trồng",
          "Uchoraji ramani wa ufunikaji wa ardhi na mazao",
        ),
      description =
        text(
          "Collect ground-truth observations and visual imagery classifications of land cover, perennial crops, and shade trees to train and validate remote-sensing models."
        ),
      icon = "layers",
      formTemplateIds = listOf("lulc_ground_truth"),
      programIds = listOf("fdap", "fao_lccs", "open_foris", "fao_fra"),
      goals = listOf(GOAL_DEFORESTATION_FREE, GOAL_OPEN_EARTH_DATA),
      pillar = ImpactPillar.MITIGATION,
      status = LibraryStatus.STABLE,
    )

  private fun reddPlusTfffPack() =
    PurposePack(
      id = "redd_plus_tfff",
      organizationId = GLOBAL,
      title =
        text(
          "Forest carbon & conservation monitoring",
          "Suivi du carbone et de la conservation des forêts",
          "Monitoreo de carbono y conservación forestal",
          "Giám sát carbon và bảo tồn rừng",
          "Ufuatiliaji wa kaboni na uhifadhi wa misitu",
        ),
      description =
        text(
          "Measure forest sample plots, canopy cover, stand basal area, tree height, and degradation disturbances for carbon accounting and tropical forest conservation finance."
        ),
      icon = "public",
      formTemplateIds = listOf("redd_plus_forest_assessment"),
      programIds = listOf("redd_plus", "tfff", "art_trees", "vcs", "fao_fra"),
      goals = listOf(GOAL_FOREST_CARBON, GOAL_DEFORESTATION_FREE, GOAL_PROTECTED_AREAS),
      pillar = ImpactPillar.MITIGATION,
      status = LibraryStatus.STABLE,
    )

  // --- timber ---------------------------------------------------------------------------------

  private fun timberConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "timber.species",
        text(
          "Tree species",
          "Espèce d'arbre",
          "Especie arbórea",
          "Loài cây gỗ",
          "Spishi ya mti",
        ),
        ConceptDataType.TEXT,
        text("Scientific name of the tree species, matching the AGROVOC thesaurus where possible."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "species", "en" to "timber species", "es" to "especie"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.log_id",
        text(
          "Log ID",
          "Identifiant de la grume",
          "ID de la troza",
          "Mã khúc gỗ",
          "Kitambulisho cha gogo",
        ),
        ConceptDataType.TEXT,
        text("Identifier marked on the log (tag, hammer stamp, or barcode)."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "tag", "en" to "barcode"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.felling_permit_id",
        text(
          "Felling permit or concession ID",
          "Numéro de permis de coupe ou concession",
          "ID de permiso de aprovechamiento o concesión",
          "Số giấy phép khai thác gỗ",
          "Nambari ya kibali cha uvunaji",
        ),
        ConceptDataType.TEXT,
        text(
          "Legal harvesting permit, annual allowable cut certificate, or concession identifier."
        ),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "permit", "en" to "concession", "en" to "license"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.stump_location",
        text(
          "Stump or felling point",
          "Point d'abattage ou souche",
          "Ubicación del tocón o tala",
          "Tọa độ gốc cây khai thác",
          "Mahali pa kisiki au ukataji",
        ),
        ConceptDataType.POINT,
        text(
          "GPS coordinates of the felled tree stump, linking bucked logs back to their origin in the harvest unit."
        ),
        ConceptAggregation.COUNT_DISTINCT_FEATURES,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "stump", "en" to "felling coordinates"),
        goals = listOf(GOAL_LEGAL_TIMBER, GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "timber.volume_m3",
        text(
          "Volume (m³)",
          "Volume (m³)",
          "Volumen (m³)",
          "Thể tích (m³)",
          "Ujazo (m³)",
        ),
        ConceptDataType.DECIMAL,
        text("Volume of the log or harvest, in cubic meters."),
        ConceptAggregation.SUM,
        PrivacyClass.ORG_ONLY,
        unit = "m3",
        goals = listOf(GOAL_LEGAL_TIMBER),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "timber.harvest_unit",
        text(
          "Harvest unit",
          "Unité d'exploitation",
          "Unidad de aprovechamiento",
          "Lô hoặc khoảnh khai thác",
          "Kitengo cha uvunaji",
        ),
        ConceptDataType.TEXT,
        text("Compartment, forest management unit, or cutting block the timber comes from."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "compartment", "en" to "cutting block", "en" to "coupe"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.diameter_cm",
        text(
          "Diameter (cm)",
          "Diamètre (cm)",
          "Diámetro (cm)",
          "Đường kính (cm)",
          "Kipenyo (cm)",
        ),
        ConceptDataType.DECIMAL,
        text("Log or stem diameter at breast height (DBH), in centimeters."),
        ConceptAggregation.MEAN,
        PrivacyClass.ORG_ONLY,
        unit = "cm",
        keywords = keywords("en" to "dbh", "en" to "girth"),
      ),
      concept(
        "timber.length_m",
        text(
          "Log length (m)",
          "Longueur de la grume (m)",
          "Longitud de la troza (m)",
          "Chiều dài khúc gỗ (m)",
          "Urefu wa gogo (m)",
        ),
        ConceptDataType.DECIMAL,
        text("Measured length of the bucked log section, in meters."),
        ConceptAggregation.MEAN,
        PrivacyClass.ORG_ONLY,
        unit = "m",
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.felling_date",
        text(
          "Felling date",
          "Date d'abattage",
          "Fecha de tala",
          "Ngày khai thác",
          "Tarehe ya ukataji",
        ),
        ConceptDataType.DATE,
        text("Date the tree was felled."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
    )

  private fun timberHarvestFellingTemplate(): FormTemplate {
    val t = timberConcepts()
    val c = coreConcepts()
    return template(
      id = "timber_harvest_felling",
      title =
        text(
          "Timber harvest & chain-of-custody check",
          "Contrôle d'abattage et chaîne de contrôle du bois",
          "Control de aprovechamiento y cadena de custodia",
          "Kiểm tra khai thác và chuỗi hành trình sản phẩm gỗ",
          "Ukaguzi wa uvunaji wa mbao na mnyororo wa ufuatiliaji",
        ),
      description =
        text(
          "Felling and log-registration form linking log barcodes, species, dimensions, and cubic volume to stump coordinates and harvesting permits."
        ),
      questions =
        listOf(
          question(t.byId("timber.felling_date"), "felling_date", EditorQuestionType.DATE, true) to
            t.byId("timber.felling_date"),
          question(
            t.byId("timber.felling_permit_id"),
            "felling_permit_id",
            EditorQuestionType.TEXT,
            required = true,
            hint = "Harvesting permit, coupe license, or concession ID.",
          ) to t.byId("timber.felling_permit_id"),
          question(
            t.byId("timber.harvest_unit"),
            "harvest_unit",
            EditorQuestionType.TEXT,
            required = true,
            hint = "Forest management unit, compartment, or cutting block.",
          ) to t.byId("timber.harvest_unit"),
          question(
            t.byId("timber.stump_location"),
            "stump_location",
            EditorQuestionType.LOCATION,
            required = true,
            hint = "Capture GPS coordinates at the felled stump.",
          ) to t.byId("timber.stump_location"),
          question(
            t.byId("timber.species"),
            "species",
            EditorQuestionType.TEXT,
            required = true,
            hint = "Scientific or common name of the harvested tree species.",
          ) to t.byId("timber.species"),
          question(
            t.byId("timber.log_id"),
            "log_id",
            EditorQuestionType.TEXT,
            required = true,
            hint = "Barcode, hammer stamp, or physical tag number on the log.",
          ) to t.byId("timber.log_id"),
          question(t.byId("timber.diameter_cm"), "diameter_cm", EditorQuestionType.NUMBER) to
            t.byId("timber.diameter_cm"),
          question(t.byId("timber.length_m"), "length_m", EditorQuestionType.NUMBER) to
            t.byId("timber.length_m"),
          question(
            t.byId("timber.volume_m3"),
            "volume_m3",
            EditorQuestionType.NUMBER,
            required = true,
          ) to t.byId("timber.volume_m3"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun timberTraceabilityPack() =
    PurposePack(
      id = "timber_traceability",
      organizationId = GLOBAL,
      title =
        text(
          "Timber traceability",
          "Traçabilité du bois",
          "Trazabilidad de la madera",
          "Truy xuất nguồn gốc gỗ",
          "Ufuatiliaji wa mbao",
        ),
      description =
        text(
          "Record harvest compartments, felling permits, stump coordinates, tree species, log tags, and timber volumes for legal harvest and chain-of-custody verification."
        ),
      icon = "carpenter",
      formTemplateIds = listOf("timber_harvest_felling"),
      exportProfileIds = listOf("eudr_geojson"),
      programIds = listOf("fsc", "pefc", "flegt", "eudr"),
      goals = listOf(GOAL_LEGAL_TIMBER, GOAL_DEFORESTATION_FREE),
      pillar = ImpactPillar.MITIGATION,
      status = LibraryStatus.STABLE,
    )
}
