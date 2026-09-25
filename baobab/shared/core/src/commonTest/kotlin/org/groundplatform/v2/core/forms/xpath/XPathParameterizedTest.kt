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
package org.groundplatform.v2.core.forms.xpath

import groundplatform.v2.forms.DataType
import groundplatform.v2.forms.GeoPoint
import groundplatform.v2.forms.SecondaryInstance
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.groundplatform.v2.core.forms.xpath.ast.XPathDependency
import org.groundplatform.v2.core.forms.xpath.model.XPathValue

/**
 * Data-driven parameterized test suite verifying XPath 1.0 and XForms specification compliance
 * against ProtoForms Protocol Buffer literals ([groundplatform.v2.forms.RecordInstance] &
 * [groundplatform.v2.forms.FormDef]).
 */
class XPathParameterizedTest {

  data class ParameterizedCase(
    val name: String,
    val expression: String,
    val contextPath: String? = null,
    val activeLanguage: String? = "English",
    val expectedBoolean: Boolean? = null,
    val expectedNumber: Double? = null,
    val expectedString: String? = null,
    val numberTolerance: Double = 1e-6,
    val customAssertion: ((XPathValue, EvaluationContext) -> Unit)? = null,
  ) {
    override fun toString(): String = "$name [$expression]"
  }

  companion object {

    // Shared rich RecordInstance fixture constructed via Kotlin Proto DSL
    val FIXTURE_RECORD =
      buildRecordInstance(
        formId = "household",
        version = "2026091701",
        instanceId = "uuid:8f7e6d5c-4b3a-2109-8765-4321fedcba98",
        deviceId = "android-pixel-9",
      ) {
        string("head_name", "Ada Lovelace")
        int32("household_size", 3)
        double("land_hectares", 4.5)
        bool("is_registered", true)
        date("schedule_date", 2026, 9, 17)
        time("visit_time", 12, 0, 0)
        string("empty_str", "")
        // Fields named after XPath keywords to test lexical disambiguation
        int32("div", 20)
        int32("mod", 4)
        int32("and", 3)
        multiSelect("crops", "coffee", "cocoa")

        // Geospatial fixtures
        geopoint("point_a", 0.0, 0.0)
        geopoint("point_b", 0.0, 1.0)
        geopoint("inside_pt", 0.5, 0.5)
        geopoint("outside_pt", 5.0, 5.0)
        // Simple 1-degree square near equator: (0,0) -> (0,1) -> (1,1) -> (1,0) -> (0,0)
        geoshape(
          "plot_polygon",
          GeoPoint(latitude = 0.0, longitude = 0.0),
          GeoPoint(latitude = 0.0, longitude = 1.0),
          GeoPoint(latitude = 1.0, longitude = 1.0),
          GeoPoint(latitude = 1.0, longitude = 0.0),
          GeoPoint(latitude = 0.0, longitude = 0.0),
        )
        // Self-intersecting bowtie polygon: (0,0) -> (1,1) -> (1,0) -> (0,1) -> (0,0)
        geoshape(
          "bowtie_polygon",
          GeoPoint(latitude = 0.0, longitude = 0.0),
          GeoPoint(latitude = 1.0, longitude = 1.0),
          GeoPoint(latitude = 1.0, longitude = 0.0),
          GeoPoint(latitude = 0.0, longitude = 1.0),
          GeoPoint(latitude = 0.0, longitude = 0.0),
        )

        group("location_group") {
          string("selected_country", "CH")
          string("city_choice", "ZRH")
        }

        // 1-level repeat: household members
        repeat("member") {
          item {
            string("name", "Alice")
            int32("age", 12)
            bool("active", true)
            string("nickname", "Ali")
          }
          item {
            string("name", "Bob")
            int32("age", 25)
            bool("active", false)
            string("nickname", "")
          }
          item {
            string("name", "Charlie")
            int32("age", 40)
            bool("active", true)
            string("nickname", "Chuck")
          }
        }

        // 2-level nested repeat: parcels -> trees
        repeat("parcel") {
          item {
            string("parcel_id", "P1")
            repeat("tree") {
              item { string("species", "Acacia") }
              item { string("species", "Mahogany") }
            }
          }
          item {
            string("parcel_id", "P2")
            repeat("tree") {
              item { string("species", "Oak") }
              item { string("species", "Teak") }
            }
          }
        }
      }

    val FIXTURE_FORM_DEF =
      buildTestFormDef(
        formId = "household",
        defaultLanguage = "English",
        secondaryInstances =
          listOf(
            SecondaryInstance(
              id = "cities",
              inline_data =
                """
                code,name,country
                ZRH,Zurich,CH
                GVA,Geneva,CH
                MUC,Munich,DE
                """
                  .trimIndent(),
            )
          ),
        translations =
          mapOf(
            "English" to mapOf("welcome_msg" to "Welcome to Ground", "cocoa_label" to "Cacao Tree"),
            "French" to mapOf("welcome_msg" to "Bienvenue sur Ground", "cocoa_label" to "Cacaoyer"),
          ),
        choicesByFieldRef =
          mapOf(
            "/household/crops" to
              listOf("coffee" to "Arabica Coffee", "cocoa" to "itext:cocoa_label")
          ),
      )

    fun createEvaluationContext(
      contextPath: String? = null,
      activeLanguage: String? = "English",
    ): EvaluationContext =
      EvaluationContext.fromRecordInstance(
        recordInstance = FIXTURE_RECORD,
        formDef = FIXTURE_FORM_DEF,
        contextPath = contextPath,
        activeLanguage = activeLanguage,
        // Fixed clock: 2026-09-17T00:00:00Z
        clockEpochMillis = { 1789603200000L },
        randomSeed = 12345L,
      )

    val ALL_CASES: List<ParameterizedCase> =
      listOf(
        // =========================================================================
        // 1. Arithmetic, Logical, Precedence & Disambiguation
        // =========================================================================
        ParameterizedCase(
          name = "Operator precedence: multiplication before addition",
          expression = "1 + 2 * 3",
          expectedNumber = 7.0,
        ),
        ParameterizedCase(
          name = "Parentheses override operator precedence",
          expression = "(1 + 2) * 3",
          expectedNumber = 9.0,
        ),
        ParameterizedCase(
          name = "XPath div and mod operators",
          expression = "10 div 4 + (10 mod 3)",
          expectedNumber = 3.5,
        ),
        ParameterizedCase(
          name = "Unary minus operator",
          expression = "-5 + 3 * -2",
          expectedNumber = -11.0,
        ),
        ParameterizedCase(
          name = "Logical AND and OR short-circuit operators",
          expression = "true() and false() or true()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "ProtoForms && and || operator aliases",
          expression = "1 < 2 && (3 > 5 || 10 = 10)",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Lexical disambiguation of div, mod, and as element names vs operators",
          expression = "/household/div div /household/mod * /household/and",
          expectedNumber = 15.0, // (20 / 4) * 3 = 15
        ),

        // =========================================================================
        // 2. Existential NodeSet & Type-Aware Comparisons
        // =========================================================================
        ParameterizedCase(
          name = "Existential equality on repeat field NodeSet (true when any matches)",
          expression = "/household/member/age = 25",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Existential inequality on repeat field NodeSet",
          expression = "/household/member/age > 35",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Existential comparison false when no node matches",
          expression = "/household/member/age < 10",
          expectedBoolean = false,
        ),
        ParameterizedCase(
          name = "XPath 1.0 Section 3.4: non-empty NodeSet compared to true() is true",
          expression = "/household/member/age = true()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "XPath 1.0 Section 3.4: empty NodeSet compared to false() is true",
          expression = "/household/non_existent_field = false()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Native DateVal comparison against ISO date string literal",
          expression = "/household/schedule_date = '2026-09-17'",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Native DateVal comparison against today()",
          expression = "/household/schedule_date >= today()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Date arithmetic in days (schedule_date - 7 days)",
          expression = "/household/schedule_date - date('2026-09-10')",
          expectedNumber = 7.0,
        ),

        // =========================================================================
        // 3. Path Addressing, Root Aliasing, Axes & Metadata
        // =========================================================================
        ParameterizedCase(
          name = "Absolute path using schema root name (/household/...)",
          expression = "/household/head_name",
          expectedString = "Ada Lovelace",
        ),
        ParameterizedCase(
          name = "Absolute path using generic XForms /data/... root alias",
          expression = "/data/head_name",
          expectedString = "Ada Lovelace",
        ),
        ParameterizedCase(
          name = "Metadata instanceID resolution via /data/meta/instanceID",
          expression = "/data/meta/instanceID",
          expectedString = "uuid:8f7e6d5c-4b3a-2109-8765-4321fedcba98",
        ),
        ParameterizedCase(
          name = "Metadata deviceID resolution",
          expression = "/household/meta/deviceID",
          expectedString = "android-pixel-9",
        ),
        ParameterizedCase(
          name = "Relative parent navigation (../name from member[2]/age)",
          expression = "../name",
          contextPath = "/household/member[2]/age",
          expectedString = "Bob",
        ),
        ParameterizedCase(
          name = "Relative multi-step upward navigation (../../head_name)",
          expression = "../../head_name",
          contextPath = "/household/member[2]/age",
          expectedString = "Ada Lovelace",
        ),
        ParameterizedCase(
          name = "XLSForm variable syntax \${head_name}",
          expression = "\${head_name}",
          expectedString = "Ada Lovelace",
        ),
        ParameterizedCase(
          name = "Descendant axis sum (descendant::age)",
          expression = "sum(/household/descendant::age)",
          expectedNumber = 77.0,
        ),
        ParameterizedCase(
          name = "Complex boolean predicate filtering repeat items (ODK nodeset-vs-boolean)",
          // `active = true()` is TRUE for every member that HAS an `active` node, because XPath
          // 1.0 §3.4 converts the node-set to a boolean by existence. So this filters on
          // `age > 15` alone and the first match is Bob, not Charlie. ODK Collect, Enketo and
          // Kobo all behave this way; see the `= 'true'` case below for the intended idiom.
          expression = "/household/member[age > 15 and active = true()]/name",
          expectedString = "Bob",
        ),
        ParameterizedCase(
          name = "ODK idiom: boolean field compared against the string 'true'",
          expression = "/household/member[age > 15 and active = 'true']/name",
          expectedString = "Charlie",
        ),
        ParameterizedCase(
          name = "ODK idiom: boolean field compared against the string 'false'",
          // Bob is the only member with active=false. Coercing 'false' to a boolean would make
          // this match nobody, since every non-empty string converts to true.
          expression = "/household/member[active = 'false']/name",
          expectedString = "Bob",
        ),
        ParameterizedCase(
          name = "Non-empty nodeset equals true() regardless of node value",
          expression = "/household/member[2]/active = true()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "Non-empty nodeset does not equal false()",
          expression = "/household/member[2]/active = false()",
          expectedBoolean = false,
        ),
        ParameterizedCase(
          name = "Missing node equals false() (empty nodeset converts to false)",
          expression = "/household/member[2]/no_such_field = false()",
          expectedBoolean = true,
        ),

        // =========================================================================
        // 4. Repeats, Aggregations & indexed-repeat()
        // =========================================================================
        ParameterizedCase(
          name = "count() on repeat nodeset",
          expression = "count(/household/member)",
          expectedNumber = 3.0,
        ),
        ParameterizedCase(
          name = "count-non-empty() ignores empty string repeat fields",
          expression = "count-non-empty(/household/member/nickname)",
          expectedNumber = 2.0,
        ),
        ParameterizedCase(
          name = "min() and max() across repeat items",
          expression = "max(/household/member/age) - min(/household/member/age)",
          expectedNumber = 28.0, // 40 - 12
        ),

        // =========================================================================
        // 4b. Node-set identity: duplicates removed, results in document order
        //
        // A node-set is a *set*. When several context nodes reach the same target,
        // the result must collapse to one node. These previously over-counted,
        // because XPathNode uses identity equality while traversal allocates a
        // fresh wrapper per visit, defeating every dedup attempt in the evaluator.
        // =========================================================================
        ParameterizedCase(
          name = "Parent step from repeat items collapses to a single node",
          // All 3 members share one parent. Returned 3 before the fix.
          expression = "count(/household/member/..)",
          expectedNumber = 1.0,
        ),
        ParameterizedCase(
          name = "Ancestor axis from repeat items collapses to a single node",
          // Likewise reached once per member. Returned 3 before the fix.
          expression = "count(/household/member/ancestor::household)",
          expectedNumber = 1.0,
        ),
        ParameterizedCase(
          name = "Two-level parent step from nested repeat collapses to a single node",
          // 4 trees across 2 parcels all climb to the same root. Returned 4 before.
          expression = "count(/household/parcel/tree/../..)",
          expectedNumber = 1.0,
        ),
        ParameterizedCase(
          name = "preceding-sibling across repeat items yields the union, not a concatenation",
          // member[1] has none, member[2] has {1}, member[3] has {1,2}; the union is
          // {member[1], member[2]} = 2. Concatenation gave 3.
          expression = "count(/household/member/preceding-sibling::member)",
          expectedNumber = 2.0,
        ),
        ParameterizedCase(
          name = "Union of a node-set with itself is idempotent",
          // linkedSetOf could not dedup identity-equal nodes, so this returned 6.
          expression = "count(/household/member | /household/member)",
          expectedNumber = 3.0,
        ),
        ParameterizedCase(
          name = "Union returns nodes in document order regardless of operand order",
          // string() takes the first node in *document order*, so listing member[3]
          // first must not change the answer. Returned "Charlie" before the fix.
          expression = "string(/household/member[3]/name | /household/member[1]/name)",
          expectedString = "Alice",
        ),
        // Controls: dedup must not merge genuinely distinct nodes, nor disturb order.
        ParameterizedCase(
          name = "Dedup preserves distinct children that share a parent",
          expression = "count(/household/member/name)",
          expectedNumber = 3.0,
        ),
        ParameterizedCase(
          name = "Dedup distinguishes same-positioned nodes under different parents",
          // tree[1]/tree[2] exist under both parcel[1] and parcel[2]; keying on the
          // full root-to-node index path must keep all 4 distinct.
          expression = "count(/household/parcel/tree)",
          expectedNumber = 4.0,
        ),
        ParameterizedCase(
          name = "Dedup preserves document order of repeat items",
          expression = "join(',', /household/member/name)",
          expectedString = "Alice,Bob,Charlie",
        ),
        ParameterizedCase(
          name = "position(..) inside repeat context",
          expression = "position(..)",
          contextPath = "/household/member[2]/name",
          expectedNumber = 2.0,
        ),
        ParameterizedCase(
          name = "last() inside repeat predicate",
          expression = "/household/member[position() = last()]/name",
          expectedString = "Charlie",
        ),
        ParameterizedCase(
          name = "indexed-repeat() 1-level repeat lookup",
          expression = "indexed-repeat(/household/member/name, /household/member, 2)",
          expectedString = "Bob",
        ),
        ParameterizedCase(
          name = "indexed-repeat() 2-level nested repeat lookup",
          expression =
            "indexed-repeat(/household/parcel/tree/species, /household/parcel, 2, /household/parcel/tree, 1)",
          expectedString = "Oak",
        ),

        // =========================================================================
        // 5. Secondary Instances, pulldata() & current() Predicate Pushdown
        // =========================================================================
        ParameterizedCase(
          name = "pulldata() O(1) indexed equality lookup",
          expression = "pulldata('cities', 'name', 'code', 'ZRH')",
          expectedString = "Zurich",
        ),
        ParameterizedCase(
          name = "pulldata() returns empty string for missing key",
          expression = "pulldata('cities', 'name', 'code', 'UNKNOWN')",
          expectedString = "",
        ),
        ParameterizedCase(
          name = "instance('cities') path traversal with equality predicate",
          expression = "instance('cities')/root/item[code = 'GVA']/name",
          expectedString = "Geneva",
        ),
        ParameterizedCase(
          name = "Cascading select filter using current() inside instance() predicate",
          expression =
            "count(instance('cities')/root/item[country = current()/../selected_country])",
          contextPath = "/household/location_group/city_choice",
          expectedNumber = 2.0, // ZRH and GVA match CH
        ),

        // =========================================================================
        // 6. String, Regex, Cryptographic Digest & UUID Functions
        // =========================================================================
        ParameterizedCase(
          name = "concat() combining literals and field values",
          expression =
            "concat('Survey: ', /household/head_name, ' (', /household/household_size, ')')",
          expectedString = "Survey: Ada Lovelace (3)",
        ),
        ParameterizedCase(
          name = "join() across repeat nodeset",
          expression = "join(' | ', /household/member/name)",
          expectedString = "Alice | Bob | Charlie",
        ),
        ParameterizedCase(
          name = "substr() with positive and negative indices",
          expression = "concat(substr('GroundPlatform', 0, 6), '-', substr('GroundPlatform', -8))",
          expectedString = "Ground-Platform",
        ),
        ParameterizedCase(
          name = "substring-before() and substring-after()",
          expression =
            "concat(substring-before('2026-09-17', '-'), '/', substring-after('2026-09-17', '-'))",
          expectedString = "2026/09-17",
        ),
        ParameterizedCase(
          name = "translate() character mapping and deletion",
          expression = "translate('12:30:45', ':0', '-X')",
          expectedString = "12-3X-45",
        ),
        ParameterizedCase(
          name = "normalize-space() whitespace collapsing",
          expression = "normalize-space('   Open   Foris \n  Ground   ')",
          expectedString = "Open Foris Ground",
        ),
        ParameterizedCase(
          name = "starts-with(), ends-with(), and contains()",
          expression =
            "starts-with(/data/meta/instanceID, 'uuid:') and contains(/household/head_name, 'Lovelace') and ends-with('plot.geojson', '.geojson')",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "regex() pattern matching true and false cases",
          expression =
            "regex('GH-2026-01', '^[A-Z]{2}-[0-9]{4}-[0-9]{2}$') and not(regex('123', '[a-z]+'))",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "digest() MD5 hex calculation",
          expression = "digest('hello world', 'MD5', 'hex')",
          expectedString = "5eb63bbbe01eeed093cb22bb8f5acdc3",
        ),
        ParameterizedCase(
          name = "digest() SHA-256 hex calculation",
          expression = "digest('hello world', 'SHA-256', 'hex')",
          expectedString = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9",
        ),
        ParameterizedCase(
          name = "digest() SHA-384 hex calculation",
          expression = "digest('hello world', 'SHA-384', 'hex')",
          expectedString =
            "fdbd8e75a67f29f701a4e040385e2e23986303ea10239211af907fcbb83578b3e417cb71ce646efd0819dd8c088de1bd",
        ),
        ParameterizedCase(
          name = "digest() SHA-512 base64 calculation",
          expression = "digest('hello world', 'SHA-512', 'base64')",
          expectedString =
            "MJ7MSJwS1utMxA9QyQLytNDtd+5RGnx6m808qG1M2G+YndNbxf9JlnDaNCVbRbDP2DDoH2Bdz33FVC6TrpzXbw==",
        ),
        ParameterizedCase(
          name = "base64-decode() decoding UTF-8 text",
          expression = "base64-decode('SGVsbG8gR3JvdW5k')",
          expectedString = "Hello Ground",
        ),
        ParameterizedCase(
          name = "uuid() RFC 4122 format and fixed-length alphanumeric token",
          expression = "string-length(uuid()) = 36 and string-length(uuid(16)) = 16",
          expectedBoolean = true,
        ),

        // =========================================================================
        // 7. Boolean, Control-Flow (if, once, coalesce) & Checklists
        // =========================================================================
        ParameterizedCase(
          name = "if() lazy branch evaluation and rich DateVal type preservation",
          expression = "if(/household/is_registered, /household/schedule_date, 'fallback')",
          expectedString = "2026-09-17",
          customAssertion = { result, _ ->
            assertTrue(
              result.unwrapNodeSet() is XPathValue.DateVal,
              "Expected DateVal preserved through if()",
            )
            assertEquals(2026, result.toTypedValue()?.date_value?.year)
          },
        ),
        ParameterizedCase(
          name = "coalesce() returns first non-empty value",
          expression = "coalesce(/household/empty_str, '', /household/head_name)",
          expectedString = "Ada Lovelace",
        ),
        ParameterizedCase(
          name = "once() retains existing non-empty value on field",
          expression = "once('New Name')",
          contextPath = "/household/head_name",
          expectedString = "Ada Lovelace",
        ),
        ParameterizedCase(
          name = "once() computes new value when current context field is empty",
          expression = "once('Computed Default')",
          contextPath = "/household/empty_str",
          expectedString = "Computed Default",
        ),
        ParameterizedCase(
          name = "boolean-from-string() strict XForms rules ('true' and '1' only)",
          expression =
            "boolean-from-string('true') and boolean-from-string('1') and not(boolean-from-string('false')) and not(boolean-from-string('yes'))",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "checklist() bounds check",
          expression = "checklist(2, 3, true(), false(), true(), true())",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "weighted-checklist() sum of truthy weights",
          expression = "weighted-checklist(5, 10, true(), 3.5, false(), 10, true(), 4.0)",
          expectedBoolean = true, // 3.5 + 4.0 = 7.5 in [5, 10]
        ),

        // =========================================================================
        // 8. Date, Time & Mathematical Functions
        // =========================================================================
        ParameterizedCase(
          name = "format-date() with XForms pattern tokens (%Y, %m, %d, %a, %b, %e)",
          expression = "format-date(/household/schedule_date, '%Y-%m-%d (%a, %b %e)')",
          expectedString = "2026-09-17 (Thu, Sep 17)",
        ),
        ParameterizedCase(
          name = "format-date-time() with millisecond precision (%H, %M, %S, %3)",
          expression = "format-date-time('2026-09-17T14:05:09.125Z', '%Y/%m/%d %H:%M:%S.%3')",
          expectedString = "2026/09/17 14:05:09.125",
        ),
        ParameterizedCase(
          name = "decimal-time() converts 12:00:00 to 0.5 days",
          expression = "decimal-time(/household/visit_time)",
          expectedNumber = 0.5,
        ),
        ParameterizedCase(
          name = "Mathematical functions: round, pow, abs, sqrt, int",
          expression = "round(sqrt(pow(3, 2) + pow(4, 2)) + abs(-1.234), 2)",
          expectedNumber = 6.23, // 5 + 1.234 = 6.234 -> 6.23
        ),
        ParameterizedCase(
          name = "Trigonometric identity sin^2(x) + cos^2(x) = 1",
          expression = "round(pow(sin(0.7), 2) + pow(cos(0.7), 2), 6)",
          expectedNumber = 1.0,
        ),

        // =========================================================================
        // 9. Multi-Select (ValueList), Translations (jr:itext) & jr:choice-name
        // =========================================================================
        ParameterizedCase(
          name = "selected() on native ValueList multi-select field",
          expression =
            "selected(/household/crops, 'cocoa') and not(selected(/household/crops, 'tea'))",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "selected-at() 0-based index on multi-select field",
          expression = "selected-at(/household/crops, 1)",
          expectedString = "cocoa",
        ),
        ParameterizedCase(
          name = "count-selected() on multi-select field",
          expression = "count-selected(/household/crops)",
          expectedNumber = 2.0,
        ),
        ParameterizedCase(
          name = "jr:itext() lookup in default English language",
          expression = "jr:itext('welcome_msg')",
          activeLanguage = "English",
          expectedString = "Welcome to Ground",
        ),
        ParameterizedCase(
          name = "jr:itext() lookup in French translation catalog",
          expression = "jr:itext('welcome_msg')",
          activeLanguage = "French",
          expectedString = "Bienvenue sur Ground",
        ),
        ParameterizedCase(
          name = "jr:choice-name() resolves localized itext choice label",
          expression = "jr:choice-name('cocoa', '/household/crops')",
          activeLanguage = "French",
          expectedString = "Cacaoyer",
        ),

        // =========================================================================
        // 10. Geospatial Functions (distance, area, geofence, intersects)
        // =========================================================================
        ParameterizedCase(
          name = "2-argument distance(point_a, point_b) along equator (~111.32 km for 1 deg)",
          expression = "round(distance(/household/point_a, /household/point_b) div 1000, 1)",
          expectedNumber = 111.3,
          numberTolerance = 0.2,
        ),
        ParameterizedCase(
          name = "1-argument distance(plot_polygon) perimeter in meters",
          expression = "distance(/household/plot_polygon) > 400000",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "area(plot_polygon) spherical area in square meters",
          expression = "area(/household/plot_polygon) > 10000000000",
          expectedBoolean = true, // 1 deg x 1 deg square is ~1.239e10 m^2
        ),
        ParameterizedCase(
          name = "geofence() point-in-polygon inclusion (inside point)",
          expression = "geofence(/household/inside_pt, /household/plot_polygon)",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "geofence() point-in-polygon exclusion (outside point)",
          expression = "geofence(/household/outside_pt, /household/plot_polygon)",
          expectedBoolean = false,
        ),
        ParameterizedCase(
          name = "intersects() detects self-intersection on figure-8 bowtie polygon",
          expression =
            "intersects(/household/bowtie_polygon) and not(intersects(/household/plot_polygon))",
          expectedBoolean = true,
        ),

        // =========================================================================
        // 11. Deterministic Seeded Randomize (Park-Miller PRNG)
        // =========================================================================
        ParameterizedCase(
          name = "randomize() with numeric seed produces deterministic order",
          expression =
            "join(',', randomize(/household/member/name, 42)) = join(',', randomize(/household/member/name, 42))",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "randomize() with string seed hashes via SHA-256 deterministically",
          expression =
            "join(',', randomize(/household/member/name, 'seed-alpha')) = join(',', randomize(/household/member/name, 'seed-alpha'))",
          expectedBoolean = true,
        ),

        // =========================================================================
        // 12. Spec-conformance regressions
        // =========================================================================

        // XPath 1.0 §4.4: round() returns the number closest to the argument that
        // is an integer; if two are equally close, the one closest to POSITIVE
        // infinity wins. So round(-1.5) is -1, not -2.
        ParameterizedCase(
          name = "round() breaks ties toward positive infinity (negative half)",
          expression = "round(-1.5)",
          expectedNumber = -1.0,
        ),
        ParameterizedCase(
          name = "round() breaks ties toward positive infinity (positive half)",
          expression = "round(1.5)",
          expectedNumber = 2.0,
        ),
        ParameterizedCase(
          name = "round() rounds negative non-ties to nearest",
          expression = "round(-1.6)",
          expectedNumber = -2.0,
        ),
        ParameterizedCase(
          name = "round() with places argument breaks ties toward positive infinity",
          expression = "round(-1.25, 1)",
          expectedNumber = -1.2,
        ),

        // XPath 1.0 §4.2: substring() returns characters whose position is
        // >= round(start) and < round(start) + round(length). Infinite and
        // out-of-range bounds must degrade gracefully, not underflow to "".
        ParameterizedCase(
          name = "substring() with -Infinity start returns whole string",
          expression = "substring('12345', -1 div 0)",
          expectedString = "12345",
        ),
        ParameterizedCase(
          name = "substring() with negative start and infinite length returns whole string",
          expression = "substring('12345', -42, 1 div 0)",
          expectedString = "12345",
        ),
        ParameterizedCase(
          name = "substring() with huge finite length does not overflow to empty",
          expression = "substring('12345', 2, 100000000000)",
          expectedString = "2345",
        ),
        ParameterizedCase(
          name = "substring() with NaN start returns empty string",
          expression = "substring('12345', 0 div 0)",
          expectedString = "",
        ),

        // XPath 1.0 §4.3: string(-0) is "-0". Kotlin's `-0.0 == 0.0` is true, so a
        // naive zero check loses the sign.
        ParameterizedCase(
          name = "negative zero formats as -0 per string() rules",
          expression = "string(-0.0)",
          expectedString = "-0",
        ),
        ParameterizedCase(
          name = "positive zero still formats as 0",
          expression = "string(0.0)",
          expectedString = "0",
        ),

        // XPath 1.0 §2.4: predicates on a reverse axis are numbered by proximity
        // position, counting back from the context node. member[3] is Charlie, so
        // the nearest preceding sibling is Bob -- not Alice.
        ParameterizedCase(
          name = "preceding-sibling::[1] selects the nearest preceding sibling",
          expression = "/household/member[3]/preceding-sibling::member[1]/name",
          expectedString = "Bob",
        ),
        ParameterizedCase(
          name = "preceding-sibling::[2] selects the second-nearest preceding sibling",
          expression = "/household/member[3]/preceding-sibling::member[2]/name",
          expectedString = "Alice",
        ),
        ParameterizedCase(
          name = "following-sibling::[1] still selects the nearest following sibling",
          expression = "/household/member[1]/following-sibling::member[1]/name",
          expectedString = "Bob",
        ),

        // XPath 1.0 §3.7: after a `*` that was parsed as a NameTest, a following NCName such as
        // `div`/`mod`/`and`/`or` is an OperatorName, not a name test. These expressions fail to
        // parse at all if the lexer treats the operator as an element name.
        ParameterizedCase(
          name = "'and' is an operator directly after a wildcard name test",
          expression = "/household/member[1]/* and true()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "'or' is an operator directly after a wildcard name test",
          expression = "/household/member[1]/* or false()",
          expectedBoolean = true,
        ),
        ParameterizedCase(
          name = "'*' is still a multiply operator between two operands",
          expression = "6 * 7",
          expectedNumber = 42.0,
        ),
      )
  }

  @Test
  fun testAllParameterizedUseCasesAndEdgeCases() {
    for (case in ALL_CASES) {
      val context =
        createEvaluationContext(
          contextPath = case.contextPath,
          activeLanguage = case.activeLanguage,
        )
      val compiled = XPathEngine.compile(case.expression)
      val result = compiled.evaluate(context)

      case.expectedBoolean?.let { expected ->
        assertEquals(
          expected = expected,
          actual = result.toBoolean(),
          message = "Boolean mismatch in case: ${case.name} (expr: ${case.expression})",
        )
      }

      case.expectedNumber?.let { expected ->
        val actual = result.toNumber()
        assertTrue(
          abs(expected - actual) <= case.numberTolerance,
          "Number mismatch in case: ${case.name} (expr: ${case.expression}) -> expected $expected, got $actual",
        )
      }

      case.expectedString?.let { expected ->
        assertEquals(
          expected = expected,
          actual = result.toXPathString(),
          message = "String mismatch in case: ${case.name} (expr: ${case.expression})",
        )
      }

      case.customAssertion?.invoke(result, context)
    }
  }

  @Test
  fun testStaticDependencyExtractionForReactiveDags() {
    val expr =
      XPathEngine.compile(
        "if(\${has_shade} = 'yes', pulldata('trees', 'factor', 'code', ../species) * sum(/household/parcel/area), 0)"
      )
    val deps = expr.dependencies

    assertTrue(
      deps.contains(XPathDependency.FieldDependency("has_shade", isAbsolute = false)),
      "Should extract XLSForm variable dependency",
    )
    assertTrue(
      deps.contains(XPathDependency.SecondaryInstanceDependency("trees")),
      "Should extract secondary instance dependency from pulldata()",
    )
    assertTrue(
      deps.contains(XPathDependency.FieldDependency("../species", isAbsolute = false)),
      "Should extract relative field dependency",
    )
    assertTrue(
      deps.contains(XPathDependency.FieldDependency("/household/parcel/area", isAbsolute = true)),
      "Should extract absolute field dependency",
    )
    assertTrue(
      deps.contains(XPathDependency.RepeatContextDependency),
      "Should detect upward parent '../' repeat context dependency",
    )
  }

  @Test
  fun testLosslessTypedValueConversionForCalculateExpressions() {
    val context = createEvaluationContext()

    val intTv = XPathEngine.evaluateTypedValue("10 + 32", context, DataType.TYPE_INT32)
    assertEquals(42, intTv?.int32_value)

    val doubleTv = XPathEngine.evaluateTypedValue("4.5 * 2", context, DataType.TYPE_DOUBLE)
    assertEquals(9.0, doubleTv?.double_value)

    val boolTv =
      XPathEngine.evaluateTypedValue(
        "/household/household_size > 1",
        context,
        DataType.TYPE_BOOLEAN,
      )
    assertEquals(true, boolTv?.bool_value)

    val dateTv =
      XPathEngine.evaluateTypedValue("/household/schedule_date", context, DataType.TYPE_DATE)
    assertNotNull(dateTv?.date_value)
    assertEquals(2026, dateTv.date_value.year)
    assertEquals(9, dateTv.date_value.month)
    assertEquals(17, dateTv.date_value.day)

    val multiSelectFv =
      XPathEngine.evaluateFieldValue("/household/crops", context, DataType.TYPE_SELECT_MULTIPLE)
    assertEquals(2, multiSelectFv?.list_value?.values?.size)
  }

  @Test
  fun testSyntaxErrorDiagnosticsAndEdgeCases() {
    // Empty expression throws XPathSyntaxException
    assertFailsWith<XPathSyntaxException> { XPathEngine.compile("   ") }
    // Unclosed string literal
    assertFailsWith<XPathSyntaxException> { XPathEngine.compile("concat('hello, 'world')") }
    // Unbalanced brackets
    assertFailsWith<XPathSyntaxException> { XPathEngine.compile("/household/member[age > 18/name") }
    // Unknown function throws XPathEvaluationException at runtime
    assertFailsWith<XPathEvaluationException> {
      XPathEngine.evaluate("non_existent_func(1, 2)", createEvaluationContext())
    }
  }
}
