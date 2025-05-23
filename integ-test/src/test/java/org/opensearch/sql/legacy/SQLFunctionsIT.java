/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.sql.legacy;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.both;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasValue;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.opensearch.sql.legacy.TestsConstants.TEST_INDEX_ACCOUNT;
import static org.opensearch.sql.util.MatcherUtils.hitAny;
import static org.opensearch.sql.util.MatcherUtils.kvDouble;
import static org.opensearch.sql.util.MatcherUtils.kvInt;
import static org.opensearch.sql.util.MatcherUtils.kvString;
import static org.opensearch.sql.util.MatcherUtils.rows;
import static org.opensearch.sql.util.MatcherUtils.schema;
import static org.opensearch.sql.util.MatcherUtils.verifyDataRows;
import static org.opensearch.sql.util.MatcherUtils.verifySchema;

import com.fasterxml.jackson.core.JsonFactory;
import java.io.IOException;
import java.util.Date;
import java.util.stream.IntStream;
import org.hamcrest.collection.IsMapContaining;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.json.JsonXContentParser;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentParser;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

@Ignore(
    "OpenSearch DSL format is deprecated in 3.0.0. Ignore legacy IT that relies on json format"
        + " response for now. Need to decide what to do with these test cases.")
/** Created by allwefantasy on 8/25/16. */
public class SQLFunctionsIT extends SQLIntegTestCase {

  @Override
  protected void init() throws Exception {
    loadIndex(Index.ACCOUNT);
    loadIndex(Index.BANK);
    loadIndex(Index.ONLINE);
    loadIndex(Index.DATE);
  }

  @Test
  public void functionFieldAliasAndGroupByAlias() throws Exception {
    String query =
        "SELECT "
            + "floor(substring(address,0,3)*20) as key,"
            + "sum(age) cvalue FROM "
            + TEST_INDEX_ACCOUNT
            + " where address is not null "
            + "group by key order by cvalue desc limit 10  ";
    final JSONObject result = executeQuery(query);

    IntStream.rangeClosed(0, 9)
        .forEach(
            i -> {
              Assert.assertNotNull(
                  result.query(String.format("/aggregations/key/buckets/%d/key", i)));
              Assert.assertNotNull(
                  result.query(String.format("/aggregations/key/buckets/%d/cvalue/value", i)));
            });
  }

  /**
   * todo fix the issue.
   *
   * @see <a
   *     href="https://github.com/opendistro-for-elasticsearch/sql/issues/59">https://github.com/opendistro-for-elasticsearch/sql/issues/59</a>
   */
  @Ignore
  public void normalFieldAlias() throws Exception {

    // here is a bug,csv field with spa
    String query =
        "SELECT "
            + "address as key,age from "
            + TEST_INDEX_ACCOUNT
            + " where address is not null "
            + "limit 10  ";

    assertThat(executeQuery(query), hitAny(kvString("/_source/key", not(isEmptyOrNullString()))));
  }

  @Test
  public void functionAlias() throws Exception {
    // here is a bug,if only script fields are included,then all fields will return; fix later
    String query =
        "SELECT "
            + "substring(address,0,3) as key,address from "
            + TEST_INDEX_ACCOUNT
            + " where address is not null "
            + "order by address desc limit 10  ";

    assertThat(
        executeQuery(query),
        hitAny(
            both(kvString("/_source/address", equalTo("863 Wythe Place")))
                .and(kvString("/fields/key/0", equalTo("863")))));
  }

  @Test
  public void caseChangeTest() throws IOException {
    String query =
        "SELECT LOWER(firstname) "
            + "FROM opensearch-sql_test_index_account "
            + "WHERE UPPER(lastname)='DUKE' "
            + "ORDER BY upper(lastname) ";

    assertThat(
        executeQuery(query),
        hitAny(
            kvString("/_source/address", equalTo("880 Holmes Lane")),
            kvString("/fields/LOWER(firstname)/0", equalTo("amber"))));
  }

  @Test
  public void caseChangeTestWithLocale() throws IOException {
    // Uses Turkish locale to check if we pass correct locale for case changing functions
    // "IL".toLowerCase() in a Turkish locale returns "ıl"
    // https://stackoverflow.com/questions/11063102/using-locales-with-javas-tolowercase-and-touppercase

    String query =
        "SELECT LOWER(state.keyword, 'tr') "
            + "FROM opensearch-sql_test_index_account "
            + "WHERE account_number=1";

    assertThat(
        executeQuery(query),
        hitAny(kvString("/fields/LOWER(state.keyword, 'tr')/0", equalTo("ıl"))));
  }

  @Test
  public void caseChangeWithAggregationTest() throws IOException {
    String query =
        "SELECT UPPER(e.firstname) AS upper, COUNT(*)"
            + "FROM opensearch-sql_test_index_account e "
            + "WHERE LOWER(e.lastname)='duke' "
            + "GROUP BY upper";

    assertThat(
        executeQuery(query),
        hitAny("/aggregations/upper/buckets", kvString("/key", equalTo("AMBER"))));
  }

  @Test
  public void castIntFieldToDoubleWithoutAliasTest() throws IOException {
    String query =
        "SELECT CAST(age AS DOUBLE) FROM "
            + TestsConstants.TEST_INDEX_ACCOUNT
            + " ORDER BY age DESC LIMIT 5";

    SearchHit[] hits = query(query).getHits();
    checkSuccessfulFieldCast(hits, "cast_age", "DOUBLE");
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields().get("cast_age").getValue(), is(40.0));
    }
  }

  @Test
  public void castIntFieldToDoubleWithAliasTest() throws IOException {
    String query =
        "SELECT CAST(age AS DOUBLE) AS test_alias FROM "
            + TestsConstants.TEST_INDEX_ACCOUNT
            + " ORDER BY age LIMIT 5";

    SearchHit[] hits = query(query).getHits();
    checkSuccessfulFieldCast(hits, "test_alias", "DOUBLE");
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields().get("test_alias").getValue(), is(20.0));
    }
  }

  @Test
  public void castIntFieldToStringWithoutAliasTest() throws IOException {
    String query =
        "SELECT CAST(balance AS STRING) FROM "
            + TestsConstants.TEST_INDEX_ACCOUNT
            + " ORDER BY balance LIMIT 1";

    SearchHit[] hits = query(query).getHits();
    checkSuccessfulFieldCast(hits, "cast_balance", "STRING");
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields().get("cast_balance").getValue(), is("1011"));
    }
  }

  @Test
  public void castIntFieldToStringWithAliasTest() throws IOException {
    String query =
        "SELECT CAST(balance AS STRING) AS cast_string_alias FROM "
            + TestsConstants.TEST_INDEX_ACCOUNT
            + " ORDER BY cast_string_alias DESC LIMIT 1";

    SearchHit[] hits = query(query).getHits();
    checkSuccessfulFieldCast(hits, "cast_string_alias", "STRING");
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields().get("cast_string_alias").getValue(), is("9838"));
    }
  }

  @Test
  public void castIntFieldToFloatWithoutAliasJdbcFormatTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(balance AS FLOAT) AS cast_balance FROM "
                + TestsConstants.TEST_INDEX_ACCOUNT
                + " ORDER BY balance DESC LIMIT 1");

    verifySchema(response, schema("CAST(balance AS FLOAT)", "cast_balance", "float"));

    verifyDataRows(response, rows(49989.0));
  }

  @Test
  public void castIntFieldToFloatWithAliasJdbcFormatTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(balance AS FLOAT) AS jdbc_float_alias "
                + "FROM "
                + TestsConstants.TEST_INDEX_ACCOUNT
                + " ORDER BY jdbc_float_alias LIMIT 1");

    verifySchema(response, schema("CAST(balance AS FLOAT)", "jdbc_float_alias", "float"));

    verifyDataRows(response, rows(1011.0));
  }

  @Test
  public void castIntFieldToDoubleWithoutAliasOrderByTest() throws IOException {
    String query =
        "SELECT CAST(age AS DOUBLE) FROM "
            + TestsConstants.TEST_INDEX_ACCOUNT
            + " ORDER BY age LIMIT 1";

    SearchHit[] hits = query(query).getHits();
    checkSuccessfulFieldCast(hits, "cast_age", "DOUBLE");
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields().get("cast_age").getValue(), is(20.0));
    }
  }

  @Test
  public void castIntFieldToDoubleWithAliasOrderByTest() throws IOException {
    String query =
        "SELECT CAST(age AS DOUBLE) AS alias FROM "
            + TestsConstants.TEST_INDEX_ACCOUNT
            + " ORDER BY alias DESC LIMIT 1";

    SearchHit[] hits = query(query).getHits();
    checkSuccessfulFieldCast(hits, "alias", "DOUBLE");
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields().get("alias").getValue(), is(40.0));
    }
  }

  @Test
  public void castIntFieldToFloatWithoutAliasJdbcFormatGroupByTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(balance AS FLOAT) FROM "
                + TestsConstants.TEST_INDEX_ACCOUNT
                + " GROUP BY balance ORDER BY balance DESC LIMIT 5");

    verifySchema(response, schema("CAST(balance AS FLOAT)", null, "float"));

    verifyDataRows(
        response, rows(49989.0), rows(49795.0), rows(49741.0), rows(49671.0), rows(49587.0));
  }

  @Test
  public void castIntFieldToFloatWithAliasJdbcFormatGroupByTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(balance AS FLOAT) AS jdbc_float_alias "
                + " FROM "
                + TestsConstants.TEST_INDEX_ACCOUNT
                + " GROUP BY jdbc_float_alias "
                + " ORDER BY jdbc_float_alias ASC "
                + " LIMIT 5");

    verifySchema(response, schema("CAST(balance AS FLOAT)", "jdbc_float_alias", "float"));

    verifyDataRows(
        response, rows(1011.0), rows(10116.0), rows(10138.0), rows(10147.0), rows(10178.0));
  }

  @Test
  public void castIntFieldToDoubleWithAliasJdbcFormatGroupByTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(age AS DOUBLE) AS jdbc_double_alias "
                + "FROM "
                + TestsConstants.TEST_INDEX_ACCOUNT
                + " GROUP BY jdbc_double_alias DESC LIMIT 5");

    verifySchema(response, schema("jdbc_double_alias", "jdbc_double_alias", "double"));

    verifyDataRows(response, rows("31.0"), rows("39.0"), rows("26.0"), rows("32.0"), rows("35.0"));
  }

  @Test
  public void castKeywordFieldToDatetimeWithoutAliasJdbcFormatTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(date_keyword AS DATETIME) FROM "
                + TestsConstants.TEST_INDEX_DATE
                + " ORDER BY date_keyword");

    verifySchema(response, schema("cast_date_keyword", null, "date"));

    verifyDataRows(response, rows("2014-08-19 07:09:13.434"), rows("2019-09-25 02:04:13.469"));
  }

  @Test
  public void castKeywordFieldToDatetimeWithAliasJdbcFormatTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(date_keyword AS DATETIME) AS test_alias FROM "
                + TestsConstants.TEST_INDEX_DATE
                + " ORDER BY date_keyword");

    verifySchema(response, schema("test_alias", null, "date"));

    verifyDataRows(response, rows("2014-08-19 07:09:13.434"), rows("2019-09-25 02:04:13.469"));
  }

  @Test
  public void castFieldToDatetimeWithWhereClauseJdbcFormatTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(date_keyword AS DATETIME) FROM "
                + TestsConstants.TEST_INDEX_DATE
                + " WHERE date_keyword IS NOT NULL ORDER BY date_keyword");

    verifySchema(response, schema("cast_date_keyword", null, "date"));

    verifyDataRows(response, rows("2014-08-19 07:09:13.434"), rows("2019-09-25 02:04:13.469"));
  }

  @Test
  public void castFieldToDatetimeWithGroupByJdbcFormatTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT CAST(date_keyword AS DATETIME) AS test_alias FROM "
                + TestsConstants.TEST_INDEX_DATE
                + " GROUP BY test_alias DESC");

    verifySchema(response, schema("test_alias", "test_alias", "double"));

    verifyDataRows(response, rows("2014-08-19T07:09:13.434Z"), rows("2019-09-25T02:04:13.469Z"));
  }

  @Test
  public void castBoolFieldToNumericValueInSelectClause() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT "
                + " male, "
                + " CAST(male AS INT) AS cast_int, "
                + " CAST(male AS LONG) AS cast_long, "
                + " CAST(male AS FLOAT) AS cast_float, "
                + " CAST(male AS DOUBLE) AS cast_double "
                + "FROM "
                + TestsConstants.TEST_INDEX_BANK
                + " "
                + "WHERE account_number = 1 OR account_number = 13");

    verifySchema(
        response,
        schema("male", "boolean"),
        schema("CAST(male AS INT)", "cast_int", "integer"),
        schema("CAST(male AS LONG)", "cast_long", "long"),
        schema("CAST(male AS FLOAT)", "cast_float", "float"),
        schema("CAST(male AS DOUBLE)", "cast_double", "double"));
    verifyDataRows(response, rows(true, 1, 1, 1.0, 1.0), rows(false, 0, 0, 0.0, 0.0));
  }

  @Test
  public void castBoolFieldToNumericValueWithGroupByAlias() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT "
                + "CAST(male AS INT) AS cast_int, "
                + "COUNT(*) "
                + "FROM "
                + TestsConstants.TEST_INDEX_BANK
                + " "
                + "GROUP BY cast_int");

    verifySchema(
        response,
        schema("CAST(male AS INT)", "cast_int", "integer"),
        schema("COUNT(*)", "integer"));
    verifyDataRows(response, rows(0, 3), rows(1, 4));
  }

  @Test
  public void castStatementInWhereClauseGreaterThanTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT balance FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE (account_number < CAST(age AS DOUBLE)) ORDER BY balance LIMIT 5");

    verifySchema(response, schema("balance", null, "long"));

    verifyDataRows(response, rows(4180), rows(5686), rows(7004), rows(7831), rows(14127));
  }

  @Test
  public void castStatementInWhereClauseLessThanTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT balance FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE (account_number > CAST(age AS DOUBLE)) ORDER BY balance LIMIT 5");

    verifySchema(response, schema("balance", null, "long"));

    verifyDataRows(response, rows(1011), rows(1031), rows(1110), rows(1133), rows(1172));
  }

  @Test
  public void castStatementInWhereClauseEqualToConstantTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT balance FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE (CAST(age AS DOUBLE) = 36.0) ORDER BY balance LIMIT 5");

    verifySchema(response, schema("balance", null, "long"));
    verifyDataRows(response, rows(1249), rows(1463), rows(3960), rows(5686), rows(6025));
  }

  @Test
  public void castStatementInWhereClauseLessThanConstantTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT balance FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE (CAST(age AS DOUBLE) < 36.0) ORDER BY balance LIMIT 5");

    verifySchema(response, schema("balance", null, "long"));

    verifyDataRows(response, rows(1011), rows(1031), rows(1110), rows(1133), rows(1172));
  }

  /**
   * Testing compilation Result comparison is empty then comparing different types (Date and
   * keyword)
   */
  @Test
  public void castStatementInWhereClauseDatetimeCastTest() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT date_keyword FROM "
                + TestsConstants.TEST_INDEX_DATE
                + " WHERE (CAST(date_keyword AS DATETIME) = '2014-08-19T07:09:13.434Z')");

    String schema_result = "{\"name\":\"date_keyword\",\"type\":\"keyword\"}";
    assertEquals(response.getJSONArray("schema").get(0).toString(), schema_result);
  }

  @Test
  public void concat_ws_field_and_string() throws Exception {
    // here is a bug,csv field with spa
    String query =
        "SELECT "
            + " concat_ws('-',age,'-') as age,address from "
            + TEST_INDEX_ACCOUNT
            + " "
            + " limit 10  ";

    assertThat(executeQuery(query), hitAny(kvString("/fields/age/0", endsWith("--"))));
  }

  /**
   * Ignore this test case because painless doesn't allowlist String.split function.
   *
   * @see <a
   *     href="https://www.elastic.co/guide/en/elasticsearch/painless/7.0/painless-api-reference.html">https://www.elastic.co/guide/en/elasticsearch/painless/7.0/painless-api-reference.html</a>
   */
  @Ignore
  public void whereConditionLeftFunctionRightVariableEqualTest() throws Exception {

    String query =
        "SELECT "
            + " * from "
            + TestsConstants.TEST_INDEX
            + " "
            + " where split(address,' ')[0]='806' limit 1000  ";

    assertThat(executeQuery(query).query("/hits/total"), equalTo(4));
  }

  /**
   * Ignore this test case because painless doesn't allowlist String.split function.
   *
   * @see <a
   *     href="https://www.elastic.co/guide/en/elasticsearch/painless/7.0/painless-api-reference.html">https://www.elastic.co/guide/en/elasticsearch/painless/7.0/painless-api-reference.html</a>
   */
  @Ignore
  public void whereConditionLeftFunctionRightVariableGreatTest() throws Exception {

    String query =
        "SELECT "
            + " * from "
            + TestsConstants.TEST_INDEX
            + " "
            + " where floor(split(address,' ')[0]+0) > 805 limit 1000  ";

    assertThat(executeQuery(query).query("/hits/total"), equalTo(223));
  }

  @Test
  public void concat_ws_fields() throws Exception {

    // here is a bug,csv field with spa
    String query =
        "SELECT "
            + " concat_ws('-',age,address) as combine,address from "
            + TEST_INDEX_ACCOUNT
            + " "
            + " limit 10  ";
    assertThat(executeQuery(query), hitAny(kvString("/fields/combine/0", containsString("-"))));
  }

  @Test
  public void functionLogs() throws Exception {
    String query =
        "SELECT log10(100) as a, log(1) as b, log(2, 4) as c, log2(8) as d from "
            + TEST_INDEX_ACCOUNT
            + " limit 1";

    assertThat(
        executeQuery(query),
        hitAny(
            both(kvDouble("/fields/a/0", equalTo(Math.log10(100))))
                .and(kvDouble("/fields/b/0", equalTo(Math.log(1))))
                .and(kvDouble("/fields/c/0", closeTo(Math.log(4) / Math.log(2), 0.0001)))
                .and(kvDouble("/fields/d/0", closeTo(Math.log(8) / Math.log(2), 0.0001)))));
  }

  @Test
  public void functionPow() throws Exception {
    String query =
        "SELECT pow(account_number, 2) as key,"
            + "abs(age - 60) as new_age from "
            + TEST_INDEX_ACCOUNT
            + " WHERE firstname = 'Virginia' and lastname='Ayala' limit 1";

    assertThat(
        executeQuery(query),
        hitAny(
            both(kvDouble("/fields/new_age/0", equalTo(21.0)))
                .and(kvDouble("/fields/key/0", equalTo(625.0)))));
  }

  @Test
  public void operatorSubstring() throws IOException {
    assertThat(
        executeQuery(
            "SELECT substring('sampleName', 1, 4) AS substring FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/substring/0", equalTo("samp"))));

    assertThat(
        executeQuery(
            "SELECT substring('sampleName', 0, 20) AS substring FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/substring/0", equalTo("sampleName"))));
  }

  @Test
  public void operatorLength() throws IOException {
    assertThat(
        executeQuery(
            "SELECT LENGTH(lastname) FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE lastname IS NOT NULL GROUP BY LENGTH(lastname) ORDER BY LENGTH(lastname)",
            "jdbc"),
        containsString("\"type\": \"integer\""));

    assertThat(
        executeQuery("SELECT LENGTH('sampleName') AS length FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/length/0", equalTo(10))));
  }

  @Test
  public void operatorReplace() {
    String query = "SELECT REPLACE('elastic', 'el', 'fant') FROM " + TEST_INDEX_ACCOUNT;
    assertThat(executeQuery(query, "jdbc"), containsString("fantastic"));
  }

  @Ignore(
      "The LOCATE function is not implemented in new SQL engine. https://github"
          + ".com/opensearch-project/sql/issues/74")
  public void operatorLocate() throws IOException {
    String query =
        "SELECT LOCATE('a', lastname, 0) FROM "
            + TEST_INDEX_ACCOUNT
            + " WHERE lastname IS NOT NULL GROUP BY LOCATE('a', lastname, 0) ORDER BY LOCATE('a',"
            + " lastname, 0)";
    assertThat(executeQuery(query, "jdbc"), containsString("\"type\": \"integer\""));

    assertThat(
        executeQuery("SELECT LOCATE('a', 'sampleName', 3) AS locate FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/locate/0", equalTo(8))));
    assertThat(
        executeQuery("SELECT LOCATE('a', 'sampleName') AS locate FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/locate/0", equalTo(2))));
  }

  @Test
  public void rtrim() throws IOException {
    assertThat(
        executeQuery("SELECT RTRIM(' sampleName  ') AS rtrim FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/rtrim/0", equalTo(" sampleName"))));
  }

  @Test
  public void ltrim() throws IOException {
    assertThat(
        executeQuery("SELECT LTRIM(' sampleName  ') AS ltrim FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/ltrim/0", equalTo("sampleName  "))));
  }

  @Ignore(
      "The ASCII function is not implemented in new SQL engine. https://github"
          + ".com/opensearch-project/sql/issues/73")
  public void ascii() throws IOException {
    assertThat(
        executeQuery(
            "SELECT ASCII(lastname) FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE lastname IS NOT NULL GROUP BY ASCII(lastname) ORDER BY ASCII(lastname)"
                + " LIMIT 5",
            "jdbc"),
        containsString("\"type\": \"integer\""));
    assertThat(
        executeQuery("SELECT ASCII('sampleName') AS ascii FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/ascii/0", equalTo(115))));
  }

  /**
   * The following tests for LEFT and RIGHT are ignored because the OpenSearch client fails to parse
   * "LEFT"/"RIGHT" in the integTest
   */
  @Ignore
  @Test
  public void left() throws IOException {
    assertThat(
        executeQuery(
            "SELECT LEFT('sample', 2) AS left FROM " + TEST_INDEX_ACCOUNT + " ORDER BY left"),
        hitAny(kvString("/fields/left/0", equalTo("sa"))));
    assertThat(
        executeQuery(
            "SELECT LEFT('sample', 20) AS left FROM " + TEST_INDEX_ACCOUNT + " ORDER BY left"),
        hitAny(kvString("/fields/left/0", equalTo("sample"))));
  }

  @Ignore
  @Test
  public void right() throws IOException {
    assertThat(
        executeQuery(
            "SELECT RIGHT('elastic', 3) AS right FROM " + TEST_INDEX_ACCOUNT + " ORDER BY right"),
        hitAny(kvString("/fields/right/0", equalTo("tic"))));
    assertThat(
        executeQuery(
            "SELECT RIGHT('elastic', 20) AS right FROM " + TEST_INDEX_ACCOUNT + " ORDER BY right"),
        hitAny(kvString("/fields/right/0", equalTo("elastic"))));
  }

  @Test
  public void ifFuncShouldPassJDBC() {
    JSONObject response =
        executeJdbcRequest(
            "SELECT IF(age > 30, 'True', 'False') AS Ages FROM "
                + TEST_INDEX_ACCOUNT
                + " WHERE age IS NOT NULL GROUP BY Ages");
    assertEquals("IF(age > 30, 'True', 'False')", response.query("/schema/0/name"));
    assertEquals("Ages", response.query("/schema/0/alias"));
    assertEquals("keyword", response.query("/schema/0/type"));
  }

  @Test
  public void ifFuncWithBinaryComparisonAsConditionTest() throws IOException {
    assertThat(
        executeQuery("SELECT IF(2 > 0, 'hello', 'world') AS ifTrue FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/ifTrue/0", equalTo("hello"))));
    assertThat(
        executeQuery("SELECT IF(2 = 0, 'hello', 'world') AS ifFalse FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/ifFalse/0", equalTo("world"))));
  }

  @Test
  public void ifFuncWithBooleanExprInputAsConditionTest() throws IOException {
    assertThat(
        executeQuery("SELECT IF(true, 1, 0) AS ifBoolean FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/ifBoolean/0", equalTo(1))));
  }

  @Test
  public void ifFuncWithNullInputAsConditionTest() throws IOException {
    assertThat(
        executeQuery("SELECT IF(null, 1, 0) AS ifNull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/ifNull/0", equalTo(0))));
  }

  @Test
  public void ifnullShouldPassJDBC() throws IOException {
    JSONObject response =
        executeJdbcRequest(
            "SELECT IFNULL(lastname, 'unknown') AS name FROM "
                + TEST_INDEX_ACCOUNT
                + " GROUP BY name");
    assertEquals("IFNULL(lastname, 'unknown')", response.query("/schema/0/name"));
    assertEquals("name", response.query("/schema/0/alias"));
    assertEquals("keyword", response.query("/schema/0/type"));
  }

  @Test
  public void ifnullWithNotNullInputTest() throws IOException {
    assertThat(
        executeQuery("SELECT IFNULL('sample', 'IsNull') AS ifnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/ifnull/0", equalTo("sample"))));
  }

  @Test
  public void ifnullWithNullInputTest() throws IOException {
    assertThat(
        executeQuery("SELECT IFNULL(null, 10) AS ifnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/ifnull/0", equalTo(10))));
    assertThat(
        executeQuery("SELECT IFNULL('', 10) AS ifnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvString("/fields/ifnull/0", equalTo(""))));
  }

  @Test
  public void isnullShouldPassJDBC() {
    JSONObject response =
        executeJdbcRequest("SELECT ISNULL(lastname) AS name FROM " + TEST_INDEX_ACCOUNT);
    assertEquals("ISNULL(lastname)", response.query("/schema/0/name"));
    assertEquals("name", response.query("/schema/0/alias"));
    assertEquals("boolean", response.query("/schema/0/type"));
  }

  @Test
  public void isnullWithNotNullInputTest() throws IOException {
    assertThat(
        executeQuery("SELECT ISNULL('elastic') AS isnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/isnull/0", equalTo(0))));
    assertThat(
        executeQuery("SELECT ISNULL('') AS isnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/isnull/0", equalTo(0))));
  }

  @Test
  public void isnullWithNullInputTest() throws IOException {
    assertThat(
        executeQuery("SELECT ISNULL(null) AS isnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/isnull/0", equalTo(1))));
  }

  @Test
  public void isnullWithMathExpr() throws IOException {
    assertThat(
        executeQuery("SELECT ISNULL(1+1) AS isnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/isnull/0", equalTo(0))));
    assertThat(
        executeQuery("SELECT ISNULL(1+1*1/0) AS isnull FROM " + TEST_INDEX_ACCOUNT),
        hitAny(kvInt("/fields/isnull/0", equalTo(1))));
  }

  /**
   * Ignore this test case because painless doesn't allowlist String.split function.
   *
   * @see <a
   *     href="https://www.elastic.co/guide/en/elasticsearch/painless/7.0/painless-api-reference.html">https://www.elastic.co/guide/en/elasticsearch/painless/7.0/painless-api-reference.html</a>
   */
  @Ignore
  public void split_field() throws Exception {

    // here is a bug,csv field with spa
    String query =
        "SELECT "
            + " split(address,' ')[0],age from "
            + TestsConstants.TEST_INDEX
            + " where address is not null "
            + " limit 10  ";
  }

  @Test
  public void literal() throws Exception {
    String query = "SELECT 10 from " + TEST_INDEX_ACCOUNT + " limit 1";
    final SearchHit[] hits = query(query).getHits();
    assertThat(hits[0].getFields(), hasValue(contains(10)));
  }

  @Test
  public void literalWithDoubleValue() throws Exception {
    String query = "SELECT 10.0 from " + TEST_INDEX_ACCOUNT + " limit 1";

    final SearchHit[] hits = query(query).getHits();
    assertThat(hits[0].getFields(), hasValue(contains(10.0)));
  }

  @Test
  public void literalWithAlias() throws Exception {
    String query = "SELECT 10 as key from " + TEST_INDEX_ACCOUNT + " limit 1";
    final SearchHit[] hits = query(query).getHits();

    assertThat(hits.length, is(1));
    assertThat(hits[0].getFields(), hasEntry(is("key"), contains(10)));
  }

  @Test
  public void literalMultiField() throws Exception {
    String query = "SELECT 1, 2 from " + TEST_INDEX_ACCOUNT + " limit 1";
    final SearchHit[] hits = query(query).getHits();

    assertThat(hits.length, is(1));
    assertThat(hits[0].getFields(), allOf(hasValue(contains(1)), hasValue(contains(2))));
  }

  private SearchHits query(String query) throws IOException {
    final String rsp = executeQueryWithStringOutput(query);

    final XContentParser parser =
        new JsonXContentParser(
            NamedXContentRegistry.EMPTY,
            LoggingDeprecationHandler.INSTANCE,
            new JsonFactory().createParser(rsp));
    return SearchResponse.fromXContent(parser).getHits();
  }

  private void checkSuccessfulFieldCast(SearchHit[] hits, String field, String castType) {
    for (int i = 0; i < hits.length; ++i) {
      Assert.assertThat(hits[i].getFields(), IsMapContaining.hasKey(field));
      switch (castType) {
        case "FLOAT":
          assertTrue(hits[i].getFields().get(field).getValue() instanceof Float);
          break;
        case "DOUBLE":
          assertTrue(hits[i].getFields().get(field).getValue() instanceof Double);
          break;
        case "INT":
          assertTrue(hits[i].getFields().get(field).getValue() instanceof Integer);
          break;
        case "STRING":
          assertTrue(hits[i].getFields().get(field).getValue() instanceof String);
          break;
        case "DATETIME":
          assertTrue(hits[i].getFields().get(field).getValue() instanceof Date);
          break;
        case "LONG":
          assertTrue(hits[i].getFields().get(field).getValue() instanceof Long);
          break;
      }
    }
  }
}
