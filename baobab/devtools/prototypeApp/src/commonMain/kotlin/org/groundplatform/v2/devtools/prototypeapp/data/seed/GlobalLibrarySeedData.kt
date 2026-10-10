/*
 * Copyright 2026 The Ground Authors.
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
package org.groundplatform.v2.devtools.prototypeapp.data.seed

import org.groundplatform.v2.devtools.prototypeapp.domain.model.CodeListItem
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptAggregation
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptDataType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ConceptLink
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ExportProfile
import org.groundplatform.v2.devtools.prototypeapp.domain.model.FormTemplate
import org.groundplatform.v2.devtools.prototypeapp.domain.model.ImpactPillar
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryConcept
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LibraryStatus
import org.groundplatform.v2.devtools.prototypeapp.domain.model.LocalizedText
import org.groundplatform.v2.devtools.prototypeapp.domain.model.Organization
import org.groundplatform.v2.devtools.prototypeapp.domain.model.OrganizationLibrary
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PrivacyClass
import org.groundplatform.v2.devtools.prototypeapp.domain.model.PurposePack
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorChoice
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorForm
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestion
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.EditorQuestionType
import org.groundplatform.v2.devtools.prototypeapp.domain.model.editor.GeometryCapture

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
      "pame" -> OrganizationLibrary(GLOBAL, concepts = pameConcepts())
      "iplc" -> OrganizationLibrary(GLOBAL, concepts = iplcConcepts())
      "lulc" -> OrganizationLibrary(GLOBAL, concepts = lulcConcepts())
      "timber" -> OrganizationLibrary(GLOBAL, concepts = timberConcepts())
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
        text("Area (ha)", "Superficie (ha)", "Superficie (ha)", "Diện tích (ha)", "Eneo (ha)"),
        ConceptDataType.DECIMAL,
        text("Area of the plot or site in hectares."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "har",
        keywords =
          keywords(
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
    )

  private fun producerProfileTemplate(): FormTemplate {
    val c = coreConcepts()
    return template(
      id = "producer_profile",
      title = text("Producer profile", "Profil du producteur", "Perfil del productor"),
      description = text("Registers a farmer or producer with their household details."),
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
          question(c.byId("core.producer_id"), "producer_id", EditorQuestionType.TEXT) to
            c.byId("core.producer_id"),
          question(
            c.byId("core.producer_gender"),
            "producer_gender",
            EditorQuestionType.SELECT_ONE,
          ) to c.byId("core.producer_gender"),
          question(c.byId("core.household_size"), "household_size", EditorQuestionType.INTEGER) to
            c.byId("core.household_size"),
          question(c.byId("core.country"), "country", EditorQuestionType.TEXT) to
            c.byId("core.country"),
          question(c.byId("core.admin_area"), "admin_area", EditorQuestionType.TEXT) to
            c.byId("core.admin_area"),
        ),
    )
  }

  private fun producerRegistrationPack() =
    PurposePack(
      id = "producer_registration",
      organizationId = GLOBAL,
      title =
        text("Producer registration", "Enregistrement des producteurs", "Registro de productores"),
      description = text("Build a register of the farmers and producers you work with."),
      icon = "person_add",
      formTemplateIds = listOf("producer_profile"),
      status = LibraryStatus.STABLE,
    )

  // --- eudr -----------------------------------------------------------------------------------

  private fun eudrConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "eudr.commodity",
        text("Commodity", "Produit de base", "Producto básico", "Mặt hàng", "Bidhaa"),
        ConceptDataType.SELECT_ONE,
        text(
          "Relevant commodity produced on the plot, as listed in the EU Deforestation Regulation."
        ),
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
    )

  private fun eudrPlotRegistrationTemplate(): FormTemplate {
    val e = eudrConcepts()
    val c = coreConcepts()
    return template(
      id = "eudr_plot_registration",
      title =
        text(
          "EUDR plot registration",
          "Enregistrement de parcelle RDUE",
          "Registro de parcela EUDR",
        ),
      description =
        text("Records the geolocation and commodity of each production plot for due diligence."),
      questions =
        listOf(
          question(e.byId("eudr.commodity"), "commodity", EditorQuestionType.SELECT_ONE, true) to
            e.byId("eudr.commodity"),
          question(
            e.byId("eudr.production_place"),
            "production_place",
            EditorQuestionType.POLYGON,
            required = true,
            hint = "Walk or draw the plot boundary.",
            capture = GeometryCapture.GPS_OR_MAP,
          ) to e.byId("eudr.production_place"),
          question(c.byId("core.area_ha"), "area_ha", EditorQuestionType.DECIMAL) to
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
          ) to e.byId("eudr.deforestation_free"),
        ),
    )
  }

  private fun eudrDueDiligencePack() =
    PurposePack(
      id = "eudr_due_diligence",
      organizationId = GLOBAL,
      title = text("EUDR due diligence", "Diligence raisonnée RDUE", "Diligencia debida EUDR"),
      description =
        text(
          "Collect plot geolocation and commodity data for EU Deforestation Regulation statements."
        ),
      icon = "verified",
      formTemplateIds = listOf("eudr_plot_registration"),
      exportProfileIds = listOf("eudr_geojson"),
      programIds = listOf("eudr", "uk_frc"),
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
        text("Restoration intervention", "Type d'intervention", "Tipo de intervención"),
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
        text("Restoration start date", "Date de début", "Fecha de inicio"),
        ConceptDataType.DATE,
        text("Date restoration activities started on the site."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        goals = listOf(GOAL_RESTORATION),
      ),
      concept(
        "ferm.trees_planted",
        text("Trees planted", "Arbres plantés", "Árboles plantados"),
        ConceptDataType.INTEGER,
        text("Number of trees or seedlings planted."),
        ConceptAggregation.SUM,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "seedlings", "es" to "plántulas", "fr" to "plants"),
        goals = listOf(GOAL_RESTORATION),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "ferm.trees_surviving",
        text("Trees surviving", "Arbres survivants", "Árboles sobrevivientes"),
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
        text("Survival rate (%)", "Taux de survie (%)", "Tasa de supervivencia (%)"),
        ConceptDataType.DECIMAL,
        text("Share of planted trees still alive, as a percentage."),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "%",
        goals = listOf(GOAL_RESTORATION),
      ),
      concept(
        "ferm.ecosystem_type",
        text("Ecosystem type", "Type d'écosystème", "Tipo de ecosistema"),
        ConceptDataType.SELECT_ONE,
        text("Ecosystem being restored."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("forest", text("Forest")),
            item("mangrove", text("Mangrove")),
            item("grassland", text("Grassland or savanna")),
            item("wetland", text("Wetland")),
            item("cropland", text("Cropland")),
            item("other", text("Other")),
          ),
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
          "Restoration monitoring wave",
          "Suivi de la restauration",
          "Monitoreo de restauración",
        ),
      description = text("Repeat visit recording restoration progress and tree survival."),
      questions =
        listOf(
          question(
            f.byId("ferm.intervention_type"),
            "intervention_type",
            EditorQuestionType.SELECT_ONE,
            true,
          ) to f.byId("ferm.intervention_type"),
          question(
            f.byId("ferm.area_under_restoration_ha"),
            "area_under_restoration_ha",
            EditorQuestionType.DECIMAL,
          ) to f.byId("ferm.area_under_restoration_ha"),
          question(f.byId("ferm.start_date"), "start_date", EditorQuestionType.DATE) to
            f.byId("ferm.start_date"),
          question(f.byId("ferm.trees_planted"), "trees_planted", EditorQuestionType.INTEGER) to
            f.byId("ferm.trees_planted"),
          question(f.byId("ferm.trees_surviving"), "trees_surviving", EditorQuestionType.INTEGER) to
            f.byId("ferm.trees_surviving"),
          question(c.byId("core.site_photo"), "site_photo", EditorQuestionType.PHOTO) to
            c.byId("core.site_photo"),
        ),
    )
  }

  private fun fermRestorationPack() =
    PurposePack(
      id = "ferm_restoration",
      organizationId = GLOBAL,
      title = text("Restoration (FERM)", "Restauration (FERM)", "Restauración (FERM)"),
      description =
        text(
          "Monitor ecosystem restoration with indicators aligned to the Framework for Ecosystem " +
            "Restoration Monitoring."
        ),
      icon = "forest",
      formTemplateIds = listOf("ferm_monitoring_wave"),
      programIds = listOf("ferm"),
      goals = listOf(GOAL_RESTORATION),
      pillar = ImpactPillar.MITIGATION,
      status = LibraryStatus.STABLE,
    )

  // --- pame -----------------------------------------------------------------------------------

  private fun pameConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "pame.threat_type",
        text("Threat type"),
        ConceptDataType.SELECT_MULTIPLE,
        text("Threats to the protected area observed at the site."),
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
            item("other", text("Other")),
          ),
        goals = listOf(GOAL_PROTECTED_AREAS),
        pillar = ImpactPillar.PROTECTION,
      ),
      concept(
        "pame.threat_severity",
        text("Threat severity"),
        ConceptDataType.SELECT_ONE,
        text("How severe the observed threat is."),
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
        "pame.patrol_effort_km",
        text("Patrol effort (km)"),
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
        text("Patrol date"),
        ConceptDataType.DATE,
        text("Date of the patrol."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        goals = listOf(GOAL_PROTECTED_AREAS),
      ),
      concept(
        "pame.ranger_count",
        text("Rangers on patrol"),
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
        text("Species observed"),
        ConceptDataType.TEXT,
        text("Scientific or common name of wildlife observed."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "wildlife", "en" to "sighting"),
        goals = listOf(GOAL_PROTECTED_AREAS),
      ),
    )

  // --- iplc -----------------------------------------------------------------------------------

  private fun iplcConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "iplc.community_type",
        text("Community type"),
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
        text("Community name"),
        ConceptDataType.TEXT,
        text("Name of the community, as the community uses it."),
        ConceptAggregation.NONE,
        PrivacyClass.SENSITIVE,
        goals = listOf(GOAL_IPLC_RIGHTS),
      ),
      concept(
        "iplc.tenure_type",
        text("Land tenure type"),
        ConceptDataType.SELECT_ONE,
        text("How rights to the land are held."),
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
        "iplc.fpic_consent",
        text("Free, prior and informed consent"),
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
        text("Territory boundary"),
        ConceptDataType.POLYGON,
        text("Boundary of the community's territory, mapped with the community."),
        ConceptAggregation.COUNT_DISTINCT_FEATURES,
        PrivacyClass.SENSITIVE,
        keywords = keywords("en" to "territory", "en" to "community land"),
        goals = listOf(GOAL_IPLC_RIGHTS),
        pillar = ImpactPillar.PROTECTION,
      ),
    )

  // --- lulc -----------------------------------------------------------------------------------

  private fun lulcConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "lulc.land_cover_class",
        text("Land cover class"),
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
        goals = listOf(GOAL_DEFORESTATION_FREE),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.canopy_cover_pct",
        text("Canopy cover (%)"),
        ConceptDataType.DECIMAL,
        text("Share of the ground covered by tree crowns, as a percentage."),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "%",
        keywords = keywords("en" to "tree cover", "en" to "crown cover"),
        pillar = ImpactPillar.MITIGATION,
      ),
      concept(
        "lulc.tree_height_m",
        text("Tree height (m)"),
        ConceptDataType.DECIMAL,
        text("Height of the dominant trees, in meters."),
        ConceptAggregation.MEAN,
        PrivacyClass.AGGREGATE_PUBLIC,
        unit = "m",
      ),
      concept(
        "lulc.land_use",
        text("Land use"),
        ConceptDataType.SELECT_ONE,
        text("Main use of the land."),
        ConceptAggregation.COUNT_BY_CODE,
        PrivacyClass.AGGREGATE_PUBLIC,
        codeList =
          listOf(
            item("cropland", text("Cropland")),
            item("grazing", text("Grazing")),
            item("forestry", text("Forestry")),
            item("settlement", text("Settlement")),
            item("conservation", text("Conservation")),
            item("other", text("Other")),
          ),
      ),
      concept(
        "lulc.observation_date",
        text("Observation date"),
        ConceptDataType.DATE,
        text("Date the land cover was observed."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
      ),
    )

  // --- timber ---------------------------------------------------------------------------------

  private fun timberConcepts(): List<LibraryConcept> =
    listOf(
      concept(
        "timber.species",
        text("Tree species"),
        ConceptDataType.TEXT,
        text("Scientific name of the tree species, matching the AGROVOC thesaurus where possible."),
        ConceptAggregation.NONE,
        PrivacyClass.AGGREGATE_PUBLIC,
        keywords = keywords("en" to "species", "en" to "timber species", "es" to "especie"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.log_id",
        text("Log ID"),
        ConceptDataType.TEXT,
        text("Identifier marked on the log (tag or barcode)."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "tag", "en" to "barcode"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.volume_m3",
        text("Volume (m³)"),
        ConceptDataType.DECIMAL,
        text("Volume of the log or harvest, in cubic meters."),
        ConceptAggregation.SUM,
        PrivacyClass.ORG_ONLY,
        unit = "m3",
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.harvest_unit",
        text("Harvest unit"),
        ConceptDataType.TEXT,
        text("Compartment or cutting block the timber comes from."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        keywords = keywords("en" to "compartment", "en" to "cutting block", "en" to "coupe"),
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
      concept(
        "timber.diameter_cm",
        text("Diameter (cm)"),
        ConceptDataType.DECIMAL,
        text("Log or stem diameter, in centimeters."),
        ConceptAggregation.MEAN,
        PrivacyClass.ORG_ONLY,
        unit = "cm",
        keywords = keywords("en" to "dbh", "en" to "girth"),
      ),
      concept(
        "timber.felling_date",
        text("Felling date"),
        ConceptDataType.DATE,
        text("Date the tree was felled."),
        ConceptAggregation.NONE,
        PrivacyClass.ORG_ONLY,
        goals = listOf(GOAL_LEGAL_TIMBER),
      ),
    )
}
